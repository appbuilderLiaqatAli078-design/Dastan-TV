package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.model.CacheStats
import com.example.data.model.DramaSeason
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class LocalCatalogCacheDatabase private constructor(
    context: Context
) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_dramas (
                id TEXT PRIMARY KEY,
                category_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                original_title TEXT NOT NULL,
                slug TEXT NOT NULL,
                description TEXT NOT NULL,
                poster_url TEXT NOT NULL,
                thumbnail_url TEXT NOT NULL,
                episode_count INTEGER NOT NULL,
                season_number INTEGER NOT NULL,
                genre_tags_json TEXT NOT NULL,
                seasons_json TEXT NOT NULL,
                is_featured INTEGER NOT NULL,
                priority_order INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_episodes (
                id TEXT PRIMARY KEY,
                post_id INTEGER NOT NULL,
                category_id INTEGER NOT NULL,
                drama_title TEXT NOT NULL,
                title TEXT NOT NULL,
                clean_title TEXT NOT NULL,
                episode_number INTEGER NOT NULL,
                season_number INTEGER NOT NULL,
                description TEXT NOT NULL,
                published_date TEXT NOT NULL,
                poster_url TEXT NOT NULL,
                thumbnail_url TEXT NOT NULL,
                duration_label TEXT NOT NULL,
                primary_embed_url TEXT NOT NULL,
                backup_embeds_json TEXT NOT NULL,
                source_post_url TEXT NOT NULL,
                is_playable INTEGER NOT NULL,
                has_explicit_season INTEGER NOT NULL,
                is_latest_feed INTEGER NOT NULL DEFAULT 0,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_episodes_category_season ON cached_episodes(category_id, season_number, episode_number)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cache_metadata (
                cache_key TEXT PRIMARY KEY,
                content_hash TEXT NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS cached_dramas")
        db.execSQL("DROP TABLE IF EXISTS cached_episodes")
        db.execSQL("DROP TABLE IF EXISTS cache_metadata")
        onCreate(db)
    }

    @Synchronized
    fun isCacheKeyFresh(cacheKey: String, ttlMs: Long = DEFAULT_TTL_MS): Boolean {
        return try {
            readableDatabase.rawQuery(
                "SELECT updated_at_ms FROM cache_metadata WHERE cache_key = ?",
                arrayOf(cacheKey)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    val updatedAt = cursor.getLong(0)
                    (System.currentTimeMillis() - updatedAt) in 0..ttlMs
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check cache freshness for $cacheKey: ${e.message}")
            false
        }
    }

    @Synchronized
    fun saveDramas(dramas: List<DramaSeries>) {
        if (dramas.isEmpty()) return
        val contentHash = dramas.joinToString("|") {
            "${it.id}:${it.episodeCount}:${it.posterUrl.hashCode()}:${it.seasons.size}"
        }.hashCode().toString()

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            db.delete("cached_dramas", null, null)
            for (drama in dramas) {
                val genresArray = JSONArray(drama.genreTags)
                val seasonsArray = JSONArray().apply {
                    drama.seasons.forEach { s ->
                        put(
                            JSONObject().apply {
                                put("seasonNumber", s.seasonNumber)
                                put("categoryId", s.categoryId)
                                put("categoryName", s.categoryName)
                                put("slug", s.slug)
                                put("episodeCount", s.episodeCount)
                                put("hasExplicitSeasonMetadata", s.hasExplicitSeasonMetadata)
                            }
                        )
                    }
                }
                val values = ContentValues().apply {
                    put("id", drama.id)
                    put("category_id", drama.categoryId)
                    put("title", drama.title)
                    put("original_title", drama.originalTitle)
                    put("slug", drama.slug)
                    put("description", drama.description)
                    put("poster_url", drama.posterUrl)
                    put("thumbnail_url", drama.thumbnailUrl)
                    put("episode_count", drama.episodeCount)
                    put("season_number", drama.seasonNumber)
                    put("genre_tags_json", genresArray.toString())
                    put("seasons_json", seasonsArray.toString())
                    put("is_featured", if (drama.isFeatured) 1 else 0)
                    put("priority_order", drama.priorityOrder)
                    put("updated_at_ms", now)
                }
                db.insertWithOnConflict("cached_dramas", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            upsertMetadata(db, KEY_CATALOG_DRAMAS, contentHash, now)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist dramas to SQLite cache: ${e.message}")
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getCachedDramas(): List<DramaSeries> {
        val result = mutableListOf<DramaSeries>()
        try {
            readableDatabase.rawQuery(
                "SELECT * FROM cached_dramas ORDER BY priority_order ASC, episode_count DESC",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val categoryId = cursor.getInt(cursor.getColumnIndexOrThrow("category_id"))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                    val originalTitle = cursor.getString(cursor.getColumnIndexOrThrow("original_title"))
                    val slug = cursor.getString(cursor.getColumnIndexOrThrow("slug"))
                    val description = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                    val posterUrl = cursor.getString(cursor.getColumnIndexOrThrow("poster_url"))
                    val thumbnailUrl = cursor.getString(cursor.getColumnIndexOrThrow("thumbnail_url"))
                    val episodeCount = cursor.getInt(cursor.getColumnIndexOrThrow("episode_count"))
                    val seasonNumber = cursor.getInt(cursor.getColumnIndexOrThrow("season_number"))
                    val genreTagsJson = cursor.getString(cursor.getColumnIndexOrThrow("genre_tags_json"))
                    val seasonsJson = cursor.getString(cursor.getColumnIndexOrThrow("seasons_json"))
                    val isFeatured = cursor.getInt(cursor.getColumnIndexOrThrow("is_featured")) == 1
                    val priorityOrder = cursor.getInt(cursor.getColumnIndexOrThrow("priority_order"))

                    val genres = mutableListOf<String>()
                    runCatching {
                        val arr = JSONArray(genreTagsJson)
                        for (i in 0 until arr.length()) {
                            genres.add(arr.optString(i))
                        }
                    }
                    val seasons = mutableListOf<DramaSeason>()
                    runCatching {
                        val arr = JSONArray(seasonsJson)
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            seasons.add(
                                DramaSeason(
                                    seasonNumber = obj.optInt("seasonNumber", 1),
                                    categoryId = obj.optInt("categoryId", categoryId),
                                    categoryName = obj.optString("categoryName", title),
                                    slug = obj.optString("slug", slug),
                                    episodeCount = obj.optInt("episodeCount", episodeCount),
                                    hasExplicitSeasonMetadata = obj.optBoolean("hasExplicitSeasonMetadata", true)
                                )
                            )
                        }
                    }

                    result.add(
                        DramaSeries(
                            id = id,
                            categoryId = categoryId,
                            title = title,
                            originalTitle = originalTitle,
                            slug = slug,
                            description = description,
                            posterUrl = posterUrl,
                            thumbnailUrl = thumbnailUrl.ifBlank { posterUrl },
                            episodeCount = episodeCount,
                            seasonNumber = seasonNumber,
                            genreTags = genres,
                            isFeatured = isFeatured,
                            priorityOrder = priorityOrder,
                            hasMissingPoster = posterUrl.isBlank(),
                            seasons = seasons.ifEmpty {
                                listOf(
                                    DramaSeason(
                                        seasonNumber = seasonNumber.coerceAtLeast(1),
                                        categoryId = categoryId,
                                        categoryName = title,
                                        slug = slug,
                                        episodeCount = episodeCount
                                    )
                                )
                            }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached dramas: ${e.message}")
        }
        return result
    }

    @Synchronized
    fun saveEpisodes(
        episodes: List<EpisodeItem>,
        cacheKey: String,
        isLatestFeed: Boolean = false
    ) {
        if (episodes.isEmpty()) return
        val contentHash = episodes.joinToString("|") {
            "${it.postId}:${it.seasonNumber}:${it.episodeNumber}:${it.title.hashCode()}"
        }.hashCode().toString()

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            if (isLatestFeed) {
                db.execSQL("UPDATE cached_episodes SET is_latest_feed = 0 WHERE is_latest_feed = 1")
            }
            for (ep in episodes) {
                val backupsJson = JSONArray(ep.backupEmbedUrls).toString()
                val values = ContentValues().apply {
                    put("id", ep.id)
                    put("post_id", ep.postId)
                    put("category_id", ep.categoryId)
                    put("drama_title", ep.dramaTitle)
                    put("title", ep.title)
                    put("clean_title", ep.cleanTitle)
                    put("episode_number", ep.episodeNumber)
                    put("season_number", ep.seasonNumber)
                    put("description", ep.description)
                    put("published_date", ep.publishedDate)
                    put("poster_url", ep.posterUrl)
                    put("thumbnail_url", ep.thumbnailUrl)
                    put("duration_label", ep.durationLabel)
                    put("primary_embed_url", ep.primaryEmbedUrl)
                    put("backup_embeds_json", backupsJson)
                    put("source_post_url", ep.sourcePostUrl)
                    put("is_playable", if (ep.isPlayable) 1 else 0)
                    put("has_explicit_season", if (ep.hasExplicitSeason) 1 else 0)
                    put("is_latest_feed", if (isLatestFeed) 1 else 0)
                    put("updated_at_ms", now)
                }
                db.insertWithOnConflict("cached_episodes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            upsertMetadata(db, cacheKey, contentHash, now)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist episodes to SQLite cache: ${e.message}")
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun getCachedLatestEpisodes(): List<EpisodeItem> {
        return queryEpisodes(
            "SELECT * FROM cached_episodes WHERE is_latest_feed = 1 ORDER BY post_id DESC LIMIT 40",
            emptyArray()
        )
    }

    @Synchronized
    fun getCachedEpisodesForCategory(categoryId: Int): List<EpisodeItem> {
        return queryEpisodes(
            "SELECT * FROM cached_episodes WHERE category_id = ? ORDER BY season_number ASC, episode_number ASC",
            arrayOf(categoryId.toString())
        )
    }

    private fun queryEpisodes(sql: String, args: Array<String>): List<EpisodeItem> {
        val list = mutableListOf<EpisodeItem>()
        try {
            readableDatabase.rawQuery(sql, args).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    val postId = cursor.getInt(cursor.getColumnIndexOrThrow("post_id"))
                    val categoryId = cursor.getInt(cursor.getColumnIndexOrThrow("category_id"))
                    val dramaTitle = cursor.getString(cursor.getColumnIndexOrThrow("drama_title"))
                    val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                    val cleanTitle = cursor.getString(cursor.getColumnIndexOrThrow("clean_title"))
                    val episodeNumber = cursor.getInt(cursor.getColumnIndexOrThrow("episode_number"))
                    val seasonNumber = cursor.getInt(cursor.getColumnIndexOrThrow("season_number"))
                    val description = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                    val publishedDate = cursor.getString(cursor.getColumnIndexOrThrow("published_date"))
                    val posterUrl = cursor.getString(cursor.getColumnIndexOrThrow("poster_url"))
                    val thumbnailUrl = cursor.getString(cursor.getColumnIndexOrThrow("thumbnail_url"))
                    val durationLabel = cursor.getString(cursor.getColumnIndexOrThrow("duration_label"))
                    val primaryEmbedUrl = cursor.getString(cursor.getColumnIndexOrThrow("primary_embed_url"))
                    val backupsJson = cursor.getString(cursor.getColumnIndexOrThrow("backup_embeds_json"))
                    val sourcePostUrl = cursor.getString(cursor.getColumnIndexOrThrow("source_post_url"))
                    val isPlayable = cursor.getInt(cursor.getColumnIndexOrThrow("is_playable")) == 1
                    val hasExplicitSeason = cursor.getInt(cursor.getColumnIndexOrThrow("has_explicit_season")) == 1

                    val backups = mutableListOf<String>()
                    runCatching {
                        val arr = JSONArray(backupsJson)
                        for (i in 0 until arr.length()) {
                            backups.add(arr.optString(i))
                        }
                    }

                    list.add(
                        EpisodeItem(
                            id = id,
                            postId = postId,
                            categoryId = categoryId,
                            dramaTitle = dramaTitle,
                            title = title,
                            cleanTitle = cleanTitle.ifBlank { title },
                            episodeNumber = episodeNumber,
                            seasonNumber = seasonNumber,
                            description = description,
                            publishedDate = publishedDate,
                            posterUrl = posterUrl,
                            thumbnailUrl = thumbnailUrl.ifBlank { posterUrl },
                            durationLabel = durationLabel,
                            primaryEmbedUrl = primaryEmbedUrl,
                            backupEmbedUrls = backups,
                            sourcePostUrl = sourcePostUrl,
                            isPlayable = isPlayable,
                            hasMissingPoster = posterUrl.isBlank(),
                            hasMissingVideo = primaryEmbedUrl.isBlank(),
                            hasExplicitSeason = hasExplicitSeason
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query cached episodes: ${e.message}")
        }
        return list
    }

    private fun upsertMetadata(db: SQLiteDatabase, key: String, hash: String, nowMs: Long) {
        val values = ContentValues().apply {
            put("cache_key", key)
            put("content_hash", hash)
            put("updated_at_ms", nowMs)
        }
        db.insertWithOnConflict("cache_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun getCacheStats(context: Context): CacheStats {
        var dramasCount = 0
        var seasonsCount = 0
        var episodesCount = 0
        var lastUpdated = 0L

        try {
            val dramas = getCachedDramas()
            dramasCount = dramas.size
            seasonsCount = dramas.sumOf { it.seasons.size }

            readableDatabase.rawQuery("SELECT COUNT(*) FROM cached_episodes", null).use { c ->
                if (c.moveToFirst()) episodesCount = c.getInt(0)
            }
            readableDatabase.rawQuery("SELECT MAX(updated_at_ms) FROM cache_metadata", null).use { c ->
                if (c.moveToFirst()) lastUpdated = c.getLong(0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to compute SQLite cache stats: ${e.message}")
        }

        val appContext = context.applicationContext
        val imageCacheDir = File(appContext.cacheDir, IMAGE_CACHE_DIR)
        val httpCacheDir = File(appContext.cacheDir, HTTP_CACHE_DIR)
        val dbFile = appContext.getDatabasePath(DB_NAME)

        val totalBytes = folderSize(imageCacheDir) + folderSize(httpCacheDir) + (if (dbFile.exists()) dbFile.length() else 0L)

        return CacheStats(
            cachedDramasCount = dramasCount,
            cachedSeasonsCount = seasonsCount,
            cachedEpisodesCount = episodesCount,
            diskCacheBytes = totalBytes,
            lastUpdatedMs = lastUpdated,
            cacheTtlMinutes = (DEFAULT_TTL_MS / 60_000L).toInt()
        )
    }

    @Synchronized
    fun clearAllLocalCaches(context: Context) {
        try {
            val db = writableDatabase
            db.delete("cached_dramas", null, null)
            db.delete("cached_episodes", null, null)
            db.delete("cache_metadata", null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear SQLite cache tables: ${e.message}")
        }

        val appContext = context.applicationContext
        runCatching {
            Coil.imageLoader(appContext).memoryCache?.clear()
            Coil.imageLoader(appContext).diskCache?.clear()
        }
        runCatching {
            File(appContext.cacheDir, IMAGE_CACHE_DIR).deleteRecursively()
            File(appContext.cacheDir, HTTP_CACHE_DIR).deleteRecursively()
        }
    }

    private fun folderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { folderSize(it) } ?: 0L
    }

    companion object {
        private const val TAG = "LocalCatalogCacheDB"
        private const val DB_NAME = "dastan_catalog_cache.db"
        private const val DB_VERSION = 2
        const val KEY_CATALOG_DRAMAS = "catalog_dramas_v2"
        const val DEFAULT_TTL_MS = 30 * 60 * 1000L // 30 minutes TTL
        const val IMAGE_CACHE_DIR = "dastan_coil_image_cache"
        const val HTTP_CACHE_DIR = "dastan_okhttp_http_cache"

        @Volatile
        private var INSTANCE: LocalCatalogCacheDatabase? = null

        @Volatile
        private var coilInitialized = false

        fun getInstance(context: Context): LocalCatalogCacheDatabase {
            ensureCoilConfigured(context.applicationContext)
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocalCatalogCacheDatabase(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        fun ensureCoilConfigured(appContext: Context) {
            if (coilInitialized) return
            synchronized(this) {
                if (coilInitialized) return
                runCatching {
                    val loader = ImageLoader.Builder(appContext)
                        .memoryCache {
                            MemoryCache.Builder(appContext)
                                .maxSizePercent(0.20)
                                .build()
                        }
                        .diskCache {
                            DiskCache.Builder()
                                .directory(File(appContext.cacheDir, IMAGE_CACHE_DIR))
                                .maxSizeBytes(60L * 1024L * 1024L) // 60 MB cap
                                .build()
                        }
                        .respectCacheHeaders(false)
                        .crossfade(false)
                        .build()
                    Coil.setImageLoader(loader)
                    coilInitialized = true
                }
            }
        }
    }
}
