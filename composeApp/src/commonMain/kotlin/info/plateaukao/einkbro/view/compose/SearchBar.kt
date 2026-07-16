package info.plateaukao.einkbro.view.compose

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ComposedSearchBar(
    focusRequester: FocusRequester,
    onTextChanged:(String)->Unit,
    onCloseClick: ()->Unit,
    onUpClick: (String)->Unit,
    onDownClick: (String)->Unit,
) {
    Row(
        modifier = Modifier
            .height(50.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colors.background),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        val text = remember { mutableStateOf("") }
        val keyboardController = LocalSoftwareKeyboardController.current

        SearchInput(
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (focusState.hasFocus) keyboardController?.show()
                }
                .focusRequester(focusRequester),
            text = text,
            onValueChanged = onTextChanged,
        )

        SearchBarIcon(iconResId = Res.drawable.icon_arrow_down_gest, onClick = { onDownClick(text.value) })
        SearchBarIcon(iconResId = Res.drawable.icon_arrow_up_gest, onClick = { onUpClick(text.value) })
        SearchBarIcon(iconResId = Res.drawable.icon_close, onClick = { text.value = "" ; onCloseClick() })
    }
}

@Composable
fun SearchInput(
    modifier: Modifier,
    text: MutableState<String>,
    onValueChanged: (String)->Unit,
) {
    TextField(
        value = text.value,
        modifier = modifier.padding(horizontal = 5.dp),
        colors = TextFieldDefaults.textFieldColors(
            textColor = MaterialTheme.colors.onBackground,
            backgroundColor = MaterialTheme.colors.background,
        ),
        placeholder = {
            Text(stringResource(Res.string.search_hint), color = MaterialTheme.colors.onBackground)
        },
        onValueChange = {
            text.value = it
            onValueChanged(it)
        },
    )
}

@Composable
fun SearchBarIcon(
    iconResId: DrawableResource,
    onClick: ()->Unit,
) {
    Icon(
        modifier = Modifier
            .fillMaxHeight()
            .width(40.dp)
            .clickable { onClick() }
            .padding(8.dp),
        imageVector = vectorResource(iconResId),
        contentDescription = null,
        tint = MaterialTheme.colors.onBackground
    )
}


@Composable
fun PreviewSearchBar() {
    MyTheme {
        ComposedSearchBar(
            onTextChanged = {},
            onCloseClick = {},
            onUpClick = {},
            onDownClick = {},
            focusRequester = FocusRequester(),
        )
    }
}
