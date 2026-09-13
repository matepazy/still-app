package app.still.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.still.data.settings.ThemePreference
import app.still.domain.model.AppDetail
import app.still.domain.model.AppInfo
import app.still.domain.model.AppUsage
import app.still.domain.model.ChangedApp
import app.still.domain.model.DailyAppUsage
import app.still.domain.model.DailyUsage
import app.still.domain.model.DaylineKind
import app.still.domain.model.DaylineSegment
import app.still.domain.model.SessionAppUsage
import app.still.domain.model.UsageComparison
import app.still.domain.model.UsageDashboard
import app.still.domain.model.UsageSession
import app.still.ui.theme.StillTheme
import app.still.ui.today.TodayScreen
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

object PreviewFixtures {
    private val zone = ZoneId.systemDefault()
    private val date = LocalDate.of(2026, 4, 23)
    private val start = date.atStartOfDay(zone).toInstant()
    private val end = date.atTime(17, 42).atZone(zone).toInstant()

    val instagram = AppInfo("com.instagram.android", "Instagram")
    val youtube = AppInfo("com.google.android.youtube", "YouTube")
    val chrome = AppInfo("com.android.chrome", "Chrome")
    val messages = AppInfo("com.google.android.apps.messaging", "Messages")
    val spotify = AppInfo("com.spotify.music", "Spotify")
    val camera = AppInfo("com.google.android.GoogleCamera", "Camera")
    val photos = AppInfo("com.google.android.apps.photos", "Photos")

    private fun instant(hour: Int, minute: Int, second: Int = 0) = date.atTime(hour, minute, second).atZone(zone).toInstant()

    private fun session(
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        vararg apps: Pair<AppInfo, Long>,
    ) = UsageSession(
        instant(startHour, startMinute),
        instant(endHour, endMinute),
        apps.map { SessionAppUsage(it.first, Duration.ofMinutes(it.second)) },
        apps.map { it.first },
    )

    private val mainSessions = listOf(
        session(9, 42, 10, 3, instagram to 12, chrome to 6, messages to 3),
        session(11, 17, 11, 26, spotify to 6, chrome to 2),
        session(13, 4, 13, 28, youtube to 20, messages to 4),
        session(15, 11, 15, 14, camera to 3),
    )
    private val quickSessions = (0 until 23).map { index ->
        val startMinute = index * 6
        val hour = 15 + (20 + startMinute) / 60
        val minute = (20 + startMinute) % 60
        UsageSession(
            instant(hour.coerceAtMost(17), minute, 0),
            instant(hour.coerceAtMost(17), minute, 42),
            listOf(SessionAppUsage(messages, Duration.ofSeconds(42))),
            listOf(messages),
        )
    }
    private val sessions = mainSessions + quickSessions

    private val apps = listOf(
        AppUsage(instagram, Duration.ofMinutes(48), 17),
        AppUsage(youtube, Duration.ofMinutes(42), 8),
        AppUsage(chrome, Duration.ofMinutes(28), 14),
        AppUsage(messages, Duration.ofMinutes(18), 29),
        AppUsage(spotify, Duration.ofMinutes(16), 7),
        AppUsage(camera, Duration.ofMinutes(12), 6),
        AppUsage(photos, Duration.ofMinutes(3), 4),
    )

    private val dayline = buildList {
        add(DaylineSegment(start, instant(9, 42), DaylineKind.Inactive))
        sessions.sortedBy { it.start }.forEach { value ->
            add(DaylineSegment(value.start, value.end, DaylineKind.Active))
        }
    }

    val today = DailyUsage(
        date = date,
        rangeStart = start,
        rangeEnd = end,
        total = Duration.ofHours(2).plusMinutes(47),
        apps = apps,
        sessions = sessions,
        unlocks = 27,
        wakeups = 35,
        longestBreak = Duration.ofHours(1).plusMinutes(18),
        dayline = dayline,
    )

    val dashboard = UsageDashboard(
        today = today,
        comparison = UsageComparison(Duration.ofMinutes(-32), Duration.ofHours(3).plusMinutes(19)),
        mostChanged = ChangedApp(youtube, Duration.ofMinutes(34)),
        history = emptyList(),
    )

    val appDetail = AppDetail(
        date = date,
        usage = apps.first(),
        dailyUsage = listOf(25, 58, 34, 47, 61, 38, 48).mapIndexed { index, minutes ->
            DailyAppUsage(date.minusDays((6 - index).toLong()), Duration.ofMinutes(minutes.toLong()))
        },
        averageDaily = Duration.ofMinutes(38),
        sessions = mainSessions.filter { session -> session.apps.any { it.app == instagram } } +
            listOf(session(13, 4, 13, 12, instagram to 8), session(16, 21, 16, 37, instagram to 16)),
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun TodayPreview() {
    StillTheme(ThemePreference.Dark, false) { TodayScreen(PreviewFixtures.dashboard, {}, {}) }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun TodayLightPreview() {
    StillTheme(ThemePreference.Light, false) { TodayScreen(PreviewFixtures.dashboard, {}, {}) }
}
