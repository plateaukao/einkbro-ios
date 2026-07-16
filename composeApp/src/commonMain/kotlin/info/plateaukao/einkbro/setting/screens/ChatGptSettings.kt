package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.activity.SettingRoute
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.ActionSettingItem
import info.plateaukao.einkbro.setting.BooleanSettingItem
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.ListSettingWithEnumItem
import info.plateaukao.einkbro.setting.NavigateSettingItem
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.setting.ValueSettingItem
import info.plateaukao.einkbro.view.EBToast

fun buildChatGptSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        ListSettingWithEnumItem(
            Res.string.setting_title_default_ai_engine,
            null,
            null,
            config.ai::defaultGptEngine,
            listOf(
                Res.string.openai,
                Res.string.self_hosted,
                Res.string.google_gemini
            )
        ),
        NavigateSettingItem(
            Res.string.openai,
            destination = SettingRoute.GptOpenAi,
        ),
        NavigateSettingItem(
            Res.string.openai_compatible_server,
            destination = SettingRoute.GptSelfHosted,
        ),
        NavigateSettingItem(
            Res.string.google_gemini,
            destination = SettingRoute.GptGemini,
        ),
        DividerSettingItem(Res.string.web_content_processing),
        ListSettingWithEnumItem(
            Res.string.summary_gpt_type,
            null,
            Res.string.setting_summary_summary_gpt_type,
            config.ai::gptForSummary,
            listOf(
                Res.string.system_default,
                Res.string.openai,
                Res.string.self_hosted,
                Res.string.google_gemini
            )
        ),
        ValueSettingItem(
            Res.string.setting_title_gpt_prompt_for_web_page,
            null,
            Res.string.setting_summary_gpt_prompt_for_web_page,
            config.ai::gptUserPromptForWebPage
        ),
        ListSettingWithEnumItem(
            Res.string.web_processing_gpt_type,
            null,
            Res.string.setting_summary_web_processing_gpt_type,
            config.ai::gptForChatWeb,
            listOf(
                Res.string.system_default,
                Res.string.openai,
                Res.string.self_hosted,
                Res.string.google_gemini
            )
        ),
        BooleanSettingItem(
            Res.string.use_it_on_dict_search,
            null,
            Res.string.setting_summary_search_in_dict,
            config.ai::externalSearchWithGpt
        ),
        BooleanSettingItem(
            Res.string.setting_title_chat_stream,
            null,
            Res.string.setting_summary_chat_stream,
            config.ai::enableOpenAiStream
        ),
        DividerSettingItem(),
        // On Android these launch GptActionsActivity / GptQueryListActivity.
        ActionSettingItem(
            Res.string.setting_title_gpt_action_list,
            null,
            Res.string.setting_summary_gpt_action_list,
        ) { deps.onOpenGptActions() },
        ActionSettingItem(
            Res.string.setting_title_gpt_query_list,
            null,
            Res.string.setting_summary_gpt_query_list,
        ) { deps.onOpenGptQueries() },
    )
}

fun buildGptOpenAiSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        ValueSettingItem(
            Res.string.setting_title_edit_gpt_api_key,
            null,
            Res.string.setting_summary_edit_gpt_api_key,
            config.ai::gptApiKey
        ),
        ValueSettingItem(
            Res.string.setting_title_gpt_model_name,
            null,
            Res.string.setting_summary_gpt_model_name,
            config.ai::gptModel
        ),
        DividerSettingItem(),
        BooleanSettingItem(
            Res.string.use_it_on_tts,
            null,
            Res.string.setting_summary_use_gpt_for_tts,
            config.tts::useOpenAiTts
        ),
        ValueSettingItem(
            Res.string.setting_title_gpt_audio_model_name,
            null,
            Res.string.setting_summary_gpt_audio_model_name,
            config.ai::gptVoiceModel
        ),
        ValueSettingItem(
            Res.string.setting_title_gpt_prompt_for_tts,
            null,
            Res.string.setting_summary_gpt_prompt_for_tts,
            config.ai::gptVoicePrompt
        ),
    )
}

fun buildGptSelfHostedSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        ValueSettingItem(
            Res.string.setting_title_custom_gpt_url,
            null,
            Res.string.setting_summary_custom_gpt_url,
            config.ai::gptUrl
        ),
        ValueSettingItem(
            Res.string.setting_title_other_model_name,
            null,
            Res.string.setting_summary_other_model_name,
            config.ai::alternativeModel
        ),
    )
}

fun buildGptGeminiSettingItems(deps: SettingScreenDeps): List<SettingItemInterface> {
    val config = deps.config
    return listOf(
        ValueSettingItem(
            Res.string.setting_title_gemini_key,
            null,
            Res.string.setting_summary_gemini_key,
            config.ai::geminiApiKey
        ),
        ValueSettingItem(
            Res.string.setting_title_gemini_model_name,
            null,
            Res.string.setting_summary_gemini_model_name,
            config.ai::geminiModel
        ),
    )
}
