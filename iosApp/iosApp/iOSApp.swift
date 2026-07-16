import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
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
