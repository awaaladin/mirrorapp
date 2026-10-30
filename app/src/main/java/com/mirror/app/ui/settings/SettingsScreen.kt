package com.mirror.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mirror.app.data.network.NetworkConfig
import com.mirror.app.domain.model.ProPlan
import com.mirror.app.ui.auth.AuthSessionState
import com.mirror.app.ui.auth.AuthViewModel

@Composable
fun SettingsScreen(authViewModel: AuthViewModel) {
    val sessionState by authViewModel.sessionState.collectAsState()
    val user = (sessionState as? AuthSessionState.SignedIn)?.user
    val email = user?.email
    val proPlan by authViewModel.proPlan.collectAsState()
    val isPro = proPlan != ProPlan.FREE
    val context = LocalContext.current

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
                when (proPlan) {
                    ProPlan.FREE -> "FREE ACCOUNT"
                    ProPlan.SOLO -> "UNLOCKED FOREVER"
                    ProPlan.STUDIO -> "MIRROR PRO"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Text(
            when (proPlan) {
                ProPlan.FREE -> "Mirror is free. Looks you share carry a small watermark - one ₦7,500 payment removes it forever. Pay on mirror's website with the same email you use to sign in here."
                ProPlan.SOLO -> "Your one-time payment is on this account. Shared looks have no watermark, for good."
                ProPlan.STUDIO -> "Your plan includes Showroom AI skin analysis and Client Mode - manage client profiles from the Clients tab."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
        )

        if (!isPro) {
            Button(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(NetworkConfig.PRICING_URL)))
                },
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("REMOVE WATERMARK · ₦7,500 ONCE", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = { authViewModel.logOut() },
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("SIGN OUT", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
