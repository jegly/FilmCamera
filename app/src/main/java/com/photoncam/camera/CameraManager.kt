package com.photoncam.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExposureState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.roundToInt

@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cachedProvider: ProcessCameraProvider? = null

    // ── Lens enumeration ──────────────────────────────────────────────────────

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    suspend fun getAvailableLenses(): List<LensInfo> = withContext(Dispatchers.Main) {
        val provider = getCameraProvider()
        val backCameras = provider.availableCameraInfos.filter {
            it.lensFacing == CameraSelector.LENS_FACING_BACK
        }

        val lensesWithFl = backCameras.mapNotNull { info ->
            runCatching {
                val c2info = Camera2CameraInfo.from(info)
                val id = c2info.cameraId
                val fls = c2info.getCameraCharacteristic(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                ) ?: return@runCatching null
                val fl = fls.minOrNull() ?: return@runCatching null
                LensInfo(
                    id = id,
                    label = fl.toString(),
                    selector = CameraSelector.Builder()
                        .addCameraFilter { list ->
                            list.filter { Camera2CameraInfo.from(it).cameraId == id }
                        }.build(),
                ) to fl
            }.getOrNull()
        }.sortedBy { it.second }

        if (lensesWithFl.isEmpty()) {
            return@withContext listOf(LensInfo("0", "1×", CameraSelector.DEFAULT_BACK_CAMERA))
        }

        val flValues = lensesWithFl.map { it.second }
        val median = flValues.sorted()[flValues.size / 2]
        val refFl = lensesWithFl.minByOrNull { (_, fl) -> abs(fl - median) }!!.second

        lensesWithFl.map { (lens, fl) ->
            val zoom = fl / refFl
            val label = when {
                zoom < 0.95 -> "${"%.1f".format(zoom)}×"
                zoom < 1.05 -> "1×"
                else -> "${zoom.roundToInt()}×"
            }
            lens.copy(label = label)
        }
    }

    // ── Camera binding ────────────────────────────────────────────────────────

    @androidx.camera.camera2.interop.ExperimentalCamera2Interop
    suspend fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensInfo: LensInfo? = null,
    ): Result<ExposureState> = withContext(Dispatchers.Main) {
        runCatching {
            val cameraProvider = getCameraProvider()

            // FIT_CENTER = no crop, shows full sensor with letterboxing if aspect ratios differ
            previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                        .build()
                )
                .build()

            val selector = lensInfo?.selector ?: CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, selector, preview, imageCapture!!,
            )
            camera!!.cameraInfo.exposureState
        }
    }

    // ── Exposure compensation ─────────────────────────────────────────────────

    fun getEvRange(): Range<Int>? =
        camera?.cameraInfo?.exposureState?.exposureCompensationRange

    fun getEvStep(): Double =
        camera?.cameraInfo?.exposureState?.exposureCompensationStep?.toDouble() ?: 1.0

    fun getCurrentEvIndex(): Int =
        camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: 0

    suspend fun setExposureCompensation(index: Int): Result<Int> =
        runCatching {
            val ctrl = camera?.cameraControl ?: error("Camera not bound")
            suspendCancellableCoroutine { cont ->
                val future = ctrl.setExposureCompensationIndex(index)
                future.addListener({ cont.resume(future.get()) }, cameraExecutor)
            }
        }

    // ── Capture ───────────────────────────────────────────────────────────────

    suspend fun takePicture(): Result<File> = withContext(Dispatchers.IO) {
        val capture = imageCapture
            ?: return@withContext Result.failure(IllegalStateException("Camera not bound"))

        val outputFile = createOutputFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        suspendCancellableCoroutine { cont ->
            capture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        cont.resume(Result.success(outputFile))
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cont.resumeWithException(exception)
                    }
                },
            )
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private suspend fun getCameraProvider(): ProcessCameraProvider {
        cachedProvider?.let { return it }
        return suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    val provider = future.get()
                    cachedProvider = provider
                    cont.resume(provider)
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    private fun createOutputFile(): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        return File(dir, "RAW_$ts.jpg")
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}
