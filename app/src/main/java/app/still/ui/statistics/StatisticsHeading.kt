package app.still.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import app.still.ui.components.ActionDrawer
import app.still.ui.components.StillIcons

@Composable
internal fun StatisticsHeading(
    title: String,
    help: String? = null,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    leading: @Composable (() -> Unit)? = null,
) {
    var showHelp by rememberSaveable(title) { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        Text(title, Modifier.weight(1f, fill = false), style = style)
        if (help != null) {
            IconButton(
                onClick = { showHelp = true },
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(StillIcons.Info),
                        contentDescription = "About $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
    if (showHelp && help != null) {
        ActionDrawer(
            onDismissRequest = { showHelp = false },
            title = { Text(title) },
            text = { Text(help) },
            confirmButton = { TextButton(onClick = { showHelp = false }) { Text("Got it") } },
            expandToFitContent = false,
        )
    }
}
