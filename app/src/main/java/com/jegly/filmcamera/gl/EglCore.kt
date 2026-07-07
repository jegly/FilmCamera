package com.jegly.filmcamera.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface

/**
 * Minimal EGL 1.4 wrapper for an OpenGL ES 2.0 context that can render to multiple
 * window surfaces (e.g. a preview surface and a video-encoder surface) sharing one
 * context. Deliberately tiny — all calls must happen on a single GL thread.
 *
 * The chosen config requests [EGL_RECORDABLE_ANDROID] so the same context can draw
 * into a MediaCodec input surface, which is how CameraX routes VIDEO_CAPTURE output.
 */
class EglCore {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var config: EGLConfig? = null

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL14 display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Unable to initialize EGL14" }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
            "Unable to find a suitable EGLConfig"
        }
        config = configs[0]

        val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        check(context != EGL14.EGL_NO_CONTEXT) { "Failed to create EGL context" }
    }

    /** Offscreen surface used to make the context current for one-time GL setup. */
    fun createPbufferSurface(width: Int, height: Int): EGLSurface {
        val attribs = intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreatePbufferSurface(display, config, attribs, 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Failed to create pbuffer surface" }
        return eglSurface
    }

    fun createWindowSurface(surface: Surface): EGLSurface {
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, surfaceAttribs, 0)
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "Failed to create window surface" }
        return eglSurface
    }

    fun makeCurrent(eglSurface: EGLSurface) {
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent failed" }
    }

    fun swapBuffers(eglSurface: EGLSurface): Boolean = EGL14.eglSwapBuffers(display, eglSurface)

    /** Set the presentation timestamp (ns) for the next swap — required for smooth video encoding. */
    fun setPresentationTime(eglSurface: EGLSurface, nsecs: Long) {
        EGLExt_setPresentationTime(display, eglSurface, nsecs)
    }

    fun releaseSurface(eglSurface: EGLSurface) {
        if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, eglSurface)
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        config = null
    }

    companion object {
        // Extension constant from eglext.h — not exposed by EGL14.
        const val EGL_RECORDABLE_ANDROID = 0x3142

        private fun EGLExt_setPresentationTime(display: EGLDisplay, surface: EGLSurface, nsecs: Long) {
            android.opengl.EGLExt.eglPresentationTimeANDROID(display, surface, nsecs)
        }
    }
}
