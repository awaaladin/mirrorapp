package com.mirror.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mirror.app.domain.model.AccountType
import com.mirror.app.ui.auth.AuthSessionState
import com.mirror.app.ui.auth.AuthViewModel

@Composable
fun SettingsScreen(authViewModel: AuthViewModel) {
    val sessionState by authViewModel.sessionState.collectAsState()
    val user = (sessionState as? AuthSessionState.SignedIn)?.user
    val email = user?.email
    val isPro = user?.accountType == AccountType.BUSINESS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "SIGNED IN AS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                email ?: "Unknown",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                if (isPro) "PRO / MUA ACCOUNT" else "PERSONAL ACCOUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Text(
            if (isPro) "Your Pro account unlocks Client Mode - manage client profiles from the Clients tab."
            else "Upgrading to Pro unlocks Client Mode for bridal & editorial consultations. Self-serve upgrade is coming in a later phase - for now, this is set when you sign up.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )

        OutlinedButton(
            onClick = { authViewModel.logOut() },
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("SIGN OUT", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
