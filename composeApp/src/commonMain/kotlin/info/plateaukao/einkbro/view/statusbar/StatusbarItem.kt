package info.plateaukao.einkbro.view.statusbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*

enum class StatusbarItem(
    val titleResId: StringResource,
    val previewIcon: ImageVector? = null,
    val previewIconResId: DrawableResource? = null,
) {
    Time(titleResId = Res.string.toolbar_time, previewIcon = Icons.Outlined.AccessTime),
    PageInfo(titleResId = Res.string.page_count, previewIconResId = Res.drawable.ic_page_count),
    Battery(titleResId = Res.string.statusbar_item_battery, previewIcon = Icons.Outlined.BatteryFull),
    Wifi(titleResId = Res.string.statusbar_item_wifi, previewIcon = Icons.Outlined.Wifi),
    TouchPagination(
        titleResId = Res.string.touch_turn_page,
        previewIconResId = Res.drawable.ic_touch_enabled,
    ),
    VolumePagination(
        titleResId = Res.string.statusbar_item_volume_pagination,
        previewIcon = Icons.AutoMirrored.Outlined.VolumeUp,
    );

    companion object {
        val defaultItems: List<StatusbarItem> = listOf(Time, PageInfo, Battery, Wifi, TouchPagination, VolumePagination)

        fun fromOrdinal(value: Int): StatusbarItem? = entries.getOrNull(value)
    }
}

enum class StatusbarPosition(val titleResId: StringResource) {
    Top(Res.string.statusbar_position_top),
    Bottom(Res.string.statusbar_position_bottom),
}
