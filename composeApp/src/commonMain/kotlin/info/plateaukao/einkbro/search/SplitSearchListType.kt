package info.plateaukao.einkbro.search

import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.activity.BaseWhiteListType
import info.plateaukao.einkbro.browser.DomainInterface
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.preference.SplitSearchItemInfo
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.description_split_screen_string_pattern
import info.plateaukao.einkbro.resources.description_split_screen_title
import info.plateaukao.einkbro.resources.split_search_settings
import info.plateaukao.einkbro.view.dialog.DialogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

class SplitSearchListType : BaseWhiteListType() {
    override val titleId: StringResource
        get() = Res.string.split_search_settings
    override val domainHandler: DomainInterface by lazy { SplitSearchHandler() }

    override fun addDomain(
        lifecycleScope: CoroutineScope,
        dialogManager: DialogManager,
        postAction: (String) -> Unit,
    ) {
        lifecycleScope.launch {
            val title = dialogManager.getTextInput(
                titleId, Res.string.description_split_screen_title, ""
            )?.trim() ?: return@launch
            val stringPattern = dialogManager.getTextInput(
                titleId, Res.string.description_split_screen_string_pattern, ""
            )?.trim() ?: return@launch
            if (title.isNotBlank() && stringPattern.isNotBlank()) {
                (domainHandler as SplitSearchHandler).addSplitSearchItem(
                    SplitSearchItemInfo(title, stringPattern, true)
                )
                postAction(title)
            }
        }
    }
}

private class SplitSearchHandler : DomainInterface {
    private val configManager: ConfigManager = AppServices.config

    init {
        // Seed sample entries so the split-search list renders data in the catalog.
        if (configManager.splitSearchItemInfoList.isEmpty()) {
            configManager.addSplitSearchItem(
                SplitSearchItemInfo("Wikipedia", "https://en.wikipedia.org/wiki/%s", true)
            )
            configManager.addSplitSearchItem(
                SplitSearchItemInfo("Jisho", "https://jisho.org/search/%s", true)
            )
        }
    }

    override suspend fun getDomains(): List<String> =
        configManager.splitSearchItemInfoList.map { it.title }

    override suspend fun addDomain(domain: String) { /* use addSplitSearchItem instead */
    }

    fun addSplitSearchItem(splitSearchItemInfo: SplitSearchItemInfo) {
        configManager.addSplitSearchItem(splitSearchItemInfo)
    }

    override suspend fun deleteDomain(domain: String) {
        configManager.deleteSplitSearchItem(SplitSearchItemInfo(domain, domain, true))
    }

    override suspend fun deleteAllDomains() = configManager.deleteAllSplitSearchItems()
}
