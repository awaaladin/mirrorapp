package com.mirror.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mirror.app.ui.auth.AuthSessionState
import com.mirror.app.ui.auth.AuthViewModel
import com.mirror.app.ui.auth.LoginScreen
import com.mirror.app.ui.auth.SignUpScreen
import com.mirror.app.ui.beforeafter.BeforeAfterScreen
import com.mirror.app.ui.clients.ClientDirectoryScreen
import com.mirror.app.ui.clients.ClientDirectoryViewModel
import com.mirror.app.ui.components.MirrorBottomNav
import com.mirror.app.ui.components.MirrorTopBar
import com.mirror.app.domain.model.AccountType
import com.mirror.app.ui.dashboard.DashboardScreen
import com.mirror.app.ui.editor.EditorScreen
import com.mirror.app.ui.library.LibraryScreen
import com.mirror.app.ui.onboarding.OnboardingScreen
import com.mirror.app.ui.settings.SettingsScreen
import com.mirror.app.ui.showroom.ShowroomScreen
import com.mirror.app.ui.showroom.ShowroomViewModel
import com.mirror.app.ui.splash.SplashScreen
import com.mirror.app.ui.upload.UploadViewModel

private val routesWithBottomNav = setOf(
    MirrorRoutes.DASHBOARD, MirrorRoutes.EDITOR, MirrorRoutes.LIBRARY,
    MirrorRoutes.CLIENTS, MirrorRoutes.SETTINGS, MirrorRoutes.BEFORE_AFTER
)
private val routesWithTopBar = setOf(
    MirrorRoutes.DASHBOARD, MirrorRoutes.LIBRARY, MirrorRoutes.CLIENTS, MirrorRoutes.SETTINGS
)
private val unauthenticatedRoutes = setOf(MirrorRoutes.SPLASH, MirrorRoutes.LOGIN, MirrorRoutes.SIGNUP)

@Composable
fun MirrorNavHost(uploadViewModel: UploadViewModel, authViewModel: AuthViewModel, clientDirectoryViewModel: ClientDirectoryViewModel, showroomViewModel: ShowroomViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val sessionState by authViewModel.sessionState.collectAsState()

    // Global guard: if the session drops (e.g. Sign Out from Settings) while the user is
    // anywhere past the auth gate, bounce back to Login rather than leaving stale account
    // screens on-screen.
    LaunchedEffect(sessionState, currentRoute) {
        if (sessionState is AuthSessionState.SignedOut && currentRoute != null && currentRoute !in unauthenticatedRoutes) {
            navController.navigate(MirrorRoutes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            if (currentRoute in routesWithTopBar) {
                MirrorTopBar(onOpenProfile = {
                    if (currentRoute != MirrorRoutes.SETTINGS) {
                        navController.navigate(MirrorRoutes.SETTINGS)
                    }
                })
            }
        },
        bottomBar = {
            if (currentRoute in routesWithBottomNav) {
                MirrorBottomNav(currentRoute = currentRoute) { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(MirrorRoutes.DASHBOARD) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MirrorRoutes.SPLASH,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MirrorRoutes.SPLASH) {
                var splashDone by remember { mutableStateOf(false) }
                SplashScreen(onFinished = { splashDone = true })
                LaunchedEffect(splashDone, sessionState) {
                    if (splashDone && sessionState !is AuthSessionState.Loading) {
                        val destination = if (sessionState is AuthSessionState.SignedIn) {
                            MirrorRoutes.DASHBOARD
                        } else {
                            MirrorRoutes.LOGIN
                        }
                        navController.navigate(destination) {
                            popUpTo(MirrorRoutes.SPLASH) { inclusive = true }
                        }
                    }
                }
            }
            composable(MirrorRoutes.LOGIN) {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToSignUp = { navController.navigate(MirrorRoutes.SIGNUP) }
                )
                LaunchedEffect(sessionState) {
                    if (sessionState is AuthSessionState.SignedIn) {
                        navController.navigate(MirrorRoutes.DASHBOARD) {
                            popUpTo(MirrorRoutes.SPLASH) { inclusive = true }
                        }
                    }
                }
            }
            composable(MirrorRoutes.SIGNUP) {
                SignUpScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = { navController.navigate(MirrorRoutes.LOGIN) }
                )
                LaunchedEffect(sessionState) {
                    if (sessionState is AuthSessionState.SignedIn) {
                        // Fresh accounts see the onboarding walkthrough once; returning sign-ins
                        // (LOGIN route above) skip straight to the dashboard.
                        navController.navigate(MirrorRoutes.ONBOARDING) {
                            popUpTo(MirrorRoutes.SPLASH) { inclusive = true }
                        }
                    }
                }
            }
            composable(MirrorRoutes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    navController.navigate(MirrorRoutes.DASHBOARD) {
                        popUpTo(MirrorRoutes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(MirrorRoutes.DASHBOARD) {
                val savedLooks by uploadViewModel.savedLooks.collectAsState()
                val accountType = (sessionState as? AuthSessionState.SignedIn)?.user?.accountType ?: AccountType.CONSUMER
                DashboardScreen(
                    savedLooks = savedLooks,
                    accountType = accountType,
                    onStartSimulation = { navController.navigate(MirrorRoutes.EDITOR) },
                    onOpenClients = { navController.navigate(MirrorRoutes.CLIENTS) },
                    onOpenLibrary = { navController.navigate(MirrorRoutes.LIBRARY) },
                    onOpenShowroom = { navController.navigate(MirrorRoutes.SHOWROOM) },
                    onOpenLook = { look ->
                        uploadViewModel.loadLook(look)
                        navController.navigate(MirrorRoutes.EDITOR)
                    },
                    onDeleteLook = { look -> uploadViewModel.deleteLook(look.id) }
                )
            }
            composable(MirrorRoutes.EDITOR) {
                EditorScreen(
                    viewModel = uploadViewModel,
                    onBack = { navController.popBackStack() },
                    onSplitDiff = { navController.navigate(MirrorRoutes.BEFORE_AFTER) }
                )
            }
            composable(MirrorRoutes.SHOWROOM) {
                ShowroomScreen(
                    viewModel = showroomViewModel,
                    onBack = { navController.popBackStack() },
                    onSaveLook = { name, look ->
                        uploadViewModel.saveExternalLook(name, look.photo, look.landmarks, look.selections, look.catalog)
                    }
                )
            }
            composable(MirrorRoutes.LIBRARY) {
                LibraryScreen(viewModel = uploadViewModel)
            }
            composable(MirrorRoutes.CLIENTS) {
                ClientDirectoryScreen(viewModel = clientDirectoryViewModel, onNewConsultation = { navController.navigate(MirrorRoutes.EDITOR) })
            }
            composable(MirrorRoutes.SETTINGS) {
                SettingsScreen(authViewModel = authViewModel)
            }
            composable(MirrorRoutes.BEFORE_AFTER) {
                BeforeAfterScreen(viewModel = uploadViewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}
