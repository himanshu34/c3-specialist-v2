package co.nayan.canvas.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import co.nayan.canvas.interfaces.CanvasRepositoryInterface
import co.nayan.canvas.interfaces.SandboxRepositoryInterface
import co.nayan.canvas.utils.FFMPegExtraction
import co.nayan.canvas.utils.ImageCachingManager

class CanvasViewModelFactory(
    private val canvasRepository: CanvasRepositoryInterface,
    private val sandboxRepository: SandboxRepositoryInterface,
    private val isSandbox: Boolean,
    private val imageCachingManager: ImageCachingManager,
    private val videoDownloadProvider: VideoDownloadProvider,
    private val ffmPegExtraction: FFMPegExtraction
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return if (isSandbox) {
            SandboxViewModel(
                sandboxRepository,
                imageCachingManager,
                videoDownloadProvider,
                ffmPegExtraction
            )
        } else {
            CanvasViewModel(
                canvasRepository,
                imageCachingManager,
                videoDownloadProvider,
                ffmPegExtraction
            )
        } as T
    }
}