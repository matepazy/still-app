package app.still.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import app.still.ui.theme.StillSpacing

/** Shared drawer with optional content-height opening and content-originated swipe dismissal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StillDrawer(
    onDismissRequest: () -> Unit,
    dismissible: Boolean = true,
    expandToFitContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = expandToFitContent,
        confirmValueChange = { dismissible || it != SheetValue.Hidden },
    )
    val contentScrollConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(0f, available.y.coerceAtMost(0f))
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
        contentWindowInsets = { WindowInsets.safeDrawing },
    ) {
        Column(
            Modifier.fillMaxWidth()
                .nestedScroll(contentScrollConnection)
                .verticalScroll(rememberScrollState(), overscrollEffect = null)
                .padding(horizontal = StillSpacing.large)
                .padding(bottom = StillSpacing.xLarge),
            content = content,
        )
    }
}

/** Confirmation, result, and choice drawers share typography and wrapping actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionDrawer(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    dismissible: Boolean = true,
    expandToFitContent: Boolean = false,
) {
    StillDrawer(
        onDismissRequest = onDismissRequest,
        dismissible = dismissible,
        expandToFitContent = expandToFitContent,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(StillSpacing.medium),
        ) {
            icon?.invoke()
            ProvideTextStyle(MaterialTheme.typography.titleLarge, title)
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(StillSpacing.small, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(StillSpacing.small),
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
