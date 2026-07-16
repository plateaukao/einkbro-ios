package info.plateaukao.einkbro.unit

import android.content.Context
import info.plateaukao.einkbro.util.PlatformScreen

/**
 * Port of the Android ViewUnit helper. Layout queries delegate to
 * [PlatformScreen] (UIScreen-backed on iOS) so iPad gets two-column layouts.
 */
object ViewUnit {
    fun isLandscape(context: Context): Boolean = PlatformScreen.isLandscape()
    fun isWideLayout(context: Context): Boolean = PlatformScreen.isWideLayout()
    fun isTablet(context: Context): Boolean = PlatformScreen.isWideLayout()
    fun dpToPixel(context: Context, dp: Int): Float = dp.toFloat()
    fun dpToPixel(dp: Int): Float = dp.toFloat()
}
