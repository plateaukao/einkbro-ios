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

/**
 * Phase-2 schema: the tables the browser needs today. Same shapes as the
 * Android app; further tables (highlights, saved pages, userscripts, ...)
 * join the schema with their features in later phases.
 */
@Database(
    entities = [
        Bookmark::class,
        HistoryRecord::class,
        FaviconInfo::class,
        DomainConfiguration::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun faviconDao(): FaviconDao
    abstract fun domainConfigurationDao(): DomainConfigurationDao
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
