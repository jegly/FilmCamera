package com.jegly.filmcamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.view.Surface
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as Camera2Manager
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.OrientationEventListener
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import androidx.camera.core.ExposureState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import com.jegly.filmcamera.processing.LutProcessor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

enum class LutPreviewQuality(
    val label: String,
    val minFrameIntervalMs: Long,
    val useScaledLut: Boolean,
) {
    FAST("FAST", 160L, true),
    BALANCED("BALANCED", 110L, true),
    HIGH("HIGH", 80L, false),
}

@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lutProcessor: LutProcessor,
) {
    private val TAG = "CameraManager"
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    @Volatile private var camera: Camera? = null
    private var cachedProvider: ProcessCameraProvider? = null

    private val _histogramData = MutableStateFlow<FloatArray?>(null)
    val histogramData: StateFlow<FloatArray?> = _histogramData.asStateFlow()
    @Volatile private var histogramEnabled = false
    private var lastHistogramMs = 0L

    private val _lutPreviewBitmap = MutableStateFlow<ImageBitmap?>(null)
    val lutPreviewBitmap: StateFlow<ImageBitmap?> = _lutPreviewBitmap.asStateFlow()
    @Volatile private var lutPreviewEnabled = false
    @Volatile private var currentLutResId: Int? = null
    @Volatile private var lutIntensity = 1.0f
    @Volatile private var isBlackAndWhite = false
    @Volatile private var lutPreviewQuality = LutPreviewQuality.BALANCED
    private var lastLutPreviewMs = 0L
    @Volatile private var lutFrameInFlight = false

    val isBound: Boolean get() = camera != null
    val currentLensFacing: Int? get() = camera?.cameraInfo?.lensFacing

    private val _cameraParams = MutableStateFlow<CameraParams?>(null)
    val cameraParams: StateFlow<CameraParams?> = _cameraParams.asStateFlow()
    @Volatile private var cameraParamsEnabled = false

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val rotation = when {
                orientation < 45 || orientation >= 315 -> Surface.ROTATION_0
                orientation < 135                      -> Surface.ROTATION_270
                orientation < 225                      -> Surface.ROTATION_180
                else                                   -> Surface.ROTATION_90
            }
            imageCapture?.targetRotation = rotation
        }
    }

    suspend fun discoverZoomLenses(): List<LensInfo> {
        val cam = camera ?: return emptyList()
        val zoomState = cam.cameraInfo.zoomState.value ?: return emptyList()
        val minRatio = zoomState.minZoomRatio

        val cam2 = context.getSystemService(Context.CAMERA_SERVICE) as Camera2Manager
        val physicalBackFls: List<Float> = withContext(Dispatchers.IO) {
            cam2.cameraIdList.mapNotNull { id ->
                runCatching {
                    val chars = cam2.getCameraCharacteristics(id)
                    if (chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK)
                        return@runCatching null
                    chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull()
                }.getOrNull()
            }.sortedBy { it }.distinctBy { "%.2f".format(it) }
        }

        val lenses = mutableListOf<LensInfo>()
        if (minRatio < 0.95f) {
            lenses.add(LensInfo("zoom_wide", "%.1f×".format(minRatio), CameraSelector.DEFAULT_BACK_CAMERA, minRatio))
        }
        lenses.add(LensInfo("zoom_main", "1×", CameraSelector.DEFAULT_BACK_CAMERA, 1.0f))

        if (physicalBackFls.size >= 3) {
            val medianFl = physicalBackFls[physicalBackFls.size / 2]
            val teleFl = physicalBackFls.last()
            if (teleFl / medianFl >= 1.5f) {
                val teleRatio = (teleFl / medianFl).roundToInt().toFloat().coerceAtLeast(2f)
                lenses.add(LensInfo("zoom_tele", "${teleRatio.toInt()}×", CameraSelector.DEFAULT_BACK_CAMERA, teleRatio))
            }
        }

        val hasFront = withContext(Dispatchers.IO) {
            cam2.cameraIdList.any { id ->
                runCatching { cam2.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }.getOrDefault(false)
            }
        }
        if (hasFront) {
            lenses.add(LensInfo("camera_front", "Front", CameraSelector.DEFAULT_FRONT_CAMERA, null))
        }
        return lenses
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    suspend fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        lensInfo: LensInfo? = null,
    ): Result<ExposureState> {
        return withContext(Dispatchers.Main) {
            runCatching {
                Log.d(TAG, "Binding to lifecycle")
                val cameraProvider = getCameraProvider()
                cameraProvider.unbindAll()
                camera = null

                val previewBuilder = Preview.Builder()
                Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        if (!cameraParamsEnabled) return
                        val exposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                        val sensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY)
                        val aperture = result.get(CaptureResult.LENS_APERTURE)

                        val shutter = if (exposureTime != null) {
                            if (exposureTime >= 1_000_000_000L) "%.1fs".format(exposureTime / 1_000_000_000.0)
                            else "1/${(1_000_000_000.0 / exposureTime.toDouble()).roundToInt()}"
                        } else ""

                        val iso = if (sensitivity != null) "ISO $sensitivity" else ""
                        val ap = if (aperture != null) "f/%.1f".format(aperture) else ""

                        _cameraParams.value = CameraParams(shutter, iso, ap)
                    }
                })
                
                val previewUseCase = previewBuilder.build().also { p ->
                    p.setSurfaceProvider(surfaceProvider)
                }
                preview = previewUseCase

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(4096, 3072), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                            .build()
                    )
                    .build()
                imageCapture = capture

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    Size(720, 960),
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .build()
                    )
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { image ->
                            try {
                                val now = System.currentTimeMillis()
                                if (histogramEnabled) {
                                    if (now - lastHistogramMs >= 100L) {
                                        lastHistogramMs = now
                                        _histogramData.value = computeLuminanceHistogram(image)
                                    }
                                }
                                if (lutPreviewEnabled && now - lastLutPreviewMs >= lutPreviewQuality.minFrameIntervalMs && !lutFrameInFlight) {
                                    lastLutPreviewMs = now
                                    lutFrameInFlight = true
                                    try {
                                        currentLutResId?.let { lutId ->
                                            val bitmap = toBitmapRGBA(image)
                                            if (bitmap != null) {
                                                var currentBitmap = bitmap
                                                val rotation = image.imageInfo.rotationDegrees
                                                if (rotation != 0) {
                                                    val matrix = Matrix()
                                                    matrix.postRotate(rotation.toFloat())
                                                    val rotated = Bitmap.createBitmap(currentBitmap, 0, 0, currentBitmap.width, currentBitmap.height, matrix, true)
                                                    currentBitmap = rotated
                                                }
                                                
                                                if (isBlackAndWhite) {
                                                    val bw = currentBitmap.toBlackAndWhite()
                                                    currentBitmap = bw
                                                }
                                                
                                                val processedBitmap = lutProcessor.applyLut(
                                                    currentBitmap,
                                                    lutId,
                                                    lutIntensity,
                                                    applyScaling = lutPreviewQuality.useScaledLut,
                                                )
                                                _lutPreviewBitmap.value = processedBitmap.asImageBitmap()
                                            }
                                        }
                                    } finally {
                                        lutFrameInFlight = false
                                    }
                                }
                            } finally {
                                image.close()
                            }
                        }
                    }

                val recorder = Recorder.Builder()
                    .setExecutor(cameraExecutor)
                    .build()
                val video = VideoCapture.withOutput(recorder)
                videoCapture = video

                val selector = lensInfo?.selector ?: CameraSelector.DEFAULT_BACK_CAMERA
                val useCases = mutableListOf<UseCase>(previewUseCase, capture, imageAnalysis, video)
                val boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, *useCases.toTypedArray())
                camera = boundCamera
                orientationListener.enable()
                boundCamera.cameraInfo.exposureState
            }
        }.also { result ->
            result.exceptionOrNull()?.let { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e(TAG, "bindToLifecycle failed: ${e.message}", e)
            }
        }
    }

    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider) {
        preview?.setSurfaceProvider(provider)
    }

    fun getVideoCapture(): VideoCapture<Recorder>? = videoCapture

    suspend fun setZoomRatio(ratio: Float): Result<Unit> {
        val result = runCatching {
            val ctrl = camera?.cameraControl ?: error("Camera not bound")
            suspendCancellableCoroutine { cont ->
                val future = ctrl.setZoomRatio(ratio)
                future.addListener({
                    if (!cont.isActive) return@addListener
                    try {
                        future.get()
                        cont.resume(Unit)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
        result.exceptionOrNull()?.takeIf { it is kotlinx.coroutines.CancellationException }?.let { throw it }
        return result
    }

    fun getEvRange(): Range<Int>? = camera?.cameraInfo?.exposureState?.exposureCompensationRange
    fun getEvStep(): Double = camera?.cameraInfo?.exposureState?.exposureCompensationStep?.toDouble() ?: 1.0
    fun getCurrentEvIndex(): Int = camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: 0

    suspend fun setExposureCompensation(index: Int): Result<Int> {
        val result = runCatching {
            val ctrl = camera?.cameraControl ?: error("Camera not bound")
            suspendCancellableCoroutine { cont ->
                val future = ctrl.setExposureCompensationIndex(index)
                future.addListener({
                    if (!cont.isActive) return@addListener
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
        result.exceptionOrNull()?.takeIf { it is kotlinx.coroutines.CancellationException }?.let { throw it }
        return result
    }

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    fun setAeLock(locked: Boolean) {
        val ctrl = camera?.cameraControl ?: return
        Camera2CameraControl.from(ctrl).captureRequestOptions =
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
                .build()
    }

    fun setFlashEnabled(enabled: Boolean) {
        imageCapture?.flashMode = if (enabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    suspend fun takePicture(): Result<File> = withContext(Dispatchers.IO) {
        val capture = imageCapture ?: return@withContext Result.failure(IllegalStateException("Camera not bound"))
        val outputFile = createOutputFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        suspendCancellableCoroutine { cont ->
            capture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    cont.resume(Result.success(outputFile))
                }
                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            })
        }
    }

    private suspend fun getCameraProvider(): ProcessCameraProvider {
        cachedProvider?.let { return it }
        return suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                val provider = future.get()
                cachedProvider = provider
                cont.resume(provider)
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun createOutputFile(): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        return File(dir, "RAW_FC_$ts.jpg")
    }

    fun setHistogramEnabled(enabled: Boolean) {
        histogramEnabled = enabled
        if (!enabled) _histogramData.value = null
    }

    fun setLutPreviewEnabled(enabled: Boolean) {
        lutPreviewEnabled = enabled
        if (!enabled) {
            _lutPreviewBitmap.value = null
        }
    }

    fun setCurrentLutResId(resId: Int?) {
        currentLutResId = resId
    }

    fun setLutIntensity(intensity: Float) {
        lutIntensity = intensity
    }

    fun setLutPreviewQuality(quality: LutPreviewQuality) {
        lutPreviewQuality = quality
    }

    fun setIsBlackAndWhite(isBw: Boolean) {
        isBlackAndWhite = isBw
    }

    fun setCameraParamsEnabled(enabled: Boolean) {
        cameraParamsEnabled = enabled
        if (!enabled) _cameraParams.value = null
    }

    private fun computeLuminanceHistogram(image: ImageProxy): FloatArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        val counts = IntArray(256)
        
        var rowStart = 0
        for (y in 0 until height) {
            var i = rowStart
            for (x in 0 until width) {
                if (image.format == PixelFormat.RGBA_8888) {
                    val r = buffer[i].toInt() and 0xFF
                    val g = buffer[i + 1].toInt() and 0xFF
                    val b = buffer[i + 2].toInt() and 0xFF
                    val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
                    counts[lum]++
                } else {
                    counts[buffer[i].toInt() and 0xFF]++
                }
                i += pixelStride
            }
            rowStart += rowStride
        }
        val max = counts.maxOrNull()?.toFloat()?.coerceAtLeast(1f) ?: 1f
        return FloatArray(256) { counts[it] / max }
    }

    private fun toBitmapRGBA(image: ImageProxy): Bitmap? {
        if (image.format == PixelFormat.RGBA_8888) {
            val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            image.planes[0].buffer.rewind()
            bitmap.copyPixelsFromBuffer(image.planes[0].buffer)
            return bitmap
        }
        
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        val options = BitmapFactory.Options().apply { inMutable = true }
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
    }

    private fun Bitmap.toBlackAndWhite(): Bitmap {
        val width = this.width
        val height = this.height
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8)  and 0xFF
            val b =  px         and 0xFF
            val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
            pixels[i] = (px and 0xFF000000.toInt()) or (lum shl 16) or (lum shl 8) or lum
        }
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    suspend fun tapToFocus(meteringPoint: MeteringPoint, durationSeconds: Int): Result<Unit> {
        val result = runCatching {
            val ctrl = camera?.cameraControl ?: error("Camera not bound")
            val action = FocusMeteringAction.Builder(meteringPoint).apply {
                if (durationSeconds > 0) setAutoCancelDuration(durationSeconds.toLong(), TimeUnit.SECONDS)
                else disableAutoCancel()
            }.build()
            suspendCancellableCoroutine { cont ->
                val future = ctrl.startFocusAndMetering(action)
                future.addListener({
                    if (!cont.isActive) return@addListener
                    try {
                        future.get()
                        cont.resume(Unit)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        }
        result.exceptionOrNull()?.takeIf { it is kotlinx.coroutines.CancellationException }?.let { throw it }
        return result
    }

    fun shutdown() {
        orientationListener.disable()
        _lutPreviewBitmap.value = null
        _histogramData.value = null
        _cameraParams.value = null
        cameraExecutor.shutdown()
        camera = null
        cachedProvider = null
    }

    fun getImageCapture(): ImageCapture? = imageCapture
    fun getExecutor(): java.util.concurrent.Executor = cameraExecutor
}
