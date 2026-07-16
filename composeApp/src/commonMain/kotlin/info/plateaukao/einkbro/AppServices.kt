package info.plateaukao.einkbro

import android.content.Context
import android.content.SharedPreferences
import info.plateaukao.einkbro.database.BookmarkManager
import info.plateaukao.einkbro.preference.ConfigManager
import info.plateaukao.einkbro.userscript.UserScriptManager
import info.plateaukao.einkbro.view.dialog.DialogManager
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * Session-wide singletons for the ported UI. On Android these come from Koin
 * modules wired in EinkBroApplication; here one object owns them all, and a
 * Koin container is still started for code that resolves via KoinComponent.
 */
object AppServices {
    val context = Context()
    val sharedPreferences = SharedPreferences()
    val database: info.plateaukao.einkbro.database.AppDatabase =
        info.plateaukao.einkbro.database.createAppDatabase()
    val bookmarkManager = BookmarkManager(database)
    val userScriptManager = UserScriptManager(database)
    val dialogManager = DialogManager(context)

    val config: ConfigManager

    init {
        startKoin {
            modules(
                module {
                    single { bookmarkManager }
                    single { sharedPreferences }
                    single { database }
                }
            )
        }
        config = ConfigManager(context, sharedPreferences)
    }
}
