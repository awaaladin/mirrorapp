package com.mirror.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class PillarAccent { PRIMARY, SECONDARY, TERTIARY }

private data class Pillar(val icon: ImageVector, val title: String, val description: String, val accent: PillarAccent)

private val pillars = listOf(
    Pillar(
        Icons.Filled.Adjust,
        "Precision Undertone Matching",
        "Eliminates ashy or muddy simulations with spectral calibration engineered for rich depth.",
        PillarAccent.PRIMARY
    ),
    Pillar(
        Icons.Filled.Groups,
        "Self & Pro Client Modes",
        "Toggle seamlessly between personal look exploration and bridal or editorial client consultations.",
        PillarAccent.SECONDARY
    ),
    Pillar(
        Icons.Filled.Checkroom,
        "Gele & Hair Styling Integration",
        "Preview complete ceremonial headwraps and textured installations in accurate, lifelike lighting.",
        PillarAccent.TERTIARY
    )
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(2) { index ->
                    Box(
                        Modifier
                            .height(4.dp)
                            .width(if (pagerState.currentPage == index) 32.dp else 12.dp)
                            .background(
                                if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(50)
                            )
                    )
                }
            }
            Text(
                "Step 0${pagerState.currentPage + 1} of 02",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Text(
                "CALIBRATED SIMULATION",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        Text(
            "See the look before you commit to it.",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "True-to-skin digital makeup application formulated specifically for melanin-rich complexions and diverse undertones.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            if (page == 0) PillarsPage() else ModeSelectionPage()
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(2) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(if (pagerState.currentPage == index) 24.dp else 8.dp)
                        .background(
                            if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(50)
                        )
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage == 0) {
                    scope.launch { pagerState.animateScrollToPage(1) }
                } else {
                    onFinished()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Text(
                if (pagerState.currentPage == 0) "CONTINUE TO STUDIO" else "ENTER THE STUDIO",
                style = MaterialTheme.typography.labelMedium
            )
            Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
        }
        TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
            Text(
                "EXPLORE SHADE LIBRARY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "ATELIER MIRROR PRECISION · LAGOS · PARIS · LONDON",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun PillarsPage() {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        pillars.forEach { pillar ->
            val (containerColor, onContainerColor) = when (pillar.accent) {
                PillarAccent.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                PillarAccent.SECONDARY -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                PillarAccent.TERTIARY -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
                    .padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(containerColor, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(pillar.icon, contentDescription = null, tint = onContainerColor)
                }
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(pillar.title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        pillar.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Purely informational - this page used to show two selectable-looking cards that didn't
 * actually do anything when tapped, which was confusing. The real account-type choice (which
 * determines whether Client Mode unlocks) is made once, with real consequence, on the sign-up
 * form - see [com.mirror.app.ui.auth.SignUpScreen]'s account type selector. This page just
 * previews what each tier includes.
 */
@Composable
private fun ModeSelectionPage() {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Every account starts on one of two tiers, chosen when you sign up:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ModeCard(
            label = "PERSONAL",
            labelColor = MaterialTheme.colorScheme.primary,
            title = "Self Simulation",
            description = "Calibrate your unique complexion map, save shade formula sheets, and experiment with seasonal ceremonial palettes."
        )
        ModeCard(
            label = "PRO / MUA",
            labelColor = MaterialTheme.colorScheme.secondary,
            title = "Bridal & Editorial Roster",
            description = "Everything in Personal, plus a Client Mode directory: saved client shade profiles, synchronized swatch mixing trays, lighting simulation for golden hour receptions."
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("CALIBRATION PROTOCOL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Text("ISO-D65 COMPLIANT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun ModeCard(label: String, labelColor: androidx.compose.ui.graphics.Color, title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.large)
            .padding(16.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = labelColor)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontStyle = FontStyle.Normal, modifier = Modifier.padding(top = 6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
