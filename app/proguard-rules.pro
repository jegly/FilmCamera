# CameraX
-keep class androidx.camera.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep film stock data classes (referenced by string ID in WorkManager data)
-keep class com.jegly.filmcamera.film.** { *; }

# Keep GL renderer — not referenced by class name but accessed via reflection in GLSurfaceView
-keep class com.jegly.filmcamera.gl.** { *; }

# Keep processing worker — WorkManager reconstructs it by class name
-keep class com.jegly.filmcamera.processing.PhotoProcessingWorker { *; }

# Keep VideoRecorder and LongExposureCapture — Hilt injects by class name
-keep class com.jegly.filmcamera.camera.VideoRecorder { *; }
-keep class com.jegly.filmcamera.camera.LongExposureCapture { *; }

# Keep enums used in DataStore (serialised by .name())
-keepclassmembers enum com.jegly.filmcamera.** { *; }

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
