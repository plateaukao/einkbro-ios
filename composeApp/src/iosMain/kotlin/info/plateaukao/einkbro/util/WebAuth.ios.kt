package info.plateaukao.einkbro.util

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

// File-level strong refs: the session holds its presentation provider weakly,
// and an unreferenced session is deallocated before the user finishes signing in.
private val presentationProvider =
    object : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
        override fun presentationAnchorForWebAuthenticationSession(
            session: ASWebAuthenticationSession,
        ): UIWindow = UIApplication.sharedApplication.keyWindow ?: UIWindow()
    }
private var activeSession: ASWebAuthenticationSession? = null

actual object WebAuth {
    actual suspend fun authenticate(url: String, callbackScheme: String): String? =
        suspendCancellableCoroutine { cont ->
            // ASWebAuthenticationSession must be created and started on the main queue.
            dispatch_async(dispatch_get_main_queue()) {
                val nsUrl = NSURL.URLWithString(url)
                if (nsUrl == null) {
                    if (cont.isActive) cont.resume(null)
                    return@dispatch_async
                }
                val session = ASWebAuthenticationSession(
                    uRL = nsUrl,
                    callbackURLScheme = callbackScheme,
                ) { callbackUrl, _ ->
                    activeSession = null
                    if (cont.isActive) cont.resume(callbackUrl?.absoluteString)
                }
                session.presentationContextProvider = presentationProvider
                // Share Safari's Google session so a signed-in user isn't re-prompted
                // for credentials, only for consent.
                session.prefersEphemeralWebBrowserSession = false
                activeSession = session
                session.start()
            }
        }
}
