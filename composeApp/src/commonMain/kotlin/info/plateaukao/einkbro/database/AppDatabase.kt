package info.plateaukao.einkbro.database

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema history:
 *  v1 — bookmarks, history, favicons, domain_configuration (Phase 2).
 *  v2 — articles + highlights (Phase 5 text-selection highlights).
 *  v3 — saved_pages (Phase 7 offline archives).
 *  v4 — user_scripts + user_script_values (parity Phase H userscripts).
 *  v5 — chat_gpt_query (parity Phase K AI query persistence).
 *  v6 — video_transcripts (Gemini transcripts of caption-less YouTube videos).
 *  v7 — chat_sessions (chat-with-web conversations, saved by chat.html).
 *  v8 — chat_sessions.webContent (per-session page text for context restore).
 */
@Database(
    entities = [
        Bookmark::class,
        HistoryRecord::class,
        FaviconInfo::class,
        DomainConfiguration::class,
        Article::class,
        Highlight::class,
        SavedPage::class,
        UserScript::class,
        UserScriptValue::class,
        ChatGptQuery::class,
        VideoTranscript::class,
        ChatSession::class,
    ],
    version = 8,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun faviconDao(): FaviconDao
    abstract fun domainConfigurationDao(): DomainConfigurationDao
    abstract fun articleDao(): ArticleDao
    abstract fun highlightDao(): HighlightDao
    abstract fun savedPageDao(): SavedPageDao
    abstract fun userScriptDao(): UserScriptDao
    abstract fun userScriptValueDao(): UserScriptValueDao
    abstract fun chatGptQueryDao(): ChatGptQueryDao
    abstract fun videoTranscriptDao(): VideoTranscriptDao
    abstract fun chatSessionDao(): ChatSessionDao
}

/** Adds the articles + highlights tables without dropping existing data. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `articles` (" +
                "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `date` INTEGER NOT NULL, " +
                "`tags` TEXT NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `highlights` (" +
                "`articleId` INTEGER NOT NULL, `content` TEXT NOT NULL, " +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "FOREIGN KEY(`articleId`) REFERENCES `articles`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_highlights_articleId` ON `highlights` (`articleId`)"
        )
    }
}

/** Adds the saved_pages table (offline archive metadata). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_pages` (" +
                "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `filePath` TEXT NOT NULL, " +
                "`savedAt` INTEGER NOT NULL, `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
        )
    }
}

/** Adds userscript storage: the script rows and their GM_setValue key/value store. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_scripts` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL, `code` TEXT NOT NULL, `sourceUrl` TEXT, " +
                "`order` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_script_values` (" +
                "`scriptId` INTEGER NOT NULL, `key` TEXT NOT NULL, `value` TEXT NOT NULL, " +
                "PRIMARY KEY(`scriptId`, `key`))"
        )
    }
}

/** Adds the chat_gpt_query table (persisted AI query/result history). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `chat_gpt_query` (" +
                "`date` INTEGER NOT NULL, `url` TEXT NOT NULL, `model` TEXT NOT NULL, " +
                "`selectedText` TEXT NOT NULL, `result` TEXT NOT NULL, " +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
        )
    }
}

/** Adds the video_transcripts table (Gemini transcripts, cached forever). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `video_transcripts` (" +
                "`videoId` TEXT NOT NULL, `transcript` TEXT NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, PRIMARY KEY(`videoId`))"
        )
    }
}

/** Adds the chat_sessions table (chat-with-web conversations; Android v12→13). */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `chat_sessions` (" +
                "`id` TEXT NOT NULL, `title` TEXT NOT NULL, `created` INTEGER NOT NULL, " +
                "`lastUpdated` INTEGER NOT NULL, `webTitle` TEXT NOT NULL, " +
                "`webUrl` TEXT NOT NULL, `messages` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}

/** Adds chat_sessions.webContent (session context restore; Android v13→14). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `chat_sessions` ADD COLUMN `webContent` TEXT NOT NULL DEFAULT ''"
        )
    }
}

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

expect fun createAppDatabase(): AppDatabase

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY `order`, id")
    suspend fun getAllBookmarks(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE parent = :parent ORDER BY `order`, id")
    suspend fun getBookmarksByParent(parent: Int): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE isDirectory = 1 ORDER BY `order`, id")
    suspend fun getBookmarkFolders(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun getBookmarkByUrl(url: String): Bookmark?

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark): Long

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(record: HistoryRecord)

    @Query("SELECT * FROM HISTORY ORDER BY TIME DESC")
    suspend fun getAllHistory(): List<HistoryRecord>

    @Query("DELETE FROM HISTORY WHERE URL = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM HISTORY WHERE TIME = :time")
    suspend fun deleteByTime(time: Long)

    @Query("DELETE FROM HISTORY")
    suspend fun deleteAll()
}

@Dao
interface FaviconDao {
    @Query("SELECT * FROM favicons WHERE domain = :domain LIMIT 1")
    suspend fun findBy(domain: String): FaviconInfo?

    @Query("SELECT * FROM favicons")
    suspend fun getAllFavicons(): List<FaviconInfo>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(faviconInfo: FaviconInfo)

    @Delete
    suspend fun delete(faviconInfo: FaviconInfo)
}

@Dao
interface DomainConfigurationDao {
    @Query("SELECT * FROM domain_configuration")
    suspend fun getAll(): List<DomainConfiguration>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(configuration: DomainConfiguration)

    @Query("DELETE FROM domain_configuration WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)
}

@Dao
interface ArticleDao {
    @Query("SELECT * FROM articles ORDER BY date DESC")
    suspend fun getAllArticles(): List<Article>

    @Query("SELECT * FROM articles WHERE url = :url LIMIT 1")
    suspend fun getArticleByUrl(url: String): Article?

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getArticleById(id: Int): Article?

    @Insert
    suspend fun insert(article: Article): Long

    @Query("DELETE FROM articles WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM articles")
    suspend fun deleteAll()
}

@Dao
interface SavedPageDao {
    @Query("SELECT * FROM saved_pages ORDER BY savedAt DESC")
    suspend fun getAll(): List<SavedPage>

    @Insert
    suspend fun insert(savedPage: SavedPage): Long

    @Delete
    suspend fun delete(savedPage: SavedPage)

    @Query("DELETE FROM saved_pages")
    suspend fun deleteAll()
}

@Dao
interface UserScriptDao {
    @Query("SELECT * FROM user_scripts ORDER BY `order`, id")
    suspend fun getAll(): List<UserScript>

    @Query("SELECT * FROM user_scripts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): UserScript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(script: UserScript): Long

    @Update
    suspend fun update(script: UserScript)

    @Query("DELETE FROM user_scripts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM user_scripts")
    suspend fun deleteAll()
}

@Dao
interface UserScriptValueDao {
    @Query("SELECT * FROM user_script_values WHERE scriptId = :scriptId")
    suspend fun getForScript(scriptId: Long): List<UserScriptValue>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(value: UserScriptValue)

    @Query("DELETE FROM user_script_values WHERE scriptId = :scriptId AND key = :key")
    suspend fun deleteValue(scriptId: Long, key: String)

    @Query("DELETE FROM user_script_values WHERE scriptId = :scriptId")
    suspend fun deleteAllForScript(scriptId: Long)
}

@Dao
interface VideoTranscriptDao {
    @Query("SELECT * FROM video_transcripts WHERE videoId = :videoId")
    suspend fun getTranscript(videoId: String): VideoTranscript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(videoTranscript: VideoTranscript)

    @Query("SELECT * FROM video_transcripts")
    suspend fun getAllTranscripts(): List<VideoTranscript>

    @Query("DELETE FROM video_transcripts")
    suspend fun deleteAll()
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights")
    suspend fun getAllHighlights(): List<Highlight>

    @Query("SELECT * FROM highlights WHERE articleId = :articleId")
    suspend fun getHighlightsForArticle(articleId: Int): List<Highlight>

    @Insert
    suspend fun insert(highlight: Highlight)

    @Delete
    suspend fun delete(highlight: Highlight)

    @Query("DELETE FROM highlights")
    suspend fun deleteAll()
}
