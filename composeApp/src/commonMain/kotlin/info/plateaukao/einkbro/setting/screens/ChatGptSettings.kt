package info.plateaukao.einkbro.setting.screens

import info.plateaukao.einkbro.activity.SettingRoute
import info.plateaukao.einkbro.data.remote.ApiResult
import info.plateaukao.einkbro.data.remote.OpenAiRepository
import info.plateaukao.einkbro.preference.ChatGPTActionInfo
import info.plateaukao.einkbro.preference.GptActionType
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

// Fires a tiny chat request with the engine's currently saved key/model and toasts
// the outcome, so a freshly entered key or model name can be verified in place.
// The model is read lazily at click time to pick up edits made just above the button.
private fun testConnectionItem(
    deps: SettingScreenDeps,
    actionType: GptActionType,
    model: () -> String,
) = ActionSettingItem(
    Res.string.setting_test_connection,
    null,
    Res.string.setting_summary_test_connection,
) {
    EBToast.showShort(deps.context, Res.string.test_connection_testing)
    deps.scope.launch {
        val modelName = model()
        val result = OpenAiRepository().testConnection(
            ChatGPTActionInfo(actionType = actionType, model = modelName)
        )
        EBToast.show(
            deps.context,
            when (result) {
                is ApiResult.Success ->
                    getString(Res.string.test_connection_success, modelName)

                is ApiResult.Failure ->
                    getString(Res.string.test_connection_failed, result.message)
            }
        )
    }
}

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
        // No "use AI in dictionary search" item: that switch drives Android's
        // PROCESS_TEXT dict flow, which has no iOS counterpart.
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
        testConnectionItem(deps, GptActionType.OpenAi) { config.ai.gptModel },
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
        testConnectionItem(deps, GptActionType.SelfHosted) { config.ai.alternativeModel },
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
        testConnectionItem(deps, GptActionType.Gemini) { config.ai.geminiModel },
    )
}
