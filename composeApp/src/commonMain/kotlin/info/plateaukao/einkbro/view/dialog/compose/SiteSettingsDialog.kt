package info.plateaukao.einkbro.view.dialog.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CodeOff
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Copyright
import androidx.compose.material.icons.outlined.DoNotDisturbOff
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.InvertColorsOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.twotone.Cookie
import androidx.compose.material.icons.twotone.Copyright
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import info.plateaukao.einkbro.resources.whitelist_add
import info.plateaukao.einkbro.resources.menu_edit
import info.plateaukao.einkbro.resources.default_value_hint
import info.plateaukao.einkbro.resources.translation_mode
import info.plateaukao.einkbro.resources.site_settings_reset_to_inherited
import info.plateaukao.einkbro.resources.site_settings_remove_rule
import info.plateaukao.einkbro.resources.site_settings_inherited_from
import info.plateaukao.einkbro.resources.site_settings_other_rules
import info.plateaukao.einkbro.resources.site_settings_scope_whole_site
import info.plateaukao.einkbro.resources.site_settings_scope
import info.plateaukao.einkbro.preference.DomainConfigManager
import info.plateaukao.einkbro.database.SiteRuleKey
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Switch
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.database.DomainConfigurationData
import info.plateaukao.einkbro.preference.FontType
import info.plateaukao.einkbro.preference.TranslationMode
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.util.Uri
import info.plateaukao.einkbro.util.screenHeightDp
import info.plateaukao.einkbro.view.EBToast
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

const val DEFAULT_DESKTOP_VIEWPORT_WIDTH = 1280

/**
 * Entry composable; was SiteSettingsDialogFragment.Content().
 * The AdBlock/Javascript/Cookie whitelist services don't exist in the catalog,
 * so the "default" hints reflect the global toggles only.
 */
@Composable
fun SiteSettingsDialogContent(
    url: String = "https://example.com/article",
    onDismiss: () -> Unit = {},
) {
    val config = AppServices.config
    val context = LocalContext.current
    val maxDialogHeight = (screenHeightDp() * 0.85f).dp
    SiteSettingsContent(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .widthIn(min = 300.dp, max = 420.dp)
            .heightIn(max = maxDialogHeight),
        url = url,
        domainConfigs = config.domain,
        globalFontSize = config.display.fontSize,
        globalFontType = config.display.fontType,
        globalBoldFont = config.display.boldFontStyle,
        globalBlackFont = config.display.blackFontStyle,
        globalFontBoldness = config.display.fontBoldness,
        globalDesktopMode = config.browser.desktop,
        defaultViewportWidth = DEFAULT_DESKTOP_VIEWPORT_WIDTH,
        globalJavascript = config.browser.enableJavascript,
        globalAdBlock = config.browser.adBlock,
        globalCookies = config.browser.cookies,
        globalTranslationMode = config.translation.translationMode,
        onEditText = { title, _, _ ->
            // Android opens TextEditorDialogFragment for CSS/JS editing.
            EBToast.show(context, "would open text editor: $title")
        },
        onSave = { updatedConfig ->
            config.updateDomainConfig(updatedConfig)
            onDismiss()
        },
        onDeleteRule = { key ->
            config.deleteSiteRule(key)
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

/**
 * Editor for one site rule (Android SiteSettingsContent). The rule being
 * edited is chosen with the scope picker under the title: the host (whole
 * site) or any path prefix of the current URL. Fields left at "default" fall
 * through to the next rule up the chain (path -> host -> global), and the hint
 * under each row says where the value currently comes from.
 */
@Composable
fun SiteSettingsContent(
    modifier: Modifier,
    url: String,
    domainConfigs: DomainConfigManager,
    globalFontSize: Int,
    globalFontType: FontType,
    globalBoldFont: Boolean,
    globalBlackFont: Boolean,
    globalFontBoldness: Int,
    globalDesktopMode: Boolean,
    defaultViewportWidth: Int,
    globalJavascript: Boolean,
    globalAdBlock: Boolean,
    globalCookies: Boolean,
    globalTranslationMode: TranslationMode,
    onEditText: (title: String, initial: String, onResult: (String) -> Unit) -> Unit,
    onSave: (DomainConfigurationData) -> Unit,
    onDeleteRule: (key: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val host = SiteRuleKey.hostOfUrl(url).orEmpty()
    val candidateKeys = remember(url) { SiteRuleKey.candidateKeysFor(url) }
    // Open on the rule that is actually in effect for this page.
    val initialKey = remember(url) {
        domainConfigs.matchingKeys(url).firstOrNull() ?: candidateKeys.firstOrNull() ?: host
    }
    var selectedKey by remember(url) { mutableStateOf(initialKey) }

    val rule = remember(selectedKey) { domainConfigs.getRuleOrNew(selectedKey) }
    val ruleExists = remember(selectedKey) { domainConfigs.getRule(selectedKey) != null }
    val isHostRule = SiteRuleKey.pathOf(selectedKey).isEmpty()
    // What this rule inherits: the chain for the rule's own scope, minus itself.
    val scopeUrl = "https://$selectedKey"
    val parentRules = remember(selectedKey) {
        domainConfigs.matchingRules(scopeUrl).filter { it.domain != selectedKey }
    }
    val inherited = remember(selectedKey) { domainConfigs.getInheritedConfig(scopeUrl, selectedKey) }

    val defaultHint = stringResource(Res.string.default_value_hint)
    @Composable
    fun hintFor(field: (DomainConfigurationData) -> Any?): String {
        val source = parentRules.firstOrNull { field(it) != null } ?: return defaultHint
        return stringResource(Res.string.site_settings_inherited_from, source.domain)
    }
    // stringResource is @Composable, so resolve hints up front
    val hintFontSize = hintFor { it.fontSize }
    val hintFontType = hintFor { it.fontType }
    val hintBoldFont = hintFor { it.boldFontStyle }
    val hintBlackFont = hintFor { it.blackFontStyle }
    val hintFontBoldness = hintFor { it.fontBoldness }
    val hintWhiteBackground = hintFor { it.shouldUseWhiteBackground }
    val hintInvertColor = hintFor { it.shouldInvertColor }
    val hintDesktopMode = hintFor { it.desktopMode }
    val hintViewportWidth = hintFor { it.desktopViewportWidth }
    val hintJavascript = hintFor { it.enableJavascript }
    val hintAdBlock = hintFor { it.enableAdBlock }
    val hintCookies = hintFor { it.enableCookies }
    val hintTranslateSite = hintFor { it.shouldTranslateSite }
    val hintTranslationMode = hintFor { it.translationMode }

    var fontSize by remember(selectedKey) { mutableStateOf(rule.fontSize) }
    var fontType by remember(selectedKey) { mutableStateOf(rule.fontType) }
    var boldFont by remember(selectedKey) { mutableStateOf(rule.boldFontStyle) }
    var blackFont by remember(selectedKey) { mutableStateOf(rule.blackFontStyle) }
    var fontBoldness by remember(selectedKey) { mutableStateOf(rule.fontBoldness) }
    var whiteBackground by remember(selectedKey) { mutableStateOf(rule.shouldUseWhiteBackground) }
    var invertColor by remember(selectedKey) { mutableStateOf(rule.shouldInvertColor) }
    var desktopMode by remember(selectedKey) { mutableStateOf(rule.desktopMode) }
    var viewportWidth by remember(selectedKey) { mutableStateOf(rule.desktopViewportWidth) }
    var javascript by remember(selectedKey) { mutableStateOf(rule.enableJavascript) }
    var adBlock by remember(selectedKey) { mutableStateOf(rule.enableAdBlock) }
    var cookies by remember(selectedKey) { mutableStateOf(rule.enableCookies) }
    var translateSite by remember(selectedKey) { mutableStateOf(rule.shouldTranslateSite) }
    var translationMode by remember(selectedKey) { mutableStateOf(rule.translationMode) }
    var customCss by remember(selectedKey) { mutableStateOf(rule.customCss.orEmpty()) }
    var customCssEnabled by remember(selectedKey) { mutableStateOf(rule.customCssEnabled) }
    var postLoadJs by remember(selectedKey) { mutableStateOf(rule.postLoadJavascript.orEmpty()) }
    var postLoadJsEnabled by remember(selectedKey) { mutableStateOf(rule.postLoadJavascriptEnabled) }

    fun buildRule() = rule.copy(
        fontSize = fontSize,
        fontType = fontType,
        boldFontStyle = boldFont,
        blackFontStyle = blackFont,
        fontBoldness = fontBoldness,
        shouldUseWhiteBackground = whiteBackground,
        shouldInvertColor = invertColor,
        desktopMode = desktopMode,
        desktopViewportWidth = viewportWidth,
        enableJavascript = javascript,
        enableAdBlock = adBlock,
        enableCookies = cookies,
        shouldTranslateSite = translateSite,
        translationMode = translationMode,
        customCss = customCss.ifBlank { null },
        postLoadJavascript = postLoadJs.ifBlank { null },
        customCssEnabled = customCssEnabled,
        postLoadJavascriptEnabled = postLoadJsEnabled,
    )

    val overrideCount = buildRule().overrideCount

    // Effective fallbacks for each row: inherited from a parent rule, else global.
    val fbFontSize = inherited.fontSize ?: globalFontSize
    val fbFontType = inherited.fontType ?: globalFontType
    val fbBoldFont = inherited.boldFontStyle ?: globalBoldFont
    val fbBlackFont = inherited.blackFontStyle ?: globalBlackFont
    val fbFontBoldness = inherited.fontBoldness ?: globalFontBoldness
    val fbWhiteBackground = inherited.shouldUseWhiteBackground ?: false
    val fbInvertColor = inherited.shouldInvertColor ?: false
    val fbDesktopMode = inherited.desktopMode ?: globalDesktopMode
    val fbViewportWidth = inherited.desktopViewportWidth ?: defaultViewportWidth
    val fbJavascript = inherited.enableJavascript ?: globalJavascript
    val fbAdBlock = inherited.enableAdBlock ?: globalAdBlock
    val fbCookies = inherited.enableCookies ?: globalCookies
    val fbTranslateSite = inherited.shouldTranslateSite ?: false
    val fbTranslationMode = inherited.translationMode ?: globalTranslationMode

    Column(
        modifier = modifier.padding(16.dp),
    ) {
        DialogTitle(host = host, overrideCount = overrideCount)

        Spacer(Modifier.height(4.dp))

        ScopePicker(
            selectedKey = selectedKey,
            candidateKeys = candidateKeys,
            existingRules = domainConfigs.rulesForHost(host),
            onSelect = { selectedKey = it },
        )

        Spacer(Modifier.height(8.dp))
        HorizontalSeparator()

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(Res.string.setting_section_typography))

            NullableIntStepperRow(
                label = stringResource(Res.string.font_size),
                value = fontSize,
                globalValue = fbFontSize,
                min = 50,
                max = 250,
                step = 10,
                displayValue = { "${it}%" },
                fallbackHint = hintFontSize,
                onValueChange = { fontSize = it },
            )

            NullableDropdownRow(
                label = stringResource(Res.string.font_type),
                value = fontType,
                globalValue = fbFontType,
                options = FontType.entries,
                optionLabel = { stringResource(it.resId) },
                fallbackHint = hintFontType,
                onValueChange = { fontType = it },
            )

            NullableBooleanRow(
                label = stringResource(Res.string.bold_font),
                value = boldFont,
                globalValue = fbBoldFont,
                defaultOnActivate = true,
                onIconRes = Res.drawable.ic_bold_font_active,
                offIconRes = Res.drawable.ic_bold_font,
                fallbackHint = hintBoldFont,
                onValueChange = { boldFont = it },
            )

            NestedNullableIntStepper(
                value = fontBoldness,
                globalValue = fbFontBoldness,
                min = 500,
                max = 900,
                step = 100,
                enabled = (boldFont ?: fbBoldFont),
                onValueChange = { fontBoldness = it },
                hint = hintFontBoldness,
            )

            NullableBooleanRow(
                label = stringResource(Res.string.black_font),
                value = blackFont,
                globalValue = fbBlackFont,
                defaultOnActivate = true,
                onIcon = Icons.TwoTone.Copyright,
                offIcon = Icons.Outlined.Copyright,
                fallbackHint = hintBlackFont,
                onValueChange = { blackFont = it },
            )

            SectionHeader(stringResource(Res.string.setting_title_ui))

            NullableBooleanRow(
                label = stringResource(Res.string.white_background),
                value = whiteBackground,
                globalValue = fbWhiteBackground,
                defaultOnActivate = true,
                onIconRes = Res.drawable.ic_white_background_active,
                offIconRes = Res.drawable.ic_white_background,
                fallbackHint = hintWhiteBackground,
                onValueChange = { whiteBackground = it },
            )

            NullableBooleanRow(
                label = stringResource(Res.string.menu_invert_color),
                value = invertColor,
                globalValue = fbInvertColor,
                defaultOnActivate = true,
                onIcon = Icons.Outlined.InvertColorsOff,
                offIcon = Icons.Outlined.InvertColors,
                fallbackHint = hintInvertColor,
                onValueChange = { invertColor = it },
            )

            SectionHeader(stringResource(Res.string.setting_title_behavior))

            NullableBooleanRow(
                label = stringResource(Res.string.desktop_mode),
                value = desktopMode,
                globalValue = fbDesktopMode,
                onIconRes = Res.drawable.icon_desktop_activate,
                offIconRes = Res.drawable.icon_desktop,
                fallbackHint = hintDesktopMode,
                onValueChange = { desktopMode = it },
            )

            NestedNullableIntStepper(
                value = viewportWidth,
                globalValue = fbViewportWidth,
                min = 800,
                max = 2400,
                step = 80,
                enabled = (desktopMode ?: fbDesktopMode),
                onValueChange = { viewportWidth = it },
                label = stringResource(Res.string.site_force_viewport_width),
                hint = if (inherited.desktopViewportWidth != null) hintViewportWidth
                    else stringResource(Res.string.site_force_viewport_width_hint),
            )

            NullableBooleanRow(
                label = stringResource(Res.string.setting_title_javascript),
                value = javascript,
                globalValue = fbJavascript,
                onIcon = Icons.Outlined.Code,
                offIcon = Icons.Outlined.CodeOff,
                fallbackHint = hintJavascript,
                onValueChange = { javascript = it },
            )

            NullableBooleanRow(
                label = stringResource(Res.string.setting_title_adblock),
                value = adBlock,
                globalValue = fbAdBlock,
                onIcon = Icons.Outlined.Block,
                offIcon = Icons.Outlined.DoNotDisturbOff,
                fallbackHint = hintAdBlock,
                onValueChange = { adBlock = it },
            )

            NullableBooleanRow(
                label = stringResource(Res.string.setting_title_cookie),
                value = cookies,
                globalValue = fbCookies,
                onIcon = Icons.TwoTone.Cookie,
                offIcon = Icons.Outlined.Cookie,
                fallbackHint = hintCookies,
                onValueChange = { cookies = it },
            )

            SectionHeader(stringResource(Res.string.action_category_translation))

            NullableBooleanRow(
                label = stringResource(Res.string.action_category_translation),
                value = translateSite,
                globalValue = fbTranslateSite,
                defaultOnActivate = true,
                onIcon = Icons.Outlined.Translate,
                offIcon = Icons.Outlined.Translate,
                fallbackHint = hintTranslateSite,
                onValueChange = { translateSite = it },
            )
            NestedNullableDropdown(
                value = translationMode,
                globalValue = fbTranslationMode,
                options = TranslationMode.entries,
                optionLabel = { stringResource(it.labelResId) },
                enabled = (translateSite ?: fbTranslateSite),
                fallbackHint = hintTranslationMode,
                onValueChange = { translationMode = it },
            )

            SectionHeader(stringResource(Res.string.setting_section_advanced))

            val cssLabel = stringResource(Res.string.site_custom_css)
            EditTextButtonRow(
                label = cssLabel,
                hasContent = customCss.isNotBlank(),
                enabled = customCssEnabled,
                onEnabledChange = { customCssEnabled = it },
                onClick = {
                    onEditText(cssLabel, customCss) {
                        // Newly written code is meant to take effect; saving the
                        // editor unchanged must not flip a switched-off script back on.
                        if (it.isNotBlank() && it != customCss) customCssEnabled = true
                        customCss = it
                    }
                },
            )

            val jsLabel = stringResource(Res.string.site_post_load_js)
            EditTextButtonRow(
                label = jsLabel,
                hasContent = postLoadJs.isNotBlank(),
                enabled = postLoadJsEnabled,
                onEnabledChange = { postLoadJsEnabled = it },
                onClick = {
                    onEditText(jsLabel, postLoadJs) {
                        if (it.isNotBlank() && it != postLoadJs) postLoadJsEnabled = true
                        postLoadJs = it
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalSeparator()
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (!isHostRule && ruleExists) {
                // A path rule with nothing set is pointless, so "reset" removes it.
                OutlinedButton(
                    onClick = { onDeleteRule(selectedKey) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colors.onBackground,
                    ),
                ) {
                    Text(stringResource(Res.string.site_settings_remove_rule), fontSize = 13.sp)
                }
            } else {
                OutlinedButton(
                    onClick = {
                        fontSize = null; fontType = null; boldFont = null; blackFont = null
                        fontBoldness = null; desktopMode = null; viewportWidth = null; javascript = null
                        adBlock = null; cookies = null
                        whiteBackground = null; invertColor = null
                        translateSite = null; translationMode = null
                        customCss = ""; postLoadJs = ""
                        customCssEnabled = true; postLoadJsEnabled = true
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colors.onBackground,
                    ),
                ) {
                    Text(
                        stringResource(
                            if (isHostRule) Res.string.reset_to_global
                            else Res.string.site_settings_reset_to_inherited
                        ),
                        fontSize = 13.sp,
                    )
                }
            }
            Button(
                onClick = {
                    val updated = buildRule()
                    if (!isHostRule && updated.isEmpty) {
                        // saving an empty path rule == removing it
                        if (ruleExists) onDeleteRule(selectedKey) else onDismiss()
                    } else {
                        onSave(updated)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.onBackground,
                    contentColor = MaterialTheme.colors.background,
                ),
            ) {
                // android.R.string.ok has no CMP counterpart in strings.xml
                Text("OK", fontSize = 13.sp)
            }
        }
    }
}

/**
 * "Apply to" row: a dropdown over every path prefix of the current URL plus
 * any other rules already stored for this host. Rules that exist are marked
 * with their override count so the user can see which scopes are in play.
 */
@Composable
private fun ScopePicker(
    selectedKey: String,
    candidateKeys: List<String>,
    existingRules: List<DomainConfigurationData>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val existingByKey = existingRules.associateBy { it.domain }
    val otherRules = existingRules.filter { it.domain !in candidateKeys }
    val color = MaterialTheme.colors.onBackground

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.site_settings_scope),
            fontSize = 12.sp,
            color = color.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = scopeLabel(selectedKey),
                modifier = Modifier.weight(1f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                candidateKeys.forEach { key ->
                    ScopeMenuItem(
                        key = key,
                        overrideCount = existingByKey[key]?.overrideCount ?: 0,
                        selected = key == selectedKey,
                        onClick = { onSelect(key); expanded = false },
                    )
                }
                if (otherRules.isNotEmpty()) {
                    HorizontalSeparator()
                    Text(
                        text = stringResource(Res.string.site_settings_other_rules),
                        fontSize = 11.sp,
                        color = color.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    otherRules.forEach { other ->
                        ScopeMenuItem(
                            key = other.domain,
                            overrideCount = other.overrideCount,
                            selected = other.domain == selectedKey,
                            onClick = { onSelect(other.domain); expanded = false },
                        )
                    }
                }
            }
        }
    }
}

/** Host rules read as the whole site; path rules show just their path. */
@Composable
private fun scopeLabel(key: String): String {
    val path = SiteRuleKey.pathOf(key)
    return if (path.isEmpty()) stringResource(Res.string.site_settings_scope_whole_site) else path
}

@Composable
private fun ScopeMenuItem(
    key: String,
    overrideCount: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(onClick = onClick) {
        Text(
            text = scopeLabel(key),
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (overrideCount > 0) {
            Spacer(Modifier.width(12.dp))
            OverrideBadge(overrideCount)
        }
    }
}

/** Nested dropdown override (translation mode) under its parent toggle, with a rail. */
@Composable
private fun <T> NestedNullableDropdown(
    value: T?,
    globalValue: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    enabled: Boolean,
    fallbackHint: String,
    onValueChange: (T?) -> Unit,
) {
    val hasOverride = value != null
    val effectiveValue = value ?: globalValue
    var expanded by remember { mutableStateOf(false) }
    val color = if (enabled && hasOverride) MaterialTheme.colors.onBackground
        else MaterialTheme.colors.onBackground.copy(alpha = 0.55f)

    NestedRail(enabled = enabled) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = hasOverride,
                enabled = enabled,
                onCheckedChange = { checked -> onValueChange(if (checked) effectiveValue else null) },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colors.onBackground,
                    uncheckedColor = MaterialTheme.colors.onBackground,
                    checkmarkColor = MaterialTheme.colors.background,
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(Res.string.translation_mode), fontSize = 13.sp, color = color)
                if (!hasOverride) {
                    Text(
                        text = fallbackHint,
                        fontSize = 10.sp,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.45f),
                    )
                }
            }
            OutlinedButton(
                onClick = { if (enabled && hasOverride) expanded = true },
                enabled = enabled && hasOverride,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            ) {
                Text(optionLabel(effectiveValue), fontSize = 12.sp)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(onClick = {
                            onValueChange(option)
                            expanded = false
                        }) {
                            Text(optionLabel(option))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogTitle(host: String, overrideCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = host.ifBlank { "—" },
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colors.onBackground,
        )
        if (overrideCount > 0) {
            OverrideBadge(overrideCount)
        }
    }
}

@Composable
private fun OverrideBadge(count: Int) {
    val text = if (count == 1) {
        stringResource(Res.string.site_settings_overrides_count, count)
    } else {
        stringResource(Res.string.site_settings_overrides_count_plural, count)
    }
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.12f),
                shape = CircleShape,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onBackground,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

/**
 * A row with a nullable boolean override. Left checkbox toggles per-site override.
 * State icon before the label shows current value. Tapping the icon enables override
 * (if not yet) and flips the value.
 */
@Composable
private fun NullableBooleanRow(
    label: String,
    value: Boolean?,
    globalValue: Boolean,
    defaultOnActivate: Boolean = globalValue,
    onIcon: ImageVector? = null,
    offIcon: ImageVector? = null,
    onIconRes: DrawableResource? = null,
    offIconRes: DrawableResource? = null,
    fallbackHint: String = stringResource(Res.string.default_value_hint),
    onValueChange: (Boolean?) -> Unit,
) {
    val hasOverride = value != null
    val effectiveValue = value ?: globalValue
    val color = if (hasOverride) MaterialTheme.colors.onBackground
        else MaterialTheme.colors.onBackground.copy(alpha = 0.55f)
    val defaultHint = fallbackHint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = hasOverride,
            onCheckedChange = { checked ->
                onValueChange(if (checked) defaultOnActivate else null)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colors.onBackground,
                uncheckedColor = MaterialTheme.colors.onBackground,
                checkmarkColor = MaterialTheme.colors.background,
            ),
        )
        StateIcon(
            isOn = effectiveValue,
            onIcon = onIcon, offIcon = offIcon,
            onIconRes = onIconRes, offIconRes = offIconRes,
            tint = color,
            modifier = Modifier.noRippleClickable {
                if (hasOverride) {
                    onValueChange(!effectiveValue)
                } else {
                    onValueChange(!effectiveValue)
                }
            },
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 14.sp, color = color)
            if (!hasOverride) {
                Text(
                    text = defaultHint,
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun StateIcon(
    isOn: Boolean,
    onIcon: ImageVector?,
    offIcon: ImageVector?,
    onIconRes: DrawableResource?,
    offIconRes: DrawableResource?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val iconModifier = modifier.size(28.dp)
    if (onIcon != null && offIcon != null) {
        Icon(
            imageVector = if (isOn) onIcon else offIcon,
            contentDescription = null,
            modifier = iconModifier,
            tint = tint,
        )
    } else if (onIconRes != null && offIconRes != null) {
        Icon(
            imageVector = vectorResource(if (isOn) onIconRes else offIconRes),
            contentDescription = null,
            modifier = iconModifier,
            tint = tint,
        )
    }
}

/**
 * Translation: checkbox + translate icon + label on one row,
 * mode dropdown nested below with a left rail (only enabled when translateSite is true).
 */
@Composable
private fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )

/**
 * A row with a nullable int stepper override (font size, boldness).
 * E-ink-friendly: discrete [−] value [+] controls instead of a continuous slider.
 */
@Composable
private fun NullableIntStepperRow(
    label: String,
    value: Int?,
    globalValue: Int,
    min: Int,
    max: Int,
    step: Int,
    displayValue: (Int) -> String,
    fallbackHint: String = stringResource(Res.string.default_value_hint),
    onValueChange: (Int?) -> Unit,
) {
    val hasOverride = value != null
    val effectiveValue = value ?: globalValue
    val color = if (hasOverride) MaterialTheme.colors.onBackground
        else MaterialTheme.colors.onBackground.copy(alpha = 0.55f)
    val defaultHint = fallbackHint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = hasOverride,
            onCheckedChange = { checked ->
                onValueChange(if (checked) effectiveValue else null)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colors.onBackground,
                uncheckedColor = MaterialTheme.colors.onBackground,
                checkmarkColor = MaterialTheme.colors.background,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 14.sp, color = color)
            if (!hasOverride) {
                Text(
                    text = defaultHint,
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.45f),
                )
            }
        }
        Stepper(
            value = effectiveValue,
            min = min,
            max = max,
            step = step,
            enabled = hasOverride,
            displayValue = displayValue,
            onValueChange = { onValueChange(it) },
        )
    }
}

/**
 * Stepper used standalone, nested under a parent row (e.g. font boldness under bold font).
 * Renders inside a NestedRail so the dependency is visible.
 */
@Composable
private fun NestedNullableIntStepper(
    value: Int?,
    globalValue: Int,
    min: Int,
    max: Int,
    step: Int,
    enabled: Boolean,
    onValueChange: (Int?) -> Unit,
    label: String? = null,
    hint: String? = null,
) {
    val hasOverride = value != null
    val effectiveValue = value ?: globalValue
    val color = if (enabled && hasOverride) MaterialTheme.colors.onBackground
        else MaterialTheme.colors.onBackground.copy(alpha = 0.55f)

    NestedRail(enabled = enabled) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = hasOverride,
                onCheckedChange = { checked ->
                    if (enabled) onValueChange(if (checked) effectiveValue else null)
                },
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colors.onBackground,
                    uncheckedColor = MaterialTheme.colors.onBackground,
                    checkmarkColor = MaterialTheme.colors.background,
                ),
            )
            if (label != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = label, fontSize = 13.sp, color = color)
                    if (!hasOverride && hint != null) {
                        Text(
                            text = hint,
                            fontSize = 10.sp,
                            color = MaterialTheme.colors.onBackground.copy(alpha = 0.45f),
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            Stepper(
                value = effectiveValue,
                min = min,
                max = max,
                step = step,
                enabled = enabled && hasOverride,
                displayValue = { it.toString() },
                onValueChange = { onValueChange(it) },
            )
        }
    }
}

@Composable
private fun Stepper(
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    enabled: Boolean,
    displayValue: (Int) -> String,
    onValueChange: (Int) -> Unit,
) {
    val tint = if (enabled) MaterialTheme.colors.onBackground
        else MaterialTheme.colors.onBackground.copy(alpha = 0.3f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton(
            icon = Icons.Outlined.Remove,
            contentDescription = "−",
            tint = tint,
            enabled = enabled && value > min,
            onClick = { onValueChange((value - step).coerceAtLeast(min)) },
        )
        Text(
            text = displayValue(value),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            modifier = Modifier.width(48.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton(
            icon = Icons.Outlined.Add,
            contentDescription = "+",
            tint = tint,
            enabled = enabled && value < max,
            onClick = { onValueChange((value + step).coerceAtMost(max)) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * A row with a nullable enum dropdown override.
 */
@Composable
private fun <T> NullableDropdownRow(
    label: String,
    value: T?,
    globalValue: T,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    fallbackHint: String = stringResource(Res.string.default_value_hint),
    onValueChange: (T?) -> Unit,
) {
    val hasOverride = value != null
    val effectiveValue = value ?: globalValue
    var expanded by remember { mutableStateOf(false) }
    val color = if (hasOverride) MaterialTheme.colors.onBackground
        else MaterialTheme.colors.onBackground.copy(alpha = 0.55f)
    val defaultHint = fallbackHint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = hasOverride,
            onCheckedChange = { checked ->
                onValueChange(if (checked) effectiveValue else null)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colors.onBackground,
                uncheckedColor = MaterialTheme.colors.onBackground,
                checkmarkColor = MaterialTheme.colors.background,
            ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, fontSize = 14.sp, color = color)
            if (!hasOverride) {
                Text(
                    text = defaultHint,
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.45f),
                )
            }
        }
        OutlinedButton(
            onClick = { if (hasOverride) expanded = true },
            enabled = hasOverride,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colors.onBackground,
            ),
        ) {
            Text(optionLabel(effectiveValue), fontSize = 12.sp)
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(onClick = {
                        onValueChange(option)
                        expanded = false
                    }) {
                        Text(optionLabel(option))
                    }
                }
            }
        }
    }
}

/**
 * A row that opens a full-screen editor for a per-site CSS / JS override,
 * with a switch to keep the code but stop applying it.
 */
@Composable
private fun EditTextButtonRow(
    label: String,
    hasContent: Boolean,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    // Dim when there is nothing to apply, or the saved script is switched off.
    val active = hasContent && enabled
    val color = if (active) MaterialTheme.colors.onBackground
        else MaterialTheme.colors.onBackground.copy(alpha = 0.55f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Temporarily disable the script without deleting it. Always shown so
        // the switch is discoverable; inert until there is code to switch.
        Switch(
            checked = active,
            enabled = hasContent,
            onCheckedChange = onEnabledChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colors.primary,
                checkedTrackColor = MaterialTheme.colors.primary,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.Gray,
                disabledUncheckedThumbColor = Color.LightGray,
                disabledUncheckedTrackColor = Color.LightGray,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = color,
        )
        OutlinedButton(
            onClick = onClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        ) {
            Text(
                stringResource(if (hasContent) Res.string.menu_edit else Res.string.whitelist_add),
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Wraps nested content with a left vertical rail to indicate dependence on the row above.
 * Uses drawBehind so we don't introduce IntrinsicSize.Min, which collapses weight(1f)
 * children to their min intrinsic width inside verticalScroll.
 */
@Composable
private fun NestedRail(enabled: Boolean, content: @Composable () -> Unit) {
    val railColor = if (enabled) MaterialTheme.colors.onBackground.copy(alpha = 0.35f)
        else MaterialTheme.colors.onBackground.copy(alpha = 0.15f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val railX = 20.dp.toPx()
                val railW = 2.dp.toPx()
                drawRect(
                    color = railColor,
                    topLeft = androidx.compose.ui.geometry.Offset(railX, 0f),
                    size = androidx.compose.ui.geometry.Size(railW, size.height),
                )
            }
            .padding(start = 38.dp, end = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
