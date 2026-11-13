package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.local.LocalCatalogCacheDatabase
import com.example.data.model.ContentSource
import com.example.data.model.DramaSeason
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class DramaNitamCategory(
    val id: Int,
    val name: String,
    val slug: String,
    val count: Int
)

class DramaNitamContentRepository(
    context: Context? = null
) {
    private val appContext: Context? = context?.applicationContext
    private val localCacheDb: LocalCatalogCacheDatabase? = appContext?.let {
        LocalCatalogCacheDatabase.getInstance(it)
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (appContext != null) {
                val cacheDir = File(appContext.cacheDir, "dastan_dramanitam_http_cache")
                cache(Cache(directory = cacheDir, maxSize = 20L * 1024L * 1024L))
            }
        }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Volatile
    private var cachedCategories: List<DramaNitamCategory> = emptyList()

    @Volatile
    private var cachedDramas: List<DramaSeries> = emptyList()

    @Volatile
    private var cachedLatestEpisodes: List<EpisodeItem> = emptyList()

    private val seasonEpisodesMemoryCache = ConcurrentHashMap<Int, List<EpisodeItem>>()
    private val postPortalUrlMap = ConcurrentHashMap<Int, String>()
    private val postSourceUrlMap = ConcurrentHashMap<Int, String>()
    private val postPosterUrlMap = ConcurrentHashMap<Int, String>()
    private val postDurationMap = ConcurrentHashMap<Int, String>()
    private val postAudioLangMap = ConcurrentHashMap<Int, String>()
    private val postEpisodeCountMap = ConcurrentHashMap<Int, Int>()

    suspend fun fetchDubbedCatalog(
        forceRefresh: Boolean = false
    ): Triple<List<DramaSeries>, List<EpisodeItem>, List<String>> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedDramas.isNotEmpty()) {
            val categoryNames = cachedCategories.map { it.name }.ifEmpty {
                cachedDramas.map { it.sourceCategoryName }.filter { it.isNotBlank() }.distinct()
            }
            return@withContext Triple(cachedDramas, cachedLatestEpisodes, categoryNames)
        }

        if (!forceRefresh && localCacheDb != null) {
            val sqliteDramas = localCacheDb.getCachedDramas(ContentSource.DRAMANITAM_DUBBED)
            val sqliteLatest = localCacheDb.getCachedLatestEpisodes(ContentSource.DRAMANITAM_DUBBED)
            val isFresh = localCacheDb.isCacheKeyFresh(LocalCatalogCacheDatabase.KEY_DRAMANITAM_DRAMAS)
            if (sqliteDramas.isNotEmpty()) {
                sqliteDramas.forEach { drama ->
                    drama.seasons.forEach { season ->
                        if (season.episodePortalUrl.isNotBlank()) {
                            postPortalUrlMap[season.categoryId] = season.episodePortalUrl
                        }
                        postPosterUrlMap[season.categoryId] = drama.posterUrl
                        postAudioLangMap[season.categoryId] = drama.audioLanguage
                        postEpisodeCountMap[season.categoryId] = season.episodeCount
                    }
                }
                cachedDramas = sqliteDramas
                cachedLatestEpisodes = sqliteLatest
                val catNames = sqliteDramas.map { it.sourceCategoryName }.filter { it.isNotBlank() }.distinct()
                if (isFresh) {
                    return@withContext Triple(sqliteDramas, sqliteLatest, catNames)
                }
            }
        }

        try {
            val categories = fetchCategoriesInternal()
            cachedCategories = categories
            val categoryById = categories.associateBy { it.id }

            // Prioritize Turkish Dramas (6) & Korean Dramas (5), plus recent posts across all categories
            val priorityCategoryIds = buildList {
                categories.firstOrNull { it.slug.contains("turkish", ignoreCase = true) }?.let { add(it.id) }
                categories.firstOrNull { it.slug.contains("korean", ignoreCase = true) }?.let { add(it.id) }
                categories.firstOrNull { it.slug.contains("chinese", ignoreCase = true) }?.let { add(it.id) }
                categories.forEach { cat ->
                    if (!contains(cat.id) && size < 5) add(cat.id)
                }
            }

            val rawPostsById = LinkedHashMap<Int, JSONObject>()

            coroutineScope {
                val deferredCategoryPosts = priorityCategoryIds.map { catId ->
                    async {
                        runCatching {
                            val url = "${BASE_API_URL}posts?categories=$catId&per_page=35&_embed=1"
                            JSONArray(executeGet(url))
                        }.getOrNull()
                    }
                }
                val deferredLatestPosts = async {
                    runCatching {
                        val url = "${BASE_API_URL}posts?per_page=40&_embed=1"
                        JSONArray(executeGet(url))
                    }.getOrNull()
                }

                val arrays = deferredCategoryPosts.awaitAll() + deferredLatestPosts.await()
                for (arr in arrays.filterNotNull()) {
                    for (i in 0 until arr.length()) {
                        val postObj = arr.optJSONObject(i) ?: continue
                        val postId = postObj.optInt("id", -1)
                        if (postId > 0 && !rawPostsById.containsKey(postId)) {
                            rawPostsById[postId] = postObj
                        }
                    }
                }
            }

            val parsedPostEntries = rawPostsById.values.mapNotNull { postObj ->
                parseDramaPostObject(postObj, categoryById)
            }

            val groupedDramas = groupPostsIntoMultiSeasonDramas(parsedPostEntries)
            val latestDubbedEpisodes = parsedPostEntries.take(25).map { entry ->
                EpisodeItem(
                    id = "dramanitam_ep_${entry.postId}_s${entry.seasonNumber}_e${entry.latestEpisodeAdded}",
                    postId = entry.postId,
                    categoryId = entry.postId,
                    dramaTitle = entry.cleanDramaTitle,
                    title = "${entry.cleanDramaTitle} • S${entry.seasonNumber} E${entry.latestEpisodeAdded}",
                    cleanTitle = "Episode ${entry.latestEpisodeAdded} (${entry.audioLanguage})",
                    episodeNumber = entry.latestEpisodeAdded,
                    seasonNumber = entry.seasonNumber,
                    description = entry.synopsis,
                    publishedDate = entry.publishedDate,
                    posterUrl = entry.posterUrl,
                    thumbnailUrl = entry.posterUrl,
                    durationLabel = entry.durationLabel,
                    primaryEmbedUrl = entry.episodePortalUrl.ifBlank { entry.sourcePostUrl },
                    backupEmbedUrls = listOfNotNull(entry.sourcePostUrl.takeIf { it.isNotBlank() }),
                    sourcePostUrl = entry.sourcePostUrl,
                    isPlayable = true,
                    hasMissingPoster = entry.posterUrl.isBlank(),
                    hasMissingVideo = false,
                    hasExplicitSeason = entry.hasExplicitSeason,
                    source = ContentSource.DRAMANITAM_DUBBED,
                    sourceCategoryName = entry.categoryName,
                    audioLanguage = entry.audioLanguage
                )
            }

            if (groupedDramas.isNotEmpty()) {
                cachedDramas = groupedDramas
                cachedLatestEpisodes = latestDubbedEpisodes
                localCacheDb?.saveDramas(groupedDramas, ContentSource.DRAMANITAM_DUBBED)
                if (latestDubbedEpisodes.isNotEmpty()) {
                    localCacheDb?.saveEpisodes(
                        episodes = latestDubbedEpisodes,
                        cacheKey = "dramanitam_latest_feed",
                        isLatestFeed = true
                    )
                }
            }

            val categoryNames = categories.map { it.name }.ifEmpty {
                groupedDramas.map { it.sourceCategoryName }.filter { it.isNotBlank() }.distinct()
            }
            Triple(cachedDramas, cachedLatestEpisodes, categoryNames)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch DramaNitam catalog: ${e.message}", e)
            val fallbackDramas = cachedDramas.ifEmpty {
                localCacheDb?.getCachedDramas(ContentSource.DRAMANITAM_DUBBED).orEmpty()
            }
            val fallbackLatest = cachedLatestEpisodes.ifEmpty {
                localCacheDb?.getCachedLatestEpisodes(ContentSource.DRAMANITAM_DUBBED).orEmpty()
            }
            val catNames = fallbackDramas.map { it.sourceCategoryName }.filter { it.isNotBlank() }.distinct()
            Triple(fallbackDramas, fallbackLatest, catNames)
        }
    }

    suspend fun fetchEpisodesForSeasonPost(
        seasonPostId: Int,
        dramaTitle: String,
        expectedSeasonNumber: Int = 1,
        forceRefresh: Boolean = false
    ): List<EpisodeItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            seasonEpisodesMemoryCache[seasonPostId]?.let { cached ->
                if (cached.isNotEmpty()) return@withContext cached
            }
            val sqliteCached = localCacheDb?.getCachedEpisodesForCategory(
                categoryId = seasonPostId,
                source = ContentSource.DRAMANITAM_DUBBED
            ).orEmpty()
            val isFresh = localCacheDb?.isCacheKeyFresh("dramanitam_cat_eps_$seasonPostId") == true
            if (sqliteCached.isNotEmpty() && isFresh) {
                seasonEpisodesMemoryCache[seasonPostId] = sqliteCached
                return@withContext sqliteCached
            }
        }

        try {
            var portalUrl = postPortalUrlMap[seasonPostId].orEmpty()
            var sourcePostUrl = postSourceUrlMap[seasonPostId].orEmpty()
            var posterUrl = postPosterUrlMap[seasonPostId].orEmpty()
            var durationLabel = postDurationMap[seasonPostId] ?: "45m"
            var audioLang = postAudioLangMap[seasonPostId] ?: "Hindi/Urdu Dubbed"
            var expectedCount = postEpisodeCountMap[seasonPostId] ?: 1

            if (portalUrl.isBlank() || posterUrl.isBlank()) {
                runCatching {
                    val postJson = JSONObject(executeGet("${BASE_API_URL}posts/$seasonPostId?_embed=1"))
                    val parsed = parseDramaPostObject(postJson, emptyMap())
                    if (parsed != null) {
                        portalUrl = parsed.episodePortalUrl
                        sourcePostUrl = parsed.sourcePostUrl
                        posterUrl = parsed.posterUrl
                        durationLabel = parsed.durationLabel
                        audioLang = parsed.audioLanguage
                        expectedCount = parsed.episodeCount
                    }
                }
            }

            val parsedEpisodes = mutableListOf<EpisodeItem>()

            // If we have an episode-wise blogspot/portal URL, fetch and parse real Episode blocks
            if (portalUrl.isNotBlank() && !portalUrl.contains("#?url=")) {
                runCatching {
                    val portalHtml = executeGet(portalUrl)
                    val extractedFromPortal = parseEpisodeWiseHtmlPage(
                        html = portalHtml,
                        seasonPostId = seasonPostId,
                        dramaTitle = dramaTitle,
                        seasonNumber = expectedSeasonNumber,
                        posterUrl = posterUrl,
                        durationLabel = durationLabel,
                        audioLanguage = audioLang,
                        portalUrl = portalUrl
                    )
                    parsedEpisodes.addAll(extractedFromPortal)
                }.onFailure { e ->
                    Log.w(TAG, "Failed to parse portal page $portalUrl: ${e.message}")
                }
            }

            // If the portal page is a dynamic JS redirector (like nibosob/hivenew) or didn't expose static Episode blocks,
            // generate episode items backed by the portal/post URL so all announced episodes are accessible
            if (parsedEpisodes.isEmpty()) {
                val safeCount = expectedCount.coerceIn(1, 60)
                val targetStreamUrl = portalUrl.ifBlank { sourcePostUrl }
                for (epNum in 1..safeCount) {
                    parsedEpisodes.add(
                        EpisodeItem(
                            id = "dramanitam_ep_${seasonPostId}_s${expectedSeasonNumber}_e$epNum",
                            postId = seasonPostId * 100 + epNum,
                            categoryId = seasonPostId,
                            dramaTitle = dramaTitle,
                            title = "$dramaTitle • Season $expectedSeasonNumber Episode $epNum",
                            cleanTitle = "Episode $epNum ($audioLang)",
                            episodeNumber = epNum,
                            seasonNumber = expectedSeasonNumber,
                            description = "$dramaTitle — Season $expectedSeasonNumber, Episode $epNum in $audioLang.",
                            publishedDate = "",
                            posterUrl = posterUrl,
                            thumbnailUrl = posterUrl,
                            durationLabel = durationLabel,
                            primaryEmbedUrl = targetStreamUrl,
                            backupEmbedUrls = listOfNotNull(
                                sourcePostUrl.takeIf { it.isNotBlank() && it != targetStreamUrl }
                            ),
                            sourcePostUrl = sourcePostUrl.ifBlank { targetStreamUrl },
                            isPlayable = targetStreamUrl.isNotBlank(),
                            hasMissingPoster = posterUrl.isBlank(),
                            hasMissingVideo = targetStreamUrl.isBlank(),
                            hasExplicitSeason = true,
                            source = ContentSource.DRAMANITAM_DUBBED,
                            sourceCategoryName = audioLang,
                            audioLanguage = audioLang
                        )
                    )
                }
            }

            val sorted = parsedEpisodes
                .distinctBy { it.episodeNumber }
                .sortedBy { it.episodeNumber }

            if (sorted.isNotEmpty()) {
                seasonEpisodesMemoryCache[seasonPostId] = sorted
                localCacheDb?.saveEpisodes(
                    episodes = sorted,
                    cacheKey = "dramanitam_cat_eps_$seasonPostId",
                    isLatestFeed = false
                )
            }
            sorted
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load DramaNitam episodes for post $seasonPostId: ${e.message}")
            localCacheDb?.getCachedEpisodesForCategory(
                categoryId = seasonPostId,
                source = ContentSource.DRAMANITAM_DUBBED
            ).orEmpty()
        }
    }

    private fun fetchCategoriesInternal(): List<DramaNitamCategory> {
        val raw = executeGet("${BASE_API_URL}categories?per_page=100")
        val arr = JSONArray(raw)
        val result = mutableListOf<DramaNitamCategory>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val count = obj.optInt("count", 0)
            val slug = obj.optString("slug", "")
            val name = decodeHtmlEntities(obj.optString("name", ""))
            if (count <= 0 || slug.equals("uncategorized", ignoreCase = true)) continue
            result.add(
                DramaNitamCategory(
                    id = obj.optInt("id"),
                    name = name,
                    slug = slug,
                    count = count
                )
            )
        }
        // Sort so Turkish Dramas and Korean Dramas appear first
        return result.sortedWith(
            compareBy<DramaNitamCategory> { cat ->
                when {
                    cat.name.contains("Turkish", ignoreCase = true) -> 0
                    cat.name.contains("Korean", ignoreCase = true) -> 1
                    cat.name.contains("Chinese", ignoreCase = true) -> 2
                    else -> 3
                }
            }.thenByDescending { it.count }
        )
    }

    internal data class ParsedDramaPost(
        val postId: Int,
        val rawTitle: String,
        val cleanDramaTitle: String,
        val slug: String,
        val categoryId: Int,
        val categoryName: String,
        val seasonNumber: Int,
        val hasExplicitSeason: Boolean,
        val episodeCount: Int,
        val latestEpisodeAdded: Int,
        val audioLanguage: String,
        val durationLabel: String,
        val posterUrl: String,
        val synopsis: String,
        val publishedDate: String,
        val sourcePostUrl: String,
        val episodePortalUrl: String
    )

    internal fun parseDramaPostObject(
        postObj: JSONObject,
        categoryById: Map<Int, DramaNitamCategory>
    ): ParsedDramaPost? {
        val postId = postObj.optInt("id", -1)
        if (postId <= 0) return null

        val rawTitle = decodeHtmlEntities(
            postObj.optJSONObject("title")?.optString("rendered", "").orEmpty()
        ).trim()
        if (rawTitle.isBlank()) return null

        val slug = postObj.optString("slug", "drama-$postId")
        val contentHtml = postObj.optJSONObject("content")?.optString("rendered", "").orEmpty()
        val excerptHtml = postObj.optJSONObject("excerpt")?.optString("rendered", "").orEmpty()
        val sourcePostUrl = postObj.optString("link", "")
        val publishedDate = postObj.optString("date", "").substringBefore("T")

        val categoriesJson = postObj.optJSONArray("categories")
        var primaryCatId = 6
        var categoryName = "Hindi & Urdu Dubbed"
        if (categoriesJson != null && categoriesJson.length() > 0) {
            primaryCatId = categoriesJson.optInt(0, 6)
            categoryName = categoryById[primaryCatId]?.name ?: inferCategoryNameFromTitleAndContent(rawTitle, contentHtml)
        } else {
            categoryName = inferCategoryNameFromTitleAndContent(rawTitle, contentHtml)
        }

        val (cleanTitle, explicitSeasonFromTitle) = extractCleanTitleAndSeason(rawTitle)
        val explicitSeasonFromHtml = extractSeasonFromHtmlMetadata(contentHtml)
        val seasonNumber = explicitSeasonFromTitle ?: explicitSeasonFromHtml ?: 1
        val hasExplicitSeason = (explicitSeasonFromTitle != null || explicitSeasonFromHtml != null)

        val (episodeCount, latestAdded) = extractEpisodeCounts(rawTitle, contentHtml)
        val audioLang = extractAudioLanguage(rawTitle, contentHtml)
        val durationLabel = extractDurationLabel(contentHtml)
        val posterUrl = extractPosterUrl(postObj, contentHtml)
        val portalUrl = extractEpisodePortalUrl(contentHtml)
        val synopsis = extractCleanSynopsis(excerptHtml, contentHtml, cleanTitle, categoryName, audioLang)

        if (portalUrl.isNotBlank()) postPortalUrlMap[postId] = portalUrl
        if (sourcePostUrl.isNotBlank()) postSourceUrlMap[postId] = sourcePostUrl
        if (posterUrl.isNotBlank()) postPosterUrlMap[postId] = posterUrl
        postDurationMap[postId] = durationLabel
        postAudioLangMap[postId] = audioLang
        postEpisodeCountMap[postId] = episodeCount

        return ParsedDramaPost(
            postId = postId,
            rawTitle = rawTitle,
            cleanDramaTitle = cleanTitle,
            slug = slug,
            categoryId = primaryCatId,
            categoryName = categoryName,
            seasonNumber = seasonNumber,
            hasExplicitSeason = hasExplicitSeason,
            episodeCount = episodeCount,
            latestEpisodeAdded = latestAdded,
            audioLanguage = audioLang,
            durationLabel = durationLabel,
            posterUrl = posterUrl,
            synopsis = synopsis,
            publishedDate = publishedDate,
            sourcePostUrl = sourcePostUrl,
            episodePortalUrl = portalUrl
        )
    }

    internal fun groupPostsIntoMultiSeasonDramas(
        posts: List<ParsedDramaPost>
    ): List<DramaSeries> {
        if (posts.isEmpty()) return emptyList()

        val groupedByNormalizedTitle = LinkedHashMap<String, MutableList<ParsedDramaPost>>()
        for (post in posts) {
            val key = post.cleanDramaTitle
                .lowercase()
                .replace(Regex("""[^a-z0-9]+"""), " ")
                .trim()
                .ifBlank { "dramanitam_${post.postId}" }
            groupedByNormalizedTitle.getOrPut(key) { mutableListOf() }.add(post)
        }

        var priorityIdx = 1
        return groupedByNormalizedTitle.values.map { siblingPosts ->
            val sortedSiblings = siblingPosts.sortedBy { it.seasonNumber }
            val usedSeasonNumbers = mutableSetOf<Int>()
            val seasons = sortedSiblings.mapIndexed { idx, post ->
                var sNum = post.seasonNumber
                if (usedSeasonNumbers.contains(sNum)) {
                    sNum = (usedSeasonNumbers.maxOrNull() ?: idx) + 1
                }
                usedSeasonNumbers.add(sNum)

                DramaSeason(
                    seasonNumber = sNum,
                    categoryId = post.postId,
                    categoryName = "Season $sNum (${post.audioLanguage})",
                    slug = post.slug,
                    episodeCount = post.episodeCount,
                    hasExplicitSeasonMetadata = post.hasExplicitSeason,
                    source = ContentSource.DRAMANITAM_DUBBED,
                    episodePortalUrl = post.episodePortalUrl
                )
            }.sortedBy { it.seasonNumber }

            val primary = sortedSiblings.firstOrNull { it.posterUrl.isNotBlank() } ?: sortedSiblings.first()
            val totalEps = seasons.sumOf { it.episodeCount }
            val genreTags = buildList {
                if (primary.categoryName.isNotBlank()) add(primary.categoryName)
                add(primary.audioLanguage)
                if (seasons.size > 1) add("${seasons.size} Seasons")
            }.distinct()

            val currentOrder = priorityIdx++
            DramaSeries(
                id = "dramanitam_drama_${primary.postId}",
                categoryId = primary.postId,
                title = primary.cleanDramaTitle,
                originalTitle = primary.rawTitle,
                slug = primary.slug,
                description = primary.synopsis,
                posterUrl = primary.posterUrl,
                thumbnailUrl = primary.posterUrl,
                episodeCount = totalEps,
                seasonNumber = seasons.first().seasonNumber,
                genreTags = genreTags,
                isFeatured = currentOrder <= 5,
                priorityOrder = currentOrder,
                hasMissingPoster = primary.posterUrl.isBlank(),
                seasons = seasons,
                source = ContentSource.DRAMANITAM_DUBBED,
                sourceCategoryName = primary.categoryName,
                audioLanguage = primary.audioLanguage,
                episodePortalUrl = primary.episodePortalUrl
            )
        }
    }

    internal fun parseEpisodeWiseHtmlPage(
        html: String,
        seasonPostId: Int,
        dramaTitle: String,
        seasonNumber: Int,
        posterUrl: String,
        durationLabel: String,
        audioLanguage: String,
        portalUrl: String
    ): List<EpisodeItem> {
        val result = mutableListOf<EpisodeItem>()
        // Match "Episode: 01 ... <a href="...">GDrive</a> | <a href="...">XCloud</a> ..."
        val episodeMarkerRegex = Regex(
            """Episode\s*[:\-]?\s*0*(\d{1,3})\b""",
            RegexOption.IGNORE_CASE
        )
        val matches = episodeMarkerRegex.findAll(html).toList()
        if (matches.isEmpty()) return emptyList()

        val anchorRegex = Regex(
            """<a[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""",
             setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )

        for (i in matches.indices) {
            val match = matches[i]
            val epNum = match.groupValues[1].toIntOrNull() ?: continue
            if (epNum <= 0 || epNum > 500) continue

            val chunkStart = match.range.last + 1
            val chunkEnd = if (i + 1 < matches.size) {
                matches[i + 1].range.first
            } else {
                (chunkStart + 2500).coerceAtMost(html.length)
            }
            if (chunkStart >= chunkEnd) continue

            val chunkHtml = html.substring(chunkStart, chunkEnd)
            val serverUrls = mutableListOf<String>()
            val serverLabels = mutableListOf<String>()

            for (aMatch in anchorRegex.findAll(chunkHtml)) {
                val href = aMatch.groupValues[1].trim()
                val label = stripHtmlTags(aMatch.groupValues[2]).trim()
                if (href.startsWith("http", ignoreCase = true) &&
                    !href.contains("blogger.com", ignoreCase = true) &&
                    !href.contains("telegram", ignoreCase = true) &&
                    !href.contains("how-to-download", ignoreCase = true)
                ) {
                    serverUrls.add(href)
                    if (label.isNotBlank()) serverLabels.add(label)
                }
            }

            if (serverUrls.isNotEmpty()) {
                val primaryUrl = serverUrls.first()
                val backups = serverUrls.drop(1) + listOfNotNull(portalUrl.takeIf { it.isNotBlank() })
                val serverSummary = if (serverLabels.isNotEmpty()) {
                    "Servers: ${serverLabels.distinct().joinToString(", ")}"
                } else {
                    audioLanguage
                }
                result.add(
                    EpisodeItem(
                        id = "dramanitam_ep_${seasonPostId}_s${seasonNumber}_e$epNum",
                        postId = seasonPostId * 1000 + epNum,
                        categoryId = seasonPostId,
                        dramaTitle = dramaTitle,
                        title = "$dramaTitle • S$seasonNumber E$epNum",
                        cleanTitle = "Episode $epNum ($audioLanguage)",
                        episodeNumber = epNum,
                        seasonNumber = seasonNumber,
                        description = "$dramaTitle — Season $seasonNumber, Episode $epNum ($audioLanguage). $serverSummary",
                        publishedDate = "",
                        posterUrl = posterUrl,
                        thumbnailUrl = posterUrl,
                        durationLabel = durationLabel,
                        primaryEmbedUrl = primaryUrl,
                        backupEmbedUrls = backups.distinct(),
                        sourcePostUrl = portalUrl,
                        isPlayable = true,
                        hasMissingPoster = posterUrl.isBlank(),
                        hasMissingVideo = false,
                        hasExplicitSeason = true,
                        source = ContentSource.DRAMANITAM_DUBBED,
                        sourceCategoryName = audioLanguage,
                        audioLanguage = audioLanguage
                    )
                )
            }
        }

        return result.distinctBy { it.episodeNumber }.sortedBy { it.episodeNumber }
    }

    internal fun extractCleanTitleAndSeason(rawTitle: String): Pair<String, Int?> {
        val seasonRegex = Regex("""(?:\bSeason|\bS)\s*[:\-]?\s*0*(\d{1,2})\b""", RegexOption.IGNORE_CASE)
        val explicitSeason = seasonRegex.find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()

        var cleaned = rawTitle
            .replace(Regex("""\|.*$"""), "")
            .replace(Regex("""[–—-]\s*(?:Episodes?|Ep|Complete|All\s+Episodes).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[–—-]\s*DramaNitam.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[.*?]"""), "")
            .replace(Regex("""\(\s*(?:Season\s*[:\-]?\s*\d+|Hindi\s*Dubbed|Urdu\s*Dubbed|Hindi|Urdu).*?\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bin\s+(?:Hindi|Urdu)(?:\s+Dubbed)?\b.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:Hindi|Urdu)\s+Dubbed\b.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bSeason\s*[:\-]?\s*0*\d+\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '–', '—', '|', ':')

        if (cleaned.isBlank()) {
            cleaned = rawTitle.substringBefore("[").substringBefore("|").substringBefore("–").trim()
        }
        return cleaned to explicitSeason
    }

    private fun extractSeasonFromHtmlMetadata(html: String): Int? {
        val regex = Regex("""<li>\s*Season\s*:\s*0*(\d{1,2})\s*</li>""", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractEpisodeCounts(rawTitle: String, html: String): Pair<Int, Int> {
        // Check title for "Episodes 12-18 Added" or "Episodes 05 Added"
        val addedRangeRegex = Regex(
            """Episodes?\s*0*(\d{1,3})(?:\s*[-–]\s*0*(\d{1,3}))?\s*Added""",
            RegexOption.IGNORE_CASE
        )
        val rangeMatch = addedRangeRegex.find(rawTitle)
        val addedHigh = rangeMatch?.groupValues?.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull()
            ?: rangeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

        // Check HTML <li>Episodes: 50</li>
        val htmlEpRegex = Regex("""<li>\s*Episodes?\s*:\s*0*(\d{1,4})""", RegexOption.IGNORE_CASE)
        val htmlTotal = htmlEpRegex.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()

        val latestAdded = (addedHigh ?: htmlTotal ?: 1).coerceAtLeast(1)
        val totalCount = (addedHigh ?: htmlTotal ?: 12).coerceAtLeast(latestAdded)
        return totalCount to latestAdded
    }

    private fun extractAudioLanguage(rawTitle: String, html: String): String {
        val htmlAudioRegex = Regex("""<li>\s*Audio\s*:\s*([^<]+)</li>""", RegexOption.IGNORE_CASE)
        val fromHtml = htmlAudioRegex.find(html)?.groupValues?.getOrNull(1)?.trim()
        if (!fromHtml.isNullOrBlank()) {
            return decodeHtmlEntities(fromHtml)
        }
        return when {
            rawTitle.contains("Urdu", ignoreCase = true) && rawTitle.contains("Hindi", ignoreCase = true) ->
                "Hindi & Urdu Dubbed"
            rawTitle.contains("Urdu", ignoreCase = true) -> "Urdu Dubbed"
            else -> "Hindi Dubbed"
        }
    }

    private fun extractDurationLabel(html: String): String {
        val durRegex = Regex("""<li>\s*Duration\s*:\s*([^<]+)</li>""", RegexOption.IGNORE_CASE)
        val raw = durRegex.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        if (raw.isBlank()) return "45m"
        return raw.replace("mint.", "m", ignoreCase = true)
            .replace("mins", "m", ignoreCase = true)
            .replace("min", "m", ignoreCase = true)
            .trim()
    }

    private fun extractPosterUrl(postObj: JSONObject, contentHtml: String): String {
        // 1. Check _embedded["wp:featuredmedia"][0]["source_url"]
        val embedded = postObj.optJSONObject("_embedded")
        val mediaArr = embedded?.optJSONArray("wp:featuredmedia")
        if (mediaArr != null && mediaArr.length() > 0) {
            val mediaObj = mediaArr.optJSONObject(0)
            val sourceUrl = mediaObj?.optString("source_url", "").orEmpty()
            if (sourceUrl.isNotBlank()) return normalizeHttpsUrl(sourceUrl)
        }
        // 2. Fallback to first <img> in contentHtml
        val imgRegex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val fromImg = imgRegex.find(contentHtml)?.groupValues?.getOrNull(1).orEmpty()
        return normalizeHttpsUrl(fromImg)
    }

    private fun extractEpisodePortalUrl(contentHtml: String): String {
        val anchorRegex = Regex("""<a[^>]+href=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        for (match in anchorRegex.findAll(contentHtml)) {
            val url = match.groupValues[1].trim()
            val lower = url.lowercase()
            if (lower.contains("blogspot.com") ||
                lower.contains("terabox") ||
                lower.contains("xcloud") ||
                lower.contains("gdflix")
            ) {
                return normalizeHttpsUrl(url)
            }
        }
        return ""
    }

    private fun inferCategoryNameFromTitleAndContent(rawTitle: String, html: String): String {
        val combined = "$rawTitle $html"
        return when {
            combined.contains("Turkish", ignoreCase = true) -> "Turkish Dramas"
            combined.contains("Korean", ignoreCase = true) -> "Korean Dramas"
            combined.contains("Chinese", ignoreCase = true) -> "Chinese Dramas"
            combined.contains("Japanese", ignoreCase = true) -> "Japanese Drama"
            combined.contains("Thai", ignoreCase = true) -> "Thai Dramas"
            else -> "Korean & Turkish Dubbed"
        }
    }

    private fun extractCleanSynopsis(
        excerptHtml: String,
        contentHtml: String,
        cleanTitle: String,
        categoryName: String,
        audioLanguage: String
    ): String {
        val pRegex = Regex("""<p[^>]*>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (m in pRegex.findAll(contentHtml)) {
            val text = decodeHtmlEntities(stripHtmlTags(m.groupValues[1])).trim()
            if (text.length > 45 &&
                !text.contains("Download", ignoreCase = true) &&
                !text.contains("DramaNitam", ignoreCase = true)
            ) {
                return text
            }
        }
        val cleanExcerpt = decodeHtmlEntities(stripHtmlTags(excerptHtml)).trim()
        if (cleanExcerpt.length > 30) {
            return cleanExcerpt
        }
        return "Stream $cleanTitle ($categoryName) in $audioLanguage on Dastan TV."
    }

    private fun normalizeHttpsUrl(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.isBlank() -> ""
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://") -> "https://${trimmed.removePrefix("http://")}"
            else -> trimmed
        }
    }

    private fun stripHtmlTags(input: String): String {
        return input.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&#8211;", "–")
            .replace("&#8212;", "—")
            .replace("&#8217;", "’")
            .replace("&#8216;", "‘")
            .replace("&#8220;", "“")
            .replace("&#8221;", "”")
            .replace("&#038;", "&")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&nbsp;", " ")
    }

    private fun executeGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} for $url")
            }
            return response.body?.string().orEmpty()
        }
    }

    fun clearMemoryCaches() {
        cachedCategories = emptyList()
        cachedDramas = emptyList()
        cachedLatestEpisodes = emptyList()
        seasonEpisodesMemoryCache.clear()
    }

    companion object {
        private const val TAG = "DramaNitamRepo"
        const val BASE_API_URL = "https://dramanitam.com/wp-json/wp/v2/"
    }
}
