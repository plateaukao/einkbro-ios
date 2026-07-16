package info.plateaukao.einkbro.catalog

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.activity.AdBlockSettingScreen
import info.plateaukao.einkbro.activity.DataListScreen
import info.plateaukao.einkbro.activity.GptActionsScreen
import info.plateaukao.einkbro.activity.GptQueryListScreen
import info.plateaukao.einkbro.activity.HighlightsScreen
import info.plateaukao.einkbro.activity.SavedPagesScreen
import info.plateaukao.einkbro.activity.UserScriptListScreen
import info.plateaukao.einkbro.view.dialog.compose.ETtsVoiceDialogContent
import info.plateaukao.einkbro.view.dialog.compose.FontBoldnessContent
import info.plateaukao.einkbro.view.dialog.compose.FontBrowserDialogContent
import info.plateaukao.einkbro.view.dialog.compose.FontDialogContent
import info.plateaukao.einkbro.view.dialog.compose.HighlightStyleContent
import info.plateaukao.einkbro.view.dialog.compose.ReaderFontDialogContent
import info.plateaukao.einkbro.view.dialog.compose.ReaderSettingsDialogContent
import info.plateaukao.einkbro.view.dialog.compose.SiteSettingsDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TranslateDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TranslationConfigDialogContent
import info.plateaukao.einkbro.view.dialog.compose.TtsSettingDialogContent

/**
 * Renders dialog content the way EinkBro's Android dialog window chrome did:
 * inset, rounded corner, thin border (background_with_border_margin.xml).
 */
@Composable
fun DialogFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .wrapContentSize()
                .border(1.dp, MaterialTheme.colors.onBackground, RoundedCornerShape(5.dp)),
            shape = RoundedCornerShape(5.dp),
            color = MaterialTheme.colors.background,
        ) {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                content()
            }
        }
    }
}

val settingsSection = CatalogSection(
    title = "Settings",
    entries = listOf(
        CatalogEntry("Settings", "Full settings tree with search (all 15 screens)") { onClose ->
            info.plateaukao.einkbro.activity.SettingsScreen(onClose = onClose)
        },
    ),
)

val coreDialogsSection = CatalogSection(
    title = "Browser Dialogs",
    entries = listOf(
        CatalogEntry("Main menu", "Browser menu grid") { onClose ->
            info.plateaukao.einkbro.view.dialog.compose.MenuDialogContent(onDismiss = onClose)
        },
        CatalogEntry("Fast toggles", "Quick settings toggles") { onClose ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.FastToggleDialogContent(onDismiss = onClose)
            }
        },
        CatalogEntry("Touch areas", "Tap-to-page-turn zones config") { onClose ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.TouchAreaDialogContent(onDismiss = onClose)
            }
        },
        CatalogEntry("Toolbar actions config", "Choose visible toolbar icons") { onClose ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.ToolbarConfigDialogContent(onDismiss = onClose)
            }
        },
        CatalogEntry("Link context menu", "Long-press link menu") { onClose ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.ContextMenuDialogContent(onDismiss = onClose)
            }
        },
        CatalogEntry("Task menu", "Automation task templates") { onClose ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.TaskMenuDialogContent(onDismiss = onClose)
            }
        },
        CatalogEntry("Page AI actions", "GPT quick actions for the page") { onClose ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.PageAiActionDialogContent(onDismiss = onClose)
            }
        },
        CatalogEntry("Custom task input", "Free-form AI task prompt") { onClose ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.CustomTaskInputDialogContent(onDismiss = onClose)
            }
        },
        CatalogEntry("HTTP authentication", "Basic-auth credential dialog") { onClose ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.AuthenticationDialogContent(onDismiss = onClose)
            }
        },
        CatalogEntry("Edit GPT action", "Add/edit AI action") { onClose ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.ShowEditGptActionDialogContent(onDismiss = onClose)
            }
        },
        CatalogEntry("Text editor", "CSS/JS editor dialog") { onClose ->
            info.plateaukao.einkbro.view.dialog.compose.TextEditorDialogContent(onDismiss = onClose)
        },
        CatalogEntry("Table of contents", "EPUB/reader TOC navigation") { onClose ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.TocDialogContent(onDismiss = onClose)
            }
        },
    ),
)

val fontReaderSection = CatalogSection(
    title = "Fonts & Reader Dialogs",
    entries = listOf(
        CatalogEntry("Font settings", "Font size / type dialog") { onClose ->
            DialogFrame { FontDialogContent(onDismiss = onClose) }
        },
        CatalogEntry("Font browser", "Custom font folder browser") { onClose ->
            DialogFrame { FontBrowserDialogContent(onDismiss = onClose) }
        },
        CatalogEntry("Font boldness", "Boldness slider dialog") { _ ->
            DialogFrame { FontBoldnessContent() }
        },
        CatalogEntry("Reader font settings", "Font dialog in reader mode") { onClose ->
            DialogFrame { ReaderFontDialogContent(onDismiss = onClose) }
        },
        CatalogEntry("Reader settings", "Reader mode options dialog") { onClose ->
            DialogFrame { ReaderSettingsDialogContent(onDismiss = onClose) }
        },
        CatalogEntry("Highlight style", "Text highlight style picker") { _ ->
            DialogFrame { HighlightStyleContent() }
        },
    ),
)

val translateTtsSection = CatalogSection(
    title = "Translate & TTS Dialogs",
    entries = listOf(
        CatalogEntry("Translate panel", "Translation result panel (sample translation)") { onClose ->
            TranslateDialogContent(closeAction = onClose)
        },
        CatalogEntry("Translation config", "Per-site translation mode picker") { onClose ->
            DialogFrame { TranslationConfigDialogContent(onDismiss = onClose) }
        },
        CatalogEntry("Language settings", "Translation language picker") { onClose ->
            DialogFrame { LanguageSettingDialogContentEntry(onClose) }
        },
        CatalogEntry("TTS settings", "Read-aloud dialog, mid-reading state") { onClose ->
            DialogFrame { TtsSettingDialogContent(onDismiss = onClose) }
        },
        CatalogEntry("TTS voice picker", "Edge-TTS voice list") { _ ->
            DialogFrame { ETtsVoiceDialogContent() }
        },
        CatalogEntry("Site settings", "Per-site overrides (largest dialog)") { onClose ->
            SiteSettingsDialogContent(onDismiss = onClose)
        },
    ),
)

@Composable
private fun LanguageSettingDialogContentEntry(onClose: () -> Unit) {
    info.plateaukao.einkbro.view.dialog.compose.LanguageSettingDialogContent(onDismiss = onClose)
}

val bookmarksSection = CatalogSection(
    title = "Bookmarks & Status Bar",
    entries = listOf(
        CatalogEntry("Bookmarks", "Bookmark list with folders, edit, context menu") { onClose ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                info.plateaukao.einkbro.view.dialog.compose.BookmarksDialogContent(closeAction = onClose)
            }
        },
        CatalogEntry("Bookmark context menu", "Long-press bookmark actions") { _ ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.BookmarkContextMenuScreen()
            }
        },
        CatalogEntry("Bookmark edit", "Edit bookmark title/folder") { onClose ->
            info.plateaukao.einkbro.view.dialog.BookmarkEditContent(dismissAction = onClose)
        },
        CatalogEntry("Text-selection menu", "Action mode menu over selected text") { _ ->
            DialogFrame {
                info.plateaukao.einkbro.view.dialog.compose.PreviewActionModeMenu()
            }
        },
        CatalogEntry("Status bar", "E-ink status bar (time, page info, battery)") { _ ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                info.plateaukao.einkbro.view.statusbar.Statusbar()
            }
        },
        CatalogEntry("Toolbar config screen", "Full-screen toolbar action arrangement") { onClose ->
            info.plateaukao.einkbro.activity.ToolbarConfigScreen(onClose = onClose)
        },
        CatalogEntry("Statusbar config screen", "Choose status bar items") { onClose ->
            info.plateaukao.einkbro.activity.StatusbarConfigScreen(onClose = onClose)
        },
    ),
)

val managementSection = CatalogSection(
    title = "Management Screens",
    entries = listOf(
        CatalogEntry("Ad block settings", "Filter lists with download states") { onClose ->
            AdBlockSettingScreen(onClose = onClose)
        },
        CatalogEntry("Whitelist data", "Ad-block whitelist domains") { onClose ->
            DataListScreen(onClose = onClose)
        },
        CatalogEntry("Saved pages", "Offline saved pages list") { onClose ->
            SavedPagesScreen(onClose = onClose)
        },
        CatalogEntry("Highlights", "Articles and text highlights") { onClose ->
            HighlightsScreen(onClose = onClose)
        },
        CatalogEntry("GPT actions", "AI action editor with drag reorder") { onClose ->
            GptActionsScreen(onClose = onClose)
        },
        CatalogEntry("GPT query history", "Stored AI queries with markdown") { onClose ->
            GptQueryListScreen(onClose = onClose)
        },
        CatalogEntry("User scripts", "Userscript manager") { onClose ->
            UserScriptListScreen(onClose = onClose)
        },
    ),
)
