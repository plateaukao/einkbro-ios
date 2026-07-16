package info.plateaukao.einkbro.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Multiplatform substitutes for Android idioms used throughout the ported
 * Compose UI, keeping call sites as close to the original as possible.
 */

/** Stand-in for androidx.compose.ui.platform.LocalContext. */
val LocalContext = staticCompositionLocalOf { Context() }

/** Drop-in for Android's context.getString(resId) with typed CMP resources. */
fun Context.getString(res: StringResource): String = blockingString(res)

fun Context.getString(res: StringResource, vararg args: Any): String =
    blockingString(res, *args)

fun blockingString(res: StringResource, vararg args: Any): String =
    runBlocking { if (args.isEmpty()) getString(res) else getString(res, *args) }

/** Replacement for LocalConfiguration.current.screenWidthDp. */
@Composable
fun screenWidthDp(): Int {
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    return with(density) { size.width.toDp() }.value.toInt()
}

/** Replacement for LocalConfiguration.current.screenHeightDp. */
@Composable
fun screenHeightDp(): Int {
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    return with(density) { size.height.toDp() }.value.toInt()
}
