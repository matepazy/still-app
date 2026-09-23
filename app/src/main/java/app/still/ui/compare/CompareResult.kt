package app.still.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.still.domain.model.CompareResult
import app.still.ui.components.compactDuration
import app.still.ui.statistics.label
import java.time.Duration

@Composable
fun CompareResultView(result: CompareResult) {
    Text("You two", style = MaterialTheme.typography.headlineMedium)
    Text(result.you.range.label(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("", modifier = Modifier.weight(1f))
        Text("You", modifier = Modifier.weight(1f))
        Text("Friend", modifier = Modifier.weight(1f))
    }
    ResultRow("Daily average", result.you.averageDailyScreenTimeMillis?.let(::duration), result.friend.averageDailyScreenTimeMillis?.let(::duration))
    ResultRow("Total", result.you.totalScreenTimeMillis?.let(::duration), result.friend.totalScreenTimeMillis?.let(::duration))
    ResultRow("Check-ins", result.you.checkIns?.toString(), result.friend.checkIns?.toString())
    ResultRow("Quick checks", result.you.quickChecks?.toString(), result.friend.quickChecks?.toString())
    ResultRow("Longest break", result.you.longestBreakMillis?.let(::duration), result.friend.longestBreakMillis?.let(::duration))
    val categories = (result.you.categories.orEmpty().map { it.name } + result.friend.categories.orEmpty().map { it.name }).distinct()
    if (categories.isNotEmpty()) {
        Text("By category", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
        categories.forEach { name ->
            ResultRow(name,
                result.you.categories?.firstOrNull { it.name == name }?.millis?.let(::duration),
                result.friend.categories?.firstOrNull { it.name == name }?.millis?.let(::duration))
        }
    }
    val apps = (result.you.apps.orEmpty().map { it.label } + result.friend.apps.orEmpty().map { it.label }).distinct()
    if (apps.isNotEmpty()) {
        Text("Individual apps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))
        apps.forEach { name ->
            ResultRow(name, result.you.apps?.firstOrNull { it.label == name }?.millis?.let(::duration),
                result.friend.apps?.firstOrNull { it.label == name }?.millis?.let(::duration))
        }
    }
}

private fun duration(value: Long) = Duration.ofMillis(value).compactDuration()

@Composable
private fun ResultRow(name: String, you: String?, friend: String?) {
    if (you == null && friend == null) return
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(you ?: "—", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(friend ?: "—", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}
