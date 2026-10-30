package com.mirror.app.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mirror.app.ui.navigation.BottomNavDestination
import com.mirror.app.ui.theme.MirrorColorTokens

@Composable
fun MirrorBottomNav(
    currentRoute: String?,
    showClients: Boolean,
    onNavigate: (BottomNavDestination) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        modifier = Modifier.height(80.dp)
    ) {
        // Client Mode is Pro only; everyone else reaches its upsell from the dashboard.
        BottomNavDestination.entries
            .filter { showClients || it != BottomNavDestination.CLIENTS }
            .forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(imageVector = destination.icon, contentDescription = destination.label)
                },
                label = {
                    Text(text = destination.label.uppercase(), style = MaterialTheme.typography.labelMedium)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MirrorColorTokens.PrimaryFixedDim,
                    selectedTextColor = MirrorColorTokens.PrimaryFixedDim,
                    unselectedIconColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f),
                    indicatorColor = MaterialTheme.colorScheme.inverseSurface
                )
            )
        }
    }
}
