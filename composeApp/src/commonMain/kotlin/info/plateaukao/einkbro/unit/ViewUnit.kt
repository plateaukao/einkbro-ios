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

    // Android ViewUnit.createCountString: "current/total" rendered by
    // TabCountIcon as a fraction; collapses to the plain total when trivial.
    fun createCountString(superScript: Int, subScript: Int): String {
        if (subScript == 0 || superScript == 0) return "1"

        if (subScript == superScript) return subScript.toString()

        return "$superScript/$subScript"
    }
}
