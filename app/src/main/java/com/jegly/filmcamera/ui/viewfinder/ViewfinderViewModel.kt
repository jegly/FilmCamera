package com.jegly.filmcamera.ui.viewfinder

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.graphics.SurfaceTexture
import android.util.Range
import android.view.Surface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import com.jegly.filmcamera.camera.CameraManager
import com.jegly.filmcamera.camera.CameraParams
import com.jegly.filmcamera.camera.LensInfo
import com.jegly.filmcamera.camera.LongExposureCapture
import com.jegly.filmcamera.camera.LutPreviewQuality
import com.jegly.filmcamera.camera.ShootingMode
import com.jegly.filmcamera.camera.VideoRecorder
import com.jegly.filmcamera.film.FilmCatalog
import com.jegly.filmcamera.film.FilmStock
import com.jegly.filmcamera.film.FilmTuning
import com.jegly.filmcamera.gl.FilmGlSurfaceView
import com.jegly.filmcamera.gl.FilmLutRenderer
import com.jegly.filmcamera.processing.DateImprintColor
import com.jegly.filmcamera.processing.DateImprintFont
import com.jegly.filmcamera.processing.DateImprintPosition
import com.jegly.filmcamera.processing.DateImprintSize
import com.jegly.filmcamera.processing.DateImprintStyle
import com.jegly.filmcamera.processing.PhotoProcessingWorker
import com.jegly.filmcamera.utils.AppSettings
import com.jegly.filmcamera.utils.GalleryExporter
import com.jegly.filmcamera.utils.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.math.atan2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class GridMode(val label: String) {
    OFF("OFF"), THIRDS("3x3"), SQUARE("4x4"),
    NINES("9x9"), GOLDEN("PHI"), DIAGONAL("DIAG"),
}

data class FocusPoint(val x: Float, val y: Float, val focused: Boolean = false)

data class ViewfinderUiState(
    val selectedFilm: FilmStock = FilmCatalog.default,
    val filmTuning: FilmTuning = FilmTuning.DEFAULT,
    val favoriteFilmIds: Set<String> = emptySet(),
    val showFilmSelector: Boolean = false,
    val shootingMode: ShootingMode = ShootingMode.PHOTO,
    val timerSeconds: Int = 0,
    val timerCountdown: Int = 0,
    val isTimerRunning: Boolean = false,
    val isCapturing: Boolean = false,
    val processingCount: Int = 0,
    val longExposureProgress: Pair<Int, Int>? = null,
    val saveDng: Boolean = false,
    val longExposureFrames: Int = 15,
    val isRecording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val lastCapturedUri: Uri? = null,
    val latestGalleryUri: Uri? = null,
    val dateImprintEnabled: Boolean = true,
    val dateImprintStyle: DateImprintStyle = DateImprintStyle.CLASSIC,
    val dateImprintColor: DateImprintColor = DateImprintColor.AMBER,
    val dateImprintFont: DateImprintFont = DateImprintFont.LED,
    val dateImprintSize: DateImprintSize = DateImprintSize.MEDIUM,
    val dateImprintPosition: DateImprintPosition = DateImprintPosition.BOTTOM_RIGHT,
    val dateImprintGlow: Int = 100,
    val dateImprintBlur: Int = 50,
    val dateImprintOpacity: Int = 50,
    val dateImprintBlurRepeat: Int = 3,
    val showDateImprintMenu: Boolean = false,
    val lightLeakEnabled: Boolean = true,
    val flashEnabled: Boolean = false,
    val screenFlashActive: Boolean = false,
    val availableLenses: List<LensInfo> = emptyList(),
    val selectedLens: LensInfo? = null,
    val evIndex: Int = 0,
    val evRange: Range<Int>? = null,
    val evStep: Double = 1.0,
    val mainZoomRatio: Float = 1.0f,
    val focusPoint: FocusPoint? = null,
    val focusDurationSeconds: Int = 5,
    val histogramEnabled: Boolean = false,
    val histogramData: FloatArray? = null,
    val lutPreviewEnabled: Boolean = false,
    val lutPreviewQuality: LutPreviewQuality = LutPreviewQuality.BALANCED,
    val lutPreviewBitmap: ImageBitmap? = null,
    val cameraParamsEnabled: Boolean = false,
    val cameraParams: CameraParams? = null,
    val levelEnabled: Boolean = false,
    val levelAngle: Float = 0f,
    val gridMode: GridMode = GridMode.OFF,
    val showSettingsMenu: Boolean = false,
    val showTuningSheet: Boolean = false,
    val totalShotsTaken: Int = 0,
    val error: String? = null,
)

@HiltViewModel
class ViewfinderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraManager: CameraManager,
    private val workManager: WorkManager,
    private val galleryExporter: GalleryExporter,
    private val settingsRepository: SettingsRepository,
    private val videoRecorder: VideoRecorder,
    private val longExposureCapture: LongExposureCapture,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewfinderUiState())
    val uiState: StateFlow<ViewfinderUiState> = _uiState.asStateFlow()

    private var evJob: Job? = null
    private var bindJob: Job? = null
    private var zoomJob: Job? = null
    private var focusJob: Job? = null
    private var timerJob: Job? = null
    private var lastLutToggleAtMs: Long = 0L
    private var savedLensId: String? = null
    private val reportedWorkIds = mutableSetOf<UUID>()

    private var filmRenderer: FilmLutRenderer? = null

    private val sensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val levelListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val roll = Math.toDegrees(atan2(event.values[0].toDouble(), event.values[1].toDouble())).toFloat()
            _uiState.update { it.copy(levelAngle = roll) }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    init {
        viewModelScope.launch {
            val saved = settingsRepository.settings.first()
            savedLensId = saved.selectedLensId
            val film = FilmCatalog.findById(saved.filmId) ?: FilmCatalog.default
            _uiState.update { s -> s.copy(
                selectedFilm        = film,
                filmTuning          = saved.filmTuning,
                lightLeakEnabled    = saved.lightLeakEnabled,
                dateImprintEnabled  = saved.dateImprintEnabled,
                dateImprintStyle    = saved.dateImprintStyle,
                dateImprintColor    = saved.dateImprintColor,
                dateImprintFont     = saved.dateImprintFont,
                dateImprintSize     = saved.dateImprintSize,
                dateImprintPosition = saved.dateImprintPosition,
                dateImprintGlow     = saved.dateImprintGlow,
                dateImprintBlur     = saved.dateImprintBlur,
                dateImprintOpacity  = saved.dateImprintOpacity,
                dateImprintBlurRepeat = saved.dateImprintBlurRepeat,
                totalShotsTaken     = saved.totalShotsTaken,
                favoriteFilmIds     = saved.favoriteFilmIds,
                flashEnabled        = saved.flashEnabled,
                evIndex             = saved.evIndex,
                mainZoomRatio       = saved.mainZoomRatio,
                focusDurationSeconds = saved.focusDurationSeconds,
                histogramEnabled    = saved.histogramEnabled,
                lutPreviewEnabled   = saved.lutPreviewEnabled,
                lutPreviewQuality   = saved.lutPreviewQuality,
                cameraParamsEnabled = saved.cameraParamsEnabled,
                levelEnabled        = saved.levelEnabled,
                gridMode            = runCatching { GridMode.valueOf(saved.gridMode) }.getOrDefault(GridMode.OFF),
                shootingMode        = saved.shootingMode,
                timerSeconds        = saved.timerSeconds,
                saveDng             = saved.saveDng,
                longExposureFrames  = saved.longExposureFrames,
            ) }
            cameraManager.setHistogramEnabled(saved.histogramEnabled)
            cameraManager.setLutPreviewEnabled(saved.lutPreviewEnabled)
            cameraManager.setLutPreviewQuality(saved.lutPreviewQuality)
            cameraManager.setCurrentLutResId(film.lutResId)
            cameraManager.setIsBlackAndWhite(film.isBlackAndWhite)
            cameraManager.setLutIntensity(saved.filmTuning.lutIntensity)
            cameraManager.setCameraParamsEnabled(saved.cameraParamsEnabled)
            if (saved.levelEnabled) registerLevelSensor()
            if (saved.mainZoomRatio != 1.0f) cameraManager.setZoomRatio(saved.mainZoomRatio)
        }
        viewModelScope.launch { cameraManager.histogramData.collect { data -> _uiState.update { it.copy(histogramData = data) } } }
        viewModelScope.launch { cameraManager.lutPreviewBitmap.collect { bmp -> _uiState.update { it.copy(lutPreviewBitmap = bmp) } } }
        viewModelScope.launch { cameraManager.cameraParams.collect { p -> _uiState.update { it.copy(cameraParams = p) } } }
        viewModelScope.launch(Dispatchers.IO) {
            galleryExporter.getLatestPhotoUri()?.let { uri -> _uiState.update { it.copy(latestGalleryUri = uri) } }
        }
        viewModelScope.launch {
            workManager.getWorkInfosByTagLiveData(PhotoProcessingWorker.WORK_TAG).asFlow().collect { infos ->
                _uiState.update { it.copy(processingCount = infos.count { i -> i.state == WorkInfo.State.RUNNING || i.state == WorkInfo.State.ENQUEUED }) }
                infos.filter { it.state == WorkInfo.State.SUCCEEDED && it.id !in reportedWorkIds }.forEach { info ->
                    reportedWorkIds.add(info.id)
                    info.outputData.getString(PhotoProcessingWorker.KEY_GALLERY_URI)?.let { s ->
                        val uri = Uri.parse(s)
                        _uiState.update { it.copy(lastCapturedUri = uri, latestGalleryUri = uri) }
                        persistSettings()
                    }
                }
                infos.filter { it.state == WorkInfo.State.FAILED && it.id !in reportedWorkIds }.forEach { info ->
                    reportedWorkIds.add(info.id); _uiState.update { it.copy(error = "Processing failed") }
                }
                if (infos.any { it.state.isFinished }) {
                    workManager.pruneWork()
                    reportedWorkIds.retainAll(infos.filter { !it.state.isFinished }.map { it.id }.toSet())
                }
            }
        }
        viewModelScope.launch {
            videoRecorder.state.collect { s -> _uiState.update { it.copy(isRecording = s.isRecording, recordingDurationMs = s.durationMs, error = s.error ?: it.error) } }
        }
    }

    fun attachGlSurface(glView: FilmGlSurfaceView) {
        val renderer = filmRenderer ?: FilmLutRenderer(
            context = context,
            onSurfaceTextureReady = { st -> bindCameraToSurfaceTexture(st) },
            onGlError = { message ->
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    _uiState.update { it.copy(lutPreviewEnabled = false, error = message) }
                    cameraManager.setLutPreviewEnabled(false)
                }
            },
        ).also {
            filmRenderer = it
        }

        renderer.onFrameAvailable = {
            glView.requestRender()
        }
        glView.init(renderer)
        glView.onResume()
        glView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY

        renderer.surfaceTexture?.let { bindCameraToSurfaceTexture(it) }
        glView.requestRender()

        val film = _uiState.value.selectedFilm
        renderer.setLut(film.lutResId)
        renderer.tuning = _uiState.value.filmTuning
    }

    private fun bindCameraToSurfaceTexture(surfaceTexture: SurfaceTexture) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            val provider = Preview.SurfaceProvider { request ->
                surfaceTexture.setDefaultBufferSize(request.resolution.width, request.resolution.height)
                val surface = Surface(surfaceTexture)
                request.provideSurface(surface, cameraManager.getExecutor()) {
                    surface.release()
                }
            }
            cameraManager.setPreviewSurfaceProvider(provider)
        }
    }

    fun restoreStandardPreview(previewView: PreviewView, glView: FilmGlSurfaceView? = null) {
        runCatching { glView?.onPause() }
        runCatching { filmRenderer?.onFrameAvailable = null }
        cameraManager.setPreviewSurfaceProvider(previewView.surfaceProvider)
    }

    @OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: androidx.camera.view.PreviewView) {
        bindJob?.cancel()
        bindJob = viewModelScope.launch {
            val lensToUse = _uiState.value.selectedLens?.takeIf { it.zoomRatio == null }
            cameraManager.bindToLifecycle(lifecycleOwner, previewView.surfaceProvider, lensToUse)
                .onSuccess { exposureState ->
                    val savedEv = _uiState.value.evIndex
                    val range = exposureState.exposureCompensationRange
                    val clamped = savedEv.coerceIn(range.lower, range.upper)
                    _uiState.update { it.copy(evIndex = clamped, evRange = range, evStep = exposureState.exposureCompensationStep.toDouble()) }
                    if (clamped != 0) cameraManager.setExposureCompensation(clamped)
                    
                    if (_uiState.value.availableLenses.isEmpty()) {
                        val lenses = cameraManager.discoverZoomLenses()
                        if (lenses.isNotEmpty()) {
                            val restored = savedLensId?.let { id -> lenses.firstOrNull { it.id == id } }
                            val default = restored ?: lenses.firstOrNull { it.label == "1x" } ?: lenses.first()
                            _uiState.update { s -> s.copy(availableLenses = lenses, selectedLens = s.selectedLens ?: default) }
                        }
                    }
                    cameraManager.setZoomRatio(effectiveZoomRatio())
                    val isFront = _uiState.value.selectedLens?.id == "camera_front"
                    if (!isFront) cameraManager.setFlashEnabled(_uiState.value.flashEnabled)
                }
                .onFailure { e -> _uiState.update { it.copy(error = "Camera init failed: ${e.message}") } }
        }
    }

    fun setLutIntensity(v: Int) { 
        val intensity = v / 100f
        _uiState.update { it.copy(filmTuning = it.filmTuning.copy(lutIntensity = intensity)) }
        cameraManager.setLutIntensity(intensity)
        filmRenderer?.tuning = _uiState.value.filmTuning
    }
    fun setGrainAmount(v: Int)  { 
        _uiState.update { it.copy(filmTuning = it.filmTuning.copy(grainAmount = v / 100f)) } 
        filmRenderer?.tuning = _uiState.value.filmTuning
    }
    fun setGrainSize(v: Int)    { 
        _uiState.update { it.copy(filmTuning = it.filmTuning.copy(grainSize = 1f + (v / 100f) * 3f)) } 
        filmRenderer?.tuning = _uiState.value.filmTuning
    }
    fun setLightLeakIntensity(v: Int) { 
        _uiState.update { it.copy(filmTuning = it.filmTuning.copy(lightLeakIntensity = v / 100f)) } 
        filmRenderer?.tuning = _uiState.value.filmTuning
    }
    fun commitTuning() { persistSettings() }
    fun toggleTuningSheet() { _uiState.update { it.copy(showTuningSheet = !it.showTuningSheet) } }

    fun setShootingMode(mode: ShootingMode) { if (_uiState.value.shootingMode == mode) return; _uiState.update { it.copy(shootingMode = mode) }; persistSettings() }
    fun setTimerSeconds(s: Int) { _uiState.update { it.copy(timerSeconds = s) }; persistSettings() }
    fun setSaveDng(v: Boolean)  { _uiState.update { it.copy(saveDng = v) }; persistSettings() }
    fun setLongExposureFrames(n: Int) { _uiState.update { it.copy(longExposureFrames = n.coerceIn(5, 30)) }; persistSettings() }

    fun capture() {
        val s = _uiState.value
        if (s.isCapturing || s.isTimerRunning) return
        when (s.shootingMode) {
            ShootingMode.VIDEO -> handleVideoCapture()
            ShootingMode.LONG_EXPOSURE -> captureLongExposure()
            ShootingMode.PHOTO -> if (s.timerSeconds > 0) startTimer() else capturePhoto()
        }
    }

    private fun handleVideoCapture() {
        if (_uiState.value.isRecording) {
            videoRecorder.stopRecording()
        } else {
            val vc = cameraManager.getVideoCapture()
            if (vc != null) videoRecorder.startRecording(vc)
        }
    }

    private fun startTimer() {
        val secs = _uiState.value.timerSeconds
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _uiState.update { it.copy(isTimerRunning = true, timerCountdown = secs) }
            for (i in secs downTo 1) { _uiState.update { it.copy(timerCountdown = i) }; delay(1000) }
            _uiState.update { it.copy(isTimerRunning = false, timerCountdown = 0) }
            capturePhoto()
        }
    }

    @OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun capturePhoto() {
        val state = _uiState.value
        val useScreenFlash = state.flashEnabled && state.selectedLens?.id == "camera_front"
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, error = null) }
            if (useScreenFlash) { cameraManager.setAeLock(true); _uiState.update { it.copy(screenFlashActive = true) }; delay(300) }
            cameraManager.takePicture()
                .onSuccess { rawFile ->
                    if (useScreenFlash) { cameraManager.setAeLock(false); _uiState.update { it.copy(screenFlashActive = false) } }
                    _uiState.update { it.copy(isCapturing = false, totalShotsTaken = it.totalShotsTaken + 1) }
                    persistSettings()
                    workManager.enqueue(
                        OneTimeWorkRequestBuilder<PhotoProcessingWorker>().addTag(PhotoProcessingWorker.WORK_TAG)
                            .setInputData(buildWorkerData(rawFile.absolutePath)).build()
                    )
                }
                .onFailure { e ->
                    if (useScreenFlash) { cameraManager.setAeLock(false); _uiState.update { it.copy(screenFlashActive = false) } }
                    _uiState.update { it.copy(isCapturing = false, error = "Capture failed: ${e.message}") }
                }
        }
    }

    private fun captureLongExposure() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, error = null, longExposureProgress = Pair(0, state.longExposureFrames)) }
            val ic = cameraManager.getImageCapture() ?: run { _uiState.update { it.copy(isCapturing = false, error = "Camera not ready") }; return@launch }
            longExposureCapture.capture(ic, cameraManager.getExecutor(), frameCount = state.longExposureFrames) { cur, tot ->
                _uiState.update { it.copy(longExposureProgress = Pair(cur, tot)) }
            }.onSuccess { rawFile ->
                _uiState.update { it.copy(isCapturing = false, longExposureProgress = null, totalShotsTaken = it.totalShotsTaken + 1) }
                persistSettings()
                workManager.enqueue(OneTimeWorkRequestBuilder<PhotoProcessingWorker>().addTag(PhotoProcessingWorker.WORK_TAG).setInputData(buildWorkerData(rawFile.absolutePath)).build())
            }.onFailure { e ->
                _uiState.update { it.copy(isCapturing = false, longExposureProgress = null, error = "Long exposure failed: ${e.message}") }
            }
        }
    }

    private fun buildWorkerData(path: String) = with(_uiState.value) {
        workDataOf(
            PhotoProcessingWorker.KEY_RAW_FILE_PATH to path,
            PhotoProcessingWorker.KEY_FILM_ID to selectedFilm.id,
            PhotoProcessingWorker.KEY_DATE_IMPRINT_ENABLED to dateImprintEnabled,
            PhotoProcessingWorker.KEY_DATE_STYLE to dateImprintStyle.name,
            PhotoProcessingWorker.KEY_DATE_COLOR to dateImprintColor.name,
            PhotoProcessingWorker.KEY_DATE_FONT to dateImprintFont.name,
            PhotoProcessingWorker.KEY_DATE_SIZE to dateImprintSize.name,
            PhotoProcessingWorker.KEY_DATE_POSITION to dateImprintPosition.name,
            PhotoProcessingWorker.KEY_DATE_GLOW to dateImprintGlow,
            PhotoProcessingWorker.KEY_DATE_BLUR to dateImprintBlur,
            PhotoProcessingWorker.KEY_DATE_OPACITY to dateImprintOpacity,
            PhotoProcessingWorker.KEY_DATE_BLUR_REPEAT to dateImprintBlurRepeat,
            PhotoProcessingWorker.KEY_LIGHT_LEAK_ENABLED to lightLeakEnabled,
        )
    }

    fun selectFilm(film: FilmStock) { 
        _uiState.update { it.copy(selectedFilm = film, showFilmSelector = false) }
        cameraManager.setCurrentLutResId(film.lutResId)
        cameraManager.setIsBlackAndWhite(film.isBlackAndWhite)
        filmRenderer?.setLut(film.lutResId)
        persistSettings() 
    }
    fun toggleFilmSelector() { _uiState.update { it.copy(showFilmSelector = !it.showFilmSelector) } }
    fun toggleFavorite(id: String) { val u = _uiState.value.favoriteFilmIds.let { if (id in it) it - id else it + id }; _uiState.update { it.copy(favoriteFilmIds = u) }; persistSettings() }

    fun adjustExposure(delta: Int) {
        val s = _uiState.value; val range = s.evRange ?: return
        val step = if (s.evStep > 0.0) ((1.0 / 3.0) / s.evStep + 0.5).toInt().coerceAtLeast(1) else 1
        val newIdx = (s.evIndex + delta * step).coerceIn(range.lower, range.upper)
        if (newIdx == s.evIndex) return
        _uiState.update { it.copy(evIndex = newIdx) }
        evJob?.cancel(); evJob = viewModelScope.launch {
            cameraManager.setExposureCompensation(newIdx).onSuccess { persistSettings() }
                .onFailure { e -> val m = e.message ?: ""; if (!m.contains("Camera not bound") && !m.contains("Canceled") && !m.contains("OperationCanceled")) _uiState.update { it.copy(error = "EV failed: $m") } }
        }
    }

    private fun effectiveZoomRatio() = _uiState.value.let { if (it.selectedLens?.id == "zoom_main") it.mainZoomRatio else it.selectedLens?.zoomRatio ?: 1.0f }

    fun setMainZoomRatio(ratio: Float) {
        val c = ratio.coerceIn(1.0f, 8.0f); _uiState.update { it.copy(mainZoomRatio = c) }
        zoomJob?.cancel(); zoomJob = viewModelScope.launch { cameraManager.setZoomRatio(c).onFailure { e -> val m = e.message ?: ""; if (!m.contains("OperationCanceled") && !m.contains("Camera not bound")) _uiState.update { it.copy(error = "Zoom failed: $m") } } }
    }
    fun commitMainZoomRatio() { persistSettings() }
    fun reapplyZoom()  { viewModelScope.launch { cameraManager.setZoomRatio(effectiveZoomRatio()) } }
    fun reapplyEv()    { val i = _uiState.value.evIndex; if (i == 0) return; viewModelScope.launch { cameraManager.setExposureCompensation(i) } }

    fun selectLens(lens: LensInfo) {
        if (_uiState.value.selectedLens?.id == lens.id) return
        _uiState.update { it.copy(selectedLens = lens, mainZoomRatio = if (lens.id == "zoom_main") 1.0f else it.mainZoomRatio) }
        persistSettings()
    }
    fun selectNextLens() { val l = _uiState.value.availableLenses; if (l.size < 2) return; val c = _uiState.value.selectedLens ?: return; selectLens(l[(l.indexOfFirst { it.id == c.id } + 1) % l.size]) }
    fun selectPrevLens() { val l = _uiState.value.availableLenses; if (l.size < 2) return; val c = _uiState.value.selectedLens ?: return; selectLens(l[(l.indexOfFirst { it.id == c.id } - 1 + l.size) % l.size]) }

    fun tapToFocus(x: Float, y: Float, factory: MeteringPointFactory) {
        val mp = factory.createPoint(x, y); val dur = _uiState.value.focusDurationSeconds
        focusJob?.cancel(); _uiState.update { it.copy(focusPoint = FocusPoint(x, y)) }
        focusJob = viewModelScope.launch {
            cameraManager.tapToFocus(mp, dur).onSuccess { _uiState.update { s -> s.copy(focusPoint = s.focusPoint?.copy(focused = true)) } }
            if (dur > 0) { delay(dur * 1000L); _uiState.update { it.copy(focusPoint = null) } }
        }
    }

    fun toggleFlash() { val e = !_uiState.value.flashEnabled; _uiState.update { it.copy(flashEnabled = e) }; if (_uiState.value.selectedLens?.id != "camera_front") cameraManager.setFlashEnabled(e); persistSettings() }

    fun toggleDateImprintMenu()  { _uiState.update { it.copy(showDateImprintMenu = !it.showDateImprintMenu) } }
    fun setDateImprintEnabled(v: Boolean)       { _uiState.update { it.copy(dateImprintEnabled = v) }; persistSettings() }
    fun setDateImprintStyle(v: DateImprintStyle){ _uiState.update { it.copy(dateImprintStyle = v) }; persistSettings() }
    fun setDateImprintColor(v: DateImprintColor){ _uiState.update { it.copy(dateImprintColor = v) }; persistSettings() }
    fun setDateImprintFont(v: DateImprintFont)  { _uiState.update { it.copy(dateImprintFont = v) }; persistSettings() }
    fun setDateImprintSize(v: DateImprintSize)  { _uiState.update { it.copy(dateImprintSize = v) }; persistSettings() }
    fun setDateImprintPosition(v: DateImprintPosition) { _uiState.update { it.copy(dateImprintPosition = v) }; persistSettings() }
    fun setDateImprintGlow(v: Int)    { _uiState.update { it.copy(dateImprintGlow = v.coerceIn(0,100)) }; persistSettings() }
    fun setDateImprintBlur(v: Int)    { _uiState.update { it.copy(dateImprintGlow = v.coerceIn(0,100)) }; persistSettings() }
    fun setDateImprintOpacity(v: Int) { _uiState.update { it.copy(dateImprintOpacity = v.coerceIn(0,100)) }; persistSettings() }
    fun setDateImprintBlurRepeat(v: Int) { _uiState.update { it.copy(dateImprintBlurRepeat = v.coerceIn(0,20)) }; persistSettings() }
    fun toggleLightLeak() { _uiState.update { it.copy(lightLeakEnabled = !it.lightLeakEnabled) }; persistSettings() }

    fun toggleHistogram()    { val e = !_uiState.value.histogramEnabled;    _uiState.update { it.copy(histogramEnabled = e) };    cameraManager.setHistogramEnabled(e);    persistSettings() }
    fun toggleLutPreview() {
        val now = System.currentTimeMillis()
        if (now - lastLutToggleAtMs < 350L) return
        lastLutToggleAtMs = now
        val enabled = !_uiState.value.lutPreviewEnabled
        _uiState.update { it.copy(lutPreviewEnabled = enabled) }
        cameraManager.setLutPreviewEnabled(enabled)
        persistSettings()
    }
    fun toggleCameraParams() { val e = !_uiState.value.cameraParamsEnabled; _uiState.update { it.copy(cameraParamsEnabled = e) }; cameraManager.setCameraParamsEnabled(e); persistSettings() }
    fun setLutPreviewQuality(quality: LutPreviewQuality) {
        _uiState.update { it.copy(lutPreviewQuality = quality) }
        cameraManager.setLutPreviewQuality(quality)
        persistSettings()
    }
    fun setGridMode(m: GridMode) { _uiState.update { it.copy(gridMode = m) }; persistSettings() }
    fun toggleLevel() { val e = !_uiState.value.levelEnabled; _uiState.update { it.copy(levelEnabled = e) }; if (e) registerLevelSensor() else sensorManager.unregisterListener(levelListener); persistSettings() }

    fun toggleSettingsMenu()    { _uiState.update { it.copy(showSettingsMenu = !it.showSettingsMenu) } }
    fun saveAndCloseSettings()  { persistSettings(); _uiState.update { it.copy(showSettingsMenu = false) } }
    fun dismissError()          { _uiState.update { it.copy(error = null) } }
    fun clearLastCapture()      { _uiState.update { it.copy(lastCapturedUri = null) } }
    fun setFocusDuration(s: Int){ _uiState.update { it.copy(focusDurationSeconds = s.coerceIn(0, 30)) } }

    private fun persistSettings() {
        val s = _uiState.value
        viewModelScope.launch {
            settingsRepository.save(AppSettings(
                filmId = s.selectedFilm.id, lightLeakEnabled = s.lightLeakEnabled,
                selectedLensId = s.selectedLens?.id, evIndex = s.evIndex,
                dateImprintEnabled = s.dateImprintEnabled, dateImprintStyle = s.dateImprintStyle,
                dateImprintColor = s.dateImprintColor, dateImprintFont = s.dateImprintFont,
                dateImprintSize = s.dateImprintSize, dateImprintPosition = s.dateImprintPosition,
                dateImprintGlow = s.dateImprintGlow, dateImprintBlur = s.dateImprintBlur,
                dateImprintOpacity = s.dateImprintOpacity, dateImprintBlurRepeat = s.dateImprintBlurRepeat,
                totalShotsTaken = s.totalShotsTaken, favoriteFilmIds = s.favoriteFilmIds,
                flashEnabled = s.flashEnabled, mainZoomRatio = s.mainZoomRatio,
                focusDurationSeconds = s.focusDurationSeconds, histogramEnabled = s.histogramEnabled,
                lutPreviewEnabled = s.lutPreviewEnabled,
                lutPreviewQuality = s.lutPreviewQuality,
                cameraParamsEnabled = s.cameraParamsEnabled, levelEnabled = s.levelEnabled,
                gridMode = s.gridMode.name, filmTuning = s.filmTuning,
                shootingMode = s.shootingMode, timerSeconds = s.timerSeconds,
                saveDng = s.saveDng, longExposureFrames = s.longExposureFrames,
            ))
        }
    }

    private fun registerLevelSensor() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(levelListener, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        cameraManager.shutdown()
        sensorManager.unregisterListener(levelListener)
    }
}
