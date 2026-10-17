package com.mirror.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.firebase.messaging.FirebaseMessaging
import com.mirror.app.data.auth.BackendAuthRepository
import com.mirror.app.data.auth.SessionStore
import com.mirror.app.data.clients.ClientRepository
import com.mirror.app.data.landmark.FaceLandmarkerHelper
import com.mirror.app.data.looks.LookStore
import com.mirror.app.data.network.NetworkConfig
import com.mirror.app.data.network.createMirrorBackendApi
import com.mirror.app.data.notifications.registerDeviceToken
import com.mirror.app.data.shades.CustomShadeStore
import com.mirror.app.data.shades.ShadeRepository
import com.mirror.app.data.showroom.ShowroomRepository
import com.mirror.app.ui.showroom.ShowroomViewModel
import com.mirror.app.ui.auth.AuthViewModel
import com.mirror.app.ui.clients.ClientDirectoryViewModel
import com.mirror.app.ui.navigation.MirrorNavHost
import com.mirror.app.ui.theme.MirrorTheme
import com.mirror.app.ui.update.UpdateAvailableDialog
import com.mirror.app.ui.update.UpdateCheckViewModel
import com.mirror.app.ui.upload.UploadViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Manual construction for MVP - no DI framework wired up yet. FaceLandmarkerHelper
    // holds native resources released in UploadViewModel.onCleared(), so the ViewModel must
    // survive configuration changes rather than being rebuilt on every recomposition.
    private val uploadViewModel: UploadViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val localApi = createMirrorBackendApi(NetworkConfig.LOCAL_BASE_URL, timeoutSeconds = 3)
                val deployedApi = createMirrorBackendApi(NetworkConfig.DEPLOYED_BASE_URL, timeoutSeconds = 20)
                @Suppress("UNCHECKED_CAST")
                return UploadViewModel(
                    FaceLandmarkerHelper(applicationContext),
                    ShadeRepository(localApi, deployedApi),
                    CustomShadeStore(applicationContext),
                    lookStore = LookStore(applicationContext)
                ) as T
            }
        }
    }

    private val authViewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                // Short timeout for local (fail fast so the deployed fallback kicks in quickly
                // when there's no local dev server); longer for deployed (cold serverless starts).
                val localApi = createMirrorBackendApi(NetworkConfig.LOCAL_BASE_URL, timeoutSeconds = 3)
                val deployedApi = createMirrorBackendApi(NetworkConfig.DEPLOYED_BASE_URL, timeoutSeconds = 20)
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(BackendAuthRepository(localApi, deployedApi, SessionStore(applicationContext))) as T
            }
        }
    }

    private val clientDirectoryViewModel: ClientDirectoryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val localApi = createMirrorBackendApi(NetworkConfig.LOCAL_BASE_URL, timeoutSeconds = 3)
                val deployedApi = createMirrorBackendApi(NetworkConfig.DEPLOYED_BASE_URL, timeoutSeconds = 20)
                @Suppress("UNCHECKED_CAST")
                return ClientDirectoryViewModel(ClientRepository(localApi, deployedApi), SessionStore(applicationContext)) as T
            }
        }
    }

    // Showroom's own ViewModel/landmarker, separate from uploadViewModel so it can never alter the
    // Studio. The deployed timeout is long because Gemini analysis can take a while on a cold start.
    private val showroomViewModel: ShowroomViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val localApi = createMirrorBackendApi(NetworkConfig.LOCAL_BASE_URL, timeoutSeconds = 3)
                val deployedApi = createMirrorBackendApi(NetworkConfig.DEPLOYED_BASE_URL, timeoutSeconds = 60)
                @Suppress("UNCHECKED_CAST")
                return ShowroomViewModel(
                    FaceLandmarkerHelper(applicationContext),
                    ShowroomRepository(localApi, deployedApi, SessionStore(applicationContext))
                ) as T
            }
        }
    }

    private val updateCheckViewModel: UpdateCheckViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val localApi = createMirrorBackendApi(NetworkConfig.LOCAL_BASE_URL, timeoutSeconds = 3)
                val deployedApi = createMirrorBackendApi(NetworkConfig.DEPLOYED_BASE_URL, timeoutSeconds = 20)
                @Suppress("UNCHECKED_CAST")
                return UpdateCheckViewModel(localApi, deployedApi) as T
            }
        }
    }

    // Android 13+ requires explicit runtime consent to show any notification, including the
    // "new update available" one - requesting it doesn't block anything if the user declines,
    // since the in-app update check (UpdateCheckViewModel) still works without it.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Registers the token on every launch, not just MirrorMessagingService.onNewToken - a
        // token issued before this device's last app open wouldn't otherwise trigger onNewToken
        // again, so this is what actually gets a long-time user's device registered.
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) return@addOnCompleteListener
            val token = task.result ?: return@addOnCompleteListener
            lifecycleScope.launch {
                val localApi = createMirrorBackendApi(NetworkConfig.LOCAL_BASE_URL, timeoutSeconds = 3)
                val deployedApi = createMirrorBackendApi(NetworkConfig.DEPLOYED_BASE_URL, timeoutSeconds = 20)
                registerDeviceToken(localApi, deployedApi, token)
            }
        }

        updateCheckViewModel.checkForUpdate()

        setContent {
            MirrorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MirrorNavHost(
                        uploadViewModel = uploadViewModel,
                        authViewModel = authViewModel,
                        clientDirectoryViewModel = clientDirectoryViewModel,
                        showroomViewModel = showroomViewModel
                    )
                    val availableUpdate by updateCheckViewModel.availableUpdate.collectAsState()
                    availableUpdate?.let { update ->
                        UpdateAvailableDialog(update = update, onDismiss = { updateCheckViewModel.dismiss() })
                    }
                }
            }
        }
    }
}
