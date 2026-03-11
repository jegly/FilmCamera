package com.photoncam.ui.viewfinder

import android.net.Uri
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photoncam.camera.CameraManager
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
)

@HiltViewModel
class ViewfinderViewModel @Inject constructor(
    private val cameraManager: CameraManager,
    private val imageProcessor: ImageProcessor,
    private val galleryExporter: GalleryExporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewfinderUiState())
    val uiState: StateFlow<ViewfinderUiState> = _uiState.asStateFlow()

    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        viewModelScope.launch {
            cameraManager.bindToLifecycle(lifecycleOwner, previewView)
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Camera init failed: ${e.message}") }
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
