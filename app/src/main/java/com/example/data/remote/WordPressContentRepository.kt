package com.example.data.remote

import android.content.Context
import android.text.Html
import android.util.Log
import com.example.data.local.LocalCatalogCacheDatabase
import com.example.data.model.ApiHealthReport
import com.example.data.model.CacheStats
import com.example.data.model.CatalogOverride
import com.example.data.model.DramaSeason
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query

interface WordPressRawApi {
    @GET("categories")
    suspend fun getCategories(
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1
    ): Response<okhttp3.ResponseBody>

    @GET("posts")
    suspend fun getPostsByCategory(
        @Query("categories") categoryId: Int,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1,
        @Query("_embed") embed: Int = 1
    ): Response<okhttp3.ResponseBody>

    @GET("posts")
    suspend fun getLatestPosts(
        @Query("per_page") perPage: Int = 40,
        @Query("page") page: Int = 1,
        @Query("_embed") embed: Int = 1
    ): Response<okhttp3.ResponseBody>
}

class WordPressContentRepository(context: Context? = null) {

    private val appContext: Context? = context?.applicationContext
    private val localCacheDb: LocalCatalogCacheDatabase? = appContext?.let {
        LocalCatalogCacheDatabase.getInstance(it)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            appContext?.let { ctx ->
                val httpCacheDir = File(ctx.cacheDir, LocalCatalogCacheDatabase.HTTP_CACHE_DIR)
                cache(Cache(httpCacheDir, 20L * 1024L * 1024L)) // 20 MB HTTP response cache
            }
        }
        .build()

    private val api: WordPressRawApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .build()
        .create(WordPressRawApi::class.java)

    private val cacheMutex = Mutex()
    private val categoryFetchLocks = ConcurrentHashMap<String, Mutex>()
    private var cachedCategories: List<DramaSeries> = emptyList()
    private val cachedCategoryEpisodes = ConcurrentHashMap<Int, List<EpisodeItem>>()
    private val categoryToSeasonMap = ConcurrentHashMap<Int, Int>()
    private val categoryToDramaTitleMap = ConcurrentHashMap<Int, String>()
    private var cachedLatestEpisodes: List<EpisodeItem> = emptyList()
    private var lastCacheTimestampMs: Long = 0L
    private var lastHealthReport: ApiHealthReport = ApiHealthReport()

    companion object {
        private const val TAG = "WPContentRepo"
        const val BASE_URL = "https://giveme5.co/wp-json/wp/v2/"
        private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes metadata cache

        private val IFRAME_SRC_REGEX = Regex(
            """<iframe[^>]+src=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val IMG_SRC_REGEX = Regex(
            """<img[^>]+src=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val EPISODE_NUM_REGEX = Regex(
            """(?:Episode|Ep|Bolum|Bölüm)\s*#?\s*0*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        private val SEASON_NUM_REGEX = Regex(
            """(?:Season|Sezon|\bS)\s*0*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        private val TRAILING_NUM_REGEX = Regex(
            """\s+(\d+)\s*$"""
        )
        private val DURATION_REGEX = Regex(
            """\b(\d{1,2}\s*(?:h|hr|hrs)\s*\d{0,2}\s*(?:m|min|mins)?|\d{2,3}\s*(?:min|mins|minutes))\b""",
            RegexOption.IGNORE_CASE
        )

        data class CanonicalDramaResolution(
            val dramaKey: String,
            val canonicalTitle: String,
            val seasonNumber: Int,
            val hasExplicitSeason: Boolean
        )

        data class ResolvedStreamInfo(
            val originalEmbedUrl: String,
            val resolvedPlaybackUrl: String,
            val isDirectMediaStream: Boolean,
            val providerName: String,
            val durationSeconds: Int = 0,
            val requiredReferer: String = "https://giveme5.co/",
            val backupDirectUrls: List<String> = emptyList()
        )

        data class SyncDeltaReport(
            val newDramasCount: Int = 0,
            val newSeasonsCount: Int = 0,
            val newEpisodesCount: Int = 0,
            val updatedEpisodesCount: Int = 0,
            val totalDramasCount: Int = 0,
            val totalEpisodesCount: Int = 0
        )

        fun detectProviderName(url: String): String {
            val lower = url.lowercase()
            return when {
                lower.contains("vidmoly") -> "Vidmoly HD"
                lower.contains("ok.ru") || lower.contains("odnoklassniki") || lower.contains("okcdn.ru") -> "OK.ru HD"
                lower.contains(".m3u8") -> "Direct HLS Stream"
                lower.contains(".mp4") -> "Direct MP4 Stream"
                else -> "HD Stream"
            }
        }

        fun normalizeEmbedUrl(rawUrl: String): String {
            var cleaned = rawUrl.trim()
            if (cleaned.startsWith("//")) {
                cleaned = "https:$cleaned"
            } else if (cleaned.startsWith("http://")) {
                cleaned = "https://${cleaned.removePrefix("http://")}"
            }
            return cleaned
        }

        fun parseOkRuEmbedHtml(embedUrl: String, rawHtml: String): ResolvedStreamInfo? {
            if (rawHtml.isBlank()) return null
            val optionsMatch = Regex("""data-options="([^"]+)"""").find(rawHtml) ?: return null
            val rawOptions = optionsMatch.groupValues.getOrNull(1).orEmpty()
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")

            return try {
                val outerJson = JSONObject(rawOptions)
                val flashvars = outerJson.optJSONObject("flashvars") ?: return null
                val metaJson = flashvars.optJSONObject("metadata") ?: run {
                    val metadataRaw = flashvars.optString("metadata", "")
                    if (metadataRaw.isBlank()) return null
                    JSONObject(metadataRaw)
                }
                val movieObj = metaJson.optJSONObject("movie")
                val durationSec = movieObj?.optInt("duration", 0) ?: 0
                val hlsUrl = metaJson.optString("hlsManifestUrl", "").trim()

                val mp4Urls = mutableListOf<String>()
                val videosArray = metaJson.optJSONArray("videos")
                if (videosArray != null) {
                    for (i in 0 until videosArray.length()) {
                        val vObj = videosArray.optJSONObject(i) ?: continue
                        val vUrl = vObj.optString("url", "").trim()
                        if (vUrl.startsWith("http")) {
                            mp4Urls.add(vUrl)
                        }
                    }
                }
                // Prefer higher quality MP4s at the front of backup list
                val orderedMp4s = mp4Urls.reversed()
                val directCandidates = buildList {
                    if (hlsUrl.startsWith("http")) add(hlsUrl)
                    addAll(orderedMp4s)
                }.distinct()

                ResolvedStreamInfo(
                    originalEmbedUrl = embedUrl,
                    resolvedPlaybackUrl = embedUrl,
                    isDirectMediaStream = false,
                    providerName = "OK.ru HD",
                    durationSeconds = durationSec,
                    requiredReferer = "https://giveme5.co/",
                    backupDirectUrls = directCandidates
                )
            } catch (e: Exception) {
                null
            }
        }

        fun resolveCanonicalDrama(rawCategoryName: String, slug: String): CanonicalDramaResolution? {
            val cleanedName = rawCategoryName.trim()
            val lowerName = cleanedName.lowercase()
            val lowerSlug = slug.lowercase().trim()

            // Exclude non-drama or catch-all categories
            if (cleanedName.isBlank() ||
                lowerSlug == "uncategorized" ||
                lowerSlug == "blog" ||
                lowerName == "blog" ||
                lowerSlug == "ko-6" // WordPress ID=1 default catch-all with mixed posts
            ) {
                return null
            }

            // Extract season number from category name or slug
            val explicitSeasonMatch = SEASON_NUM_REGEX.find(cleanedName)
                ?: SEASON_NUM_REGEX.find(lowerSlug.replace("-", " "))
            val trailingNumMatch = TRAILING_NUM_REGEX.find(cleanedName)
            val resolvedSeason = explicitSeasonMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: trailingNumMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 1
            val hasExplicitSeason = explicitSeasonMatch != null || trailingNumMatch != null

            return when {
                lowerName.contains("mehmed") ||
                    lowerName.contains("muhammad fateh") ||
                    lowerName.contains("m fateh") ||
                    lowerSlug.contains("fateh") ||
                    lowerSlug.contains("mehmed") -> {
                    CanonicalDramaResolution(
                        dramaKey = "sultan_mehmed_fateh",
                        canonicalTitle = "Sultan Mehmed Fateh",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                lowerName.contains("osman") && !lowerName.contains("orhan") -> {
                    CanonicalDramaResolution(
                        dramaKey = "kurulus_osman",
                        canonicalTitle = "Kurulus Osman",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                lowerName.contains("orhan") -> {
                    CanonicalDramaResolution(
                        dramaKey = "kurulus_orhan",
                        canonicalTitle = "Kurulus Orhan",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                lowerName.contains("ertugrul") || lowerSlug.startsWith("de-") -> {
                    CanonicalDramaResolution(
                        dramaKey = "dirilis_ertugrul",
                        canonicalTitle = "Dirilis Ertugrul",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                lowerName.contains("barbaros") -> {
                    CanonicalDramaResolution(
                        dramaKey = "barbaroslar",
                        canonicalTitle = "Barbaroslar",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                lowerName.contains("great seljuk") || lowerName.contains("seljuk") -> {
                    CanonicalDramaResolution(
                        dramaKey = "great_seljuk",
                        canonicalTitle = "Great Seljuk",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                lowerName.contains("salahuddin") || lowerName.contains("ayubi") -> {
                    CanonicalDramaResolution(
                        dramaKey = "salahuddin_ayubi",
                        canonicalTitle = "Salahuddin Ayubi",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                lowerName.contains("sultan ah") || lowerName.contains("abdulhamid") -> {
                    CanonicalDramaResolution(
                        dramaKey = "sultan_abdulhamid",
                        canonicalTitle = "Sultan Abdulhamid",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                lowerName.contains("mehmetcik") -> {
                    CanonicalDramaResolution(
                        dramaKey = "mehmetcik",
                        canonicalTitle = "Mehmetcik",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                lowerName.contains("mendirman") -> {
                    CanonicalDramaResolution(
                        dramaKey = "mendirman",
                        canonicalTitle = "Mendirman Jaloliddin",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                lowerName.contains("mavera") -> {
                    CanonicalDramaResolution(
                        dramaKey = "mavera",
                        canonicalTitle = "Mavera",
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }

                else -> {
                    val baseTitle = cleanedName
                        .replace(Regex("""(?i)\b(?:season|sezon|s)\s*\d+\b"""), "")
                        .replace(Regex("""\s+\d+\s*$"""), "")
                        .trim()
                        .ifBlank { cleanedName }
                    val key = baseTitle.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                    CanonicalDramaResolution(
                        dramaKey = key.ifBlank { "drama_${slug.lowercase()}" },
                        canonicalTitle = baseTitle,
                        seasonNumber = resolvedSeason,
                        hasExplicitSeason = hasExplicitSeason
                    )
                }
            }
        }

        fun doesPostBelongToDrama(postTitle: String, targetDramaTitle: String): Boolean {
            val lowerPost = postTitle.lowercase()
            val lowerTarget = targetDramaTitle.lowercase()

            if (lowerTarget.contains("mehmed") || lowerTarget.contains("fateh")) {
                return lowerPost.contains("mehmed") ||
                    lowerPost.contains("fateh") ||
                    lowerPost.contains("mehmet")
            }
            if (lowerTarget.contains("osman")) {
                if (lowerPost.contains("orhan") && !lowerPost.contains("osman")) return false
                return true
            }
            if (lowerTarget.contains("orhan")) {
                if (lowerPost.contains("osman") && !lowerPost.contains("orhan")) return false
                return true
            }
            return true
        }

        fun formatCleanEpisodeTitle(rawTitle: String, dramaTitle: String, episodeNumber: Int): String {
            val withoutBoilerplate = rawTitle
                .replace(Regex("""(?i)\bhow\s+to\s+watch\s+"""), "")
                .replace(Regex("""(?i)\s*with\s+urdu\s+subtitles(?:\s+by\s+giveme5)?"""), "")
                .replace(Regex("""(?i)\s*in\s+urdu\s+subtitles(?:\s+by\s+giveme5)?"""), "")
                .replace(Regex("""(?i)\s*by\s+giveme5"""), "")
                .trim()

            // Check if there is a descriptive subtitle after ':' or '–' or '-'
            val subtitleMatch = Regex("""(?i)(?:episode|ep)\s*0*\d+(?:\s*[–\-:]\s*season\s*\d+)?\s*[–\-:]\s*(.+)$""")
                .find(withoutBoilerplate)
            val extractedSubtitle = subtitleMatch?.groupValues?.getOrNull(1)
                ?.replace(Regex("""(?i)^season\s*\d+\s*[–\-:]?\s*"""), "")
                ?.trim()
                .orEmpty()

            return if (extractedSubtitle.length >= 4 && !extractedSubtitle.equals("season $episodeNumber", ignoreCase = true)) {
                "Episode $episodeNumber: $extractedSubtitle"
            } else {
                "Episode $episodeNumber • $dramaTitle"
            }
        }
    }

    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 500L,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null
        for (attempt in 1..maxAttempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "API attempt $attempt/$maxAttempts failed: ${e.message}")
                if (attempt < maxAttempts) {
                    delay(currentDelay)
                    currentDelay *= 2
                }
            }
        }
        throw lastException ?: IllegalStateException("API request failed after $maxAttempts attempts")
    }

    private suspend fun fetchAllCategoriesRawJson(): Pair<String, Int> {
        val firstResp = executeWithRetry { api.getCategories(perPage = 100, page = 1) }
        val statusCode = firstResp.code()
        val firstBody = firstResp.body()?.string() ?: "[]"
        val totalPages = firstResp.headers()["X-WP-TotalPages"]?.toIntOrNull() ?: 1
        if (totalPages <= 1) {
            return Pair(firstBody, statusCode)
        }
        val mergedArray = JSONArray(firstBody)
        for (page in 2..totalPages.coerceAtMost(5)) {
            runCatching {
                val pageResp = executeWithRetry { api.getCategories(perPage = 100, page = page) }
                val pageArr = JSONArray(pageResp.body()?.string() ?: "[]")
                for (i in 0 until pageArr.length()) {
                    mergedArray.put(pageArr.get(i))
                }
            }
        }
        return Pair(mergedArray.toString(), statusCode)
    }

    suspend fun resolveEmbedStream(embedUrl: String): ResolvedStreamInfo = withContext(Dispatchers.IO) {
        val normalized = normalizeEmbedUrl(embedUrl)
        val lower = normalized.lowercase()
        val provider = detectProviderName(normalized)

        if (lower.contains(".m3u8") || lower.contains(".mp4")) {
            return@withContext ResolvedStreamInfo(
                originalEmbedUrl = normalized,
                resolvedPlaybackUrl = normalized,
                isDirectMediaStream = true,
                providerName = provider
            )
        }

        if (lower.contains("ok.ru/videoembed/")) {
            runCatching {
                val req = okhttp3.Request.Builder()
                    .url(normalized)
                    .header("Referer", "https://giveme5.co/")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    )
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string().orEmpty()
                        parseOkRuEmbedHtml(normalized, html)?.let { return@withContext it }
                    }
                }
            }
        }

        ResolvedStreamInfo(
            originalEmbedUrl = normalized,
            resolvedPlaybackUrl = normalized,
            isDirectMediaStream = false,
            providerName = provider,
            requiredReferer = "https://giveme5.co/"
        )
    }

    suspend fun syncLatestCatalogDelta(): SyncDeltaReport = withContext(Dispatchers.IO) {
        val previousDramas = cacheMutex.withLock {
            cachedCategories.ifEmpty { localCacheDb?.getCachedDramas().orEmpty() }
        }
        val previousLatest = cacheMutex.withLock {
            cachedLatestEpisodes.ifEmpty { localCacheDb?.getCachedLatestEpisodes().orEmpty() }
        }
        val prevDramaKeys = previousDramas.map { it.slug }.toSet()
        val prevSeasonCatIds = previousDramas.flatMap { it.allCategoryIds }.toSet()
        val prevEpisodesByPostId = previousLatest.associateBy { it.postId }

        val (freshDramas, freshLatest) = fetchDramasAndLatestEpisodes(forceRefresh = true)

        val newDramasCount = freshDramas.count { it.slug !in prevDramaKeys }
        val newSeasonsCount = freshDramas.flatMap { it.allCategoryIds }.count { it !in prevSeasonCatIds }
        var newEpisodesCount = 0
        var updatedEpisodesCount = 0

        for (ep in freshLatest) {
            val oldEp = prevEpisodesByPostId[ep.postId]
            if (oldEp == null) {
                newEpisodesCount++
            } else if (oldEp.primaryEmbedUrl != ep.primaryEmbedUrl ||
                oldEp.backupEmbedUrls != ep.backupEmbedUrls ||
                oldEp.title != ep.title ||
                oldEp.posterUrl != ep.posterUrl
            ) {
                updatedEpisodesCount++
            }
            // Merge into per-category episode cache if that category was already loaded
            if (ep.categoryId > 0 && cachedCategoryEpisodes.containsKey(ep.categoryId)) {
                val existingCatEps = cachedCategoryEpisodes[ep.categoryId].orEmpty()
                val mergedCatEps = deduplicateSeasonEpisodes(existingCatEps + ep)
                cachedCategoryEpisodes[ep.categoryId] = mergedCatEps
                localCacheDb?.saveEpisodes(
                    episodes = mergedCatEps,
                    cacheKey = "cat_episodes_${ep.categoryId}",
                    isLatestFeed = false
                )
            }
        }

        SyncDeltaReport(
            newDramasCount = newDramasCount,
            newSeasonsCount = newSeasonsCount,
            newEpisodesCount = newEpisodesCount,
            updatedEpisodesCount = updatedEpisodesCount,
            totalDramasCount = freshDramas.size,
            totalEpisodesCount = freshLatest.size + cachedCategoryEpisodes.values.sumOf { it.size }
        )
    }

    suspend fun fetchDramasAndLatestEpisodes(
        forceRefresh: Boolean = false
    ): Pair<List<DramaSeries>, List<EpisodeItem>> = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            val now = System.currentTimeMillis()
            if (!forceRefresh &&
                cachedCategories.isNotEmpty() &&
                cachedLatestEpisodes.isNotEmpty() &&
                (now - lastCacheTimestampMs) < CACHE_TTL_MS
            ) {
                return@withContext Pair(cachedCategories, cachedLatestEpisodes)
            }

            // Warm in-memory cache from SQLite database if fresh and not force-refreshing
            if (!forceRefresh && cachedCategories.isEmpty() && localCacheDb != null) {
                if (localCacheDb.isCacheKeyFresh(LocalCatalogCacheDatabase.KEY_CATALOG_DRAMAS, CACHE_TTL_MS)) {
                    val dbDramas = localCacheDb.getCachedDramas()
                    val dbLatest = localCacheDb.getCachedLatestEpisodes()
                    if (dbDramas.isNotEmpty() && dbLatest.isNotEmpty()) {
                        cachedCategories = dbDramas
                        cachedLatestEpisodes = dbLatest
                        lastCacheTimestampMs = now
                        registerSeasonLookupMaps(dbDramas)
                        return@withContext Pair(dbDramas, dbLatest)
                    }
                }
            }
        }

        val startTime = System.currentTimeMillis()
        var statusCode = 200
        var errorMessage: String? = null
        var duplicateCount = 0

        try {
            val (categoriesBody, catStatus) = fetchAllCategoriesRawJson()
            statusCode = catStatus

            val latestPostsResponse = executeWithRetry { api.getLatestPosts(perPage = 50, page = 1) }
            val latestPostsBody = latestPostsResponse.body()?.string() ?: "[]"

            // Parse categories first so we know each category's canonical drama & season number
            val initialDramas = parseCategoriesJson(categoriesBody, emptyMap(), emptyMap())
            registerSeasonLookupMaps(initialDramas)

            val parsedLatestResult = parsePostsJson(
                jsonString = latestPostsBody,
                defaultCategoryId = 0,
                defaultDramaName = "Turkish Historical Drama",
                forcedSeasonNumber = null
            )
            val latestEpisodes = parsedLatestResult.first
            duplicateCount += parsedLatestResult.second

            // Map category ID -> latest poster & thumbnail from episodes in that category
            val categoryPosters = mutableMapOf<Int, String>()
            val categoryThumbnails = mutableMapOf<Int, String>()
            for (ep in latestEpisodes) {
                if (ep.categoryId > 0 && ep.posterUrl.isNotBlank() && !categoryPosters.containsKey(ep.categoryId)) {
                    categoryPosters[ep.categoryId] = ep.posterUrl
                    categoryThumbnails[ep.categoryId] = ep.thumbnailUrl.ifBlank { ep.posterUrl }
                }
            }

            val parsedDramas = parseCategoriesJson(categoriesBody, categoryPosters, categoryThumbnails)
            registerSeasonLookupMaps(parsedDramas)

            val latency = System.currentTimeMillis() - startTime
            val missingPosters = parsedDramas.count { it.hasMissingPoster } +
                latestEpisodes.count { it.hasMissingPoster }
            val missingVideos = latestEpisodes.count { it.hasMissingVideo }
            val playableCount = latestEpisodes.count { it.isPlayable }

            localCacheDb?.saveDramas(parsedDramas)
            localCacheDb?.saveEpisodes(
                episodes = latestEpisodes,
                cacheKey = "latest_episodes_feed",
                isLatestFeed = true
            )

            cacheMutex.withLock {
                cachedCategories = parsedDramas
                cachedLatestEpisodes = latestEpisodes
                lastCacheTimestampMs = System.currentTimeMillis()
                lastHealthReport = ApiHealthReport(
                    endpointUrl = "https://giveme5.co/wp-json/wp/v2",
                    isHealthy = (statusCode in 200..299) && latestPostsResponse.isSuccessful,
                    httpStatusCode = statusCode,
                    responseLatencyMs = latency,
                    totalCategoriesFetched = parsedDramas.sumOf { it.seasons.size },
                    totalEpisodesCached = latestEpisodes.size + cachedCategoryEpisodes.values.sumOf { it.size },
                    playableEpisodesCount = playableCount,
                    missingPosterCount = missingPosters,
                    missingVideoCount = missingVideos,
                    duplicateSkippedCount = duplicateCount,
                    lastCheckedTimestampMs = System.currentTimeMillis(),
                    errorMessage = null
                )
            }

            Pair(parsedDramas, latestEpisodes)
        } catch (e: Exception) {
            errorMessage = e.localizedMessage ?: "Network error connecting to giveme5.co"
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "Failed to fetch live WP content; falling back to SQLite/memory cache", e)

            val offlineDramas = cachedCategories.ifEmpty { localCacheDb?.getCachedDramas().orEmpty() }
            val offlineLatest = cachedLatestEpisodes.ifEmpty { localCacheDb?.getCachedLatestEpisodes().orEmpty() }
            if (offlineDramas.isNotEmpty()) {
                registerSeasonLookupMaps(offlineDramas)
            }

            cacheMutex.withLock {
                cachedCategories = offlineDramas
                cachedLatestEpisodes = offlineLatest
                lastHealthReport = lastHealthReport.copy(
                    isHealthy = false,
                    httpStatusCode = if (statusCode == 200) 503 else statusCode,
                    responseLatencyMs = latency,
                    lastCheckedTimestampMs = System.currentTimeMillis(),
                    errorMessage = errorMessage
                )
                Pair(offlineDramas, offlineLatest)
            }
        }
    }

    private fun registerSeasonLookupMaps(dramas: List<DramaSeries>) {
        for (drama in dramas) {
            for (season in drama.seasons) {
                categoryToSeasonMap[season.categoryId] = season.seasonNumber
                categoryToDramaTitleMap[season.categoryId] = drama.title
            }
        }
    }

    suspend fun fetchEpisodesForCategory(
        categoryId: Int,
        dramaTitle: String,
        page: Int = 1,
        forceRefresh: Boolean = false,
        expectedSeasonNumber: Int? = null
    ): List<EpisodeItem> = withContext(Dispatchers.IO) {
        val lockKey = "${categoryId}_$page"
        val mutex = categoryFetchLocks.getOrPut(lockKey) { Mutex() }

        mutex.withLock {
            if (page == 1 && !forceRefresh) {
                cachedCategoryEpisodes[categoryId]?.let {
                    if (it.isNotEmpty()) return@withContext it
                }
                val cacheKey = "cat_episodes_$categoryId"
                if (localCacheDb != null && localCacheDb.isCacheKeyFresh(cacheKey, CACHE_TTL_MS)) {
                    val sqlCached = localCacheDb.getCachedEpisodesForCategory(categoryId)
                    if (sqlCached.isNotEmpty()) {
                        cachedCategoryEpisodes[categoryId] = sqlCached
                        return@withContext sqlCached
                    }
                }
            }

            val resolvedSeasonNum = expectedSeasonNumber ?: categoryToSeasonMap[categoryId]
            val resolvedDramaName = categoryToDramaTitleMap[categoryId] ?: dramaTitle

            try {
                val response = executeWithRetry {
                    api.getPostsByCategory(categoryId = categoryId, perPage = 50, page = page, embed = 1)
                }
                val body = response.body()?.string() ?: "[]"
                val parsed = parsePostsJson(
                    jsonString = body,
                    defaultCategoryId = categoryId,
                    defaultDramaName = resolvedDramaName,
                    forcedSeasonNumber = resolvedSeasonNum
                ).first
                    .filter { doesPostBelongToDrama(it.title, resolvedDramaName) }
                    .sortedWith(compareBy<EpisodeItem> { it.seasonNumber }.thenBy { it.episodeNumber })

                if (page == 1) {
                    val deduplicated = deduplicateSeasonEpisodes(parsed)
                    cachedCategoryEpisodes[categoryId] = deduplicated
                    localCacheDb?.saveEpisodes(
                        episodes = deduplicated,
                        cacheKey = "cat_episodes_$categoryId",
                        isLatestFeed = false
                    )
                    val discoveredPoster = deduplicated.firstOrNull { it.posterUrl.isNotBlank() }?.posterUrl
                    val discoveredThumb = deduplicated.firstOrNull { it.thumbnailUrl.isNotBlank() }?.thumbnailUrl
                    if (!discoveredPoster.isNullOrBlank()) {
                        cacheMutex.withLock {
                            cachedCategories = cachedCategories.map { drama ->
                                if (drama.allCategoryIds.contains(categoryId) && drama.posterUrl.isBlank()) {
                                    drama.copy(
                                        posterUrl = discoveredPoster,
                                        thumbnailUrl = discoveredThumb ?: discoveredPoster,
                                        hasMissingPoster = false
                                    )
                                } else {
                                    drama
                                }
                            }
                            localCacheDb?.saveDramas(cachedCategories)
                        }
                    }
                    deduplicated
                } else {
                    val existing = cachedCategoryEpisodes[categoryId].orEmpty()
                    val merged = deduplicateSeasonEpisodes(existing + parsed)
                    cachedCategoryEpisodes[categoryId] = merged
                    localCacheDb?.saveEpisodes(
                        episodes = merged,
                        cacheKey = "cat_episodes_$categoryId",
                        isLatestFeed = false
                    )
                    parsed
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch episodes for category $categoryId page $page; checking offline SQLite cache", e)
                cachedCategoryEpisodes[categoryId]
                    ?: localCacheDb?.getCachedEpisodesForCategory(categoryId).orEmpty()
            }
        }
    }

    fun deduplicateSeasonEpisodes(episodes: List<EpisodeItem>): List<EpisodeItem> {
        val bySeasonAndEp = LinkedHashMap<String, EpisodeItem>()
        // Sort so playable and higher postId (newer corrected uploads) come first
        val prioritized = episodes.sortedWith(
            compareByDescending<EpisodeItem> { it.isPlayable }
                .thenByDescending { it.postId }
        )
        for (ep in prioritized) {
            val key = "${ep.seasonNumber}_${ep.episodeNumber}"
            val existing = bySeasonAndEp[key]
            if (existing == null) {
                bySeasonAndEp[key] = ep
            } else {
                // Merge backup embed mirrors from duplicate post so no mirror is lost
                val mergedBackups = buildList {
                    addAll(existing.backupEmbedUrls)
                    if (ep.primaryEmbedUrl.isNotBlank() && ep.primaryEmbedUrl != existing.primaryEmbedUrl) {
                        add(ep.primaryEmbedUrl)
                    }
                    addAll(ep.backupEmbedUrls)
                }.distinct()
                bySeasonAndEp[key] = existing.copy(backupEmbedUrls = mergedBackups)
            }
        }
        return bySeasonAndEp.values.sortedWith(
            compareBy<EpisodeItem> { it.seasonNumber }.thenBy { it.episodeNumber }
        )
    }

    fun clearInMemoryCache() {
        cachedCategories = emptyList()
        cachedCategoryEpisodes.clear()
        cachedLatestEpisodes = emptyList()
        lastCacheTimestampMs = 0L
    }

    fun getCacheStats(): CacheStats {
        val ctx = appContext
        val db = localCacheDb
        return if (ctx != null && db != null) {
            db.getCacheStats(ctx)
        } else {
            CacheStats(
                cachedDramasCount = cachedCategories.size,
                cachedSeasonsCount = cachedCategories.sumOf { it.seasons.size },
                cachedEpisodesCount = cachedLatestEpisodes.size + cachedCategoryEpisodes.values.sumOf { it.size },
                diskCacheBytes = 0L,
                lastUpdatedMs = lastCacheTimestampMs
            )
        }
    }

    fun clearAllCaches() {
        clearInMemoryCache()
        appContext?.let { ctx ->
            localCacheDb?.clearAllLocalCaches(ctx)
        }
        runCatching { okHttpClient.cache?.evictAll() }
    }

    suspend fun runApiHealthCheck(): ApiHealthReport = withContext(Dispatchers.IO) {
        fetchDramasAndLatestEpisodes(forceRefresh = true)
        cacheMutex.withLock { lastHealthReport }
    }

    fun getLatestHealthReport(): ApiHealthReport = lastHealthReport

    fun parseCategoriesJson(
        jsonString: String,
        categoryPosters: Map<Int, String>,
        categoryThumbnails: Map<Int, String> = emptyMap()
    ): List<DramaSeries> {
        val array = JSONArray(jsonString)

        data class RawCategoryEntry(
            val id: Int,
            val count: Int,
            val rawName: String,
            val slug: String,
            val rawDesc: String,
            val resolution: CanonicalDramaResolution
        )

        val entriesByDramaKey = LinkedHashMap<String, MutableList<RawCategoryEntry>>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optInt("id", 0)
            val count = obj.optInt("count", 0)
            val rawName = decodeHtml(obj.optString("name", "")).trim()
            val slug = obj.optString("slug", "")
            val rawDesc = decodeHtml(obj.optString("description", "")).trim()

            // Skip invalid, empty, or WordPress default catch-all category ID=1
            if (id <= 1 || count <= 0 || rawName.isBlank()) continue
            val resolution = resolveCanonicalDrama(rawName, slug) ?: continue

            entriesByDramaKey.getOrPut(resolution.dramaKey) { mutableListOf() }.add(
                RawCategoryEntry(
                    id = id,
                    count = count,
                    rawName = rawName,
                    slug = slug,
                    rawDesc = rawDesc,
                    resolution = resolution
                )
            )
        }

        val unifiedDramas = mutableListOf<DramaSeries>()
        var index = 0

        for ((dramaKey, entries) in entriesByDramaKey) {
            // Deduplicate seasons by seasonNumber (keep category with highest episode count if duplicate category exists)
            val seasons = entries
                .groupBy { it.resolution.seasonNumber }
                .map { (seasonNum, seasonEntries) ->
                    val best = seasonEntries.maxByOrNull { it.count } ?: seasonEntries.first()
                    DramaSeason(
                        seasonNumber = seasonNum,
                        categoryId = best.id,
                        categoryName = best.rawName,
                        slug = best.slug,
                        episodeCount = seasonEntries.maxOf { it.count },
                        hasExplicitSeasonMetadata = best.resolution.hasExplicitSeason || entries.size > 1
                    )
                }
                .sortedBy { it.seasonNumber }

            val primarySeason = seasons.first()
            val canonicalTitle = entries.first().resolution.canonicalTitle
            val totalEpisodes = seasons.sumOf { it.episodeCount }

            val discoveredPoster = seasons.firstNotNullOfOrNull { s ->
                categoryPosters[s.categoryId]?.takeIf { it.isNotBlank() }
            } ?: ""
            val discoveredThumb = seasons.firstNotNullOfOrNull { s ->
                categoryThumbnails[s.categoryId]?.takeIf { it.isNotBlank() }
            } ?: discoveredPoster

            val genres = inferGenres(canonicalTitle)
            val customDesc = entries.firstNotNullOfOrNull { it.rawDesc.takeIf { d -> d.isNotBlank() } }
            val seasonsSummary = if (seasons.size > 1) {
                "Seasons ${seasons.joinToString(", ") { it.seasonNumber.toString() }} ($totalEpisodes total episodes)"
            } else {
                "$totalEpisodes episodes"
            }
            val description = customDesc ?: "Stream $canonicalTitle in HD with authentic Urdu subtitles and dubbing on Dastan TV. Available: $seasonsSummary."

            unifiedDramas.add(
                DramaSeries(
                    id = "drama_${primarySeason.categoryId}",
                    categoryId = primarySeason.categoryId,
                    title = canonicalTitle,
                    originalTitle = canonicalTitle,
                    slug = dramaKey,
                    description = description,
                    posterUrl = discoveredPoster,
                    thumbnailUrl = discoveredThumb,
                    episodeCount = totalEpisodes,
                    seasonNumber = primarySeason.seasonNumber,
                    genreTags = genres,
                    isFeatured = index < 5,
                    isHidden = false,
                    isVipOnly = false,
                    priorityOrder = index,
                    hasMissingPoster = discoveredPoster.isBlank(),
                    seasons = seasons
                )
            )
            index++
        }

        return unifiedDramas.sortedByDescending { it.episodeCount }
    }

    fun parsePostsJson(
        jsonString: String,
        defaultCategoryId: Int,
        defaultDramaName: String,
        forcedSeasonNumber: Int? = null
    ): Pair<List<EpisodeItem>, Int> {
        val list = mutableListOf<EpisodeItem>()
        val seenPostIds = HashSet<Int>()
        val seenSlugs = HashSet<String>()
        var duplicatesSkipped = 0

        val array = JSONArray(jsonString)
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val postId = obj.optInt("id", 0)
            val slug = obj.optString("slug", "")
            if (postId <= 0) continue

            if (!seenPostIds.add(postId) || (slug.isNotBlank() && !seenSlugs.add(slug))) {
                duplicatesSkipped++
                continue
            }

            val dateStr = obj.optString("date", "").substringBefore("T")
            val link = obj.optString("link", "")
            val titleObj = obj.optJSONObject("title")
            val renderedTitle = decodeHtml(titleObj?.optString("rendered", "") ?: "Episode")

            val contentObj = obj.optJSONObject("content")
            val rawHtmlContent = contentObj?.optString("rendered", "") ?: ""
            val plainDescription = decodeHtml(
                rawHtmlContent
                    .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("<[^>]+>"), " ")
            ).replace(Regex("\\s+"), " ").trim().take(700)

            // Extract categories array (prefer non-1 category IDs)
            val catsArray = obj.optJSONArray("categories")
            val resolvedCategoryId = if (defaultCategoryId > 0) {
                defaultCategoryId
            } else if (catsArray != null && catsArray.length() > 0) {
                var chosenCat = catsArray.optInt(0, 0)
                for (cIdx in 0 until catsArray.length()) {
                    val candidate = catsArray.optInt(cIdx, 0)
                    if (candidate > 1) {
                        chosenCat = candidate
                        break
                    }
                }
                chosenCat
            } else {
                0
            }

            // Extract optimized poster and lightweight thumbnail URLs
            val (posterUrl, thumbnailUrl) = extractPosterAndThumbnailUrls(obj, rawHtmlContent)

            // Extract all authorized video iframe embed URLs (Vidmoly, OK.ru, etc.)
            val embedUrls = extractAuthorizedEmbedUrls(rawHtmlContent)
            val primaryEmbed = embedUrls.firstOrNull() ?: ""
            val backupEmbeds = if (embedUrls.size > 1) embedUrls.drop(1) else emptyList()

            val epNum = EPISODE_NUM_REGEX.find(renderedTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: (i + 1)

            val titleSeasonMatch = SEASON_NUM_REGEX.find(renderedTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val mappedCategorySeason = categoryToSeasonMap[resolvedCategoryId]
            val seasonNum = titleSeasonMatch ?: forcedSeasonNumber ?: mappedCategorySeason ?: 1
            val hasExplicitSeason = titleSeasonMatch != null || forcedSeasonNumber != null || mappedCategorySeason != null

            val mappedDramaTitle = categoryToDramaTitleMap[resolvedCategoryId]
            val inferredDramaTitle = mappedDramaTitle ?: inferDramaTitleFromEpisode(renderedTitle, defaultDramaName)

            val cleanTitle = formatCleanEpisodeTitle(renderedTitle, inferredDramaTitle, epNum)
            val durationLabel = DURATION_REGEX.find(plainDescription)?.groupValues?.getOrNull(1)?.trim().orEmpty()

            list.add(
                EpisodeItem(
                    id = "ep_$postId",
                    postId = postId,
                    categoryId = resolvedCategoryId,
                    dramaTitle = inferredDramaTitle,
                    title = renderedTitle,
                    cleanTitle = cleanTitle,
                    episodeNumber = epNum,
                    seasonNumber = seasonNum,
                    description = plainDescription.ifBlank {
                        "Stream $cleanTitle in HD on Dastan TV."
                    },
                    publishedDate = dateStr,
                    posterUrl = posterUrl,
                    thumbnailUrl = thumbnailUrl.ifBlank { posterUrl },
                    durationLabel = durationLabel,
                    primaryEmbedUrl = primaryEmbed,
                    backupEmbedUrls = backupEmbeds,
                    sourcePostUrl = link,
                    isPlayable = primaryEmbed.isNotBlank(),
                    isHidden = false,
                    isVipOnly = false,
                    hasMissingPoster = posterUrl.isBlank(),
                    hasMissingVideo = primaryEmbed.isBlank(),
                    hasExplicitSeason = hasExplicitSeason
                )
            )
        }
        return Pair(list, duplicatesSkipped)
    }

    private fun extractPosterAndThumbnailUrls(postObj: JSONObject, rawHtml: String): Pair<String, String> {
        val embedded = postObj.optJSONObject("_embedded")
        val mediaArray = embedded?.optJSONArray("wp:featuredmedia")
        if (mediaArray != null && mediaArray.length() > 0) {
            val mediaObj = mediaArray.optJSONObject(0)
            val sizesObj = mediaObj?.optJSONObject("media_details")?.optJSONObject("sizes")

            // Prefer medium (711x400) for thumbnails and medium_large (768x432) for posters instead of huge full images
            val mediumUrl = sizesObj?.optJSONObject("medium")?.optString("source_url", "")?.trim().orEmpty()
            val mediumLargeUrl = sizesObj?.optJSONObject("medium_large")?.optString("source_url", "")?.trim().orEmpty()
            val thumbUrl = sizesObj?.optJSONObject("thumbnail")?.optString("source_url", "")?.trim().orEmpty()
            val fullUrl = mediaObj?.optString("source_url", "")?.trim().orEmpty()

            val bestPoster = listOf(mediumLargeUrl, mediumUrl, fullUrl).firstOrNull { it.startsWith("http") }.orEmpty()
            val bestThumb = listOf(mediumUrl, thumbUrl, bestPoster).firstOrNull { it.startsWith("http") }.orEmpty()
            if (bestPoster.isNotBlank()) {
                return Pair(bestPoster, bestThumb.ifBlank { bestPoster })
            }
        }
        val imgMatch = IMG_SRC_REGEX.find(rawHtml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (imgMatch.startsWith("http")) return Pair(imgMatch, imgMatch)
        return Pair("", "")
    }

    fun extractAuthorizedEmbedUrls(rawHtml: String): List<String> {
        val urls = mutableListOf<String>()
        IFRAME_SRC_REGEX.findAll(rawHtml).forEach { match ->
            val rawSrc = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val src = normalizeEmbedUrl(rawSrc)
            val lower = src.lowercase()
            val isAd = lower.contains("bicea.org") ||
                lower.contains("doubleclick") ||
                lower.contains("googlesyndication") ||
                lower.contains("adsterra")
            if (src.startsWith("https://") && !isAd) {
                urls.add(src)
            }
        }
        return urls.distinct().sortedBy { url ->
            when {
                url.contains("vidmoly", ignoreCase = true) -> 0
                url.contains("ok.ru", ignoreCase = true) -> 1
                else -> 2
            }
        }
    }

    private fun inferDramaTitleFromEpisode(episodeTitle: String, fallback: String): String {
        val beforeEp = episodeTitle
            .replace(Regex("""(?i)^how\s+to\s+watch\s+"""), "")
            .substringBefore("Episode", missingDelimiterValue = "")
            .substringBefore("episode", missingDelimiterValue = "")
            .substringBefore("EPISODE", missingDelimiterValue = "")
            .replace(Regex("""(?i)\bseason\s*\d+\b"""), "")
            .replace(Regex("[-–:]\\s*$"), "")
            .trim()
        val resolution = resolveCanonicalDrama(beforeEp, "")
        return resolution?.canonicalTitle ?: if (beforeEp.length >= 4) beforeEp else fallback
    }

    private fun inferGenres(dramaName: String): List<String> {
        val lower = dramaName.lowercase()
        val tags = mutableListOf("Historical", "Urdu Sub/Dub")
        when {
            lower.contains("sultan") || lower.contains("osman") || lower.contains("ertugrul") || lower.contains("mehmed") || lower.contains("fateh") || lower.contains("orhan") ->
                tags.add("Ottoman Empire")
            lower.contains("barbaros") ->
                tags.add("Naval Action")
            lower.contains("alp") || lower.contains("seljuk") || lower.contains("kudus") || lower.contains("salahuddin") ->
                tags.add("Epic Warfare")
            else ->
                tags.add("Turkish Drama")
        }
        return tags
    }

    private fun decodeHtml(input: String): String {
        if (input.isBlank()) return ""
        return try {
            Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } catch (e: Exception) {
            input
                .replace("&#8217;", "'")
                .replace("&#8211;", "–")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .trim()
        }
    }

    // Merge Admin Firestore Overrides with Live WordPress Dramas
    fun applyAdminOverridesToDramas(
        dramas: List<DramaSeries>,
        overrides: List<CatalogOverride>,
        includeHiddenForAdmin: Boolean
    ): List<DramaSeries> {
        val dramaOverrides = overrides
            .filter { it.targetType == "DRAMA" || it.targetType == "CATEGORY" }
            .associateBy { it.targetId }

        return dramas.map { drama ->
            val ov = dramaOverrides[drama.categoryId.toString()]
                ?: dramaOverrides[drama.id]
                ?: drama.seasons.firstNotNullOfOrNull { dramaOverrides[it.categoryId.toString()] }
            if (ov != null) {
                val resolvedPoster = ov.customPosterUrl.ifBlank { drama.posterUrl }
                drama.copy(
                    title = ov.customTitle.ifBlank { drama.title },
                    description = ov.customDescription.ifBlank { drama.description },
                    posterUrl = resolvedPoster,
                    thumbnailUrl = ov.customPosterUrl.ifBlank { drama.thumbnailUrl.ifBlank { resolvedPoster } },
                    isHidden = ov.isHidden,
                    isFeatured = ov.isFeatured,
                    isVipOnly = ov.isVipOnly,
                    priorityOrder = ov.priorityOrder.toInt(),
                    hasMissingPoster = resolvedPoster.isBlank()
                )
            } else {
                drama
            }
        }.filter { includeHiddenForAdmin || !it.isHidden }
            .sortedWith(
                compareByDescending<DramaSeries> { it.isFeatured }
                    .thenBy { it.priorityOrder }
                    .thenByDescending { it.episodeCount }
            )
    }

    // Merge Admin Firestore Overrides with Live WordPress Episodes (Supports Admin Season + Episode Correction)
    fun applyAdminOverridesToEpisodes(
        episodes: List<EpisodeItem>,
        overrides: List<CatalogOverride>,
        includeHiddenForAdmin: Boolean
    ): List<EpisodeItem> {
        val epOverrides = overrides
            .filter { it.targetType == "EPISODE" }
            .associateBy { it.targetId }

        return episodes.map { ep ->
            val ov = epOverrides[ep.postId.toString()] ?: epOverrides[ep.id]
            if (ov != null) {
                val pOrder = ov.priorityOrder.toInt()
                // Admin correction encoding: priorityOrder = seasonNumber * 1000 + episodeNumber
                val correctedSeason = if (pOrder >= 1000) (pOrder / 1000).coerceAtLeast(1) else ep.seasonNumber
                val correctedEpisode = if (pOrder >= 1000) (pOrder % 1000).coerceAtLeast(1)
                else if (pOrder in 1..999) pOrder else ep.episodeNumber
                val updatedTitle = ov.customTitle.ifBlank { ep.title }

                ep.copy(
                    title = updatedTitle,
                    cleanTitle = if (ov.customTitle.isNotBlank()) ov.customTitle else formatCleanEpisodeTitle(updatedTitle, ep.dramaTitle, correctedEpisode),
                    description = ov.customDescription.ifBlank { ep.description },
                    posterUrl = ov.customPosterUrl.ifBlank { ep.posterUrl },
                    thumbnailUrl = ov.customPosterUrl.ifBlank { ep.thumbnailUrl },
                    seasonNumber = correctedSeason,
                    episodeNumber = correctedEpisode,
                    isHidden = ov.isHidden,
                    isVipOnly = ov.isVipOnly
                )
            } else {
                ep
            }
        }.filter { includeHiddenForAdmin || !it.isHidden }
            .sortedWith(compareBy<EpisodeItem> { it.seasonNumber }.thenBy { it.episodeNumber })
    }
}
