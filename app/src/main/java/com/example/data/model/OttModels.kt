package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val role: String = "user", // "user" or "admin"
    val membershipPlan: String = "FREE", // "FREE" or "YEARLY_VIP"
    val preferredQuality: String = "AUTO", // "AUTO", "1080P", "720P", "480P"
    val subtitleEnabled: Boolean = true,
    val isBlocked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FavoriteItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dramaId: String = "",
    val title: String = "",
    val posterUrl: String = "",
    val episodeCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
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
    val durationMs: Long = 2700000L, // Default 45m episode duration
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val cleanDramaTitle: String
        get() = dramaTitle
            .replace(Regex("\\s*\\(S\\d+\\s*[•·-]?\\s*E\\d+\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*•\\s*Season\\s*\\d+.*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "Turkish Historical Drama" }

    val parsedSeasonNumber: Int
        get() {
            val tagMatch = Regex("\\(S(\\d+)\\s*[•·-]?\\s*E(\\d+)\\)", RegexOption.IGNORE_CASE)
                .find(dramaTitle)
            if (tagMatch != null) {
                return tagMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
            }
            val titleMatch = Regex("(?:Season|S)\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find("$dramaTitle $episodeTitle")
            return titleMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        }

    val parsedEpisodeNumber: Int
        get() {
            val tagMatch = Regex("\\(S(\\d+)\\s*[•·-]?\\s*E(\\d+)\\)", RegexOption.IGNORE_CASE)
                .find(dramaTitle)
            if (tagMatch != null) {
                return tagMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
            }
            val epMatch = Regex("(?:Episode|Ep|E)\\s*(\\d+)", RegexOption.IGNORE_CASE)
                .find(episodeTitle)
            return epMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        }
}

@IgnoreExtraProperties
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

@IgnoreExtraProperties
data class CatalogOverride(
    @DocumentId val id: String = "",
    val appId: String = "dastan_tv",
    val targetType: String = "DRAMA", // "DRAMA", "EPISODE", "CATEGORY", "BANNER"
    val targetId: String = "",
    val customTitle: String = "",
    val customDescription: String = "",
    val customPosterUrl: String = "",
    val isHidden: Boolean = false,
    val isFeatured: Boolean = false,
    val isVipOnly: Boolean = false,
    val priorityOrder: Long = 0L,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppConfig(
    @DocumentId val id: String = "",
    val appId: String = "dastan_tv",
    val announcementBanner: String = "Stream authentic Urdu-dubbed & subtitled Turkish historical dramas in HD.",
    val maintenanceMode: Boolean = false,
    val yearlyVipPricePkr: Long = 2499L,
    val paymentGatewayEnabled: Boolean = false,
    val lastApiSyncEpochMs: Long = 0L,
    val lastApiLatencyMs: Long = 0L,
    val lastApiHealthy: Boolean = true,
    val updatedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class DramaSeason(
    val seasonNumber: Int,
    val categoryId: Int,
    val categoryName: String,
    val slug: String = "",
    val episodeCount: Int = 0,
    val hasExplicitSeasonMetadata: Boolean = true
) {
    val displayLabel: String
        get() = if (hasExplicitSeasonMetadata || seasonNumber > 0) "Season $seasonNumber" else "Episodes"
}

// Domain models for Content API + Merged Admin Overrides
data class DramaSeries(
    val id: String,
    val categoryId: Int,
    val title: String,
    val originalTitle: String,
    val slug: String,
    val description: String,
    val posterUrl: String,
    val episodeCount: Int,
    val seasonNumber: Int,
    val genreTags: List<String>,
    val isFeatured: Boolean = false,
    val isHidden: Boolean = false,
    val isVipOnly: Boolean = false,
    val priorityOrder: Int = 0,
    val hasMissingPoster: Boolean = false,
    val thumbnailUrl: String = posterUrl,
    val seasons: List<DramaSeason> = listOf(
        DramaSeason(
            seasonNumber = seasonNumber.coerceAtLeast(1),
            categoryId = categoryId,
            categoryName = title,
            slug = slug,
            episodeCount = episodeCount
        )
    )
) {
    val totalSeasonsCount: Int
        get() = seasons.size.coerceAtLeast(1)

    val allCategoryIds: List<Int>
        get() = if (seasons.isNotEmpty()) seasons.map { it.categoryId }.distinct() else listOf(categoryId)

    fun categoryIdForSeason(targetSeason: Int): Int {
        return seasons.firstOrNull { it.seasonNumber == targetSeason }?.categoryId
            ?: seasons.firstOrNull()?.categoryId
            ?: categoryId
    }
}

data class EpisodeItem(
    val id: String,
    val postId: Int,
    val categoryId: Int,
    val dramaTitle: String,
    val title: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val description: String,
    val publishedDate: String,
    val posterUrl: String,
    val primaryEmbedUrl: String,
    val backupEmbedUrls: List<String>,
    val sourcePostUrl: String,
    val isPlayable: Boolean,
    val isHidden: Boolean = false,
    val isVipOnly: Boolean = false,
    val hasMissingPoster: Boolean = false,
    val hasMissingVideo: Boolean = false,
    val cleanTitle: String = title,
    val thumbnailUrl: String = posterUrl,
    val durationLabel: String = "",
    val hasExplicitSeason: Boolean = true
)

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

data class CacheStats(
    val cachedDramasCount: Int = 0,
    val cachedSeasonsCount: Int = 0,
    val cachedEpisodesCount: Int = 0,
    val diskCacheBytes: Long = 0L,
    val lastUpdatedMs: Long = 0L,
    val cacheTtlMinutes: Int = 30
) {
    val formattedDiskSize: String
        get() {
            val mb = diskCacheBytes.toDouble() / (1024.0 * 1024.0)
            return if (mb >= 0.1) String.format(java.util.Locale.US, "%.1f MB", mb)
            else "${(diskCacheBytes / 1024L).coerceAtLeast(0L)} KB"
        }
}
