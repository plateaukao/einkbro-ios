package info.plateaukao.einkbro.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.plateaukao.einkbro.util.System
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Ad-block whitelist stub, seeded with sample entries for the catalog. */
class AdBlock : BaseWebConfig(
    initialDomains = listOf(
        "example.com",
        "docs.kotlinlang.org",
    )
)

// ---------------------------------------------------------------------------
// Minimal stand-ins for the io.github.edsuns.adfilter library used by the
// ad-block filter-list settings screen (AdBlockSettingScreen). Filter lists
// live in memory; "download" simulates a short download then marks success.
// ---------------------------------------------------------------------------

enum class DownloadState {
    NONE,
    ENQUEUED,
    DOWNLOADING,
    INSTALLING,
    SUCCESS,
    FAILED,
    CANCELLED,
}

class Filter(
    val id: String,
    var name: String = "",
    val url: String = "",
    var isEnabled: Boolean = true,
    var downloadState: DownloadState = DownloadState.NONE,
    var updateTime: Long = 0L,
    var filtersCount: Int = 0,
) {
    fun hasDownloaded(): Boolean = updateTime > 0L

    fun copy(): Filter = Filter(
        id = id,
        name = name,
        url = url,
        isEnabled = isEnabled,
        downloadState = downloadState,
        updateTime = updateTime,
        filtersCount = filtersCount,
    )
}

class FilterViewModel : ViewModel() {

    private val _filters = MutableStateFlow(
        listOf(
            Filter(
                id = "easylist",
                name = "EasyList",
                url = "https://easylist.to/easylist/easylist.txt",
                isEnabled = true,
                downloadState = DownloadState.SUCCESS,
                updateTime = System.currentTimeMillis() - 86_400_000L,
                filtersCount = 48_320,
            ),
            Filter(
                id = "easyprivacy",
                name = "EasyPrivacy",
                url = "https://easylist.to/easylist/easyprivacy.txt",
                isEnabled = false,
                downloadState = DownloadState.SUCCESS,
                updateTime = System.currentTimeMillis() - 7L * 86_400_000L,
                filtersCount = 17_204,
            ),
            Filter(
                id = "custom",
                name = "",
                url = "https://filters.example.com/custom.txt",
                isEnabled = false,
                downloadState = DownloadState.NONE,
            ),
        ).associateBy { it.id }
    )
    val filters: StateFlow<Map<String, Filter>> = _filters

    fun addFilter(name: String, url: String): Filter {
        val filter = Filter(id = "filter-${System.currentTimeMillis()}", name = name, url = url)
        _filters.value = _filters.value + (filter.id to filter)
        return filter
    }

    fun removeFilter(id: String) {
        _filters.value = _filters.value - id
    }

    fun setFilterEnabled(id: String, enabled: Boolean) {
        mutate(id) { it.isEnabled = enabled }
    }

    /** Simulates the enqueue -> download -> install -> success lifecycle. */
    fun download(id: String) {
        viewModelScope.launch {
            mutate(id) { it.downloadState = DownloadState.ENQUEUED }
            delay(400)
            mutate(id) { it.downloadState = DownloadState.DOWNLOADING }
            delay(800)
            mutate(id) {
                it.downloadState = DownloadState.SUCCESS
                it.updateTime = System.currentTimeMillis()
                if (it.filtersCount == 0) it.filtersCount = 12_345
            }
        }
    }

    private fun mutate(id: String, block: (Filter) -> Unit) {
        val current = _filters.value[id] ?: return
        val updated = current.copy().also(block)
        _filters.value = _filters.value + (id to updated)
    }
}

/** Stand-in for io.github.edsuns.adfilter.AdFilter's singleton accessor. */
object AdFilter {
    val viewModel = FilterViewModel()
    fun get(): AdFilter = this
}
