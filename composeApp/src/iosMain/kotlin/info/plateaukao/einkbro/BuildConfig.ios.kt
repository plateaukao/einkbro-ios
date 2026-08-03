package info.plateaukao.einkbro

import platform.Foundation.NSBundle

internal actual fun appVersionName(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
        ?: "0.0"
