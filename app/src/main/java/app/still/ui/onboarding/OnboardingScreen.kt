package app.still.ui.onboarding

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.still.R
import app.still.ui.UsageUiState
import app.still.ui.components.StillWordmark
import app.still.ui.theme.StillSpacing

@Composable
fun OnboardingScreen(
    usageState: UsageUiState,
    hasUsageAccess: () -> Boolean,
    usageSettingsIntent: () -> Intent,
    appInfoIntent: () -> Intent,
    installedFromApk: () -> Boolean,
    onComplete: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(page, usageState) {
        if (page == 1 && hasUsageAccess()) onComplete()
    }
    if (page == 0) {
        OnboardingFrame(0, "Set up Still", { page = 1 }) { IntroPage() }
    } else {
        UsageAccessFlow(hasUsageAccess, usageSettingsIntent, appInfoIntent, installedFromApk, onComplete) { openSettings ->
            OnboardingFrame(1, "Allow screen-time access", openSettings) { UsageAccessPage() }
        }
    }
}
@Composable
private fun OnboardingFrame(page: Int, buttonLabel: String, onButtonClick: () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(page, modifier = Modifier.weight(1f), label = "Onboarding page") { content() }
        PageDots(page)
        Spacer(Modifier.height(StillSpacing.large))
        Button(
            onClick = onButtonClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) { Text(buttonLabel) }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun IntroPage() {
    OnboardingPageLayout(
        icon = R.drawable.ic_onboarding_overview,
        artworkDescription = "A phone-use dayline surrounding a quiet clock",
        title = "Understand your screen time.",
        body = "Still shows when you use your phone, what changes from day to day, and how often you check it.",
        showWordmark = true,
    )
}

@Composable
private fun UsageAccessPage() {
    OnboardingPageLayout(
        icon = R.drawable.ic_onboarding_usage_access,
        artworkDescription = "A private app-usage timeline",
        title = "Allow screen-time access",
        body = "Still needs Android's Usage Access permission to calculate how long you use each app.",
    ) {
        Spacer(Modifier.height(StillSpacing.large))
        PrivacyNote("Your usage information is processed and stored on this device.")
    }
}

@Composable
private fun OnboardingPageLayout(
    @DrawableRes icon: Int,
    artworkDescription: String,
    title: String,
    body: String,
    showWordmark: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = artworkDescription,
            modifier = Modifier.size(if (showWordmark) 178.dp else 150.dp),
        )
        Spacer(Modifier.height(if (showWordmark) 28.dp else StillSpacing.large))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (showWordmark) Alignment.Start else Alignment.CenterHorizontally) {
            if (showWordmark) {
                StillWordmark(markSize = 28.dp)
                Spacer(Modifier.height(StillSpacing.medium))
            }
            Text(
                title,
                style = if (showWordmark) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                textAlign = if (showWordmark) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(StillSpacing.medium))
            Text(
                body,
                style = if (showWordmark) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = if (showWordmark) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            content()
        }
    }
}

@Composable
private fun PrivacyNote(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(StillSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
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
