package com.example.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

enum class ContentSource(
    val sourceId: String,
    val displayTitle: String,
    val badgeLabel: String,
    val subtitle: String,
    val apiBaseUrl: String,
    val refererUrl: String
) {
    DRAMA_COLLECTION(
        sourceId = "giveme5",
        displayTitle = "Drama Collection",
        badgeLabel = "Drama Collection",
        subtitle = "Turkish & Historical Series • Urdu Subtitles",
        apiBaseUrl = "https://giveme5.co/wp-json/wp/v2/",
        refererUrl = "https://giveme5.co/"
    ),
    DRAMANITAM_DUBBED(
        sourceId = "dramanitam",
        displayTitle = "Hindi & Urdu Dubbed",
        badgeLabel = "DramaNitam Dubbed",
        subtitle = "Korean, Turkish & Asian Dramas • Hindi/Urdu Dubbed",
        apiBaseUrl = "https://dramanitam.com/wp-json/wp/v2/",
        refererUrl = "https://www.dramanitam.com/"
    );

    companion object {
        fun fromSourceId(raw: String?): ContentSource {
            return entries.firstOrNull { it.sourceId.equals(raw, ignoreCase = true) }
                ?: DRAMA_COLLECTION
        }
    }
}

@Immutable
data class DramaSeason(
    val seasonNumber: Int,
    val categoryId: Int,
    val categoryName: String,
    val slug: String,
    val episodeCount: Int,
    val hasExplicitSeasonMetadata: Boolean = true,
    val source: ContentSource = ContentSource.DRAMA_COLLECTION,
    val episodePortalUrl: String = ""
) {
    val scopedCategoryKey: String
        get() = "${source.sourceId}_cat_$categoryId"
}

@Immutable
data class DramaSeries(
    val id: String,
    val categoryId: Int,
    val title: String,
    val originalTitle: String,
    val slug: String,
    val description: String,
    val posterUrl: String,
    val thumbnailUrl: String = posterUrl,
    val episodeCount: Int,
    val seasonNumber: Int,
    val genreTags: List<String>,
    val rating: String = "9.6",
    val releaseYear: String = "2024",
    val isFeatured: Boolean = false,
    val isHidden: Boolean = false,
    val isVipOnly: Boolean = false,
    val priorityOrder: Int = 100,
    val hasMissingPoster: Boolean = false,
    val seasons: List<DramaSeason> = emptyList(),
    val source: ContentSource = ContentSource.DRAMA_COLLECTION,
    val sourceCategoryName: String = "",
    val audioLanguage: String = "Urdu Subtitles",
    val episodePortalUrl: String = ""
) {
    val totalSeasonsCount: Int
        get() = seasons.size.coerceAtLeast(1)

    val allCategoryIds: List<Int>
        get() = if (seasons.isNotEmpty()) seasons.map { it.categoryId }.distinct() else listOf(categoryId)

    val scopedCategoryKey: String
        get() = "${source.sourceId}_cat_$categoryId"

    fun categoryIdForSeason(targetSeason: Int): Int {
        return seasons.firstOrNull { it.seasonNumber == targetSeason }?.categoryId ?: categoryId
    }

    fun scopedCategoryKeyForSeason(targetSeason: Int): String {
        return "${source.sourceId}_cat_${categoryIdForSeason(targetSeason)}"
    }
}

@Immutable
data class EpisodeItem(
    val id: String,
    val postId: Int,
    val categoryId: Int,
    val dramaTitle: String,
    val title: String,
    val cleanTitle: String = title,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val description: String,
    val publishedDate: String,
    val posterUrl: String,
    val thumbnailUrl: String = posterUrl,
    val durationLabel: String = "2h 10m",
    val primaryEmbedUrl: String,
    val backupEmbedUrls: List<String>,
    val sourcePostUrl: String,
    val isPlayable: Boolean,
    val isHidden: Boolean = false,
    val isFeatured: Boolean = false,
    val isVipOnly: Boolean = false,
    val hasMissingPoster: Boolean = false,
    val hasMissingVideo: Boolean = false,
    val hasExplicitSeason: Boolean = true,
    val source: ContentSource = ContentSource.DRAMA_COLLECTION,
    val sourceCategoryName: String = "",
    val audioLanguage: String = "Urdu Subtitles"
) {
    val allServers: List<String>
        get() = (listOf(primaryEmbedUrl) + backupEmbedUrls)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    val scopedCategoryKey: String
        get() = "${source.sourceId}_cat_$categoryId"
}

@Immutable
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val role: String = "user",
    val membershipPlan: String = "FREE",
    val preferredQuality: String = "AUTO",
    val subtitleEnabled: Boolean = true,
    val isBlocked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val uid: String
        get() = userId.ifBlank { id }
}

@Immutable
data class FavoriteItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dramaId: String = "",
    val title: String = "",
    val posterUrl: String = "",
    val episodeCount: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
data class WatchHistoryItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val episodeId: String = "",
    val categoryId: Long = 0L,
    val episodeTitle: String = "",
    val dramaTitle: String = "",
    val posterUrl: String = "",
    val embedUrl: String = "",
    val progressMs: Long = 0L,
    val durationMs: Long = 1L,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val progressFraction: Float
        get() = if (durationMs <= 0L) 0f else (progressMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val parsedSeasonNumber: Int
        get() {
            val regex = Regex("""\(S(\d+)\s*[•·-]\s*E(\d+)\)""", RegexOption.IGNORE_CASE)
            return regex.find(dramaTitle)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        }

    val parsedEpisodeNumber: Int
        get() {
            val regex = Regex("""\(S(\d+)\s*[•·-]\s*E(\d+)\)""", RegexOption.IGNORE_CASE)
            return regex.find(dramaTitle)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1
        }

    val cleanDramaTitle: String
        get() = dramaTitle.replace(Regex("""\s*\(S\d+\s*[•·-]\s*E\d+\)\s*$""", RegexOption.IGNORE_CASE), "").trim()
}

@Immutable
data class SavedEpisodeItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val episodeId: String = "",
    val categoryId: Long = 0L,
    val title: String = "",
    val dramaName: String = "",
    val posterUrl: String = "",
    val embedUrl: String = "",
    val synopsis: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
data class CatalogOverride(
    @DocumentId val id: String = "",
    val targetType: String = "DRAMA", // DRAMA or EPISODE
    val targetId: String = "",
    val customTitle: String = "",
    val customDescription: String = "",
    val customPosterUrl: String = "",
    val isHidden: Boolean = false,
    val isFeatured: Boolean = false,
    val isVipOnly: Boolean = false,
    val priorityOrder: Long = 100L,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
data class AppConfig(
    @DocumentId val id: String = "",
    val appId: String = "dastan_tv_global",
    val announcementBanner: String = "Welcome to Dastan TV — Stream Ottoman & Historical Epics in Urdu",
    val maintenanceMode: Boolean = false,
    val yearlyVipPricePkr: Long = 1000L,
    val paymentGatewayEnabled: Boolean = false,
    val lastApiSyncEpochMs: Long = 0L,
    val lastApiLatencyMs: Long = 0L,
    val lastApiHealthy: Boolean = true,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Immutable
data class ApiHealthReport(
    val endpointUrl: String = "https://giveme5.co/wp-json/wp/v2",
    val isHealthy: Boolean = true,
    val httpStatusCode: Int = 200,
    val responseLatencyMs: Long = 0L,
    val totalCategoriesFetched: Int = 0,
    val totalEpisodesCached: Int = 0,
    val playableEpisodesCount: Int = 0,
    val missingPosterCount: Int = 0,
    val missingVideoCount: Int = 0,
    val duplicateSkippedCount: Int = 0,
    val lastCheckedTimestampMs: Long = 0L,
    val errorMessage: String? = null
)

@Immutable
data class CacheStats(
    val cachedDramasCount: Int = 0,
    val cachedSeasonsCount: Int = 0,
    val cachedEpisodesCount: Int = 0,
    val diskCacheBytes: Long = 0L,
    val lastUpdatedMs: Long = 0L,
    val cacheTtlMinutes: Int = 30
) {
    val formattedDiskSize: String
        get() = "%.1f MB".format(diskCacheBytes.toDouble() / (1024.0 * 1024.0))
}

@Immutable
data class CatalogSyncDelta(
    val newDramasCount: Int = 0,
    val newSeasonsCount: Int = 0,
    val newEpisodesCount: Int = 0,
    val updatedEpisodesCount: Int = 0,
    val syncedAtEpochMs: Long = System.currentTimeMillis()
)
