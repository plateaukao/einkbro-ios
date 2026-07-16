package info.plateaukao.einkbro.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIScreen

@OptIn(ExperimentalForeignApi::class)
actual object PlatformScreen {
    actual fun isWideLayout(): Boolean = UIScreen.mainScreen.bounds.useContents {
        minOf(size.width, size.height) >= 600.0
    }

    actual fun isLandscape(): Boolean = UIScreen.mainScreen.bounds.useContents {
        size.width > size.height
    }
}
