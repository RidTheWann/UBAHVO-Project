package com.ridthewann.ubahvo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ridthewann.ubahvo.ui.ConverterScreen
import com.ridthewann.ubahvo.ui.ConverterViewModel
import com.ridthewann.ubahvo.ui.ConverterViewModelFactory
import com.ridthewann.ubahvo.ui.theme.UbahvoTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ConverterViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as UbahvoApplication
        viewModel = ViewModelProvider(this, ConverterViewModelFactory(app))
            .get(ConverterViewModel::class.java)

        readSharedText(intent)

        setContent {
            val localFilePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    // This URI is consumed by the current one-shot conversion only. Do not persist
                    // a read grant across reboots; the input is copied into private cache at start.
                    // Document providers can be slow (especially cloud-backed files), so keep all
                    // metadata IPC off the main thread to avoid freezing the UI or triggering an ANR.
                    lifecycleScope.launch {
                        val details = withContext(Dispatchers.IO) {
                            queryLocalFileDetails(uri)
                        }
                        viewModel.onLocalFileSelected(
                            uri = uri,
                            displayName = details.displayName,
                            mimeType = details.mimeType,
                            sizeBytes = details.sizeBytes
                        )
                    }
                }
            }

            UbahvoTheme {
                ConverterScreen(
                    viewModel = viewModel,
                    onOpenOutput = ::openOutput,
                    onShareOutput = ::shareOutput,
                    onPickLocalFile = {
                        localFilePicker.launch(arrayOf("video/*", "audio/*", "application/octet-stream"))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) viewModel.refreshRecentOutputs()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSharedText(intent)
    }

    private fun readSharedText(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val url = Regex("https?://\\S+")
                .find(sharedText)
                ?.value
                ?.trimEnd('.', ',', ')', ']', '>')
                ?: sharedText
            viewModel.onUrlChanged(url)
        }
    }

    private fun openOutput(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_media_player, Toast.LENGTH_LONG).show()
        }
    }

    private fun shareOutput(uri: Uri, mimeType: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share_output)))
    }

    private fun queryLocalFileDetails(uri: Uri): LocalFileDetails {
        var displayName = "media_input"
        var sizeBytes: Long? = null
        var mimeType: String? = null

        // A remote/document provider can throw or block while responding. Call this method only
        // from Dispatchers.IO, and retain useful defaults if metadata is unavailable.
        try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: displayName
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex).takeIf { it > 0L }
                    }
                }
            }
        } catch (_: Exception) {
            // Use the fallback name and unknown size if the provider cannot return metadata.
        }

        mimeType = try {
            contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
        return LocalFileDetails(displayName, sizeBytes, mimeType)
    }
    private data class LocalFileDetails(val displayName: String, val sizeBytes: Long?, val mimeType: String?)
}
