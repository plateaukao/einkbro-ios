package info.plateaukao.einkbro.activity

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.activity.SettingRoute.About
import info.plateaukao.einkbro.activity.SettingRoute.Backup
import info.plateaukao.einkbro.activity.SettingRoute.Behavior
import info.plateaukao.einkbro.activity.SettingRoute.ChatGPT
import info.plateaukao.einkbro.activity.SettingRoute.DataControl
import info.plateaukao.einkbro.activity.SettingRoute.Gesture
import info.plateaukao.einkbro.activity.SettingRoute.Main
import info.plateaukao.einkbro.activity.SettingRoute.Misc
import info.plateaukao.einkbro.activity.SettingRoute.Search
import info.plateaukao.einkbro.activity.SettingRoute.StartControl
import info.plateaukao.einkbro.activity.SettingRoute.Toolbar
import info.plateaukao.einkbro.activity.SettingRoute.Ui
import info.plateaukao.einkbro.activity.SettingRoute.UserAgent
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.*
import info.plateaukao.einkbro.setting.DividerSettingItem
import info.plateaukao.einkbro.setting.GesturePickerScreen
import info.plateaukao.einkbro.setting.SearchSettingScreen
import info.plateaukao.einkbro.setting.SettingItemInterface
import info.plateaukao.einkbro.setting.SettingScreen
import info.plateaukao.einkbro.setting.screens.BackupOps
import info.plateaukao.einkbro.setting.screens.SettingScreenDeps
import info.plateaukao.einkbro.setting.screens.buildAboutSettingItems
import info.plateaukao.einkbro.setting.screens.buildBackupSettingItems
import info.plateaukao.einkbro.setting.screens.buildBehaviorSettingItems
import info.plateaukao.einkbro.setting.screens.buildChatGptSettingItems
import info.plateaukao.einkbro.setting.screens.buildClearDataSettingItems
import info.plateaukao.einkbro.setting.screens.buildGestureSettingItems
import info.plateaukao.einkbro.setting.screens.buildGptGeminiSettingItems
import info.plateaukao.einkbro.setting.screens.buildGptOpenAiSettingItems
import info.plateaukao.einkbro.setting.screens.buildGptSelfHostedSettingItems
import info.plateaukao.einkbro.setting.screens.buildMainSettingItems
import info.plateaukao.einkbro.setting.screens.buildMiscSettingItems
import info.plateaukao.einkbro.setting.screens.buildSearchSettingItems
import info.plateaukao.einkbro.setting.screens.buildStartSettingItems
import info.plateaukao.einkbro.setting.screens.buildToolbarSettingItems
import info.plateaukao.einkbro.setting.screens.buildUiSettingItems
import info.plateaukao.einkbro.setting.screens.buildUserAgentSettingItems
import info.plateaukao.einkbro.unit.IntentUnit
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.dialog.DialogManager
import info.plateaukao.einkbro.view.compose.MyTheme
import android.content.Context
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

enum class SettingRoute(val titleId: StringResource) {
    Main(Res.string.settings),
    Ui(Res.string.setting_title_ui),
    Toolbar(Res.string.setting_title_toolbar),
    Behavior(Res.string.setting_title_behavior),
    Gesture(Res.string.setting_gestures),
    Backup(Res.string.setting_title_data),
    StartControl(Res.string.setting_title_start_control),
    DataControl(Res.string.setting_title_clear_control),
    UserAgent(Res.string.setting_title_userAgent),
    Search(Res.string.setting_title_search),
    About(Res.string.title_about),
    ChatGPT(Res.string.setting_title_chat_gpt),
    GptOpenAi(Res.string.openai),
    GptSelfHosted(Res.string.openai_compatible_server),
    GptGemini(Res.string.google_gemini),
    Misc(Res.string.misc),
    GesturePicker(Res.string.setting_gestures);
}

/**
 * Real backup operations (parity Phase J): export builds a ZIP / bookmark JSON
 * and hands it to the iOS share sheet; import uses the system file picker and
 * restores through [BackupManager]. LAN share/receive is deferred (needs the
 * multicast entitlement, which requires an Apple developer provisioning setup).
 */
private class RealBackupOps(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
) : BackupOps {
    override fun exportAppData() {
        scope.launch {
            val bytes = info.plateaukao.einkbro.backup.BackupManager.exportBackupZip()
            val path = info.plateaukao.einkbro.util.FileStore.writeBytes(
                "backup", "einkbro-backup.zip", bytes,
            )
            if (path != null) info.plateaukao.einkbro.util.FileStore.share(path)
            else EBToast.show(context, "Couldn't create backup")
        }
    }

    override fun importAppData() {
        info.plateaukao.einkbro.util.FilePicker.pick { _, bytes ->
            scope.launch {
                val ok = info.plateaukao.einkbro.backup.BackupManager.importBackupZip(bytes)
                EBToast.show(
                    context,
                    if (ok) "Backup restored — relaunch to apply all settings"
                    else "Not a valid EinkBro backup",
                )
            }
        }
    }

    override fun shareAppData() {
        scope.launch {
            val bytes = info.plateaukao.einkbro.backup.BackupManager.exportBackupZip()
            info.plateaukao.einkbro.util.LanShare.serveBytes(bytes) { err ->
                EBToast.show(context, err)
            }
            info.plateaukao.einkbro.AppServices.dialogManager.showOkCancelDialog(
                title = "Share app data",
                message = "Broadcasting on the local network — start Receive on the other device, then tap OK when done.",
                okAction = { info.plateaukao.einkbro.util.LanShare.stop() },
                showNegativeButton = false,
            )
        }
    }

    override fun receiveAppData() {
        info.plateaukao.einkbro.util.LanShare.receiveBytes(
            onError = { EBToast.show(context, it) },
            onConnected = { EBToast.show(context, "Receiving app data…") },
            onReceived = { bytes ->
                scope.launch {
                    val ok = info.plateaukao.einkbro.backup.BackupManager.importBackupZip(bytes)
                    // Close the "waiting" dialog now that the transfer finished.
                    DialogManager.pendingOkCancel.value = null
                    EBToast.show(
                        context,
                        if (ok) "Backup restored — relaunch to apply all settings"
                        else "Received data is not a valid EinkBro backup",
                    )
                }
            },
        )
        info.plateaukao.einkbro.AppServices.dialogManager.showOkCancelDialog(
            title = "Receive app data",
            message = "Waiting for a device sharing app data on the local network…",
            okAction = { info.plateaukao.einkbro.util.LanShare.stop() },
            showNegativeButton = false,
        )
    }

    override fun exportBookmarks() {
        scope.launch {
            val text = info.plateaukao.einkbro.backup.BackupManager.exportBookmarksJson()
            val path = info.plateaukao.einkbro.util.FileStore.writeBytes(
                "backup", "bookmarks.json", text.encodeToByteArray(),
            )
            if (path != null) info.plateaukao.einkbro.util.FileStore.share(path)
            else EBToast.show(context, "Couldn't export bookmarks")
        }
    }

    override fun importBookmarks() {
        info.plateaukao.einkbro.util.FilePicker.pick { _, bytes ->
            scope.launch {
                info.plateaukao.einkbro.backup.BackupManager.importBookmarks(bytes.decodeToString())
                EBToast.show(context, "Bookmarks imported")
            }
        }
    }
}

/**
 * Ported from SettingActivity: the whole settings NavHost with top bar and
 * in-settings search, backed by AppServices.config.
 */
@Composable
fun SettingsScreen(
    onClose: () -> Unit = {},
    onOpenUserScripts: () -> Unit = {},
    onOpenGptActions: () -> Unit = {},
    onOpenGptQueries: () -> Unit = {},
    onOpenToolbarConfig: () -> Unit = {},
    onOpenStatusbarConfig: () -> Unit = {},
    onOpenAdBlockSettings: () -> Unit = {},
    onOpenWhitelist: (WhiteListType) -> Unit = {},
    // Android's SettingActivity accepts a route extra (IntentUnit.gotoSettings)
    // so callers like the touch-area dialog can land directly on a sub-screen.
    initialRoute: SettingRoute = Main,
) {
    val config = AppServices.config
    val dialogManager = AppServices.dialogManager
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val deps = remember {
        SettingScreenDeps(
            context, config, scope, RealBackupOps(context, scope),
            onOpenUserScripts, onOpenGptActions, onOpenGptQueries,
            onOpenToolbarConfig, onOpenStatusbarConfig, onOpenAdBlockSettings, onOpenWhitelist,
        )
    }

    val mainSettings = remember { buildMainSettingItems() }
    val uiSettingItems = remember { buildUiSettingItems(deps) }
    val behaviorSettingItems = remember { buildBehaviorSettingItems(deps) }
    val toolbarSettingItems = remember { buildToolbarSettingItems(deps) }
    val gestureSettingItems = remember { buildGestureSettingItems(deps) }
    val searchSettingItems = remember { buildSearchSettingItems(deps) }
    val dataSettingItems = remember { buildBackupSettingItems(deps) }
    val clearDataSettingItems = remember { buildClearDataSettingItems(deps) }
    val miscSettingItems = remember { buildMiscSettingItems(deps) }
    val userAgentSettingItems = remember { buildUserAgentSettingItems(deps) }
    val chatGptSettingItems = remember { buildChatGptSettingItems(deps) }
    val gptOpenAiSettingItems = remember { buildGptOpenAiSettingItems(deps) }
    val gptSelfHostedSettingItems = remember { buildGptSelfHostedSettingItems(deps) }
    val gptGeminiSettingItems = remember { buildGptGeminiSettingItems(deps) }
    val startSettingItems = remember { buildStartSettingItems(deps) }

    val allSearchableSettings: List<Pair<StringResource, SettingItemInterface>> = remember {
        listOf(
            Ui.titleId to uiSettingItems,
            Toolbar.titleId to toolbarSettingItems,
            Behavior.titleId to behaviorSettingItems,
            Gesture.titleId to gestureSettingItems,
            Search.titleId to searchSettingItems,
            Backup.titleId to dataSettingItems,
            DataControl.titleId to clearDataSettingItems,
            StartControl.titleId to startSettingItems,
            Misc.titleId to miscSettingItems,
            ChatGPT.titleId to chatGptSettingItems,
            SettingRoute.GptOpenAi.titleId to gptOpenAiSettingItems,
            SettingRoute.GptSelfHosted.titleId to gptSelfHostedSettingItems,
            SettingRoute.GptGemini.titleId to gptGeminiSettingItems,
            UserAgent.titleId to userAgentSettingItems,
        ).flatMap { (categoryResId, items) ->
            items.filter { it !is DividerSettingItem }
                .map { categoryResId to it }
        }
    }

    // On Android this opened the URL in BrowserActivity and finished.
    val handleLink: (String) -> Unit = { url ->
        IntentUnit.launchUrl(context, url)
        onClose()
    }

    val navController: NavHostController = rememberNavController()
    MyTheme {
        val backStackEntry = navController.currentBackStackEntryAsState()
        val currentScreen =
            SettingRoute.valueOf(backStackEntry.value?.destination?.route ?: initialRoute.name)
        var isSearching by rememberSaveable { mutableStateOf(false) }
        var searchQuery by rememberSaveable { mutableStateOf("") }

        Scaffold(
            // Keep the top bar and content clear of the iOS status bar / home
            // indicator (the Android Activity handled system windows itself).
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            topBar = {
                if (isSearching) {
                    SearchSettingBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = { isSearching = false; searchQuery = "" }
                    )
                } else {
                    SettingBar(
                        currentScreen = currentScreen,
                        navigateUp = {
                            if (navController.previousBackStackEntry != null) navController.navigateUp()
                            else onClose()
                        },
                        close = onClose,
                        onSearch = { isSearching = true }
                    )
                }
            }
        ) { innerPadding ->
            if (isSearching) {
                SearchSettingScreen(
                    query = searchQuery,
                    allSettings = allSearchableSettings,
                    navController = navController,
                    dialogManager = dialogManager,
                    linkAction = handleLink,
                    modifier = Modifier.padding(innerPadding),
                )
            } else NavHost(
                navController = navController,
                startDestination = initialRoute.name,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(1)) },
                exitTransition = { fadeOut(animationSpec = tween(1)) },
            ) {
                val action = handleLink
                composable(Main.name) {
                    SettingScreen(navController, mainSettings, dialogManager, action, 2)
                }
                composable(Ui.name) {
                    SettingScreen(navController, uiSettingItems, dialogManager, action, 1)
                }
                composable(Toolbar.name) {
                    SettingScreen(navController, toolbarSettingItems, dialogManager, action, 1)
                }
                composable(Behavior.name) {
                    SettingScreen(navController, behaviorSettingItems, dialogManager, action, 1)
                }
                composable(Gesture.name) {
                    SettingScreen(navController, gestureSettingItems, dialogManager, action, 2)
                }
                composable(SettingRoute.GesturePicker.name) {
                    GesturePickerScreen(navController)
                }
                composable(Backup.name) {
                    SettingScreen(navController, dataSettingItems, dialogManager, action, 1)
                }
                composable(StartControl.name) {
                    SettingScreen(navController, startSettingItems, dialogManager, action, 1)
                }
                composable(DataControl.name) {
                    SettingScreen(navController, clearDataSettingItems, dialogManager, action, 1)
                }
                composable(UserAgent.name) {
                    SettingScreen(navController, userAgentSettingItems, dialogManager, action, 1)
                }
                composable(Misc.name) {
                    SettingScreen(navController, miscSettingItems, dialogManager, action, 1)
                }
                composable(ChatGPT.name) {
                    SettingScreen(navController, chatGptSettingItems, dialogManager, action, 1)
                }
                composable(SettingRoute.GptOpenAi.name) {
                    SettingScreen(navController, gptOpenAiSettingItems, dialogManager, action, 1)
                }
                composable(SettingRoute.GptSelfHosted.name) {
                    SettingScreen(navController, gptSelfHostedSettingItems, dialogManager, action, 1)
                }
                composable(SettingRoute.GptGemini.name) {
                    SettingScreen(navController, gptGeminiSettingItems, dialogManager, action, 1)
                }
                composable(Search.name) {
                    SettingScreen(navController, searchSettingItems, dialogManager, action, 1)
                }
                composable(About.name) {
                    SettingScreen(navController, buildAboutSettingItems(deps), dialogManager, action, 2)
                }
            }
        }
    }
}

@Composable
fun SettingBar(
    currentScreen: SettingRoute,
    navigateUp: () -> Unit,
    close: () -> Unit,
    onSearch: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                stringResource(currentScreen.titleId),
                color = MaterialTheme.colors.onPrimary
            )
        },
        navigationIcon = {
            IconButton(onClick = navigateUp) {
                Icon(
                    tint = MaterialTheme.colors.onPrimary,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back)
                )
            }
        },
        actions = {
            IconButton(onClick = onSearch) {
                Icon(
                    tint = MaterialTheme.colors.onPrimary,
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(Res.string.search_hint)
                )
            }
            if (currentScreen != SettingRoute.Main) {
                IconButton(onClick = close) {
                    Icon(
                        tint = MaterialTheme.colors.onPrimary,
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.back)
                    )
                }
            }
        }
    )
}

@Composable
fun SearchSettingBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        stringResource(Res.string.search_settings_hint),
                        color = MaterialTheme.colors.onPrimary.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                colors = TextFieldDefaults.textFieldColors(
                    textColor = MaterialTheme.colors.onPrimary,
                    cursorColor = MaterialTheme.colors.onPrimary,
                    backgroundColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    tint = MaterialTheme.colors.onPrimary,
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back)
                )
            }
        },
    )
}
