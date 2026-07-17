package info.plateaukao.einkbro.view.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

val NormalTextModifier = Modifier.padding(6.dp)

private val DarkColors = darkColors(
    // Android keeps primary black even in dark mode, but its dark-sensitive
    // dialogs are XML views there; in this all-Compose port every
    // primary-tinted widget (Slider, Switch track, TextField cursor/indicator,
    // progress spinners) would be black-on-black. Gray keeps them readable.
    primary = Color.Gray,
    // Stays Gray: top app bars render on the dark *surface* (black) but color
    // their text/icons with onPrimary — Black here made headers unreadable.
    // The two filled Buttons override their colors explicitly, so nothing
    // renders onPrimary on top of the gray primary.
    onPrimary = Color.Gray,
    secondary = Color.Gray,
    onSecondary = Color.White,
    surface = Color.Black,
    onSurface = Color.Gray,
    background = Color.Black,
    onBackground = Color.Gray,
)
private val LightColors = lightColors(
    primary = Color.Black,
    onPrimary = Color.White,
    surface = Color.White,
    onSurface = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
)
