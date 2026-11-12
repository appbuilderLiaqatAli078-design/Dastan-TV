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
)

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
    val hasMissingPoster: Boolean = false
)

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
    val hasMissingVideo: Boolean = false
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
