package info.plateaukao.einkbro.catalog

import androidx.compose.runtime.Composable

/**
 * Registry of demo screens for the UI catalog. Bundles register their entries
 * in [catalogSections]; the App renders home navigation from this list.
 */
class CatalogEntry(
    val name: String,
    val description: String = "",
    val content: @Composable (onClose: () -> Unit) -> Unit,
)

class CatalogSection(
    val title: String,
    val entries: List<CatalogEntry>,
)
