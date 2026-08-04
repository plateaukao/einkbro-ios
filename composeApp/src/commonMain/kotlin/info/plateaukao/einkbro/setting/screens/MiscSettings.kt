package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.activity.SettingRoute.UserAgent
import info.plateaukao.einkbro.preference.HighlightStyle
import info.plateaukao.einkbro.preference.TranslationTextStyle
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.ActionSettingItem
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.ListSettingWithEnumItem
import info.plateaukao.einkbro.setting.NavigateSettingItem
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.setting.ValueSettingItem
import info.plateaukao.einkbro.view.dialog.TranslationLanguageDialog
import kotlinx.coroutines.launch

fun buildMiscSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        ListSettingWithEnumItem(
            Res.string.setting_title_highlight_style,
            null,
            Res.string.setting_summary_highlight_style,
            config = config.display::highlightStyle,
            options = HighlightStyle.entries
                .map { it.stringResId },
        ),
        ListSettingWithEnumItem(
            Res.string.setting_title_translation_style,
            null,
            Res.string.setting_summary_translation_style,
            config = config.translation::translationTextStyle,
            options = TranslationTextStyle.entries.map { it.stringResId },
        ),
        NavigateSettingItem(
            Res.string.setting_title_userAgent,
            null,
            destination = UserAgent
        ),
        ValueSettingItem(
            Res.string.setting_title_edit_homepage,
            null,
            config = config::favoriteUrl,
            showValue = false
        ),
        // On Android this opens PrinterDocumentPaperSizeDialog.
        ActionSettingItem(Res.string.setting_title_pdf_paper_size, null) {
            deps.scope.launch {
                val sizes = info.plateaukao.einkbro.preference.PaperSize.entries
                val picked = info.plateaukao.einkbro.AppServices.dialogManager
                    .getSelectedOptionWithString(
                        Res.string.setting_title_pdf_paper_size,
                        sizes.map { it.sizeString },
                        deps.config.display.pdfPaperSize.ordinal,
                    ) ?: return@launch
                deps.config.display.pdfPaperSize = sizes[picked]
            }
        },
        DividerSettingItem(),
//        BooleanSettingItem(
//            Res.string.setting_title_enable_inplace_translate,
//            null,
//            Res.string.setting_summary_enable_inplace_translate,
//            config::enableInplaceParagraphTranslate
//        ),
        // Android changes the target language from the on-page language label
        // shown while a translation is active; iOS has no such overlay, so the
        // language is configured here instead.
        ActionSettingItem(Res.string.translation_language, null) {
            deps.scope.launch {
                val language = TranslationLanguageDialog(deps.context).show()
                    ?: return@launch
                config.translation.translationLanguage = language
            }
        },
        ValueSettingItem(
            Res.string.setting_title_translated_langs,
            null,
            Res.string.setting_summary_translated_langs,
            config.translation::preferredTranslateLanguageString
        ),
        ActionSettingItem(
            Res.string.setting_dual_caption,
            null,
            Res.string.setting_summary_dual_caption,
        ) {
            deps.scope.launch {
                TranslationLanguageDialog(deps.context).showDualCaptionLocale()
            }
        },
    )
}
