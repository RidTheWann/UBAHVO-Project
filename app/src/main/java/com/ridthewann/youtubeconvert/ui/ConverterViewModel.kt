package com.ridthewann.youtubeconvert.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ridthewann.youtubeconvert.YoutubeConvertApplication
import com.ridthewann.youtubeconvert.core.OutputMode
import com.ridthewann.youtubeconvert.core.VideoQuality
import com.ridthewann.youtubeconvert.data.DownloadRepository
import com.ridthewann.youtubeconvert.data.RecentOutput
import com.ridthewann.youtubeconvert.data.YouTubeUrlValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ConverterUiState(
    val url: String = "",
    val mode: OutputMode = OutputMode.MP4,
    val quality: VideoQuality = VideoQuality.P720,
    val rightsConfirmed: Boolean = false,
    val isBusy: Boolean = false,
    val progress: Float = 0f,
    val etaSeconds: Long = 0L,
    val stageMessage: String = "Tempel tautan video untuk memulai.",
    val videoTitle: String? = null,
    val errorMessage: String? = null,
    val outputUri: Uri? = null,
    val outputName: String? = null,
    val outputMimeType: String = "video/mp4",
    val lastLogLine: String? = null,
    val engineReady: Boolean = false,
    val isUpdatingYtDlp: Boolean = false,
    val ytdlpVersion: String? = null,
    val ytdlpUpdateMessage: String? = null,
    val recentOutputs: List<RecentOutput> = emptyList(),
    val isRefreshingHistory: Boolean = false,
    val recentOutputsError: String? = null
)

class ConverterViewModel(
    private val application: YoutubeConvertApplication,
    private val repository: DownloadRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ConverterUiState(
            stageMessage = if (application.engineReady) {
                "Tempel tautan video untuk memulai."
            } else {
                "Fitur simpan belum tersedia di perangkat ini."
            },
            errorMessage = application.engineError,
            engineReady = application.engineReady
        )
    )
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    private var activeProcessId: String? = null
    private var activeJob: Job? = null
    private var historyRefreshJob: Job? = null
    private var refreshHistoryAgain = false

    /**
     * Load recent app-owned MediaStore outputs. If a refresh is requested while one is running,
     * queue another pass so a just-completed conversion is reflected in the list.
     */
    fun refreshRecentOutputs() {
        if (historyRefreshJob?.isActive == true) {
            refreshHistoryAgain = true
            return
        }

        historyRefreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingHistory = true, recentOutputsError = null) }
            try {
                do {
                    refreshHistoryAgain = false
                    try {
                        val outputs = repository.recentOutputs()
                        _uiState.update {
                            it.copy(recentOutputs = outputs, recentOutputsError = null)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        _uiState.update {
                            it.copy(
                                recentOutputsError = "Riwayat tidak dapat dimuat. Periksa folder Downloads/YoutubeConvert."
                            )
                        }
                    }
                } while (refreshHistoryAgain)
            } finally {
                _uiState.update { it.copy(isRefreshingHistory = false) }
                historyRefreshJob = null
            }
        }
    }

    fun onUrlChanged(value: String) {
        _uiState.update {
            it.copy(
                url = value,
                errorMessage = null,
                outputUri = null,
                outputName = null,
                videoTitle = null
            )
        }
    }

    fun setMode(mode: OutputMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                outputUri = null,
                outputName = null,
                outputMimeType = mode.mimeType,
                errorMessage = null
            )
        }
    }

    fun setQuality(quality: VideoQuality) {
        _uiState.update { it.copy(quality = quality, errorMessage = null) }
    }

    fun setRightsConfirmed(confirmed: Boolean) {
        _uiState.update { it.copy(rightsConfirmed = confirmed) }
    }

    fun updateYtDlp() {
        val snapshot = _uiState.value
        if (snapshot.isBusy || snapshot.isUpdatingYtDlp) return

        if (!application.engineReady) {
            _uiState.update {
                it.copy(
                    ytdlpUpdateMessage = application.engineError
                        ?: "Pembaruan belum tersedia di perangkat ini."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUpdatingYtDlp = true,
                    ytdlpUpdateMessage = "Memeriksa pembaruan…",
                    errorMessage = null
                )
            }

            try {
                val result = repository.updateYtDlp()
                val version = result.versionName?.takeIf { it.isNotBlank() }
                    ?: result.version?.takeIf { it.isNotBlank() }
                    ?: "versi stabil terbaru"

                _uiState.update {
                    it.copy(
                        ytdlpVersion = version,
                        ytdlpUpdateMessage = if (result.updated) {
                            "Dukungan tautan berhasil diperbarui ke $version."
                        } else {
                            "Dukungan tautan sudah menggunakan versi terbaru ($version)."
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val detail = error.message?.takeIf { it.isNotBlank() }?.take(140)
                _uiState.update {
                    it.copy(
                        ytdlpUpdateMessage = if (detail == null) {
                            "Pembaruan gagal. Periksa koneksi internet lalu coba lagi."
                        } else {
                            "Pembaruan gagal. Periksa koneksi internet lalu coba lagi."
                        }
                    )
                }
            } finally {
                _uiState.update { it.copy(isUpdatingYtDlp = false) }
            }
        }
    }

    fun start() {
        val snapshot = _uiState.value
        if (snapshot.isBusy || snapshot.isUpdatingYtDlp) return

        if (!application.engineReady) {
            _uiState.update {
                it.copy(
                    errorMessage = application.engineError
                        ?: "Fitur simpan belum siap. Tutup lalu buka kembali UBAHVO. Jika masalah berlanjut, coba lagi setelah pembaruan."
                )
            }
            return
        }

        val normalizedUrl = YouTubeUrlValidator.normalize(snapshot.url)
        if (normalizedUrl == null) {
            _uiState.update {
                it.copy(errorMessage = "Masukkan tautan YouTube yang benar, dimulai dengan https://.")
            }
            return
        }

        if (!snapshot.rightsConfirmed) {
            _uiState.update {
                it.copy(errorMessage = "Centang kotak izin sebelum menyimpan.")
            }
            return
        }

        val processId = UUID.randomUUID().toString()
        activeProcessId = processId
        activeJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isBusy = true,
                    progress = 0f,
                    etaSeconds = 0L,
                    stageMessage = "Memeriksa tautan…",
                    videoTitle = null,
                    errorMessage = null,
                    outputUri = null,
                    outputName = null,
                    lastLogLine = null
                )
            }

            try {
                val info = repository.getInfo(normalizedUrl)
                _uiState.update {
                    it.copy(
                        videoTitle = info.title ?: info.fulltitle ?: "Video YouTube",
                        stageMessage = "Memulai konversi…"
                    )
                }

                val result = repository.download(
                    url = normalizedUrl,
                    mode = snapshot.mode,
                    quality = snapshot.quality,
                    processId = processId
                ) { progress, eta, line ->
                    _uiState.update {
                        it.copy(
                            progress = progress,
                            etaSeconds = eta,
                            stageMessage = if (snapshot.mode == OutputMode.MP3) {
                                "Mengambil audio dan menyiapkan MP3…"
                            } else {
                                "Mengunduh dan menyiapkan MP4…"
                            },
                            lastLogLine = line.take(180)
                        )
                    }
                }

                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progress = 100f,
                        etaSeconds = 0L,
                        stageMessage = "Selesai — disimpan ke Downloads/YoutubeConvert.",
                        outputUri = result.uri,
                        outputName = result.displayName,
                        outputMimeType = result.mimeType,
                        errorMessage = null
                    )
                }
                refreshRecentOutputs()
            } catch (cancelled: CancellationException) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        stageMessage = "Proses dibatalkan.",
                        errorMessage = null
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        stageMessage = "Konversi gagal.",
                        errorMessage = friendlyError(error)
                    )
                }
            } finally {
                if (activeProcessId == processId) activeProcessId = null
            }
        }
    }

    fun cancel() {
        val processId = activeProcessId
        if (processId != null) repository.cancel(processId)
        activeJob?.cancel()
        activeJob = null
        activeProcessId = null
        _uiState.update {
            it.copy(
                isBusy = false,
                stageMessage = "Proses dibatalkan.",
                errorMessage = null
            )
        }
    }

    private fun friendlyError(error: Exception): String {
        val detail = error.message.orEmpty()
        return when {
            detail.contains("private", ignoreCase = true) ||
                detail.contains("members-only", ignoreCase = true) ||
                detail.contains("sign in", ignoreCase = true) ->
                "Video ini tampaknya privat atau membutuhkan akses akun. Aplikasi tidak melewati pembatasan tersebut."
            detail.contains("Unsupported URL", ignoreCase = true) ->
                "Tautan tidak didukung. Gunakan tautan ke satu video YouTube."
            detail.contains("No .mp4 output", ignoreCase = true) ||
                detail.contains("No .mp3 output", ignoreCase = true) ->
                "File hasil belum berhasil dibuat. Coba lagi; jika terulang, perbarui dukungan tautan."
            detail.isNotBlank() ->
                "Belum bisa menyimpan konten. Periksa internet dan pastikan video tersedia, lalu coba lagi."
            else ->
                "Konversi gagal. Periksa koneksi internet dan pastikan video tersedia."
        }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }
}

class ConverterViewModelFactory(
    private val application: YoutubeConvertApplication
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(ConverterViewModel::class.java)) {
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
        return ConverterViewModel(
            application = application,
            repository = DownloadRepository(application)
        ) as T
    }
}
