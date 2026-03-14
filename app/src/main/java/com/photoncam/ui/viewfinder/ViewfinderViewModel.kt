package com.photoncam.ui.viewfinder

import android.net.Uri
import android.util.Range
import kotlinx.coroutines.Dispatchers
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoncam.camera.CameraManager
import com.photoncam.camera.LensInfo
import com.photoncam.film.FilmCatalog
import com.photoncam.film.FilmStock
import com.photoncam.processing.DateImprintBlur
import com.photoncam.processing.DateImprintColor
import com.photoncam.processing.DateImprintFont
import com.photoncam.processing.DateImprintPosition
import com.photoncam.processing.DateImprintSize
import com.photoncam.processing.DateImprintStyle
import com.photoncam.processing.ImageProcessor
import com.photoncam.utils.AppSettings
import com.photoncam.utils.GalleryExporter
import com.photoncam.utils.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewfinderUiState(
    val selectedFilm: FilmStock = FilmCatalog.default,
    val photosTaken: Int = 0,            // roll counter (0-36), resets on reload
    val totalShotsTaken: Int = 0,        // cumulative lifetime counter
    val isCapturing: Boolean = false,
    val processingCount: Int = 0,
    val lastCapturedUri: Uri? = null,
    val latestGalleryUri: Uri? = null,   // last known gallery photo (persists across sessions)
    val favoriteFilmIds: Set<String> = emptySet(),
    val dateImprintEnabled: Boolean = true,
    val dateImprintStyle: DateImprintStyle = DateImprintStyle.CLASSIC,
    val dateImprintColor: DateImprintColor = DateImprintColor.AMBER,
    val dateImprintFont: DateImprintFont = DateImprintFont.LED,
    val dateImprintSize: DateImprintSize = DateImprintSize.MEDIUM,
    val dateImprintPosition: DateImprintPosition = DateImprintPosition.BOTTOM_RIGHT,
    val dateImprintBlur: DateImprintBlur = DateImprintBlur.SOFT,
    val showDateImprintMenu: Boolean = false,
    val lightLeakEnabled: Boolean = true,
    val error: String? = null,
    val flashEnabled: Boolean = false,
    val screenFlashActive: Boolean = false,
    val showFilmSelector: Boolean = false,
    val rollFinished: Boolean = false,
    val availableLenses: List<LensInfo> = emptyList(),
    val selectedLens: LensInfo? = null,
    val evIndex: Int = 0,
    val evRange: Range<Int>? = null,
    val evStep: Double = 1.0,
)

@HiltViewModel
class ViewfinderViewModel @Inject constructor(
    private val cameraManager: CameraManager,
    private val imageProcessor: ImageProcessor,
    private val galleryExporter: GalleryExporter,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewfinderUiState())
    val uiState: StateFlow<ViewfinderUiState> = _uiState.asStateFlow()

    private var evJob: Job? = null
    private var bindJob: Job? = null
    private var savedLensId: String? = null

    // Limit concurrent image-processing jobs to 2.
    // Each job allocates ~48 MB for the bitmap; more than 2 simultaneous jobs
    // on a 256 MB heap causes OOM when photos are taken in rapid succession.
    private val processSemaphore = Semaphore(2)

    init {
        viewModelScope.launch {
            val saved = settingsRepository.settings.first()
            savedLensId = saved.selectedLensId
            _uiState.update { s ->
                s.copy(
                    selectedFilm = FilmCatalog.findById(saved.filmId) ?: FilmCatalog.default,
                    lightLeakEnabled = saved.lightLeakEnabled,
                    dateImprintEnabled = saved.dateImprintEnabled,
                    dateImprintStyle = saved.dateImprintStyle,
                    dateImprintColor = saved.dateImprintColor,
                    dateImprintFont = saved.dateImprintFont,
                    dateImprintSize = saved.dateImprintSize,
                    dateImprintPosition = saved.dateImprintPosition,
                    dateImprintBlur = saved.dateImprintBlur,
                    totalShotsTaken = saved.totalShotsTaken,
                    favoriteFilmIds = saved.favoriteFilmIds,
                    flashEnabled = saved.flashEnabled,
                )
            }
        }
        // Populate VIEW button with last gallery photo from previous sessions
        viewModelScope.launch(Dispatchers.IO) {
            val uri = galleryExporter.getLatestPhotoUri()
            if (uri != null) _uiState.update { it.copy(latestGalleryUri = uri) }
        }
    }

    private fun persistSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            settingsRepository.save(
                AppSettings(
                    filmId = state.selectedFilm.id,
                    lightLeakEnabled = state.lightLeakEnabled,
                    selectedLensId = state.selectedLens?.id,
                    evIndex = state.evIndex,
                    dateImprintEnabled = state.dateImprintEnabled,
                    dateImprintStyle = state.dateImprintStyle,
                    dateImprintColor = state.dateImprintColor,
                    dateImprintFont = state.dateImprintFont,
                    dateImprintSize = state.dateImprintSize,
                    dateImprintPosition = state.dateImprintPosition,
                    dateImprintBlur = state.dateImprintBlur,
                    totalShotsTaken = state.totalShotsTaken,
                    favoriteFilmIds = state.favoriteFilmIds,
                    flashEnabled = state.flashEnabled,
                )
            )
        }
    }

    @OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        // Zoom-ratio lens change: camera is already bound to the logical back camera.
        // Just update the zoom ratio — no rebind needed, which would stutter the preview.
        // Guard: skip this fast-path if the front camera is currently bound; in that case
        // we must rebind to DEFAULT_BACK_CAMERA before setting a zoom ratio.
        val pendingLens = _uiState.value.selectedLens
        if (pendingLens?.zoomRatio != null && cameraManager.isBound &&
            cameraManager.currentLensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
            viewModelScope.launch {
                cameraManager.setZoomRatio(pendingLens.zoomRatio)
                    .onFailure { e -> _uiState.update { it.copy(error = "Zoom failed: ${e.message}") } }
            }
            return
        }

        bindJob?.cancel()
        bindJob = viewModelScope.launch {
            // For zoom-ratio-based devices (most modern phones) the selector is always
            // DEFAULT_BACK_CAMERA. Physical-camera lenses (some OEM phones) carry a
            // specific selector and trigger a real rebind.
            val lensToUse = _uiState.value.selectedLens?.takeIf { it.zoomRatio == null }

            cameraManager.bindToLifecycle(lifecycleOwner, previewView, lensToUse)
                .onSuccess { exposureState ->
                    _uiState.update {
                        it.copy(
                            evIndex = exposureState.exposureCompensationIndex,
                            evRange = exposureState.exposureCompensationRange,
                            evStep = exposureState.exposureCompensationStep.toDouble(),
                        )
                    }

                    // Discover zoom-ratio lenses now that the camera is bound and
                    // ZoomState is available. Skip if lenses are already populated.
                    if (_uiState.value.availableLenses.isEmpty()) {
                        val zoomLenses = cameraManager.discoverZoomLenses()
                        if (zoomLenses.isNotEmpty()) {
                            val restored = savedLensId?.let { id -> zoomLenses.firstOrNull { it.id == id } }
                            val default = restored
                                ?: zoomLenses.firstOrNull { it.label == "1×" }
                                ?: zoomLenses.first()
                            _uiState.update { s ->
                                s.copy(
                                    availableLenses = zoomLenses,
                                    selectedLens = s.selectedLens ?: default,
                                )
                            }
                            // Apply saved zoom ratio (e.g. user had ultrawide selected last session)
                            _uiState.value.selectedLens?.zoomRatio?.let { ratio ->
                                cameraManager.setZoomRatio(ratio)
                            }
                        }
                    } else {
                        // Re-binding after a full rebind (e.g. switching back from front camera):
                        // re-apply the zoom ratio for the selected lens so the preview matches
                        // the highlighted pill in the lens selector.
                        _uiState.value.selectedLens?.zoomRatio?.let { ratio ->
                            cameraManager.setZoomRatio(ratio)
                        }
                    }

                    // Re-apply hardware flash mode after each bind (ImageCapture is recreated).
                    // Only set on back camera — front camera uses screen flash instead.
                    val isFront = _uiState.value.selectedLens?.id == "camera_front"
                    if (!isFront) {
                        cameraManager.setFlashEnabled(_uiState.value.flashEnabled)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Camera init failed: ${e.message}") }
                }
        }
    }

    fun selectLens(lens: LensInfo) {
        if (_uiState.value.selectedLens?.id == lens.id) return
        _uiState.update { it.copy(selectedLens = lens) }
        persistSettings()
        // Zoom-ratio lenses: the LaunchedEffect(selectedLens?.id) in ViewfinderScreen
        // calls bindCamera(), which detects isBound + zoomRatio != null and routes to
        // setZoomRatio() without rebinding. Physical lenses trigger a full rebind.
    }

    fun selectNextLens() {
        val lenses = _uiState.value.availableLenses
        if (lenses.size < 2) return
        val current = _uiState.value.selectedLens ?: return
        val idx = lenses.indexOfFirst { it.id == current.id }
        selectLens(lenses[(idx + 1) % lenses.size])
    }

    fun selectPrevLens() {
        val lenses = _uiState.value.availableLenses
        if (lenses.size < 2) return
        val current = _uiState.value.selectedLens ?: return
        val idx = lenses.indexOfFirst { it.id == current.id }
        selectLens(lenses[(idx - 1 + lenses.size) % lenses.size])
    }

    fun adjustExposure(delta: Int) {
        val state = _uiState.value
        val range = state.evRange ?: return
        val stepsPerThird = if (state.evStep > 0.0) {
            ((1.0 / 3.0) / state.evStep + 0.5).toInt().coerceAtLeast(1)
        } else 1
        val newIndex = (state.evIndex + delta * stepsPerThird).coerceIn(range.lower, range.upper)
        if (newIndex == state.evIndex) return
        _uiState.update { it.copy(evIndex = newIndex) }

        evJob?.cancel()
        evJob = viewModelScope.launch {
            cameraManager.setExposureCompensation(newIndex)
                .onSuccess { persistSettings() }
                .onFailure { e ->
                    val msg = e.message ?: ""
                    if (!msg.contains("Camera not bound") &&
                        !msg.contains("Canceled by another") &&
                        !msg.contains("OperationCanceled")) {
                        _uiState.update { it.copy(error = "EV adjust failed: $msg") }
                    }
                }
        }
    }

    fun toggleDateImprintMenu() {
        _uiState.update { it.copy(showDateImprintMenu = !it.showDateImprintMenu) }
    }

    fun setDateImprintEnabled(enabled: Boolean) {
        _uiState.update { it.copy(dateImprintEnabled = enabled) }
        persistSettings()
    }

    fun setDateImprintStyle(style: DateImprintStyle) {
        _uiState.update { it.copy(dateImprintStyle = style) }
        persistSettings()
    }

    fun setDateImprintColor(color: DateImprintColor) {
        _uiState.update { it.copy(dateImprintColor = color) }
        persistSettings()
    }

    fun setDateImprintFont(font: DateImprintFont) {
        _uiState.update { it.copy(dateImprintFont = font) }
        persistSettings()
    }

    fun setDateImprintSize(size: DateImprintSize) {
        _uiState.update { it.copy(dateImprintSize = size) }
        persistSettings()
    }

    fun setDateImprintPosition(position: DateImprintPosition) {
        _uiState.update { it.copy(dateImprintPosition = position) }
        persistSettings()
    }

    fun setDateImprintBlur(blur: DateImprintBlur) {
        _uiState.update { it.copy(dateImprintBlur = blur) }
        persistSettings()
    }

    fun toggleLightLeak() {
        _uiState.update { it.copy(lightLeakEnabled = !it.lightLeakEnabled) }
        persistSettings()
    }

    fun selectFilm(film: FilmStock) {
        _uiState.update { it.copy(selectedFilm = film, showFilmSelector = false) }
        persistSettings()
    }

    fun toggleFilmSelector() {
        _uiState.update { it.copy(showFilmSelector = !it.showFilmSelector) }
    }

    fun toggleFlash() {
        val enabled = !_uiState.value.flashEnabled
        _uiState.update { it.copy(flashEnabled = enabled) }
        val isFront = _uiState.value.selectedLens?.id == "camera_front"
        if (!isFront) cameraManager.setFlashEnabled(enabled)
        persistSettings()
    }

    fun toggleFavorite(filmId: String) {
        val current = _uiState.value.favoriteFilmIds
        val updated = if (filmId in current) current - filmId else current + filmId
        _uiState.update { it.copy(favoriteFilmIds = updated) }
        persistSettings()
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reapplyZoom() {
        val ratio = _uiState.value.selectedLens?.zoomRatio ?: return
        viewModelScope.launch {
            cameraManager.setZoomRatio(ratio)
        }
    }

    fun reloadRoll() {
        _uiState.update { it.copy(photosTaken = 0, rollFinished = false) }
    }

    fun clearLastCapture() {
        _uiState.update { it.copy(lastCapturedUri = null) }
    }

    fun capture() {
        val state = _uiState.value
        if (state.isCapturing || state.rollFinished) return

        val film = state.selectedFilm
        val dateEnabled = state.dateImprintEnabled
        val dateStyle = state.dateImprintStyle
        val dateColor = state.dateImprintColor
        val dateFont = state.dateImprintFont
        val dateSize = state.dateImprintSize
        val datePosition = state.dateImprintPosition
        val dateBlur = state.dateImprintBlur
        val lightLeak = state.lightLeakEnabled
        val isFrontCamera = state.selectedLens?.id == "camera_front"
        val useScreenFlash = state.flashEnabled && isFrontCamera

        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, error = null) }

            if (useScreenFlash) {
                _uiState.update { it.copy(screenFlashActive = true) }
                delay(120) // let the white overlay render before shutter fires
            }

            cameraManager.takePicture()
                .onSuccess { rawFile ->
                    if (useScreenFlash) _uiState.update { it.copy(screenFlashActive = false) }
                    val newTaken = _uiState.value.photosTaken + 1
                    val newTotal = _uiState.value.totalShotsTaken + 1
                    _uiState.update {
                        it.copy(
                            isCapturing = false,
                            photosTaken = newTaken,
                            totalShotsTaken = newTotal,
                            rollFinished = newTaken >= 36,
                            processingCount = it.processingCount + 1,
                        )
                    }

                    viewModelScope.launch {
                        processSemaphore.withPermit {
                            imageProcessor.process(
                                inputFile = rawFile,
                                film = film,
                                dateImprintEnabled = dateEnabled,
                                dateImprintStyle = dateStyle,
                                dateImprintColor = dateColor,
                                dateImprintFont = dateFont,
                                dateImprintSize = dateSize,
                                dateImprintPosition = datePosition,
                                dateImprintBlur = dateBlur,
                                lightLeakEnabled = lightLeak,
                            ).onSuccess { processedFile ->
                                galleryExporter.saveToGallery(processedFile)
                                    .onSuccess { uri ->
                                        _uiState.update {
                                            it.copy(
                                                lastCapturedUri = uri,
                                                latestGalleryUri = uri,
                                                processingCount = (it.processingCount - 1).coerceAtLeast(0),
                                            )
                                        }
                                        persistSettings()
                                    }
                                    .onFailure { e ->
                                        _uiState.update {
                                            it.copy(
                                                processingCount = (it.processingCount - 1).coerceAtLeast(0),
                                                error = "Save failed: ${e.message}",
                                            )
                                        }
                                    }
                            }.onFailure { e ->
                                _uiState.update {
                                    it.copy(
                                        processingCount = (it.processingCount - 1).coerceAtLeast(0),
                                        error = "Processing failed: ${e.message}",
                                    )
                                }
                            }
                            rawFile.delete()
                        }
                    }
                }
                .onFailure { e ->
                    if (useScreenFlash) _uiState.update { it.copy(screenFlashActive = false) }
                    _uiState.update { it.copy(isCapturing = false, error = "Capture failed: ${e.message}") }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.shutdown()
    }
}
