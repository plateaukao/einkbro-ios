import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @State private var statusBarHidden = false
    @State private var deferBottomEdge = false

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
                .statusBarHiddenCompat(statusBarHidden)
                .defersBottomEdgeGesturesCompat(deferBottomEdge)
                .onOpenURL { url in
                    // File opens (.webarchive) carry a percent-encoded URL; pass the
                    // decoded filesystem path so the Kotlin side needs no decoding.
                    let value = url.isFileURL ? "file://" + url.path : url.absoluteString
                    MainViewControllerKt.handleExternalUrl(url: value)
                }
                .onAppear {
                    // The hide-statusbar pref lives in Kotlin (BrowserScreen);
                    // SwiftUI owns the actual status-bar lever, so bridge it.
                    HostBridge.shared.statusBarHiddenListener = { hidden in
                        statusBarHidden = hidden.boolValue
                    }
                    // Edge-to-edge toolbar: Kotlin decides when bottom chrome
                    // occupies the home-indicator band; SwiftUI owns the
                    // system-gesture deferral lever (UIKit resolves it via the
                    // hosting controller, not the wrapped Compose VC).
                    HostBridge.shared.defersBottomSystemGestureListener = { on in
                        deferBottomEdge = on.boolValue
                    }
                }
        }
    }
}

extension View {
    // statusBar(hidden:) was renamed in iOS 16; deployment target is 15.
    @ViewBuilder func statusBarHiddenCompat(_ hidden: Bool) -> some View {
        if #available(iOS 16.0, *) {
            self.statusBarHidden(hidden)
        } else {
            self.statusBar(hidden: hidden)
        }
    }

    // defersSystemGestures(on:) only exists on iOS 16+; on 15 the Kotlin side
    // never asks for it (HostBridge.supportsBottomGestureDeferral is false).
    @ViewBuilder func defersBottomEdgeGesturesCompat(_ on: Bool) -> some View {
        if #available(iOS 16.0, *) {
            self.defersSystemGestures(on: on ? .bottom : [])
        } else {
            self
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    // Spelled-out context type: the Kotlin framework exports a `Context` class
    // (Android shim) that would otherwise shadow SwiftUI's `Context` typealias.
    func makeUIViewController(
        context: UIViewControllerRepresentableContext<ComposeView>
    ) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(
        _ uiViewController: UIViewController,
        context: UIViewControllerRepresentableContext<ComposeView>
    ) {}
}
