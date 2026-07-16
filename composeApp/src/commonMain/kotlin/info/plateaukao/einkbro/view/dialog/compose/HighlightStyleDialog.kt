package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.preference.HighlightStyle
import info.plateaukao.einkbro.view.compose.MyTheme
import org.jetbrains.compose.resources.vectorResource

/**
 * Entry composable; was HighlightStyleDialogFragment.Content().
 */
@Composable
fun HighlightStyleContent(
    style: HighlightStyle = AppServices.config.display.highlightStyle,
    onOk: (HighlightStyle) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    Row(modifier = Modifier.horizontalScroll(scrollState)) {
        HighlightStyle.values().map { highlightStyle ->
            IconButton(
                modifier = Modifier
                    .padding(8.dp)
                    .size(40.dp)
                    .border(
                        1.dp,
                        if (style == highlightStyle) MaterialTheme.colors.onBackground else Color.Transparent
                    )
                    .background(MaterialTheme.colors.background),
                onClick = { onOk(highlightStyle) }
            ) {
                Icon(
                    modifier = Modifier.size(30.dp),
                    imageVector = vectorResource(highlightStyle.iconResId),
                    contentDescription = null,
                    tint = highlightStyle.color ?: MaterialTheme.colors.onBackground
                )
            }
        }
    }
}

@Composable
fun PreviewHighlightStyleContent() {
    MyTheme {
        HighlightStyleContent(HighlightStyle.BACKGROUND_GREEN) {}
    }
}
