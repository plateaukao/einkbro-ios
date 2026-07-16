package info.plateaukao.einkbro.unit

import android.content.Context

/**
 * Stub of the Android ViewUnit helper. Reports phone-portrait defaults;
 * grows as ported UI needs more of the original surface.
 */
object ViewUnit {
    fun isLandscape(context: Context): Boolean = false
    fun isWideLayout(context: Context): Boolean = false
    fun isTablet(context: Context): Boolean = false
    fun dpToPixel(context: Context, dp: Int): Float = dp.toFloat()
    fun dpToPixel(dp: Int): Float = dp.toFloat()
}
