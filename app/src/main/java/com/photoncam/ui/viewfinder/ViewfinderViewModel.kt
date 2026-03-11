package com.photoncam.ui.viewfinder

import android.net.Uri
import android.util.Range
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoncam.camera.CameraManager
import com.photoncam.camera.LensInfo
import com.photoncam.film.FilmCatalog
import com.photoncam.film.FilmStock
import com.photoncam.processing.ImageProcessor
import com.photoncam.utils.GalleryExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewfinderUiState(
    val selectedFilm: FilmStock = FilmCatalog.default,
    val framesRemaining: Int = 36,
    val isCapturing: Boolean = false,
    val isProcessing: Boolean = false,
    val lastCapturedUri: Uri? = null,
    val dateImprintEnabled: Boolean = true,
    val error: String? = null,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewfinderUiState())
    val uiState: StateFlow<ViewfinderUiState> = _uiState.asStateFlow()

    @OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            // Fetch available lenses only once
            val lenses = _uiState.value.availableLenses.ifEmpty {
                runCatching { cameraManager.getAvailableLenses() }.getOrDefault(emptyList())
                    .also { fetched ->
                        if (fetched.isNotEmpty()) {
                            val default = fetched.firstOrNull { it.label == "1×" } ?: fetched.first()
                            _uiState.update { s ->
                                s.copy(
                                    availableLenses = fetched,
                                    selectedLens = s.selectedLens ?: default,
                                )
                            }
                        }
                    }
            }

            val lensToUse = _uiState.value.selectedLens ?: lenses.firstOrNull()

            cameraManager.bindToLifecycle(lifecycleOwner, previewView, lensToUse)
                .onSuccess { exposureState ->
                    _uiState.update {
                        it.copy(
                            evIndex = exposureState.exposureCompensationIndex,
                            evRange = exposureState.exposureCompensationRange,
                            evStep = exposureState.exposureCompensationStep.toDouble(),
                        )
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
        // The LaunchedEffect(selectedLens) in ViewfinderScreen will re-call bindCamera
    }

    fun adjustExposure(delta: Int) {
        val state = _uiState.value
        val range = state.evRange ?: return
        val newIndex = (state.evIndex + delta).coerceIn(range.lower, range.upper)
        if (newIndex == state.evIndex) return
        _uiState.update { it.copy(evIndex = newIndex) }
        viewModelScope.launch {
            cameraManager.setExposureCompensation(newIndex)
                .onFailure { e ->
                    _uiState.update { it.copy(error = "EV adjust failed: ${e.message}") }
                }
        }
    }

    fun selectFilm(film: FilmStock) {
        _uiState.update { it.copy(selectedFilm = film, showFilmSelector = false) }
    }

    fun toggleFilmSelector() {
        _uiState.update { it.copy(showFilmSelector = !it.showFilmSelector) }
    }

    fun toggleDateImprint() {
        _uiState.update { it.copy(dateImprintEnabled = !it.dateImprintEnabled) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reloadRoll() {
        _uiState.update { it.copy(framesRemaining = 36, rollFinished = false) }
    }

    fun clearLastCapture() {
        _uiState.update { it.copy(lastCapturedUri = null) }
    }

    fun capture() {
        val state = _uiState.value
        if (state.isCapturing || state.isProcessing || state.rollFinished) return

        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, error = null) }

            cameraManager.takePicture()
                .onSuccess { rawFile ->
                    _uiState.update { it.copy(isCapturing = false, isProcessing = true) }

                    imageProcessor.process(
                        inputFile = rawFile,
                        film = state.selectedFilm,
                        dateImprintEnabled = state.dateImprintEnabled,
                    ).onSuccess { processedFile ->
                        galleryExporter.saveToGallery(processedFile)
                            .onSuccess { uri ->
                                val newFrames = state.framesRemaining - 1
                                _uiState.update {
                                    it.copy(
                                        isProcessing = false,
                                        lastCapturedUri = uri,
                                        framesRemaining = newFrames,
                                        rollFinished = newFrames <= 0,
                                    )
                                }
                            }
                            .onFailure { e ->
                                _uiState.update { it.copy(isProcessing = false, error = "Save failed: ${e.message}") }
                            }
                    }.onFailure { e ->
                        _uiState.update { it.copy(isProcessing = false, error = "Processing failed: ${e.message}") }
                    }

                    rawFile.delete()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isCapturing = false, error = "Capture failed: ${e.message}") }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraManager.shutdown()
    }
}
