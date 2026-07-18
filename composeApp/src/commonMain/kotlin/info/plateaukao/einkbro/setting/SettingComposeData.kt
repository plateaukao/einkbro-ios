package info.plateaukao.einkbro.setting

import info.plateaukao.einkbro.activity.SettingRoute
import info.plateaukao.einkbro.browser.BrowserAction
import info.plateaukao.einkbro.preference.EinkImageAdjustment
import info.plateaukao.einkbro.preference.EinkImageMode
import info.plateaukao.einkbro.preference.ToolbarPosition
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import kotlin.reflect.KMutableProperty0

interface SettingItemInterface {
    val titleResId: StringResource?
    val summaryResId: StringResource?
    val iconId: DrawableResource?
    val span: Int
}

class DividerSettingItem(
    override val titleResId: StringResource? = null,
) : SettingItemInterface {
    override val summaryResId: StringResource? = null
    override val iconId: DrawableResource? = null
    override val span: Int = 2
}

class BooleanSettingItem(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    val config: KMutableProperty0<Boolean>,
    override val span: Int = 1,
) : SettingItemInterface

class ListSettingWithEnumItem<T : Enum<T>>(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    var config: KMutableProperty0<T>,
    val options: List<StringResource>,
    override val span: Int = 1,
    /** Enum constants of T; filled by the reified factory below since common
     *  code has no javaClass.enumConstants. */
    val values: List<T>,
) : SettingItemInterface

/**
 * Factory keeping the original constructor-style call sites: captures the enum
 * constants with a reified type parameter (the Android code used
 * javaClass.enumConstants at selection time, which isn't available in
 * multiplatform common code).
 */
inline fun <reified T : Enum<T>> ListSettingWithEnumItem(
    titleResId: StringResource,
    iconId: DrawableResource? = null,
    summaryResId: StringResource? = null,
    config: KMutableProperty0<T>,
    options: List<StringResource>,
    span: Int = 1,
): ListSettingWithEnumItem<T> = ListSettingWithEnumItem(
    titleResId, iconId, summaryResId, config, options, span, enumValues<T>().toList(),
)

class ToolbarPositionSettingItem(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    val config: KMutableProperty0<ToolbarPosition>,
    override val span: Int = 1,
) : SettingItemInterface

class EinkImageSettingItem(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    val config: KMutableProperty0<EinkImageAdjustment>,
    val modeConfig: KMutableProperty0<EinkImageMode>,
    override val span: Int = 1,
) : SettingItemInterface

class ListSettingWithStrResIdItem(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    var config: KMutableProperty0<String>,
    val options: List<StringResource>,
    override val span: Int = 1,
) : SettingItemInterface

class ListSettingWithClassItem<T>(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    var config: KMutableProperty0<String>,
    val options: List<String>,
    override val span: Int = 1,
) : SettingItemInterface

class GestureActionSettingItem(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    val config: KMutableProperty0<BrowserAction>,
    override val span: Int = 1,
) : SettingItemInterface

open class ActionSettingItem(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    override val span: Int = 1,
    open val action: () -> Unit,
) : SettingItemInterface

data class ProgressState(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
)

interface ProgressCallback {
    suspend fun updateProgress(progress: Float)
}

class ProgressActionSettingItem(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    override val span: Int = 1,
    val action: suspend (ProgressCallback) -> Unit,
) : SettingItemInterface

open class NavigateSettingItem(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    override val span: Int = 1,
    val destination: SettingRoute,
) : SettingItemInterface

class VersionSettingItem(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    override val span: Int = 1,
    val destination: SettingRoute? = null,
) : SettingItemInterface

class ValueSettingItem<T>(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    override val summaryResId: StringResource? = null,
    var config: KMutableProperty0<T>,
    override val span: Int = 1,
    val showValue: Boolean = false,
) : SettingItemInterface


enum class LinkSettingItem(
    override val titleResId: StringResource,
    override val iconId: DrawableResource? = null,
    val url: String,
    override val summaryResId: StringResource? = null,
    override val span: Int = 1,
) : SettingItemInterface {
    ProjectSite(Res.string.project_site, Res.drawable.ic_home, "https://plateaukao.github.io/einkbro/"),
    LatestRelease(
        Res.string.latest_release,
        Res.drawable.icon_earth,
        "https://github.com/plateaukao/einkbro/releases"
    ),
    Twitter(Res.string.twitter, Res.drawable.icon_earth, "https://twitter.com/einkbro"),
    ChangeLogs(
        Res.string.changelogs,
        Res.drawable.icon_earth,
        "https://github.com/plateaukao/einkbro/blob/main/CHANGELOG.md"
    ),
    Contributors(
        Res.string.contributors,
        Res.drawable.icon_copyright,
        "https://github.com/plateaukao/einkbro/blob/main/CONTRIBUTORS.md"
    ),
    Medium(Res.string.medium_articles, Res.drawable.ic_reader, "https://medium.com/einkbro"),
    Manual(Res.string.manual, Res.drawable.ic_reader, "https://plateaukao.github.io/einkbro/guide.html#overview")
}
