package info.plateaukao.einkbro

// internal: keeps DEBUG and friends out of the exported ObjC framework header,
// where the DEBUG name collides with Xcode's `#define DEBUG 1`.
internal object BuildConfig {
    const val DEBUG: Boolean = true
    const val VERSION_NAME: String = "0.1.0-ios"
    const val APPLICATION_ID: String = "info.plateaukao.einkbro.ios"
}
