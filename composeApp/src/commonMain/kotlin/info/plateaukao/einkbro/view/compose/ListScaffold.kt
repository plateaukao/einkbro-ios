package info.plateaukao.einkbro.view.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import info.plateaukao.einkbro.view.compose.onTopBar
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*

/**
 * Shared chrome for the config/list activities: themed Scaffold with a
 * TopAppBar, back arrow, and optional actions. Replaces ~10 near-identical
 * copies with three different back-navigation styles and inconsistent icon
 * tinting.
 */
@Composable
fun ListScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    MyTheme {
        Scaffold(
            // Keep the top bar clear of the iOS status bar (and content clear of
            // the home indicator); otherwise the back button can't be tapped.
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            topBar = {
                TopAppBar(
                    title = { Text(title, color = MaterialTheme.colors.onTopBar) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                tint = MaterialTheme.colors.onTopBar,
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.back),
                            )
                        }
                    },
                    actions = actions,
                )
            },
            content = content,
        )
    }
}

/** Centered placeholder for empty lists (was copy-pasted in four activities). */
@Composable
fun EmptyListPlaceholder(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.h6,
            color = MaterialTheme.colors.onBackground,
        )
    }
}
