package com.ridthewann.ubahvo.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ridthewann.ubahvo.UbahvoApplication
import com.ridthewann.ubahvo.core.InputSource
import com.ridthewann.ubahvo.core.OutputMode
import com.ridthewann.ubahvo.core.VideoQuality
import com.ridthewann.ubahvo.data.DownloadRepository
import com.ridthewann.ubahvo.data.DownloadOutputParser
import com.ridthewann.ubahvo.data.YtDlpAutoUpdatePolicy
import com.ridthewann.ubahvo.data.RecentOutput
import com.ridthewann.ubahvo.data.MediaUrlValidator
import com.ridthewann.ubahvo.data.LocalMediaConversionException
import com.ridthewann.ubahvo.data.ImageLinkException
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
    val inputSource: InputSource = InputSource.LINK,
    val localFileUri: Uri? = null,
    val localFileName: String? = null,
    val localFileMimeType: String? = null,
    val localFileSizeBytes: Long? = null,
    val mode: OutputMode = OutputMode.MP4,
    val quality: VideoQuality = VideoQuality.P720,
    val rightsConfirmed: Boolean = false,
    val isBusy: Boolean = false,
    val progress: Float = 0f,
    val isProgressIndeterminate: Boolean = false,
    val etaSeconds: Long = 0L,
    val stageMessage: String = "Tempel tautan media untuk memulai.",
    val videoTitle: String? = null,
    val errorMessage: String? = null,
    val outputUri: Uri? = null,
    val outputName: String? = null,
    val outputMimeType: String = "video/mp4",
    val lastLogLine: String? = null,
    val engineReady: Boolean = false,
    val isUpdatingYtDlp: Boolean = false,
    val isAutoUpdatingYtDlp: Boolean = false,
    val ytdlpVersion: String? = null,
    val ytdlpUpdateMessage: String? = null,
    val recentOutputs: List<RecentOutput> = emptyList(),
    val isRefreshingHistory: Boolean = false,
    val recentOutputsError: String? = null
)

class ConverterViewModel(
    private val application: UbahvoApplication,
    private val repository: DownloadRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ConverterUiState(
            stageMessage = if (application.engineReady) {
                "Tempel tautan media untuk memulai."
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
    private var autoUpdateJob: Job? = null

    init {
        autoUpdateYtDlpIfDue()
    }

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
                                recentOutputsError = "Riwayat tidak dapat dimuat. Periksa folder Download/UBAHVO."
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
                inputSource = InputSource.LINK,
                url = value,
                errorMessage = null,
                outputUri = null,
                outputName = null,
                videoTitle = null
            )
        }
    }

    fun setInputSource(source: InputSource) {
        _uiState.update { current ->
            current.copy(
                inputSource = source,
                mode = if (source == InputSource.LOCAL_FILE && current.mode == OutputMode.IMAGE) {
                    OutputMode.MP4
                } else current.mode,
                outputMimeType = if (source == InputSource.LOCAL_FILE && current.mode == OutputMode.IMAGE) {
                    OutputMode.MP4.mimeType
                } else current.outputMimeType,
                errorMessage = null,
                outputUri = null,
                outputName = null,
                videoTitle = null,
                isProgressIndeterminate = false,
                stageMessage = when (source) {
                    InputSource.LINK -> "Tempel tautan media untuk memulai."
                    InputSource.LOCAL_FILE -> if (current.localFileUri == null) {
                        "Pilih file audio atau video dari perangkat."
                    } else {
                        "File siap dikonversi."
                    }
                }
            )
        }
    }

    fun onLocalFileSelected(uri: Uri, displayName: String, mimeType: String?, sizeBytes: Long?) {
        val name = displayName.trim().takeIf { it.isNotBlank() } ?: "media_input"
        val extension = name.substringAfterLast('.', "").lowercase()
        val isAudio = mimeType.orEmpty().startsWith("audio/") ||
            extension in setOf("mp3", "aac", "m4a", "wav", "flac", "ogg", "opus", "wma")
        val mode = if (isAudio) OutputMode.MP3 else OutputMode.MP4
        _uiState.update {
            it.copy(
                inputSource = InputSource.LOCAL_FILE,
                localFileUri = uri,
                localFileName = name,
                localFileMimeType = mimeType,
                localFileSizeBytes = sizeBytes?.takeIf { size -> size > 0L },
                mode = mode,
                outputMimeType = mode.mimeType,
                errorMessage = null,
                outputUri = null,
                outputName = null,
                videoTitle = null,
                isProgressIndeterminate = false,
                stageMessage = "File siap dikonversi."
            )
        }
    }

    fun setMode(mode: OutputMode) {
        if (mode == OutputMode.IMAGE && _uiState.value.inputSource != InputSource.LINK) return
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

    /**
     * Keep supported video links current automatically without making app launch or the Save
     * action wait for a network request. Successful checks run daily; failures retry after six
     * hours. The updater installs verified bytes atomically, so an active download keeps using a
     * complete script even if the next version is installed in the background.
     */
    private fun autoUpdateYtDlpIfDue() {
        if (!application.engineReady || autoUpdateJob?.isActive == true) return

        val preferences = application.getSharedPreferences(AUTO_UPDATE_PREFERENCES, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val nextCheckAt = preferences.getLong(KEY_NEXT_AUTO_UPDATE_AT, 0L)
        if (!YtDlpAutoUpdatePolicy.isDue(now, nextCheckAt)) {
            if (_uiState.value.ytdlpUpdateMessage == null) {
                _uiState.update {
                    it.copy(ytdlpUpdateMessage = "Dukungan tautan diperiksa otomatis. Tidak perlu memperbaruinya setiap kali.")
                }
            }
            return
        }

        autoUpdateJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAutoUpdatingYtDlp = true,
                    ytdlpUpdateMessage = "Memeriksa dukungan situs media otomatis di latar belakang…"
                )
            }
            var succeeded = false
            try {
                val result = repository.updateYtDlp()
                succeeded = true
                val version = result.versionName?.takeIf { it.isNotBlank() }
                    ?: result.version?.takeIf { it.isNotBlank() }
                    ?: "versi stabil terbaru"

                _uiState.update {
                    it.copy(
                        ytdlpVersion = version,
                        ytdlpUpdateMessage = if (result.updated) {
                            "Dukungan tautan otomatis diperbarui ke $version."
                        } else {
                            "Dukungan tautan sudah terbaru ($version)."
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("UBAHVO", "Automatic yt-dlp support update failed", error)
                _uiState.update {
                    it.copy(
                        ytdlpUpdateMessage = "Pemeriksaan otomatis belum berhasil. Dukungan saat ini tetap bisa dicoba; ketuk Perbarui dukungan tautan jika tautan gagal."
                    )
                }
            } finally {
                preferences.edit()
                    .putLong(
                        KEY_NEXT_AUTO_UPDATE_AT,
                        YtDlpAutoUpdatePolicy.nextCheckAt(System.currentTimeMillis(), succeeded)
                    )
                    .apply()
                _uiState.update { it.copy(isAutoUpdatingYtDlp = false) }
                autoUpdateJob = null
            }
        }
    }

    fun updateYtDlp() {
        val snapshot = _uiState.value
        if (snapshot.isBusy || snapshot.isUpdatingYtDlp || snapshot.isAutoUpdatingYtDlp) return

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
                    ytdlpUpdateMessage = "Memeriksa dukungan tautan…",
                    errorMessage = null
                )
            }
            var succeeded = false

            try {
                val result = repository.updateYtDlp()
                succeeded = true
                val version = result.versionName?.takeIf { it.isNotBlank() }
                    ?: result.version?.takeIf { it.isNotBlank() }
                    ?: "versi stabil terbaru"

                _uiState.update {
                    it.copy(
                        ytdlpVersion = version,
                        ytdlpUpdateMessage = if (result.updated) {
                            "Dukungan tautan berhasil diperbarui ke $version."
                        } else {
                            "Dukungan tautan sudah terbaru ($version)."
                        }
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("UBAHVO", "Downloader support update failed", error)
                _uiState.update {
                    it.copy(
                        ytdlpUpdateMessage = "Pembaruan gagal. Periksa koneksi internet lalu coba lagi."
                    )
                }
            } finally {
                application.getSharedPreferences(AUTO_UPDATE_PREFERENCES, Context.MODE_PRIVATE)
                    .edit()
                    .putLong(
                        KEY_NEXT_AUTO_UPDATE_AT,
                        YtDlpAutoUpdatePolicy.nextCheckAt(System.currentTimeMillis(), succeeded)
                    )
                    .apply()
                _uiState.update { it.copy(isUpdatingYtDlp = false) }
            }
        }
    }

    fun start() {
        val snapshot = _uiState.value
        if (snapshot.isBusy || snapshot.isUpdatingYtDlp) return

        if (snapshot.mode != OutputMode.IMAGE && !application.engineReady) {
            _uiState.update {
                it.copy(
                    errorMessage = application.engineError
                        ?: "Fitur media belum siap. Tutup lalu buka kembali UBAHVO. Jika masalah berlanjut, coba lagi setelah pembaruan."
                )
            }
            return
        }

        val normalizedUrl = if (snapshot.inputSource == InputSource.LINK) {
            MediaUrlValidator.normalize(snapshot.url)
        } else null

        if (snapshot.inputSource == InputSource.LINK && normalizedUrl == null) {
            _uiState.update {
                it.copy(errorMessage = "Masukkan tautan HTTPS yang valid dari situs media yang didukung.")
            }
            return
        }
        if (snapshot.inputSource == InputSource.LOCAL_FILE && snapshot.localFileUri == null) {
            _uiState.update {
                it.copy(errorMessage = "Pilih file audio atau video sebelum memulai konversi.")
            }
            return
        }
        if (!snapshot.rightsConfirmed) {
            _uiState.update { it.copy(errorMessage = "Centang kotak izin sebelum menyimpan.") }
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
                    isProgressIndeterminate = snapshot.inputSource == InputSource.LOCAL_FILE,
                    stageMessage = when {
                        snapshot.inputSource == InputSource.LOCAL_FILE -> "Menyiapkan file media…"
                        snapshot.mode == OutputMode.IMAGE -> "Memeriksa tautan gambar…"
                        else -> "Menghubungkan ke sumber media…"
                    },
                    videoTitle = null,
                    errorMessage = null,
                    outputUri = null,
                    outputName = null,
                    lastLogLine = null
                )
            }

            try {
                var lastUiProgress = -1f
                var lastUiEta = 0L
                val onProgress: (Float, Long, String) -> Unit = { progress, eta, line ->
                    val parsedTitle = if (snapshot.inputSource == InputSource.LINK) {
                        DownloadOutputParser.videoTitleFromLine(line)
                            ?: line.takeIf {
                                snapshot.mode == OutputMode.IMAGE && it.startsWith("IMAGE_TITLE:")
                            }?.removePrefix("IMAGE_TITLE:")?.takeIf { it.isNotBlank() }
                    } else null
                    val downloadStage = if (snapshot.inputSource == InputSource.LINK && snapshot.mode != OutputMode.IMAGE) {
                        DownloadOutputParser.stageMessageFromLine(line, snapshot.mode)
                    } else null
                    val imageStage = line.takeIf {
                        snapshot.mode == OutputMode.IMAGE && it.startsWith("IMAGE_STAGE:")
                    }?.removePrefix("IMAGE_STAGE:")
                    val localStage = line.takeIf { it.startsWith("LOCAL_STAGE:") }?.removePrefix("LOCAL_STAGE:")
                    val localIndeterminate = line == "LOCAL_MODE:indeterminate" || line == "IMAGE_MODE:indeterminate"
                    val localDeterminate = line == "LOCAL_MODE:determinate" || line == "IMAGE_MODE:determinate"
                    val progressChanged = lastUiProgress < 0f ||
                        kotlin.math.abs(progress - lastUiProgress) >= 0.5f || progress >= 100f
                    val etaChanged = eta > 0L && eta != lastUiEta
                    val importantLine = parsedTitle != null || downloadStage != null || imageStage != null || localStage != null ||
                        line.startsWith("LOCAL_MODE:") || line.startsWith("IMAGE_MODE:") ||
                        line.startsWith("[download] Destination:") ||
                        line.startsWith("[Merger]") ||
                        line.contains("ERROR", ignoreCase = true)

                    if (parsedTitle != null || progressChanged || etaChanged || importantLine) {
                        if (progressChanged) lastUiProgress = progress
                        if (etaChanged) lastUiEta = eta
                        _uiState.update { current ->
                            current.copy(
                                progress = progress.coerceIn(0f, 100f),
                                etaSeconds = eta.coerceAtLeast(0L),
                                videoTitle = parsedTitle ?: current.videoTitle,
                                isProgressIndeterminate = when {
                                    localIndeterminate -> true
                                    localDeterminate -> false
                                    else -> current.isProgressIndeterminate
                                },
                                stageMessage = when {
                                    localStage != null -> localStage
                                    imageStage != null -> imageStage
                                    downloadStage != null -> downloadStage
                                    snapshot.inputSource == InputSource.LOCAL_FILE ->
                                        if (progress >= 95f) "Menyimpan hasil media…" else "Mengonversi file media…"
                                    snapshot.mode == OutputMode.IMAGE -> "Menyimpan gambar dari tautan…"
                                    parsedTitle != null && snapshot.mode == OutputMode.MP3 ->
                                        "Tautan terdeteksi — menyiapkan audio…"
                                    parsedTitle != null -> "Tautan terdeteksi — menyiapkan video…"
                                    current.videoTitle == null -> "Memeriksa tautan dan memilih kualitas…"
                                    snapshot.mode == OutputMode.MP3 -> "Mengambil audio dan menyiapkan MP3…"
                                    else -> "Mengunduh dan menyiapkan MP4…"
                                },
                                lastLogLine = when {
                                    parsedTitle != null || downloadStage != null || imageStage != null ||
                                        localStage != null || line.startsWith("LOCAL_") || line.startsWith("IMAGE_") -> null
                                    else -> line.take(180)
                                }
                            )
                        }
                    }
                }

                val result = when (snapshot.inputSource) {
                    InputSource.LINK -> repository.download(
                        url = normalizedUrl!!,
                        mode = snapshot.mode,
                        quality = snapshot.quality,
                        processId = processId,
                        onProgress = onProgress
                    )
                    InputSource.LOCAL_FILE -> repository.convertLocalFile(
                        uri = snapshot.localFileUri!!,
                        displayName = snapshot.localFileName ?: "media_input",
                        mode = snapshot.mode,
                        processId = processId,
                        onProgress = onProgress
                    )
                }

                _uiState.update {
                    it.copy(
                        isBusy = false,
                        progress = 100f,
                        etaSeconds = 0L,
                        isProgressIndeterminate = false,
                        stageMessage = if (snapshot.mode == OutputMode.IMAGE) {
                            "Gambar berhasil disimpan ke Download/UBAHVO."
                        } else {
                            "Selesai — disimpan ke Download/UBAHVO."
                        },
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
                        isProgressIndeterminate = false,
                        stageMessage = "Proses dibatalkan.",
                        errorMessage = null
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        isProgressIndeterminate = false,
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
                isProgressIndeterminate = false,
                stageMessage = "Proses dibatalkan.",
                errorMessage = null
            )
        }
    }

    private fun friendlyError(error: Exception): String {
        if (error is LocalMediaConversionException) return error.userMessage
        if (error is ImageLinkException) return error.userMessage
        val detail = error.message.orEmpty()
        return when {
            detail.contains("private", ignoreCase = true) ||
                detail.contains("members-only", ignoreCase = true) ||
                detail.contains("sign in", ignoreCase = true) ->
                "Konten ini tampaknya privat atau membutuhkan akses akun. Aplikasi tidak melewati pembatasan tersebut."
            detail.contains("Unsupported URL", ignoreCase = true) ->
                "Situs atau tautan ini belum didukung, atau sumber tidak menyediakan media yang dapat disimpan. Coba tautan langsung ke satu item media."
            detail.contains("No .mp4 output", ignoreCase = true) ||
                detail.contains("No .mp3 output", ignoreCase = true) ->
                "File hasil belum berhasil dibuat. Coba lagi saat terhubung internet; UBAHVO memeriksa pembaruan komponen secara otomatis saat aplikasi dibuka."
            detail.isNotBlank() ->
                "Belum bisa menyimpan konten. Periksa koneksi internet, pastikan tautan media aktif dan ruang penyimpanan cukup, lalu coba lagi."
            else ->
                "Konversi gagal. Periksa koneksi internet dan pastikan tautan media aktif."
        }
    }

    override fun onCleared() {
        cancel()
        autoUpdateJob?.cancel()
        autoUpdateJob = null
        super.onCleared()
    }

    companion object {
        private const val AUTO_UPDATE_PREFERENCES = "automatic_ytdlp_support_updates"
        private const val KEY_NEXT_AUTO_UPDATE_AT = "next_check_at_ms"
    }
}

class ConverterViewModelFactory(
    private val application: UbahvoApplication
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
