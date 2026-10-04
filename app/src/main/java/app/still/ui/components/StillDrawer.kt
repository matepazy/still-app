package app.still.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.still.ui.theme.StillSpacing

/** Content-height sheet capped at 85% of the window, including its handle and insets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StillBottomSheet(
    onDismissRequest: () -> Unit,
    dismissible: Boolean = true,
    expandToFitContent: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
    val bottomInset = with(density) { WindowInsets.safeDrawing.getBottom(this).toDp() }
    val handleHeight = 48.dp
    val maxContentHeight = (windowHeight * .85f - handleHeight - bottomInset).coerceAtLeast(0.dp)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = expandToFitContent,
        confirmValueChange = { dismissible || it != SheetValue.Hidden },
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
        contentWindowInsets = { WindowInsets.safeDrawing },
        dragHandle = {
            Box(Modifier.fillMaxWidth().height(handleHeight), contentAlignment = Alignment.Center) {
                BottomSheetDefaults.DragHandle()
            }
        },
    ) {
        // Keep the sheet's anchor calculation in the full window coordinate space.
        // Constrain its content instead, reserving room for the handle and bottom inset.
        Column(Modifier.fillMaxWidth().heightIn(max = maxContentHeight), content = content)
    }
}

/** Shared scrolling drawer with content-originated swipe dismissal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StillDrawer(
    onDismissRequest: () -> Unit,
    dismissible: Boolean = true,
    expandToFitContent: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val contentScrollConnection = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
                Velocity(0f, available.y.coerceAtMost(0f))
        }
    }
    StillBottomSheet(
        onDismissRequest = onDismissRequest,
        dismissible = dismissible,
        expandToFitContent = expandToFitContent,
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
    expandToFitContent: Boolean = true,
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
