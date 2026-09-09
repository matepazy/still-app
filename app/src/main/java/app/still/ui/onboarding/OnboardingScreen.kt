package app.still.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.still.ui.UsageUiState
import app.still.ui.components.StillMark
import app.still.ui.theme.StillSpacing

@Composable
fun OnboardingScreen(
    usageState: UsageUiState,
    onOpenSettings: () -> Unit,
    onComplete: () -> Unit,
) {
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(page, usageState) {
        if (page == 1 && usageState is UsageUiState.Ready) onComplete()
    }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(page, modifier = Modifier.weight(1f), label = "Onboarding page") { selected ->
            if (selected == 0) IntroPage() else PermissionPage(usageState)
        }
        PageDots(page)
        Spacer(Modifier.height(StillSpacing.large))
        Button(
            onClick = { if (page == 0) page = 1 else onOpenSettings() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Text(if (page == 0) "Continue" else "Open settings")
        }
        if (page == 1) {
            Text(
                "I’ll do this later",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = onComplete)
                    .padding(top = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun IntroPage() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        StillMark(size = 184.dp)
        Spacer(Modifier.height(36.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text("Understand\nyour screen time.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(StillSpacing.large))
            Text(
                "Still shows when you use your phone, what changes from day to day, and how often you check it.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionPage(usageState: UsageUiState) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(StillSpacing.large))
        Text("Allow usage access", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(StillSpacing.medium))
        Text(
            "Android keeps app-usage statistics on your device. Still needs permission to read them and build your timeline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(StillSpacing.xLarge))
        PermissionBenefit(Icons.Default.Visibility, "See when you use apps")
        PermissionBenefit(Icons.Default.DateRange, "Build your daily timeline")
        PermissionBenefit(Icons.Default.Lock, "Your usage data stays\non this device")
        Spacer(Modifier.height(StillSpacing.medium))
        PermissionStatus(usageState)
    }
}

@Composable
private fun PermissionBenefit(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = StillSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PageDots(page: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(2) { index ->
            Box(
                Modifier
                    .size(if (index == page) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            )
        }
    }
}

@Composable
private fun PermissionStatus(state: UsageUiState) {
    when (state) {
        UsageUiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        UsageUiState.PermissionRequired -> Text("Permission not granted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is UsageUiState.Ready -> Text("Permission granted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        is UsageUiState.Error -> Text("Permission granted · data unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
