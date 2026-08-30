package info.plateaukao.einkbro.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.database.DomainConfigurationData
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.action_category_translation
import info.plateaukao.einkbro.resources.black_font
import info.plateaukao.einkbro.resources.bold_font
import info.plateaukao.einkbro.resources.desktop_mode
import info.plateaukao.einkbro.resources.font_size
import info.plateaukao.einkbro.resources.font_type
import info.plateaukao.einkbro.resources.list_empty
import info.plateaukao.einkbro.resources.menu_delete
import info.plateaukao.einkbro.resources.menu_invert_color
import info.plateaukao.einkbro.resources.setting_title_adblock
import info.plateaukao.einkbro.resources.setting_title_cookie
import info.plateaukao.einkbro.resources.setting_title_javascript
import info.plateaukao.einkbro.resources.setting_title_site_rules
import info.plateaukao.einkbro.resources.site_custom_css
import info.plateaukao.einkbro.resources.site_force_viewport_width
import info.plateaukao.einkbro.resources.site_post_load_js
import info.plateaukao.einkbro.resources.site_rule_part_off
import info.plateaukao.einkbro.resources.site_rules_delete_all_confirm
import info.plateaukao.einkbro.resources.site_rules_delete_confirm
import info.plateaukao.einkbro.resources.site_rules_empty_hint
import info.plateaukao.einkbro.resources.site_rules_no_overrides
import info.plateaukao.einkbro.resources.site_settings_overrides_count
import info.plateaukao.einkbro.resources.site_settings_overrides_count_plural
import info.plateaukao.einkbro.resources.translation_mode
import info.plateaukao.einkbro.resources.white_background
import info.plateaukao.einkbro.util.blockingString
import info.plateaukao.einkbro.view.compose.ListScaffold
import info.plateaukao.einkbro.view.compose.onTopBar
import info.plateaukao.einkbro.view.dialog.compose.HorizontalSeparator
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Every site rule (host and path scopes) in one list, so per-site settings
 * can be reviewed, edited or removed without visiting each site (Android
 * SiteRuleListActivity). Reached from Settings > Site Settings > Configured sites.
 */
@Composable
fun SiteRuleListScreen(
    onClose: () -> Unit,
    /** Opens the editor on exactly this rule key (`https://<key>`). */
    onEdit: (key: String) -> Unit,
) {
    val config = AppServices.config
    var version by remember { mutableStateOf(0) }
    val rules = remember(version) { config.allSiteRules() }
    val dialogManager = AppServices.dialogManager

    fun confirmDelete(rule: DomainConfigurationData) {
        dialogManager.showOkCancelDialog(
            message = blockingString(Res.string.site_rules_delete_confirm, rule.domain),
            okAction = { config.deleteSiteRule(rule.domain); version++ },
        )
    }

    fun confirmDeleteAll() {
        if (rules.isEmpty()) return
        dialogManager.showOkCancelDialog(
            message = blockingString(Res.string.site_rules_delete_all_confirm),
            okAction = { rules.forEach { config.deleteSiteRule(it.domain) }; version++ },
        )
    }

    ListScaffold(
        title = stringResource(Res.string.setting_title_site_rules),
        onBack = onClose,
        actions = {
            IconButton(onClick = { confirmDeleteAll() }) {
                Icon(
                    imageVector = Icons.Outlined.DeleteSweep,
                    contentDescription = stringResource(Res.string.menu_delete),
                    tint = MaterialTheme.colors.onTopBar,
                )
            }
        },
    ) { innerPadding ->
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.list_empty) + "\n" +
                        stringResource(Res.string.site_rules_empty_hint),
                    modifier = Modifier.padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                )
            }
            return@ListScaffold
        }
        // Keep host order, host rule first, then its path rules (allRules() is
        // already sorted that way); a divider separates hosts.
        val hosts = rules.map { it.host }.distinct()
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            hosts.forEachIndexed { hostIndex, host ->
                val hostRules = rules.filter { it.host == host }
                if (hostIndex > 0) item(key = "sep-$host") { HorizontalSeparator() }
                items(hostRules.size, key = { hostRules[it].domain }) { index ->
                    SiteRuleRow(
                        rule = hostRules[index],
                        onEdit = onEdit,
                        onDelete = { confirmDelete(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteRuleRow(
    rule: DomainConfigurationData,
    onEdit: (String) -> Unit,
    onDelete: (DomainConfigurationData) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(rule.domain) }
            // path rules indent under their host
            .padding(start = if (rule.isHostRule) 16.dp else 32.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (rule.isHostRule) rule.host else rule.path,
                fontWeight = if (rule.isHostRule) FontWeight.Bold else FontWeight.Normal,
                fontSize = 15.sp,
                color = MaterialTheme.colors.onBackground,
            )
            Text(
                text = overrideSummary(rule),
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.width(8.dp))
        OverrideCountBadge(rule.overrideCount)
        IconButton(onClick = { onDelete(rule) }) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(Res.string.menu_delete),
                tint = MaterialTheme.colors.onBackground,
            )
        }
    }
}

@Composable
private fun OverrideCountBadge(count: Int) {
    if (count <= 0) return
    val text = if (count == 1) {
        stringResource(Res.string.site_settings_overrides_count, count)
    } else {
        stringResource(Res.string.site_settings_overrides_count_plural, count)
    }
    Box(
        modifier = Modifier
            .background(MaterialTheme.colors.onBackground.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, fontSize = 11.sp, color = MaterialTheme.colors.onBackground)
    }
}

/** Which settings a rule touches, e.g. "Font size, Desktop mode, Custom CSS". */
@Composable
private fun overrideSummary(rule: DomainConfigurationData): String {
    val parts = buildList {
        if (rule.fontSize != null) add(stringResource(Res.string.font_size))
        if (rule.fontType != null) add(stringResource(Res.string.font_type))
        if (rule.boldFontStyle != null) add(stringResource(Res.string.bold_font))
        if (rule.fontBoldness != null) add(stringResource(Res.string.bold_font))
        if (rule.blackFontStyle != null) add(stringResource(Res.string.black_font))
        if (rule.shouldUseWhiteBackground != null) add(stringResource(Res.string.white_background))
        if (rule.shouldInvertColor != null) add(stringResource(Res.string.menu_invert_color))
        if (rule.desktopMode != null) add(stringResource(Res.string.desktop_mode))
        if (rule.desktopViewportWidth != null) add(stringResource(Res.string.site_force_viewport_width))
        if (rule.enableJavascript != null) add(stringResource(Res.string.setting_title_javascript))
        if (rule.enableAdBlock != null) add(stringResource(Res.string.setting_title_adblock))
        if (rule.enableCookies != null) add(stringResource(Res.string.setting_title_cookie))
        if (rule.shouldTranslateSite != null) add(stringResource(Res.string.action_category_translation))
        if (rule.translationMode != null) add(stringResource(Res.string.translation_mode))
        if (!rule.customCss.isNullOrBlank()) add(scriptPart(Res.string.site_custom_css, rule.customCssEnabled))
        if (!rule.postLoadJavascript.isNullOrBlank()) {
            add(scriptPart(Res.string.site_post_load_js, rule.postLoadJavascriptEnabled))
        }
    }.distinct()
    return if (parts.isEmpty()) stringResource(Res.string.site_rules_no_overrides) else parts.joinToString(", ")
}

/** Script name, marked "(off)" when the rule keeps the code but has it switched off. */
@Composable
private fun scriptPart(labelRes: StringResource, enabled: Boolean): String {
    val label = stringResource(labelRes)
    return if (enabled) label else stringResource(Res.string.site_rule_part_off, label)
}
