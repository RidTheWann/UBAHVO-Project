package com.ridthewann.youtubeconvert.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridthewann.youtubeconvert.core.OutputMode
import com.ridthewann.youtubeconvert.core.VideoQuality
import com.ridthewann.youtubeconvert.data.RecentOutput
import com.ridthewann.youtubeconvert.data.RecentOutputFormatting

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ConverterScreen(
    viewModel: ConverterViewModel,
    onOpenOutput: (Uri, String) -> Unit,
    onShareOutput: (Uri, String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Header()

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                "Mesin yt-dlp",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                state.ytdlpVersion?.let { "Versi tercatat: $it" }
                                    ?: "Periksa pembaruan stabil resmi agar dukungan situs tetap mutakhir.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::updateYtDlp,
                            enabled = state.engineReady && !state.isBusy && !state.isUpdatingYtDlp,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (state.isUpdatingYtDlp) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                androidx.compose.material3.Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null
                                )
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(if (state.isUpdatingYtDlp) "Memeriksa…" else "Perbarui")
                        }
                    }

                    val updateMessage = state.ytdlpUpdateMessage
                    if (updateMessage != null) {
                        Text(
                            updateMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateMessage.startsWith("Pembaruan yt-dlp gagal")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    if (!state.engineReady) {
                        Text(
                            "Mesin media tidak siap. Konversi dan pembaruan dinonaktifkan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PILIH FORMAT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FormatCard(
                        modifier = Modifier.weight(1f),
                        title = "Video MP4",
                        subtitle = "Video + audio",
                        selected = state.mode == OutputMode.MP4,
                        enabled = !state.isBusy && !state.isUpdatingYtDlp,
                        imageVector = Icons.Default.Movie,
                        onClick = { viewModel.setMode(OutputMode.MP4) }
                    )
                    FormatCard(
                        modifier = Modifier.weight(1f),
                        title = "Audio MP3",
                        subtitle = "Audio saja",
                        selected = state.mode == OutputMode.MP3,
                        enabled = !state.isBusy && !state.isUpdatingYtDlp,
                        imageVector = Icons.Default.AudioFile,
                        onClick = { viewModel.setMode(OutputMode.MP3) }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Tautan YouTube",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedTextField(
                        value = state.url,
                        onValueChange = viewModel::onUrlChanged,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isBusy && !state.isUpdatingYtDlp,
                        label = { Text("Tempel URL video") },
                        placeholder = { Text("https://youtu.be/…") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )

                    Text(
                        text = "Mendukung tautan video, Shorts, live yang tersedia, dan tautan youtu.be. Playlist tidak diproses.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.mode == OutputMode.MP4) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "Kualitas video",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            VideoQuality.entries.forEach { quality ->
                                FilterChip(
                                    selected = state.quality == quality,
                                    onClick = { viewModel.setQuality(quality) },
                                    enabled = !state.isBusy,
                                    label = { Text(quality.label) }
                                )
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Checkbox(
                        checked = state.rightsConfirmed,
                        onCheckedChange = viewModel::setRightsConfirmed,
                        enabled = !state.isBusy
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            "Saya memiliki hak atau izin untuk mengunduh konten ini",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Jangan gunakan untuk melewati DRM, konten privat/berbayar, atau mengunduh materi tanpa izin. Patuhi hukum dan ketentuan layanan sumber.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (state.videoTitle != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Tautan terdeteksi", style = MaterialTheme.typography.labelMedium)
                            Text(
                                state.videoTitle.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = viewModel::start,
                    enabled = state.engineReady && !state.isBusy &&
                        !state.isUpdatingYtDlp && state.url.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Sedang memproses…")
                    } else {
                        androidx.compose.material3.Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(9.dp))
                        Text("Mulai konversi", fontWeight = FontWeight.Bold)
                    }
                }

                if (state.isBusy) {
                    OutlinedButton(
                        onClick = viewModel::cancel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Batalkan proses")
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(state.stageMessage, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "${state.progress.toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                        )
                        if (state.etaSeconds > 0) {
                            Text(
                                "Perkiraan sisa ${formatEta(state.etaSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Text(
                        state.stageMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        state.errorMessage.orEmpty(),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (state.outputUri != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Konversi berhasil",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            state.outputName.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Lokasi: Download/YoutubeConvert",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    state.outputUri?.let { onOpenOutput(it, state.outputMimeType) }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                androidx.compose.material3.Icon(Icons.Default.OpenInNew, null)
                                Spacer(Modifier.width(7.dp))
                                Text("Buka")
                            }
                            OutlinedButton(
                                onClick = {
                                    state.outputUri?.let { onShareOutput(it, state.outputMimeType) }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                androidx.compose.material3.Icon(Icons.Default.Share, null)
                                Spacer(Modifier.width(7.dp))
                                Text("Bagikan")
                            }
                        }
                    }
                }
            }

            RecentOutputsCard(
                outputs = state.recentOutputs,
                isRefreshing = state.isRefreshingHistory,
                errorMessage = state.recentOutputsError,
                onRefresh = viewModel::refreshRecentOutputs,
                onOpenOutput = onOpenOutput,
                onShareOutput = onShareOutput
            )

            Text(
                "YT CONVERT • RIDTHEWANN",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentOutputsCard(
    outputs: List<RecentOutput>,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onOpenOutput: (Uri, String) -> Unit,
    onShareOutput: (Uri, String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        "Riwayat konversi",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Lima hasil terbaru di Downloads/YoutubeConvert",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        androidx.compose.material3.Icon(Icons.Default.Refresh, contentDescription = null)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (isRefreshing) "Memuat" else "Muat ulang")
                }
            }

            if (errorMessage != null) {
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (outputs.isEmpty()) {
                Text(
                    when {
                        isRefreshing -> "Memuat riwayat hasil…"
                        errorMessage != null -> "Riwayat belum tersedia."
                        else -> "Belum ada hasil konversi. File yang selesai akan muncul di sini."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                outputs.forEachIndexed { index, output ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                output.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${RecentOutputFormatting.formatSize(output.sizeBytes)} • " +
                                    RecentOutputFormatting.formatDateAdded(output.dateAddedSeconds),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onOpenOutput(output.uri, output.mimeType) }) {
                            androidx.compose.material3.Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "Buka ${output.displayName}"
                            )
                        }
                        IconButton(onClick = { onShareOutput(output.uri, output.mimeType) }) {
                            androidx.compose.material3.Icon(
                                Icons.Default.Share,
                                contentDescription = "Bagikan ${output.displayName}"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Y▶",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "YOUTUBE CONVERT",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp
            )
            Text(
                "Konversi media langsung di Android",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                "BETA",
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FormatCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    imageVector: ImageVector,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.6.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.material3.Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatEta(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60
    val remainingSeconds = safeSeconds % 60
    return if (minutes > 0) "${minutes}m ${remainingSeconds}s" else "${remainingSeconds}s"
}
