# CameraX
-keep class androidx.camera.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep film stock data classes
-keep class com.photoncam.film.** { *; }
