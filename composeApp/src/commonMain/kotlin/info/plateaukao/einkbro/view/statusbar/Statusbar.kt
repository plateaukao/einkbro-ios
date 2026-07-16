package info.plateaukao.einkbro.view.statusbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.ic_touch_disabled
import info.plateaukao.einkbro.resources.ic_touch_enabled
import info.plateaukao.einkbro.view.compose.rememberCurrentTimeText
import org.jetbrains.compose.resources.vectorResource

private val iconSize = 16.dp
private val barHeight = 22.dp

@Composable
fun Statusbar(
    items: List<StatusbarItem> = StatusbarItem.defaultItems,
    pageInfo: String = "4/21",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(barHeight)
            .background(MaterialTheme.colors.background)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        items.forEach { item ->
            when (item) {
                StatusbarItem.Time -> StatusbarTime()
                StatusbarItem.PageInfo -> StatusbarPageInfo(pageInfo)
                StatusbarItem.Battery -> StatusbarBattery()
                StatusbarItem.Wifi -> StatusbarWifi()
                StatusbarItem.TouchPagination -> StatusbarTouchPagination()
                StatusbarItem.VolumePagination -> StatusbarVolumePagination()
            }
        }
    }
}

@Composable
private fun StatusbarTime() {
    StatusbarText(rememberCurrentTimeText())
}

@Composable
private fun StatusbarPageInfo(pageInfo: String) {
    if (pageInfo.isBlank()) return
    StatusbarText(pageInfo)
}

@Composable
private fun StatusbarText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colors.onBackground,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * On Android this polls BatteryManager every minute; the iOS catalog has no
 * battery service shim, so it renders a representative fixed 80%.
 */
@Composable
private fun StatusbarBattery() {
    val percent = 80
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.BatteryFull,
            contentDescription = null,
            tint = MaterialTheme.colors.onBackground,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = "$percent%",
            color = MaterialTheme.colors.onBackground,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

/**
 * On Android this tracks ConnectivityManager wifi transport callbacks; the
 * catalog fakes an always-connected wifi state.
 */
@Composable
private fun StatusbarWifi() {
    val isWifi = true
    Icon(
        imageVector = if (isWifi) Icons.Outlined.Wifi else Icons.Outlined.WifiOff,
        contentDescription = null,
        tint = MaterialTheme.colors.onBackground,
        modifier = Modifier.size(iconSize),
    )
}

@Composable
private fun StatusbarTouchPagination() {
    // Android observes the SharedPreferences key; here the config value is
    // read once per composition (the catalog has no external pref changes).
    val enabled = AppServices.config.touch.enableTouchTurn
    Icon(
        imageVector = vectorResource(
            if (enabled) Res.drawable.ic_touch_enabled else Res.drawable.ic_touch_disabled
        ),
        contentDescription = null,
        tint = MaterialTheme.colors.onBackground,
        modifier = Modifier.size(iconSize),
    )
}

@Composable
private fun StatusbarVolumePagination() {
    val enabled = AppServices.config.touch.volumePageTurn
    Icon(
        imageVector = if (enabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
        contentDescription = null,
        tint = MaterialTheme.colors.onBackground,
        modifier = Modifier.size(iconSize),
    )
}
