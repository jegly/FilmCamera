package com.jegly.filmcamera.gl

import android.content.Context
import android.opengl.GLSurfaceView

/**
 * GLSurfaceView configured for the film shader pipeline.
 */
class FilmGlSurfaceView(context: Context) : GLSurfaceView(context) {

    lateinit var filmRenderer: FilmLutRenderer
        private set

    fun init(renderer: FilmLutRenderer) {
        if (::filmRenderer.isInitialized) {
            return
        }
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        // Required for VideoCapture integration to record GL output
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        filmRenderer = renderer
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** Called from the renderer's onFrameAvailable to trigger a redraw */
    fun isInitialized() = ::filmRenderer.isInitialized

    fun notifyFrameAvailable() {
        requestRender()
    }
}
