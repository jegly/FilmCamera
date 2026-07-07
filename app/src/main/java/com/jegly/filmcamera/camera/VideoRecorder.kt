package com.jegly.filmcamera.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class RecordingState(
    val isRecording: Boolean = false,
    val durationMs: Long = 0L,
    val error: String? = null,
)

@Singleton
class VideoRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(RecordingState())
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private var activeRecording: Recording? = null

    /**
     * Start recording to MediaStore Movies/FilmCamera.
     * The VideoCapture use case is now managed by CameraManager.
     */
    fun startRecording(videoCapture: VideoCapture<Recorder>) {
        if (_state.value.isRecording) return

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "FC_$ts.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FilmCamera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )
            .setContentValues(contentValues)
            .build()

        activeRecording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .apply {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start ->
                        _state.value = RecordingState(isRecording = true)

                    is VideoRecordEvent.Status ->
                        _state.value = _state.value.copy(
                            durationMs = event.recordingStats.recordedDurationNanos / 1_000_000,
                        )

                    is VideoRecordEvent.Finalize -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            event.outputResults.outputUri.let { uri ->
                                if (uri != android.net.Uri.EMPTY) {
                                    val cv = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                                    context.contentResolver.update(uri, cv, null, null)
                                }
                            }
                        }
                        _state.value = if (event.hasError()) {
                            RecordingState(error = "Recording failed: ${event.cause?.message}")
                        } else {
                            RecordingState()
                        }
                        activeRecording = null
                    }

                    else -> Unit
                }
            }
    }

    fun stopRecording() {
        activeRecording?.stop()
    }

    fun isRecording(): Boolean = _state.value.isRecording
}
