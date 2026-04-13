package com.jegly.filmcamera.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RawRes
import com.jegly.filmcamera.film.FilmTuning
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class FilmLutRenderer(
    private val context: Context,
    private val onSurfaceTextureReady: (SurfaceTexture) -> Unit,
    private val onGlError: ((String) -> Unit)? = null,
) : GLSurfaceView.Renderer {
    private val tag = "FilmLutRenderer"
    @Volatile var onFrameAvailable: (() -> Unit)? = null

    private var program = 0
    private var uCameraTexture = 0
    private var uSTMatrix = 0
    private var uLutTexture = 0
    private var uLutIntensity = 0
    private var uGrainAmount = 0
    private var uGrainSize = 0
    private var uTime = 0
    private var uLutSize = 0
    private var uLutLayout = 0
    private var uImgWidth = 0
    private var uResolution = 0
    private var aPosition = 0
    private var aTexCoord = 0

    private val textures = IntArray(2)
    var surfaceTexture: SurfaceTexture? = null
        private set
    private val stMatrix = FloatArray(16)

    @Volatile private var pendingLutResId: Int? = null
    @Volatile private var currentLutResId: Int = -1
    @Volatile private var currentLutSize: Int = 13
    @Volatile private var currentLutLayout: Int = 0
    @Volatile private var currentImgWidth: Int = 169
    private var lutLoaded = false

    @Volatile var tuning: FilmTuning = FilmTuning.DEFAULT

    private val vertexBuf: FloatBuffer
    private val texCoordBuf: FloatBuffer

    private var vpWidth = 1f
    private var vpHeight = 1f
    private val startTimeMs = System.currentTimeMillis()

    init {
        val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        vertexBuf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(verts); position(0) }
        val tex = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
        texCoordBuf = ByteBuffer.allocateDirect(tex.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(tex); position(0) }
    }

    fun setLut(@RawRes resId: Int?) { pendingLutResId = resId ?: -1 }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = buildProgram(loadAssetText("shaders/camera.vert"), loadAssetText("shaders/camera.frag"))
        if (program == 0) {
            onGlError?.invoke("GL shader program failed to compile/link")
            return
        }

        uCameraTexture = GLES20.glGetUniformLocation(program, "uCameraTexture")
        uSTMatrix      = GLES20.glGetUniformLocation(program, "uSTMatrix")
        uLutTexture    = GLES20.glGetUniformLocation(program, "uLutTexture")
        uLutIntensity  = GLES20.glGetUniformLocation(program, "uLutIntensity")
        uGrainAmount   = GLES20.glGetUniformLocation(program, "uGrainAmount")
        uGrainSize     = GLES20.glGetUniformLocation(program, "uGrainSize")
        uTime          = GLES20.glGetUniformLocation(program, "uTime")
        uLutSize       = GLES20.glGetUniformLocation(program, "uLutSize")
        uLutLayout     = GLES20.glGetUniformLocation(program, "uLutLayout")
        uImgWidth      = GLES20.glGetUniformLocation(program, "uImgWidth")
        uResolution    = GLES20.glGetUniformLocation(program, "uResolution")
        aPosition      = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoord      = GLES20.glGetAttribLocation(program, "aTexCoord")

        GLES20.glGenTextures(2, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // EXPLICITLY release old surfaceTexture if it exists to avoid "Surface abandoned"
        surfaceTexture?.release()
        val st = SurfaceTexture(textures[0])
        st.setOnFrameAvailableListener({ onFrameAvailable?.invoke() }, Handler(Looper.getMainLooper()))
        surfaceTexture = st
        onSurfaceTextureReady(st)
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        vpWidth = width.toFloat()
        vpHeight = height.toFloat()
    }

    override fun onDrawFrame(gl: GL10) {
        val st = surfaceTexture ?: return
        try {
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)
        } catch (e: Exception) { return }

        val pending = pendingLutResId
        if (pending != null && pending != currentLutResId) {
            uploadLut(pending)
            currentLutResId = pending
            pendingLutResId = null
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glUniform1i(uCameraTexture, 0)
        GLES20.glUniformMatrix4fv(uSTMatrix, 1, false, stMatrix, 0)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])
        GLES20.glUniform1i(uLutTexture, 1)

        val t = tuning
        GLES20.glUniform1f(uLutIntensity, if (lutLoaded) t.lutIntensity else 0f)
        GLES20.glUniform1f(uGrainAmount, t.grainAmount)
        GLES20.glUniform1f(uGrainSize, t.grainSize)
        GLES20.glUniform1f(uTime, (System.currentTimeMillis() - startTimeMs) / 1000f)
        GLES20.glUniform1i(uLutSize, currentLutSize)
        GLES20.glUniform1i(uLutLayout, currentLutLayout)
        GLES20.glUniform1i(uImgWidth, currentImgWidth)
        GLES20.glUniform2f(uResolution, vpWidth, vpHeight)

        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuf)
        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, 0, texCoordBuf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aTexCoord)
        checkGlError("onDrawFrame")
    }

    private fun uploadLut(@RawRes resId: Int) {
        if (resId <= 0) { lutLoaded = false; return }
        val bmp = runCatching { context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        if (bmp != null) { uploadPngLut(bmp); return }
        uploadCubeLut(resId)
    }

    private fun uploadPngLut(bmp: Bitmap) {
        val imgW = bmp.width
        val imgH = bmp.height
        val L = Math.cbrt(imgW.toDouble()).toInt()
        currentLutSize = L * L; currentLutLayout = 1; currentImgWidth = imgW
        val pixels = IntArray(imgW * imgH)
        bmp.getPixels(pixels, 0, imgW, 0, 0, imgW, imgH); bmp.recycle()
        val bytes = ByteBuffer.allocateDirect(pixels.size * 4).order(ByteOrder.nativeOrder())
        for (px in pixels) { bytes.put(((px shr 16) and 0xFF).toByte()); bytes.put(((px shr 8) and 0xFF).toByte()); bytes.put((px and 0xFF).toByte()); bytes.put(0xFF.toByte()) }
        bytes.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, imgW, imgH, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bytes)
        lutLoaded = true
    }

    private fun uploadCubeLut(@RawRes resId: Int) {
        val values = mutableListOf<Float>(); var size = 0
        runCatching { context.resources.openRawResource(resId).bufferedReader().forEachLine { line ->
            val t = line.trim()
            if (t.startsWith("LUT_3D_SIZE")) size = t.split("\\s+".toRegex()).last().toIntOrNull() ?: 0
            else if (!t.startsWith("#") && t.isNotEmpty() && !t.startsWith("TITLE") && !t.startsWith("DOMAIN")) {
                t.split("\\s+".toRegex()).take(3).forEach { part -> part.toFloatOrNull()?.let { v -> values.add(v) } }
            }
        } }.onFailure { lutLoaded = false; return }
        if (size == 0 || values.size < size * size * size * 3) { lutLoaded = false; return }
        currentLutSize = size; currentLutLayout = 0; currentImgWidth = size * size
        val w = size * size; val h = size
        val bytes = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        for (bi in 0 until size) for (gi in 0 until size) for (ri in 0 until size) {
            val idx = (ri + gi * size + bi * size * size) * 3
            bytes.put((values[idx + 0] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte())
            bytes.put((values[idx + 1] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte())
            bytes.put((values[idx + 2] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte())
            bytes.put(0xFF.toByte())
        }
        bytes.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bytes)
        lutLoaded = true
    }

    private fun buildProgram(vertSrc: String, fragSrc: String): Int {
        val vert = compileShader(GLES20.GL_VERTEX_SHADER, vertSrc)
        val frag = compileShader(GLES20.GL_FRAGMENT_SHADER, fragSrc)
        if (vert == 0 || frag == 0) return 0
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vert); GLES20.glAttachShader(prog, frag)
        GLES20.glLinkProgram(prog)
        val linked = IntArray(1); GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            val info = GLES20.glGetProgramInfoLog(prog)
            Log.e(tag, "Program link failed: $info")
            onGlError?.invoke("Program link failed: $info")
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type); GLES20.glShaderSource(shader, src); GLES20.glCompileShader(shader)
        val compiled = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            Log.e(tag, "Shader compile failed: $info")
            onGlError?.invoke("Shader compile failed: $info")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun checkGlError(stage: String) {
        val err = GLES20.glGetError()
        if (err != GLES20.GL_NO_ERROR) {
            val msg = "GL error 0x${err.toString(16)} at $stage"
            Log.e(tag, msg)
            onGlError?.invoke(msg)
        }
    }

    private fun loadAssetText(path: String): String = context.assets.open(path).bufferedReader().readText()

    fun release() {
        surfaceTexture?.release(); surfaceTexture = null
        if (textures[0] != 0) { GLES20.glDeleteTextures(2, textures, 0); textures[0] = 0; textures[1] = 0 }
    }
}
