package com.jegly.filmcamera.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import com.jegly.filmcamera.film.FilmTuning
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

/**
 * A CameraX [SurfaceProcessor] that grades the camera stream with a 3D film LUT
 * (plus grain and optional monochrome) entirely on the GPU. Because it sits in the
 * CameraX pipeline it applies the same look to the PREVIEW and VIDEO_CAPTURE
 * outputs simultaneously — this is what gives recorded video the film look.
 *
 * All GL work runs on a dedicated thread; public setters are thread-safe and take
 * effect on the next rendered frame. CameraX invokes [onInputSurface]/
 * [onOutputSurface] on the executor supplied to the CameraEffect — we pass a GL-thread
 * executor so those callbacks are already on the right thread.
 */
class LutSurfaceProcessor(
    private val context: Context,
) : SurfaceProcessor {

    private val tag = "LutSurfaceProcessor"

    private val glThread = HandlerThread("LutSurfaceProcessor").apply { start() }
    private val glHandler = Handler(glThread.looper)
    /** Executor that runs work on the GL thread — hand this to CameraEffect. */
    val glExecutor: Executor = Executor { glHandler.post(it) }

    private var eglCore: EglCore? = null
    private var initialized = false

    private var program = 0
    private var externalTexId = 0
    private var lutTexId = 0

    // Attribute/uniform locations
    private var aPosition = 0
    private var aTexCoord = 0
    private var uTexMatrix = 0
    private var uCameraTexture = 0
    private var uLutTexture = 0
    private var uLutIntensity = 0
    private var uGrainAmount = 0
    private var uGrainSize = 0
    private var uTime = 0
    private var uLutSize = 0
    private var uLutLayout = 0
    private var uImgWidth = 0
    private var uResolution = 0
    private var uMono = 0

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texBuffer: FloatBuffer
    private val stMatrix = FloatArray(16)
    private val texMatrix = FloatArray(16)

    private var inputSurfaceTexture: SurfaceTexture? = null
    private var inputSurface: Surface? = null

    private class OutputHolder(
        val surfaceOutput: SurfaceOutput,
        val surface: Surface,
        val eglSurface: EGLSurface,
        val size: Size,
    )
    private val outputs = mutableMapOf<SurfaceOutput, OutputHolder>()

    private val startTimeMs = System.currentTimeMillis()

    // ── Thread-safe render state ────────────────────────────────────────────────
    @Volatile private var pendingLutResId: Int? = null
    @Volatile private var pendingLutFile: File? = null
    @Volatile private var lutDirty = false
    @Volatile private var lutLoaded = false
    @Volatile private var lutSize = 2
    @Volatile private var lutLayout = 0
    @Volatile private var lutImgWidth = 4
    @Volatile private var tuning: FilmTuning = FilmTuning.DEFAULT
    @Volatile private var mono = false
    @Volatile private var released = false

    /** Set the active LUT from a raw resource (built-in film) or a file (imported .cube). */
    fun setLut(resId: Int?, file: File? = null) {
        pendingLutResId = resId
        pendingLutFile = file
        lutDirty = true
    }

    fun setTuning(t: FilmTuning) { tuning = t }
    fun setMono(isMono: Boolean) { mono = isMono }

    // ── SurfaceProcessor ─────────────────────────────────────────────────────────

    override fun onInputSurface(request: SurfaceRequest) {
        if (released) { request.willNotProvideSurface(); return }
        ensureGlInitialized()
        // Each request owns a fresh SurfaceTexture at its resolution and releases it
        // in its own result callback, so there's never a double-release.
        val st = SurfaceTexture(externalTexId)
        st.setDefaultBufferSize(request.resolution.width, request.resolution.height)
        val surface = Surface(st)
        st.setOnFrameAvailableListener({ drawFrame() }, glHandler)
        request.provideSurface(surface, glExecutor) {
            if (inputSurfaceTexture === st) inputSurfaceTexture = null
            if (inputSurface === surface) inputSurface = null
            runCatching { st.setOnFrameAvailableListener(null) }
            runCatching { surface.release() }
            runCatching { st.release() }
        }
        inputSurfaceTexture = st
        inputSurface = surface
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        if (released) { surfaceOutput.close(); return }
        ensureGlInitialized()
        val surface = surfaceOutput.getSurface(glExecutor) { _ ->
            outputs.remove(surfaceOutput)?.let { holder ->
                eglCore?.releaseSurface(holder.eglSurface)
                holder.surfaceOutput.close()
            }
        }
        val eglSurface = eglCore!!.createWindowSurface(surface)
        outputs[surfaceOutput] = OutputHolder(surfaceOutput, surface, eglSurface, surfaceOutput.size)
    }

    // ── GL ─────────────────────────────────────────────────────────────────────

    private fun ensureGlInitialized() {
        if (initialized || released) return
        val core = EglCore()
        eglCore = core
        // Make a throwaway offscreen surface current so setup GL calls are valid.
        val pbuffer = core.createPbufferSurface(1, 1)
        core.makeCurrent(pbuffer)

        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)

        aPosition     = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord     = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTexMatrix    = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uCameraTexture = GLES20.glGetUniformLocation(program, "uCameraTexture")
        uLutTexture   = GLES20.glGetUniformLocation(program, "uLutTexture")
        uLutIntensity = GLES20.glGetUniformLocation(program, "uLutIntensity")
        uGrainAmount  = GLES20.glGetUniformLocation(program, "uGrainAmount")
        uGrainSize    = GLES20.glGetUniformLocation(program, "uGrainSize")
        uTime         = GLES20.glGetUniformLocation(program, "uTime")
        uLutSize      = GLES20.glGetUniformLocation(program, "uLutSize")
        uLutLayout    = GLES20.glGetUniformLocation(program, "uLutLayout")
        uImgWidth     = GLES20.glGetUniformLocation(program, "uImgWidth")
        uResolution   = GLES20.glGetUniformLocation(program, "uResolution")
        uMono         = GLES20.glGetUniformLocation(program, "uMono")

        val tex = IntArray(2)
        GLES20.glGenTextures(2, tex, 0)
        externalTexId = tex[0]
        lutTexId = tex[1]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTexId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        vertexBuffer = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(verts); position(0) }
        val texc = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        texBuffer = ByteBuffer.allocateDirect(texc.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(texc); position(0) }

        initialized = true
    }

    private fun drawFrame() {
        val core = eglCore ?: return
        val st = inputSurfaceTexture ?: return
        if (released || outputs.isEmpty()) {
            // Still must advance the texture or the producer stalls.
            runCatching { st.updateTexImage() }
            return
        }
        try {
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)
        } catch (e: Exception) {
            return
        }

        if (lutDirty) {
            // Make current on any output so the upload has a valid context.
            core.makeCurrent(outputs.values.first().eglSurface)
            uploadPendingLut()
            lutDirty = false
        }

        val timestamp = st.timestamp
        for (holder in outputs.values) {
            core.makeCurrent(holder.eglSurface)
            GLES20.glViewport(0, 0, holder.size.width, holder.size.height)
            // CameraX supplies the correct crop/rotation/mirror transform for this output.
            holder.surfaceOutput.updateTransformMatrix(texMatrix, stMatrix)
            drawTo(texMatrix)
            core.setPresentationTime(holder.eglSurface, timestamp)
            core.swapBuffers(holder.eglSurface)
        }
    }

    private fun drawTo(matrix: FloatArray) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTexId)
        GLES20.glUniform1i(uCameraTexture, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
        GLES20.glUniform1i(uLutTexture, 1)

        GLES20.glUniformMatrix4fv(uTexMatrix, 1, false, matrix, 0)

        val t = tuning
        GLES20.glUniform1f(uLutIntensity, if (lutLoaded) t.lutIntensity else 0f)
        GLES20.glUniform1f(uGrainAmount, t.grainAmount)
        GLES20.glUniform1f(uGrainSize, t.grainSize)
        GLES20.glUniform1f(uTime, (System.currentTimeMillis() - startTimeMs) / 1000f)
        GLES20.glUniform1i(uLutSize, lutSize)
        GLES20.glUniform1i(uLutLayout, lutLayout)
        GLES20.glUniform1i(uImgWidth, lutImgWidth)
        GLES20.glUniform2f(uResolution, 1080f, 1920f)
        GLES20.glUniform1i(uMono, if (mono) 1 else 0)

        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
    }

    // ── LUT upload (raw resource or imported file) ──────────────────────────────

    private fun openLutStream(): InputStream? {
        pendingLutFile?.let { f -> return runCatching { f.inputStream() }.getOrNull() }
        val resId = pendingLutResId ?: return null
        if (resId <= 0) return null
        return runCatching { context.resources.openRawResource(resId) }.getOrNull()
    }

    private fun uploadPendingLut() {
        val resId = pendingLutResId
        val file = pendingLutFile
        if ((resId == null || resId <= 0) && file == null) { lutLoaded = false; return }

        // A PNG HaldCLUT decodes as a bitmap; a .cube is plain text.
        val bmp = openLutStream()?.use { runCatching { BitmapFactory.decodeStream(it) }.getOrNull() }
        if (bmp != null) { uploadPngLut(bmp); return }
        uploadCubeLut()
    }

    private fun uploadPngLut(bmp: Bitmap) {
        val imgW = bmp.width
        val L = Math.cbrt(imgW.toDouble()).toInt()
        lutSize = L * L; lutLayout = 1; lutImgWidth = imgW
        val pixels = IntArray(imgW * bmp.height)
        bmp.getPixels(pixels, 0, imgW, 0, 0, imgW, bmp.height); bmp.recycle()
        val bytes = ByteBuffer.allocateDirect(pixels.size * 4).order(ByteOrder.nativeOrder())
        for (px in pixels) {
            bytes.put(((px shr 16) and 0xFF).toByte())
            bytes.put(((px shr 8) and 0xFF).toByte())
            bytes.put((px and 0xFF).toByte())
            bytes.put(0xFF.toByte())
        }
        bytes.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, imgW, bmp.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bytes)
        lutLoaded = true
    }

    private fun uploadCubeLut() {
        val values = ArrayList<Float>()
        var size = 0
        val ok = openLutStream()?.use { stream ->
            runCatching {
                stream.bufferedReader().forEachLine { line ->
                    val s = line.trim()
                    if (s.startsWith("LUT_3D_SIZE")) {
                        size = s.split("\\s+".toRegex()).last().toIntOrNull() ?: 0
                    } else if (s.isNotEmpty() && !s.startsWith("#") && !s.startsWith("TITLE") && !s.startsWith("DOMAIN")) {
                        s.split("\\s+".toRegex()).take(3).forEach { p -> p.toFloatOrNull()?.let { values.add(it) } }
                    }
                }
            }.isSuccess
        } ?: false

        if (!ok || size == 0 || values.size < size * size * size * 3) { lutLoaded = false; return }
        lutSize = size; lutLayout = 0; lutImgWidth = size * size
        val w = size * size; val h = size
        val bytes = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        for (bi in 0 until size) for (gi in 0 until size) for (ri in 0 until size) {
            val idx = (ri + gi * size + bi * size * size) * 3
            bytes.put((values[idx] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte())
            bytes.put((values[idx + 1] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte())
            bytes.put((values[idx + 2] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte())
            bytes.put(0xFF.toByte())
        }
        bytes.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bytes)
        lutLoaded = true
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val linked = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] != 0) { "Program link failed: ${GLES20.glGetProgramInfoLog(p)}" }
        return p
    }

    private fun compileShader(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src); GLES20.glCompileShader(s)
        val ok = IntArray(1); GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        check(ok[0] != 0) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(s)}" }
        return s
    }

    fun release() {
        released = true
        glHandler.post {
            outputs.values.toList().forEach {
                runCatching { eglCore?.releaseSurface(it.eglSurface) }
                runCatching { it.surfaceOutput.close() }
            }
            outputs.clear()
            runCatching { inputSurfaceTexture?.setOnFrameAvailableListener(null) }
            runCatching { inputSurfaceTexture?.release() }; inputSurfaceTexture = null
            runCatching { inputSurface?.release() }; inputSurface = null
            if (program != 0) runCatching { GLES20.glDeleteProgram(program) }
            eglCore?.release(); eglCore = null
            glThread.quitSafely()
        }
    }

    companion object {
        private val VERTEX_SHADER = """
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """.trimIndent()

        private val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uCameraTexture;
            uniform sampler2D uLutTexture;
            uniform float uLutIntensity;
            uniform float uGrainAmount;
            uniform float uGrainSize;
            uniform float uTime;
            uniform int   uLutSize;
            uniform int   uLutLayout;
            uniform int   uImgWidth;
            uniform vec2  uResolution;
            uniform int   uMono;
            varying vec2 vTexCoord;

            float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }
            float grain(vec2 uv) {
                vec2 cell = floor(uv * uResolution / uGrainSize);
                float n = rand(cell + fract(uTime * 0.1));
                return (n - 0.5) * 2.0;
            }
            vec3 sampleLut(float ri, float gi, float bi) {
                float size = float(uLutSize);
                if (uLutLayout == 0) {
                    float size2 = size * size;
                    float u = (ri + gi * size + 0.5) / size2;
                    float v = (bi + 0.5) / size;
                    return texture2D(uLutTexture, vec2(u, v)).rgb;
                } else {
                    float imgW = float(uImgWidth);
                    float flat = ri + gi * size + bi * size * size;
                    float u = (mod(flat, imgW) + 0.5) / imgW;
                    float v = (floor(flat / imgW) + 0.5) / imgW;
                    return texture2D(uLutTexture, vec2(u, v)).rgb;
                }
            }
            vec3 applyLut(vec3 color) {
                float size = float(uLutSize);
                color = clamp(color, 0.0, 1.0);
                vec3 c = color * (size - 1.0);
                float ri = min(floor(c.r), size - 2.0);
                float gi = min(floor(c.g), size - 2.0);
                float bi = min(floor(c.b), size - 2.0);
                float rf = c.r - ri; float gf = c.g - gi; float bf = c.b - bi;
                vec3 v0 = mix(mix(sampleLut(ri, gi, bi), sampleLut(ri+1.0, gi, bi), rf),
                              mix(sampleLut(ri, gi+1.0, bi), sampleLut(ri+1.0, gi+1.0, bi), rf), gf);
                vec3 v1 = mix(mix(sampleLut(ri, gi, bi+1.0), sampleLut(ri+1.0, gi, bi+1.0), rf),
                              mix(sampleLut(ri, gi+1.0, bi+1.0), sampleLut(ri+1.0, gi+1.0, bi+1.0), rf), gf);
                return mix(v0, v1, bf);
            }
            void main() {
                vec3 color = texture2D(uCameraTexture, vTexCoord).rgb;
                if (uMono == 1) {
                    float l = dot(color, vec3(0.2126, 0.7152, 0.0722));
                    color = vec3(l);
                }
                if (uLutIntensity > 0.001) {
                    color = mix(color, applyLut(color), uLutIntensity);
                }
                if (uGrainAmount > 0.001) {
                    color += vec3(grain(vTexCoord) * uGrainAmount * 0.12);
                }
                gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
            }
        """.trimIndent()
    }
}
