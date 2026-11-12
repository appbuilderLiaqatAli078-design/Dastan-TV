package com.example.data.remote

import android.text.Html
import android.util.Log
import com.example.data.model.ApiHealthReport
import com.example.data.model.CatalogOverride
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    ): Response< okhttp3.ResponseBody >

    @GET("posts")
    suspend fun getPostsByCategory(
        @Query("categories") categoryId: Int,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1,
        @Query("_embed") embed: Int = 1
    ): Response< okhttp3.ResponseBody >

    @GET("posts")
    suspend fun getLatestPosts(
        @Query("per_page") perPage: Int = 40,
        @Query("page") page: Int = 1,
        @Query("_embed") embed: Int = 1
    ): Response< okhttp3.ResponseBody >
}

class WordPressContentRepository {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val api: WordPressRawApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .build()
        .create(WordPressRawApi::class.java)

    private val cacheMutex = Mutex()
    private var cachedCategories: List<DramaSeries> = emptyList()
    private val cachedCategoryEpisodes = ConcurrentHashMap<Int, List<EpisodeItem>>()
    private var cachedLatestEpisodes: List<EpisodeItem> = emptyList()
    private var lastCacheTimestampMs: Long = 0L
    private var lastHealthReport: ApiHealthReport = ApiHealthReport()

    companion object {
        private const val TAG = "WPContentRepo"
        const val BASE_URL = "https://giveme5.co/wp-json/wp/v2/"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes server/client cache

        private val IFRAME_SRC_REGEX = Regex(
            """<iframe[^>]+src=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val IMG_SRC_REGEX = Regex(
            """<img[^>]+src=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        )
        private val EPISODE_NUM_REGEX = Regex(
            """(?:Episode|Ep|Bolum)\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        private val SEASON_NUM_REGEX = Regex(
            """Season\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
    }

    private suspend fun <T> executeWithRetry(
        maxAttempts: Int = 3,
        initialDelayMs: Long = 600L,
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
        }

        val startTime = System.currentTimeMillis()
        var statusCode = 200
        var errorMessage: String? = null
        var duplicateCount = 0

        try {
            val categoriesResponse = executeWithRetry { api.getCategories(perPage = 100, page = 1) }
            statusCode = categoriesResponse.code()
            val categoriesBody = categoriesResponse.body()?.string() ?: "[]"

            val latestPostsResponse = executeWithRetry { api.getLatestPosts(perPage = 40, page = 1) }
            val latestPostsBody = latestPostsResponse.body()?.string() ?: "[]"

            val parsedLatestResult = parsePostsJson(
                jsonString = latestPostsBody,
                defaultCategoryId = 0,
                defaultDramaName = "Turkish Historical Drama"
            )
            val latestEpisodes = parsedLatestResult.first
            duplicateCount += parsedLatestResult.second

            // Map category ID -> latest poster from episodes in that category
            val categoryPosters = mutableMapOf<Int, String>()
            for (ep in latestEpisodes) {
                if (ep.categoryId > 0 && ep.posterUrl.isNotBlank() && !categoryPosters.containsKey(ep.categoryId)) {
                    categoryPosters[ep.categoryId] = ep.posterUrl
                }
            }

            val parsedDramas = parseCategoriesJson(categoriesBody, categoryPosters)
            val latency = System.currentTimeMillis() - startTime

            val missingPosters = parsedDramas.count { it.hasMissingPoster } +
                latestEpisodes.count { it.hasMissingPoster }
            val missingVideos = latestEpisodes.count { it.hasMissingVideo }
            val playableCount = latestEpisodes.count { it.isPlayable }

            cacheMutex.withLock {
                cachedCategories = parsedDramas
                cachedLatestEpisodes = latestEpisodes
                lastCacheTimestampMs = System.currentTimeMillis()
                lastHealthReport = ApiHealthReport(
                    endpointUrl = "https://giveme5.co/wp-json/wp/v2",
                    isHealthy = categoriesResponse.isSuccessful && latestPostsResponse.isSuccessful,
                    httpStatusCode = statusCode,
                    responseLatencyMs = latency,
                    totalCategoriesFetched = parsedDramas.size,
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
            Log.e(TAG, "Failed to fetch live WP content", e)
            cacheMutex.withLock {
                lastHealthReport = lastHealthReport.copy(
                    isHealthy = false,
                    httpStatusCode = if (statusCode == 200) 503 else statusCode,
                    responseLatencyMs = latency,
                    lastCheckedTimestampMs = System.currentTimeMillis(),
                    errorMessage = errorMessage
                )
                Pair(cachedCategories, cachedLatestEpisodes)
            }
        }
    }

    suspend fun fetchEpisodesForCategory(
        categoryId: Int,
        dramaTitle: String,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): List<EpisodeItem> = withContext(Dispatchers.IO) {
        if (page == 1 && !forceRefresh) {
            cachedCategoryEpisodes[categoryId]?.let { if (it.isNotEmpty()) return@withContext it }
        }
        try {
            val response = executeWithRetry {
                api.getPostsByCategory(categoryId = categoryId, perPage = 50, page = page, embed = 1)
            }
            val body = response.body()?.string() ?: "[]"
            val parsed = parsePostsJson(
                jsonString = body,
                defaultCategoryId = categoryId,
                defaultDramaName = dramaTitle
            ).first.sortedBy { it.episodeNumber }

            if (page == 1) {
                cachedCategoryEpisodes[categoryId] = parsed
                // Update category poster if we discovered one from its episodes
                val discoveredPoster = parsed.firstOrNull { it.posterUrl.isNotBlank() }?.posterUrl
                if (!discoveredPoster.isNullOrBlank()) {
                    cacheMutex.withLock {
                        cachedCategories = cachedCategories.map { drama ->
                            if (drama.categoryId == categoryId && drama.posterUrl.isBlank()) {
                                drama.copy(posterUrl = discoveredPoster, hasMissingPoster = false)
                            } else {
                                drama
                            }
                        }
                    }
                }
            } else {
                val existing = cachedCategoryEpisodes[categoryId].orEmpty()
                val merged = (existing + parsed).distinctBy { it.postId }.sortedBy { it.episodeNumber }
                cachedCategoryEpisodes[categoryId] = merged
            }
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch episodes for category $categoryId page $page", e)
            cachedCategoryEpisodes[categoryId].orEmpty()
        }
    }

    suspend fun runApiHealthCheck(): ApiHealthReport = withContext(Dispatchers.IO) {
        fetchDramasAndLatestEpisodes(forceRefresh = true)
        cacheMutex.withLock { lastHealthReport }
    }

    fun getLatestHealthReport(): ApiHealthReport = lastHealthReport

    private fun parseCategoriesJson(
        jsonString: String,
        categoryPosters: Map<Int, String>
    ): List<DramaSeries> {
        val result = mutableListOf<DramaSeries>()
        val seenNames = HashSet<String>()
        val array = JSONArray(jsonString)

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optInt("id", 0)
            val count = obj.optInt("count", 0)
            val rawName = decodeHtml(obj.optString("name", "")).trim()
            val slug = obj.optString("slug", "")
            val rawDesc = decodeHtml(obj.optString("description", "")).trim()

            if (id <= 0 || rawName.isBlank() || slug == "uncategorized" || count <= 0) continue
            val normalizedKey = rawName.lowercase()
            if (!seenNames.add(normalizedKey)) continue

            val seasonMatch = SEASON_NUM_REGEX.find(rawName)
            val seasonNum = seasonMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
            val posterUrl = categoryPosters[id] ?: ""

            val genres = inferGenres(rawName)
            val description = rawDesc.ifBlank {
                "Watch $rawName in high-definition with authentic Urdu subtitles and dubbing on Dastan TV. Featuring $count episodes of epic Turkish historical drama, loyalty, and empire."
            }

            result.add(
                DramaSeries(
                    id = "drama_$id",
                    categoryId = id,
                    title = rawName,
                    originalTitle = rawName.replace(Regex("Season\\s*\\d+", RegexOption.IGNORE_CASE), "").trim(),
                    slug = slug,
                    description = description,
                    posterUrl = posterUrl,
                    episodeCount = count,
                    seasonNumber = seasonNum,
                    genreTags = genres,
                    isFeatured = i < 5,
                    isHidden = false,
                    isVipOnly = false,
                    priorityOrder = i,
                    hasMissingPoster = posterUrl.isBlank()
                )
            )
        }
        return result.sortedByDescending { it.episodeCount }
    }

    private fun parsePostsJson(
        jsonString: String,
        defaultCategoryId: Int,
        defaultDramaName: String
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

            // Extract categories array
            val catsArray = obj.optJSONArray("categories")
            val resolvedCategoryId = if (defaultCategoryId > 0) {
                defaultCategoryId
            } else if (catsArray != null && catsArray.length() > 0) {
                catsArray.optInt(0, 0)
            } else {
                0
            }

            // Extract featured image from _embedded["wp:featuredmedia"] or fallback <img>
            val posterUrl = extractPosterUrl(obj, rawHtmlContent)

            // Extract all authorized video iframe embed URLs (Vidmoly, OK.ru, etc.)
            val embedUrls = extractAuthorizedEmbedUrls(rawHtmlContent)
            val primaryEmbed = embedUrls.firstOrNull() ?: ""
            val backupEmbeds = if (embedUrls.size > 1) embedUrls.drop(1) else emptyList()

            val epNum = EPISODE_NUM_REGEX.find(renderedTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: (i + 1)
            val seasonNum = SEASON_NUM_REGEX.find(renderedTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 1

            val inferredDramaTitle = inferDramaTitleFromEpisode(renderedTitle, defaultDramaName)

            list.add(
                EpisodeItem(
                    id = "ep_$postId",
                    postId = postId,
                    categoryId = resolvedCategoryId,
                    dramaTitle = inferredDramaTitle,
                    title = renderedTitle,
                    episodeNumber = epNum,
                    seasonNumber = seasonNum,
                    description = plainDescription.ifBlank {
                        "Stream $renderedTitle in HD on Dastan TV."
                    },
                    publishedDate = dateStr,
                    posterUrl = posterUrl,
                    primaryEmbedUrl = primaryEmbed,
                    backupEmbedUrls = backupEmbeds,
                    sourcePostUrl = link,
                    isPlayable = primaryEmbed.isNotBlank(),
                    isHidden = false,
                    isVipOnly = false,
                    hasMissingPoster = posterUrl.isBlank(),
                    hasMissingVideo = primaryEmbed.isBlank()
                )
            )
        }
        return Pair(list, duplicatesSkipped)
    }

    private fun extractPosterUrl(postObj: JSONObject, rawHtml: String): String {
        val embedded = postObj.optJSONObject("_embedded")
        val mediaArray = embedded?.optJSONArray("wp:featuredmedia")
        if (mediaArray != null && mediaArray.length() > 0) {
            val mediaObj = mediaArray.optJSONObject(0)
            val directUrl = mediaObj?.optString("source_url", "")?.trim().orEmpty()
            if (directUrl.startsWith("http")) return directUrl
        }
        val imgMatch = IMG_SRC_REGEX.find(rawHtml)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (imgMatch.startsWith("http")) return imgMatch
        return ""
    }

    private fun extractAuthorizedEmbedUrls(rawHtml: String): List<String> {
        val urls = mutableListOf<String>()
        IFRAME_SRC_REGEX.findAll(rawHtml).forEach { match ->
            var src = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (src.startsWith("//")) {
                src = "https:$src"
            }
            // Filter out ad iframes and keep video player embeds
            val lower = src.lowercase()
            val isAd = lower.contains("bicea.org") ||
                lower.contains("doubleclick") ||
                lower.contains("googlesyndication") ||
                lower.contains("adsterra")
            if (src.startsWith("https://") && !isAd) {
                urls.add(src)
            }
        }
        // Sort so Vidmoly or OK.ru comes first
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
            .substringBefore("Episode", missingDelimiterValue = "")
            .substringBefore("episode", missingDelimiterValue = "")
            .replace(Regex("[-–:]\\s*$"), "")
            .trim()
        return if (beforeEp.length >= 4) beforeEp else fallback
    }

    private fun inferGenres(dramaName: String): List<String> {
        val lower = dramaName.lowercase()
        val tags = mutableListOf("Historical", "Urdu Sub/Dub")
        when {
            lower.contains("sultan") || lower.contains("osman") || lower.contains("ertugrul") || lower.contains("mehmed") || lower.contains("fateh") ->
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
            val ov = dramaOverrides[drama.categoryId.toString()] ?: dramaOverrides[drama.id]
            if (ov != null) {
                drama.copy(
                    title = ov.customTitle.ifBlank { drama.title },
                    description = ov.customDescription.ifBlank { drama.description },
                    posterUrl = ov.customPosterUrl.ifBlank { drama.posterUrl },
                    isHidden = ov.isHidden,
                    isFeatured = ov.isFeatured,
                    isVipOnly = ov.isVipOnly,
                    priorityOrder = ov.priorityOrder.toInt(),
                    hasMissingPoster = ov.customPosterUrl.isBlank() && drama.posterUrl.isBlank()
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

    // Merge Admin Firestore Overrides with Live WordPress Episodes
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
                ep.copy(
                    title = ov.customTitle.ifBlank { ep.title },
                    description = ov.customDescription.ifBlank { ep.description },
                    posterUrl = ov.customPosterUrl.ifBlank { ep.posterUrl },
                    isHidden = ov.isHidden,
                    isVipOnly = ov.isVipOnly
                )
            } else {
                ep
            }
        }.filter { includeHiddenForAdmin || !it.isHidden }
    }
}
