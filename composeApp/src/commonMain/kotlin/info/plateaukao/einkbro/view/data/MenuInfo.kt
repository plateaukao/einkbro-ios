package info.plateaukao.einkbro.view.data

import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.ui.graphics.vector.ImageVector

data class MenuInfo(
    val title: String,
    val drawable: DrawableResource? = null,
    val imageVector: ImageVector? = null, // for other locally created data
    
    val closeMenu: Boolean = true,
    val action: (() -> Unit)? = null,
    val longClickAction: (() -> Unit)? = null,
    val cornerDrawable: DrawableResource? = null, // optional small icon at bottom-right corner (e.g. GPT type)
)

