package com.mirror.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.mirror.app.data.auth.BackendAuthRepository
import com.mirror.app.data.auth.SessionStore
import com.mirror.app.data.landmark.FaceLandmarkerHelper
import com.mirror.app.data.network.createMirrorBackendApi
import com.mirror.app.ui.auth.AuthViewModel
import com.mirror.app.ui.navigation.MirrorNavHost
import com.mirror.app.ui.theme.MirrorTheme
import com.mirror.app.ui.upload.UploadViewModel

class MainActivity : ComponentActivity() {

    // Manual construction for MVP - no DI framework wired up yet. FaceLandmarkerHelper
    // holds native resources released in UploadViewModel.onCleared(), so the ViewModel must
    // survive configuration changes rather than being rebuilt on every recomposition.
    private val uploadViewModel: UploadViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return UploadViewModel(FaceLandmarkerHelper(applicationContext)) as T
            }
        }
    }

    private val authViewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(BackendAuthRepository(createMirrorBackendApi(), SessionStore(applicationContext))) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MirrorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MirrorNavHost(uploadViewModel = uploadViewModel, authViewModel = authViewModel)
                }
            }
        }
    }
}
