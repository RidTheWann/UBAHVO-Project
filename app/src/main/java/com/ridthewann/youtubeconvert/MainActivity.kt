package com.ridthewann.youtubeconvert

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.ridthewann.youtubeconvert.ui.ConverterScreen
import com.ridthewann.youtubeconvert.ui.ConverterViewModel
import com.ridthewann.youtubeconvert.ui.ConverterViewModelFactory
import com.ridthewann.youtubeconvert.ui.theme.YoutubeConvertTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ConverterViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as YoutubeConvertApplication
        viewModel = ViewModelProvider(this, ConverterViewModelFactory(app))
            .get(ConverterViewModel::class.java)

        readSharedText(intent)

        setContent {
            YoutubeConvertTheme {
                ConverterScreen(
                    viewModel = viewModel,
                    onOpenOutput = ::openOutput,
                    onShareOutput = ::shareOutput
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
}
