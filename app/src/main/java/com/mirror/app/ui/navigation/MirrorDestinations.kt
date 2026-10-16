package com.mirror.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecentActors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.graphics.vector.ImageVector

object MirrorRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val EDITOR = "editor"
    const val LIBRARY = "library"
    const val CLIENTS = "clients"
    const val SETTINGS = "settings"
    const val BEFORE_AFTER = "before_after"
    const val SHOWROOM = "showroom"
}

/** The 5 permanent bottom navigation destinations, in display order. */
enum class BottomNavDestination(val route: String, val label: String, val icon: ImageVector) {
    LOOKS(MirrorRoutes.DASHBOARD, "Looks", Icons.Filled.AutoAwesome),
    STUDIO(MirrorRoutes.EDITOR, "Studio", Icons.Filled.Palette),
    LIBRARY(MirrorRoutes.LIBRARY, "Library", Icons.Filled.Gradient),
    CLIENTS(MirrorRoutes.CLIENTS, "Clients", Icons.Filled.RecentActors),
    SETTINGS(MirrorRoutes.SETTINGS, "Settings", Icons.Filled.Tune)
}
