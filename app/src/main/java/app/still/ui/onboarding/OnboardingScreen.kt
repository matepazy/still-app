package app.still.ui.onboarding

import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.still.R
import app.still.ui.UsageUiState
import app.still.ui.components.StillWordmark
import app.still.ui.theme.StillSpacing

private enum class OnboardingPage {
    Intro,
    RestrictedSettings,
    UsageAccess,
}

@Composable
fun OnboardingScreen(
    usageState: UsageUiState,
    onOpenRestrictedSettings: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onComplete: () -> Unit,
    showRestrictedSettings: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
) {
    val pages = remember(showRestrictedSettings) {
        buildList {
            add(OnboardingPage.Intro)
            if (showRestrictedSettings) add(OnboardingPage.RestrictedSettings)
            add(OnboardingPage.UsageAccess)
        }
    }
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val page = pages[pageIndex.coerceIn(0, pages.lastIndex)]

    LaunchedEffect(page, usageState) {
        if (page == OnboardingPage.UsageAccess && usageState is UsageUiState.Ready) onComplete()
    }

    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(page, modifier = Modifier.weight(1f), label = "Onboarding page") { selected ->
            when (selected) {
                OnboardingPage.Intro -> IntroPage()
                OnboardingPage.RestrictedSettings -> RestrictedSettingsPage()
                OnboardingPage.UsageAccess -> UsageAccessPage(usageState)
            }
        }
        PageDots(pageIndex, pages.size)
        Spacer(Modifier.height(StillSpacing.large))
        Button(
            onClick = {
                when (page) {
                    OnboardingPage.Intro -> pageIndex++
                    OnboardingPage.RestrictedSettings -> onOpenRestrictedSettings()
                    OnboardingPage.UsageAccess -> onOpenUsageSettings()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            Text(
                when (page) {
                    OnboardingPage.Intro -> "Continue"
                    OnboardingPage.RestrictedSettings -> "Open Still app info"
                    OnboardingPage.UsageAccess -> "Open Usage Access"
                },
            )
        }
        when (page) {
            OnboardingPage.Intro -> Spacer(Modifier.height(48.dp))
            OnboardingPage.RestrictedSettings -> SecondaryAction("Continue to Usage Access") { pageIndex++ }
            OnboardingPage.UsageAccess -> SecondaryAction("I’ll do this later", onComplete)
        }
    }
}

@Composable
private fun IntroPage() {
    OnboardingPageLayout(
        icon = R.drawable.ic_onboarding_overview,
        artworkDescription = "A phone-use dayline surrounding a quiet clock",
        title = "Understand\nyour screen time.",
        body = "Still shows when you use your phone, what changes from day to day, and how often you check it.",
        showWordmark = true,
    )
}

@Composable
private fun RestrictedSettingsPage() {
    OnboardingPageLayout(
        icon = R.drawable.ic_onboarding_restricted_settings,
        artworkDescription = "An unlocked Still app settings card",
        eyebrow = "ONE-TIME ANDROID STEP",
        title = "Allow restricted settings",
        body = "Android may block Usage Access for apps installed outside an app store. If “Restricted setting” appears, Still cannot remove it for you.",
    ) {
        Spacer(Modifier.height(StillSpacing.large))
        InstructionRow("1", "Tap the three-dot menu in the top-right")
        InstructionRow("2", "Choose “Allow restricted settings”")
        InstructionRow("3", "Return to Still and continue")
    }
}

@Composable
private fun UsageAccessPage(usageState: UsageUiState) {
    OnboardingPageLayout(
        icon = R.drawable.ic_onboarding_usage_access,
        artworkDescription = "A private app-usage timeline",
        eyebrow = "FINAL STEP",
        title = "Allow Usage Access",
        body = "Turn on Still in Usage Access so it can read Android’s app-usage history and build your timeline.",
    ) {
        Spacer(Modifier.height(StillSpacing.large))
        PrivacyNote("Your usage data is processed on this device and is never uploaded.")
        Spacer(Modifier.height(StillSpacing.medium))
        PermissionStatus(usageState)
    }
}

@Composable
private fun OnboardingPageLayout(
    @DrawableRes icon: Int,
    artworkDescription: String,
    title: String,
    body: String,
    eyebrow: String? = null,
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
            eyebrow?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(StillSpacing.small))
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
private fun InstructionRow(number: String, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StillSpacing.medium),
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
private fun SecondaryAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick)
            .padding(top = 16.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PageDots(page: Int, pageCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(pageCount) { index ->
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
        UsageUiState.PermissionRequired -> Text("Waiting for permission", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        is UsageUiState.Ready -> Text("Usage Access enabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        is UsageUiState.Error -> Text("Access enabled · data unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
