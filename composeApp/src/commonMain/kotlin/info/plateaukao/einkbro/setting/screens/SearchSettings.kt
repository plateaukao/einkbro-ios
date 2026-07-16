package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.ActionSettingItem
import info.plateaukao.einkbro.setting.BooleanSettingItem
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.ListSettingWithClassItem
import info.plateaukao.einkbro.setting.ListSettingWithStrResIdItem
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.setting.ValueSettingItem
import info.plateaukao.einkbro.view.EBToast

fun buildSearchSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        ListSettingWithStrResIdItem(
            Res.string.setting_title_search_engine,
            null,
            config = config.browser::searchEngine,
            options = listOf(
                Res.string.setting_summary_search_engine_startpage,
                Res.string.setting_summary_search_engine_startpage_de,
                Res.string.setting_summary_search_engine_baidu,
                Res.string.setting_summary_search_engine_bing,
                Res.string.setting_summary_search_engine_duckduckgo,
                Res.string.setting_summary_search_engine_google,
                Res.string.setting_summary_search_engine_searx,
                Res.string.setting_summary_search_engine_qwant,
                Res.string.setting_summary_search_engine_ecosia,
                Res.string.setting_title_searchEngine,
                Res.string.setting_summary_search_engine_yandex,
            )
        ),
        ValueSettingItem(
            Res.string.setting_title_searchEngine,
            null,
            Res.string.setting_summary_search_engine,
            config = config.browser::searchEngineUrl,
        ),
        BooleanSettingItem(
            Res.string.setting_title_search_suggestion,
            null,
            Res.string.setting_summary_search_suggestion,
            config.browser::enableSearchSuggestion,
        ),
        DividerSettingItem(),
        ValueSettingItem(
            Res.string.setting_title_process_text,
            null,
            Res.string.setting_summary_custom_process_text_url,
            config = config.browser::processTextUrl,
        ),
        BooleanSettingItem(
            Res.string.setting_title_external_search_pop,
            null,
            Res.string.setting_summary_external_search_pop,
            config.ai::externalSearchWithPopUp,
        ),
        DividerSettingItem(),
        // On Android this opens DataListActivity(WhiteListType.SplitSearch).
        ActionSettingItem(
            Res.string.setting_title_split_search_setting,
            null,
            Res.string.setting_summary_split_search_setting
        ) {
            EBToast.show(deps.context, "would open the split search settings list")
        },
        BooleanSettingItem(
            Res.string.setting_title_search_in_same_tab,
            null,
            Res.string.setting_summary_search_in_same_tab,
            config.ai::isExternalSearchInSameTab,
        ),
        DividerSettingItem(),
        ListSettingWithClassItem<ChatGPTActionInfo>(
            Res.string.setting_title_remote_query,
            null,
            config = config.ai::remoteQueryActionName,
            options = listOf("Search") + config.ai.gptActionList.map { it.name }
        ),
    )
}
