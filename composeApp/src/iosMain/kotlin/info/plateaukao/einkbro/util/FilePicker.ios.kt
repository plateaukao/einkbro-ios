package info.plateaukao.einkbro.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeFont
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
actual object FilePicker {
    // Strong ref: the picker holds its delegate weakly.
    private var delegate: PickerDelegate? = null

    actual fun pick(kind: PickKind, onResult: (name: String, bytes: ByteArray) -> Unit) {
        val types = when (kind) {
            PickKind.Any -> listOf(UTTypeItem, UTTypeData)
            // public.font covers .ttf / .otf / .ttc; woff files are a web
            // delivery format users rarely hold as files.
            PickKind.Font -> listOf(UTTypeFont)
        }
        val controller = UIDocumentPickerViewController(
            forOpeningContentTypes = types,
            asCopy = true,
        )
        val d = PickerDelegate(onResult)
        delegate = d
        controller.delegate = d
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(controller, animated = true, completion = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PickerDelegate(
    private val onResult: (String, ByteArray) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
        // asCopy=true copies the file to a temp URL the app can read directly.
        val data = NSData.dataWithContentsOfURL(url) ?: return
        onResult(url.lastPathComponent ?: "file", data.toByteArray())
    }
}
