package com.ridthewann.ubahvo

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.ridthewann.ubahvo.ui.ConverterScreen
import com.ridthewann.ubahvo.ui.ConverterViewModel
import com.ridthewann.ubahvo.ui.ConverterViewModelFactory
import com.ridthewann.ubahvo.ui.StartupScreen
import com.ridthewann.ubahvo.ui.StartupUiState
import com.ridthewann.ubahvo.ui.theme.UbahvoTheme
import com.ridthewann.ubahvo.updates.AppUpdateChecker
import com.ridthewann.ubahvo.updates.AppUpdateCheckJobService
import com.ridthewann.ubahvo.updates.AppUpdateInfo
import com.ridthewann.ubahvo.updates.AppUpdateNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "UBAHVO-Startup"
    }

    private lateinit var viewModel: ConverterViewModel
    private val startupState = MutableStateFlow(StartupUiState())
    private val mainContentReady = MutableStateFlow(false)
    private val appUpdateState = MutableStateFlow<AppUpdateInfo?>(null)
    private var pendingPermissionUpdate: AppUpdateInfo? = null
    private var appUpdateCheckRunning = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val update = pendingPermissionUpdate
            pendingPermissionUpdate = null
            if (granted && update != null && AppUpdateNotifications.show(this, update)) {
                AppUpdateChecker.markNotified(this, update.tag)
                appUpdateState.value = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val startup = startupState.collectAsState().value
            val ready = mainContentReady.collectAsState().value
            val update = appUpdateState.collectAsState().value
            val localFilePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null && ::viewModel.isInitialized) {
                    lifecycleScope.launch {
                        val details = withContext(Dispatchers.IO) { queryLocalFileDetails(uri) }
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
                if (!ready || !::viewModel.isInitialized) {
                    StartupScreen(startup)
                } else {
                    ConverterScreen(
                        viewModel = viewModel,
                        onOpenOutput = ::openOutput,
                        onShareOutput = ::shareOutput,
                        onPickLocalFile = {
                            localFilePicker.launch(arrayOf("video/*", "audio/*", "application/octet-stream"))
                        },
                        appUpdateInfo = update,
                        onOpenAppUpdate = ::openAppUpdate,
                        onEnableUpdateNotifications = ::openNotificationSettings,
                        onDismissAppUpdate = { appUpdateState.value = null }
                    )
                }
            }
        }

        lifecycleScope.launch {
            // Let the welcome animation establish the brand before native initialization begins.
            delay(1_350)
            startupState.value = StartupUiState(
                message = "Menyiapkan komponen media…",
                showProgress = true
            )
            val app = application as UbahvoApplication
            val prepared = try {
                app.prepareStartup { message ->
                    startupState.value = StartupUiState(message = message, showProgress = true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // A non-fatal error in an optional native/runtime startup stage must not leave
                // the Activity without its main UI. Never swallow fatal VM conditions.
                if (failure is VirtualMachineError || failure is ThreadDeath) throw failure
                app.markMediaEngineUnavailable(failure)
                StartupPreparationResult(
                    engineReady = false,
                    errorMessage = app.engineError,
                    componentMessage = "Komponen media tidak tersedia; antarmuka tetap dibuka."
                )
            }
            startupState.value = StartupUiState(
                message = if (prepared.engineReady) prepared.componentMessage
                    else "Menyelesaikan antarmuka UBAHVO…",
                showProgress = true
            )
            delay(250)

            viewModel = ViewModelProvider(this@MainActivity, ConverterViewModelFactory(app))
                .get(ConverterViewModel::class.java)
            mainContentReady.value = true

            try {
                readSharedText(intent)
            } catch (error: Exception) {
                Log.w(TAG, "Could not process the launch share intent", error)
            }

            // Background update scheduling is optional and must never be able to take down
            // the foreground converter, particularly on emulators with partial JobScheduler support.
            try {
                AppUpdateCheckJobService.schedule(this@MainActivity)
            } catch (error: Exception) {
                Log.w(TAG, "Could not schedule background app-update checks", error)
            }

            try {
                checkForAppUpdates()
            } catch (error: Exception) {
                Log.w(TAG, "Could not start the foreground app-update check", error)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshRecentOutputs()
            checkForAppUpdates()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::viewModel.isInitialized) readSharedText(intent)
    }

    private fun checkForAppUpdates() {
        if (!::viewModel.isInitialized || appUpdateCheckRunning) return
        appUpdateCheckRunning = true
        lifecycleScope.launch {
            try {
                val update = withContext(Dispatchers.IO) {
                    AppUpdateChecker.checkForUpdate(this@MainActivity)
                }
                if (update != null) {
                    appUpdateState.value = update
                    if (AppUpdateNotifications.canPost(this@MainActivity)) {
                        if (AppUpdateNotifications.show(this@MainActivity, update)) {
                            AppUpdateChecker.markNotified(this@MainActivity, update.tag)
                            appUpdateState.value = null
                        }
                    } else if (
                        Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED &&
                        !getSharedPreferences(AppUpdateChecker.PREFERENCES_NAME, MODE_PRIVATE)
                            .getBoolean(AppUpdateChecker.KEY_NOTIFICATION_PERMISSION_ASKED, false)
                    ) {
                        getSharedPreferences(AppUpdateChecker.PREFERENCES_NAME, MODE_PRIVATE)
                            .edit()
                            .putBoolean(AppUpdateChecker.KEY_NOTIFICATION_PERMISSION_ASKED, true)
                            .apply()
                        pendingPermissionUpdate = update
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            } catch (_: Exception) {
                // Version checks are opportunistic: offline or rate-limited checks never block the app.
            } catch (error: Throwable) {
                if (error is VirtualMachineError || error is ThreadDeath) throw error
                Log.e(TAG, "Non-fatal JVM error during the foreground app-update check", error)
            } finally {
                appUpdateCheckRunning = false
            }
        }
    }

    private fun openAppUpdate(update: AppUpdateInfo) {
        AppUpdateChecker.markNotified(this, update.tag)
        appUpdateState.value = null
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releaseUrl)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Tidak ada browser untuk membuka halaman pembaruan.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openNotificationSettings() {
        try {
            AppUpdateNotifications.openNotificationSettings(this)
        } catch (_: Exception) {
            Toast.makeText(this, "Buka pengaturan aplikasi untuk mengaktifkan notifikasi.", Toast.LENGTH_LONG).show()
        }
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
            // Keep defaults if the document provider cannot return metadata.
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
