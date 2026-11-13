package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.ApiHealthReport
import com.example.data.model.AppConfig
import com.example.data.model.CacheStats
import com.example.data.model.CatalogOverride
import com.example.data.model.ContentSource
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import com.example.data.model.FavoriteItem
import com.example.data.model.SavedEpisodeItem
import com.example.data.model.UserProfile
import com.example.data.model.WatchHistoryItem
import com.example.data.remote.DramaNitamContentRepository
import com.example.data.remote.WordPressContentRepository
import com.example.data.repository.DastanRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DastanViewModel(
    private val repository: DastanRepository,
    private val contentRepository: WordPressContentRepository,
    private val dramaNitamRepository: DramaNitamContentRepository,
    val currentUserId: String
) : ViewModel() {

    // Source 1: "Drama Collection" (giveme5.co)
    private val _rawDramas = MutableStateFlow<List<DramaSeries>>(emptyList())
    private val _rawLatestEpisodes = MutableStateFlow<List<EpisodeItem>>(emptyList())

    // Source 2: "Hindi & Urdu Dubbed" (dramanitam.com) — strictly isolated from Source 1
    private val _hindiUrduDramas = MutableStateFlow<List<DramaSeries>>(emptyList())
    val hindiUrduDramas: StateFlow<List<DramaSeries>> = _hindiUrduDramas.asStateFlow()

    private val _hindiUrduLatestEpisodes = MutableStateFlow<List<EpisodeItem>>(emptyList())
    val hindiUrduLatestEpisodes: StateFlow<List<EpisodeItem>> = _hindiUrduLatestEpisodes.asStateFlow()

    private val _hindiUrduCategories = MutableStateFlow<List<String>>(emptyList())
    val hindiUrduCategories: StateFlow<List<String>> = _hindiUrduCategories.asStateFlow()

    private val _isLoadingHindiUrdu = MutableStateFlow(true)
    val isLoadingHindiUrdu: StateFlow<Boolean> = _isLoadingHindiUrdu.asStateFlow()

    // Scoped episode map keyed by "${source.sourceId}_cat_${categoryId}" to prevent cross-source collisions
    private val _scopedEpisodesMap = MutableStateFlow<Map<String, List<EpisodeItem>>>(emptyMap())
    val scopedEpisodesMap: StateFlow<Map<String, List<EpisodeItem>>> = _scopedEpisodesMap.asStateFlow()

    private val _scopedPageMap = MutableStateFlow<Map<String, Int>>(emptyMap())

    private val _isLoadingContent = MutableStateFlow(true)
    val isLoadingContent: StateFlow<Boolean> = _isLoadingContent.asStateFlow()

    private val _isLoadingEpisodes = MutableStateFlow(false)
    val isLoadingEpisodes: StateFlow<Boolean> = _isLoadingEpisodes.asStateFlow()

    private val _apiHealth = MutableStateFlow(ApiHealthReport())
    val apiHealth: StateFlow<ApiHealthReport> = _apiHealth.asStateFlow()

    private val _cacheStats = MutableStateFlow(CacheStats())
    val cacheStats: StateFlow<CacheStats> = _cacheStats.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _flagSecureEnabled = MutableStateFlow(false)
    val flagSecureEnabled: StateFlow<Boolean> = _flagSecureEnabled.asStateFlow()

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .catch { error ->
            unwrapAndLog("observeUserProfile", error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val isAdmin: StateFlow<Boolean> = userProfile
        .combine(MutableStateFlow(Unit)) { profile, _ ->
            val currentUser = Firebase.auth.currentUser
            val verifiedEmailAdmin = DastanRepository.isAuthorizedAdminEmail(
                currentUser?.email,
                currentUser?.isEmailVerified == true
            )
            profile?.role == "admin" || verifiedEmailAdmin
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = DastanRepository.isAuthorizedAdminEmail(
                Firebase.auth.currentUser?.email,
                Firebase.auth.currentUser?.isEmailVerified == true
            )
        )

    val favorites: StateFlow<List<FavoriteItem>> = repository.observeFavorites(currentUserId)
        .catch { error ->
            unwrapAndLog("observeFavorites", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val watchHistory: StateFlow<List<WatchHistoryItem>> = repository.observeWatchHistory(currentUserId)
        .catch { error ->
            unwrapAndLog("observeWatchHistory", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val savedEpisodes: StateFlow<List<SavedEpisodeItem>> = repository.observeSavedEpisodes(currentUserId)
        .catch { error ->
            unwrapAndLog("observeSavedEpisodes", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val catalogOverrides: StateFlow<List<CatalogOverride>> = repository.observeCatalogOverrides()
        .catch { error ->
            unwrapAndLog("observeCatalogOverrides", error)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val appConfig: StateFlow<AppConfig> = repository.observeAppConfig()
        .catch { error ->
            unwrapAndLog("observeAppConfig", error)
            emit(AppConfig())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppConfig()
        )

    // Visible Dramas for "Drama Collection" (Source 1: giveme5.co)
    val userDramas: StateFlow<List<DramaSeries>> = combine(
        _rawDramas,
        catalogOverrides
    ) { raw, overrides ->
        contentRepository.applyAdminOverridesToDramas(
            dramas = raw,
            overrides = overrides,
            includeHiddenForAdmin = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    // All Dramas for Admin UI (includes hidden dramas so Admin can unhide/manage them)
    val adminDramas: StateFlow<List<DramaSeries>> = combine(
        _rawDramas,
        catalogOverrides
    ) { raw, overrides ->
        contentRepository.applyAdminOverridesToDramas(
            dramas = raw,
            overrides = overrides,
            includeHiddenForAdmin = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val userLatestEpisodes: StateFlow<List<EpisodeItem>> = combine(
        _rawLatestEpisodes,
        catalogOverrides
    ) { raw, overrides ->
        contentRepository.applyAdminOverridesToEpisodes(
            episodes = raw,
            overrides = overrides,
            includeHiddenForAdmin = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val adminLatestEpisodes: StateFlow<List<EpisodeItem>> = combine(
        _rawLatestEpisodes,
        catalogOverrides
    ) { raw, overrides ->
        contentRepository.applyAdminOverridesToEpisodes(
            episodes = raw,
            overrides = overrides,
            includeHiddenForAdmin = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    private val _adminUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val adminUsers: StateFlow<List<UserProfile>> = _adminUsers.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile()
        }
        refreshContent(forceRefresh = false)
        startAutomaticCatalogDeltaSync()
        viewModelScope.launch {
            isAdmin.collect { adminFlag ->
                if (adminFlag) {
                    observeUsersForAdmin()
                }
            }
        }
    }

    private fun startAutomaticCatalogDeltaSync() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10 * 60 * 1000L) // Poll every 10 minutes
                runCatching {
                    val delta = contentRepository.syncLatestCatalogDelta()
                    val (dramas, latestEps) = contentRepository.fetchDramasAndLatestEpisodes(forceRefresh = false)
                    _rawDramas.value = dramas
                    _rawLatestEpisodes.value = latestEps

                    val (dubbedDramas, dubbedEps, dubbedCats) = dramaNitamRepository.fetchDubbedCatalog(forceRefresh = true)
                    if (dubbedDramas.isNotEmpty()) {
                        _hindiUrduDramas.value = dubbedDramas
                        _hindiUrduLatestEpisodes.value = dubbedEps
                        _hindiUrduCategories.value = dubbedCats
                    }

                    _apiHealth.value = contentRepository.getLatestHealthReport()
                    _cacheStats.value = contentRepository.getCacheStats()
                    if (delta.newDramasCount > 0 || delta.newSeasonsCount > 0 || delta.newEpisodesCount > 0) {
                        _statusMessage.value = buildString {
                            append("Auto-synced new content: ")
                            val parts = mutableListOf<String>()
                            if (delta.newDramasCount > 0) parts.add("${delta.newDramasCount} new drama(s)")
                            if (delta.newSeasonsCount > 0) parts.add("${delta.newSeasonsCount} new season(s)")
                            if (delta.newEpisodesCount > 0) parts.add("${delta.newEpisodesCount} new episode(s)")
                            append(parts.joinToString(", "))
                        }
                    }
                }
            }
        }
    }

    private fun observeUsersForAdmin() {
        viewModelScope.launch {
            repository.observeAllUsersForAdmin()
                .catch { error ->
                    unwrapAndLog("observeAllUsersForAdmin", error)
                }
                .collect { users ->
                    _adminUsers.value = users
                }
        }
    }

    fun refreshContent(forceRefresh: Boolean = true) {
        // Launch Source 1 ("Drama Collection" - giveme5.co)
        viewModelScope.launch {
            _isLoadingContent.value = true
            if (forceRefresh) {
                val delta = contentRepository.syncLatestCatalogDelta()
                val (dramas, latestEps) = contentRepository.fetchDramasAndLatestEpisodes(forceRefresh = false)
                _rawDramas.value = dramas
                _rawLatestEpisodes.value = latestEps
                _apiHealth.value = contentRepository.getLatestHealthReport()
                _cacheStats.value = contentRepository.getCacheStats()
                _isLoadingContent.value = false
                val deltaDetails = buildList {
                    if (delta.newDramasCount > 0) add("+${delta.newDramasCount} new drama")
                    if (delta.newSeasonsCount > 0) add("+${delta.newSeasonsCount} new season")
                    if (delta.newEpisodesCount > 0) add("+${delta.newEpisodesCount} new ep")
                    if (delta.updatedEpisodesCount > 0) add("${delta.updatedEpisodesCount} updated")
                }
                _statusMessage.value = if (deltaDetails.isNotEmpty()) {
                    "Synced ${dramas.size} dramas (${deltaDetails.joinToString(", ")})"
                } else {
                    "Catalog up to date: ${dramas.size} Drama Collection & ${_hindiUrduDramas.value.size} Dubbed series."
                }
            } else {
                val (dramas, latestEps) = contentRepository.fetchDramasAndLatestEpisodes(forceRefresh = false)
                _rawDramas.value = dramas
                _rawLatestEpisodes.value = latestEps
                _apiHealth.value = contentRepository.getLatestHealthReport()
                _cacheStats.value = contentRepository.getCacheStats()
                _isLoadingContent.value = false
            }
        }

        // Launch Source 2 ("Hindi & Urdu Dubbed" - dramanitam.com) independently
        viewModelScope.launch {
            _isLoadingHindiUrdu.value = true
            val (dubbedDramas, dubbedEps, dubbedCats) = dramaNitamRepository.fetchDubbedCatalog(forceRefresh = forceRefresh)
            _hindiUrduDramas.value = dubbedDramas
            _hindiUrduLatestEpisodes.value = dubbedEps
            _hindiUrduCategories.value = dubbedCats
            _cacheStats.value = contentRepository.getCacheStats()
            _isLoadingHindiUrdu.value = false
        }
    }

    fun loadEpisodesForDrama(
        drama: DramaSeries,
        seasonNumber: Int = drama.seasonNumber,
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            _isLoadingEpisodes.value = true
            val targetCategoryId = drama.categoryIdForSeason(seasonNumber)
            val scopedKey = drama.scopedCategoryKeyForSeason(seasonNumber)

            when (drama.source) {
                ContentSource.DRAMANITAM_DUBBED -> {
                    val dubbedEps = dramaNitamRepository.fetchEpisodesForSeasonPost(
                        seasonPostId = targetCategoryId,
                        dramaTitle = drama.title,
                        expectedSeasonNumber = seasonNumber,
                        forceRefresh = forceRefresh
                    )
                    _scopedEpisodesMap.value = _scopedEpisodesMap.value + (scopedKey to dubbedEps)
                    _scopedPageMap.value = _scopedPageMap.value + (scopedKey to 1)

                    drama.seasons.forEach { season ->
                        val otherKey = season.scopedCategoryKey
                        if (otherKey != scopedKey && !_scopedEpisodesMap.value.containsKey(otherKey)) {
                            val otherSeasonEps = dramaNitamRepository.fetchEpisodesForSeasonPost(
                                seasonPostId = season.categoryId,
                                dramaTitle = drama.title,
                                expectedSeasonNumber = season.seasonNumber,
                                forceRefresh = false
                            )
                            if (otherSeasonEps.isNotEmpty()) {
                                _scopedEpisodesMap.value = _scopedEpisodesMap.value + (otherKey to otherSeasonEps)
                                _scopedPageMap.value = _scopedPageMap.value + (otherKey to 1)
                            }
                        }
                    }
                }

                ContentSource.DRAMA_COLLECTION -> {
                    val expectedCount = drama.seasons.firstOrNull { it.seasonNumber == seasonNumber }?.episodeCount
                        ?: drama.episodeCount
                    val epsPage1 = contentRepository.fetchEpisodesForCategory(
                        categoryId = targetCategoryId,
                        dramaTitle = drama.title,
                        expectedSeasonNumber = seasonNumber,
                        page = 1,
                        forceRefresh = forceRefresh
                    )
                    var combinedSeasonEps = epsPage1
                    var highestPageLoaded = 1

                    if (epsPage1.size >= 45 || expectedCount > epsPage1.size) {
                        val epsPage2 = contentRepository.fetchEpisodesForCategory(
                            categoryId = targetCategoryId,
                            dramaTitle = drama.title,
                            expectedSeasonNumber = seasonNumber,
                            page = 2,
                            forceRefresh = forceRefresh
                        )
                        if (epsPage2.isNotEmpty()) {
                            combinedSeasonEps = contentRepository.deduplicateSeasonEpisodes(epsPage1 + epsPage2)
                            highestPageLoaded = 2
                        }
                    }

                    _scopedEpisodesMap.value = _scopedEpisodesMap.value + (scopedKey to combinedSeasonEps)
                    _scopedPageMap.value = _scopedPageMap.value + (scopedKey to highestPageLoaded)

                    drama.seasons.forEach { season ->
                        val otherKey = "${drama.source.sourceId}_cat_${season.categoryId}"
                        if (otherKey != scopedKey && !_scopedEpisodesMap.value.containsKey(otherKey)) {
                            val otherSeasonEps = contentRepository.fetchEpisodesForCategory(
                                categoryId = season.categoryId,
                                dramaTitle = drama.title,
                                expectedSeasonNumber = season.seasonNumber,
                                page = 1,
                                forceRefresh = false
                            )
                            if (otherSeasonEps.isNotEmpty()) {
                                _scopedEpisodesMap.value = _scopedEpisodesMap.value + (otherKey to otherSeasonEps)
                                _scopedPageMap.value = _scopedPageMap.value + (otherKey to 1)
                            }
                        }
                    }

                    val (updatedDramas, _) = contentRepository.fetchDramasAndLatestEpisodes(forceRefresh = false)
                    if (updatedDramas.isNotEmpty()) {
                        _rawDramas.value = updatedDramas
                    }
                }
            }

            _cacheStats.value = contentRepository.getCacheStats()
            _isLoadingEpisodes.value = false
        }
    }

    fun loadNextEpisodePage(drama: DramaSeries, seasonNumber: Int = drama.seasonNumber) {
        viewModelScope.launch {
            if (_isLoadingEpisodes.value) return@launch
            _isLoadingEpisodes.value = true
            val targetCategoryId = drama.categoryIdForSeason(seasonNumber)
            val scopedKey = drama.scopedCategoryKeyForSeason(seasonNumber)

            if (drama.source == ContentSource.DRAMANITAM_DUBBED) {
                val refreshed = dramaNitamRepository.fetchEpisodesForSeasonPost(
                    seasonPostId = targetCategoryId,
                    dramaTitle = drama.title,
                    expectedSeasonNumber = seasonNumber,
                    forceRefresh = true
                )
                if (refreshed.isNotEmpty()) {
                    _scopedEpisodesMap.value = _scopedEpisodesMap.value + (scopedKey to refreshed)
                }
                _statusMessage.value = "All available episodes loaded for ${drama.title} Season $seasonNumber"
                _isLoadingEpisodes.value = false
                return@launch
            }

            val nextPage = (_scopedPageMap.value[scopedKey] ?: 1) + 1
            val newPageEps = contentRepository.fetchEpisodesForCategory(
                categoryId = targetCategoryId,
                dramaTitle = drama.title,
                expectedSeasonNumber = seasonNumber,
                page = nextPage,
                forceRefresh = true
            )
            if (newPageEps.isNotEmpty()) {
                val existing = _scopedEpisodesMap.value[scopedKey].orEmpty()
                val merged = (existing + newPageEps)
                    .distinctBy { it.episodeNumber }
                    .sortedBy { it.episodeNumber }
                _scopedEpisodesMap.value = _scopedEpisodesMap.value + (scopedKey to merged)
                _scopedPageMap.value = _scopedPageMap.value + (scopedKey to nextPage)
                _cacheStats.value = contentRepository.getCacheStats()
                _statusMessage.value = "Loaded Season $seasonNumber page $nextPage (${newPageEps.size} episodes)"
            } else {
                _statusMessage.value = "All episodes loaded for ${drama.title} Season $seasonNumber"
            }
            _isLoadingEpisodes.value = false
        }
    }

    fun getEpisodesForDramaSeason(
        drama: DramaSeries,
        seasonNumber: Int,
        includeHidden: Boolean = false
    ): List<EpisodeItem> {
        val targetCategoryId = drama.categoryIdForSeason(seasonNumber)
        val targetScopedKey = drama.scopedCategoryKeyForSeason(seasonNumber)

        if (drama.source == ContentSource.DRAMANITAM_DUBBED) {
            val fromScoped = _scopedEpisodesMap.value[targetScopedKey]
                ?: _hindiUrduLatestEpisodes.value.filter {
                    it.source == ContentSource.DRAMANITAM_DUBBED && it.categoryId == targetCategoryId
                }
            return fromScoped
                .filter { it.source == ContentSource.DRAMANITAM_DUBBED }
                .distinctBy { it.episodeNumber }
                .sortedBy { it.episodeNumber }
        }

        val allDramaScopedKeys = drama.allCategoryIds.map { "${drama.source.sourceId}_cat_$it" }.toSet()
        val fromTargetCategory = _scopedEpisodesMap.value[targetScopedKey]
            ?: _rawLatestEpisodes.value.filter {
                it.source == ContentSource.DRAMA_COLLECTION && it.categoryId == targetCategoryId
            }

        val fromAllCategories = allDramaScopedKeys.flatMap { key ->
            _scopedEpisodesMap.value[key].orEmpty()
        }.ifEmpty { fromTargetCategory }
            .filter { it.source == ContentSource.DRAMA_COLLECTION }

        val overriddenAll = contentRepository.applyAdminOverridesToEpisodes(
            episodes = fromAllCategories,
            overrides = catalogOverrides.value,
            includeHiddenForAdmin = includeHidden
        )

        val seasonFiltered = overriddenAll.filter { ep ->
            ep.seasonNumber == seasonNumber ||
                (ep.categoryId == targetCategoryId && !ep.hasExplicitSeason &&
                    catalogOverrides.value.none {
                        it.targetType == "EPISODE" && it.targetId == ep.postId.toString() && it.priorityOrder >= 1000
                    })
        }

        return seasonFiltered
            .distinctBy { it.episodeNumber }
            .sortedBy { it.episodeNumber }
    }

    fun getEpisodesForCategory(
        categoryId: Int,
        source: ContentSource = ContentSource.DRAMA_COLLECTION,
        includeHidden: Boolean = false
    ): List<EpisodeItem> {
        val scopedKey = "${source.sourceId}_cat_$categoryId"
        if (source == ContentSource.DRAMANITAM_DUBBED) {
            val raw = _scopedEpisodesMap.value[scopedKey]
                ?: _hindiUrduLatestEpisodes.value.filter {
                    it.source == ContentSource.DRAMANITAM_DUBBED && it.categoryId == categoryId
                }
            return raw.sortedBy { it.episodeNumber }
        }
        val raw = _scopedEpisodesMap.value[scopedKey]
            ?: _rawLatestEpisodes.value.filter {
                it.source == ContentSource.DRAMA_COLLECTION && it.categoryId == categoryId
            }
        return contentRepository.applyAdminOverridesToEpisodes(
            episodes = raw,
            overrides = catalogOverrides.value,
            includeHiddenForAdmin = includeHidden
        ).sortedBy { it.episodeNumber }
    }

    fun findDramaById(dramaId: String): DramaSeries? {
        return userDramas.value.firstOrNull { it.id == dramaId }
            ?: _hindiUrduDramas.value.firstOrNull { it.id == dramaId }
    }

    fun refreshCacheStats() {
        _cacheStats.value = contentRepository.getCacheStats()
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            contentRepository.clearAllCaches()
            dramaNitamRepository.clearMemoryCaches()
            _scopedEpisodesMap.value = emptyMap()
            _scopedPageMap.value = emptyMap()
            _cacheStats.value = contentRepository.getCacheStats()
            _statusMessage.value = "Cleared local metadata & image caches. Reloading both catalogs..."
            refreshContent(forceRefresh = true)
        }
    }

    fun toggleFavorite(drama: DramaSeries) {
        viewModelScope.launch {
            val isFav = favorites.value.any {
                it.dramaId == drama.id || it.dramaId == "drama_${drama.categoryId}"
            }
            if (isFav) {
                repository.removeFavorite(drama.id)
                _statusMessage.value = "Removed ${drama.title} from Favorites"
            } else {
                repository.addFavorite(
                    dramaId = drama.id,
                    title = drama.title,
                    posterUrl = drama.thumbnailUrl.ifBlank { drama.posterUrl },
                    episodeCount = drama.episodeCount
                )
                _statusMessage.value = "Added ${drama.title} to Favorites"
            }
        }
    }

    fun recordPlaybackProgress(
        episode: EpisodeItem,
        progressMs: Long,
        durationMs: Long,
        completed: Boolean = false
    ) {
        viewModelScope.launch {
            val formattedDramaTitle = "${episode.dramaTitle} (S${episode.seasonNumber} • E${episode.episodeNumber})"
            val formattedEpisodeTitle = "S${episode.seasonNumber} E${episode.episodeNumber} • ${episode.cleanTitle}"
            repository.saveWatchProgress(
                episodeId = episode.id,
                categoryId = episode.categoryId,
                episodeTitle = formattedEpisodeTitle,
                dramaTitle = formattedDramaTitle,
                posterUrl = episode.thumbnailUrl.ifBlank { episode.posterUrl },
                embedUrl = episode.primaryEmbedUrl,
                progressMs = progressMs,
                durationMs = durationMs,
                completed = completed
            )
        }
    }

    fun removeWatchHistory(episodeId: String) {
        viewModelScope.launch {
            repository.clearWatchHistoryItem(episodeId)
            _statusMessage.value = "Removed item from Watch History"
        }
    }

    fun toggleSaveEpisodeToVault(episode: EpisodeItem) {
        viewModelScope.launch {
            val exists = savedEpisodes.value.any { it.episodeId == episode.id }
            if (exists) {
                repository.removeSavedEpisode(episode.id)
                _statusMessage.value = "Removed episode from Saved Vault"
            } else {
                val formattedTitle = "S${episode.seasonNumber} E${episode.episodeNumber} • ${episode.cleanTitle}"
                repository.saveEpisodeToVault(
                    episodeId = episode.id,
                    categoryId = episode.categoryId,
                    title = formattedTitle,
                    dramaName = "${episode.dramaTitle} (Season ${episode.seasonNumber})",
                    posterUrl = episode.thumbnailUrl.ifBlank { episode.posterUrl },
                    embedUrl = episode.primaryEmbedUrl,
                    synopsis = episode.description
                )
                _statusMessage.value = "Saved $formattedTitle to Vault"
            }
        }
    }

    fun updateUserSettings(
        displayName: String,
        preferredQuality: String,
        subtitleEnabled: Boolean,
        flagSecure: Boolean
    ) {
        _flagSecureEnabled.value = flagSecure
        viewModelScope.launch {
            repository.updateUserPreferences(
                displayName = displayName,
                preferredQuality = preferredQuality,
                subtitleEnabled = subtitleEnabled
            ).onSuccess {
                _statusMessage.value = "Profile & playback settings saved"
            }.onFailure { e ->
                _statusMessage.value = "Failed to update settings: ${e.localizedMessage}"
            }
        }
    }

    // Admin Actions
    fun adminSaveCatalogOverride(
        targetType: String,
        targetId: String,
        customTitle: String,
        customDescription: String,
        customPosterUrl: String,
        isHidden: Boolean,
        isFeatured: Boolean,
        isVipOnly: Boolean,
        priorityOrder: Int
    ) {
        viewModelScope.launch {
            repository.saveCatalogOverride(
                targetType = targetType,
                targetId = targetId,
                customTitle = customTitle,
                customDescription = customDescription,
                customPosterUrl = customPosterUrl,
                isHidden = isHidden,
                isFeatured = isFeatured,
                isVipOnly = isVipOnly,
                priorityOrder = priorityOrder
            ).onSuccess {
                _statusMessage.value = "Admin override saved for $targetType #$targetId"
            }.onFailure { e ->
                _statusMessage.value = "Admin override failed: ${e.localizedMessage}"
            }
        }
    }

    fun adminUpdateUserMembership(
        targetUserId: String,
        membershipPlan: String,
        isBlocked: Boolean
    ) {
        viewModelScope.launch {
            repository.adminUpdateUserMembershipAndStatus(
                targetUserId = targetUserId,
                membershipPlan = membershipPlan,
                isBlocked = isBlocked
            ).onSuccess {
                _statusMessage.value = "Updated user entitlement to $membershipPlan"
            }.onFailure { e ->
                _statusMessage.value = "Failed to update user: ${e.localizedMessage}"
            }
        }
    }

    fun adminRunSyncAndHealthCheck() {
        viewModelScope.launch {
            _isLoadingContent.value = true
            val report = contentRepository.runApiHealthCheck()
            _apiHealth.value = report
            val (dramas, latestEps) = contentRepository.fetchDramasAndLatestEpisodes(forceRefresh = false)
            _rawDramas.value = dramas
            _rawLatestEpisodes.value = latestEps
            val (dubbedDramas, dubbedEps, dubbedCats) = dramaNitamRepository.fetchDubbedCatalog(forceRefresh = true)
            _hindiUrduDramas.value = dubbedDramas
            _hindiUrduLatestEpisodes.value = dubbedEps
            _hindiUrduCategories.value = dubbedCats
            _cacheStats.value = contentRepository.getCacheStats()
            val currentCfg = appConfig.value
            repository.saveAppConfig(
                announcementBanner = currentCfg.announcementBanner,
                maintenanceMode = currentCfg.maintenanceMode,
                yearlyVipPricePkr = currentCfg.yearlyVipPricePkr,
                paymentGatewayEnabled = currentCfg.paymentGatewayEnabled,
                lastApiSyncEpochMs = System.currentTimeMillis(),
                lastApiLatencyMs = report.responseLatencyMs,
                lastApiHealthy = report.isHealthy
            )
            _isLoadingContent.value = false
            _statusMessage.value = "API Health Check complete (${report.responseLatencyMs}ms, status ${report.httpStatusCode})"
        }
    }

    fun adminUpdateAppConfig(
        announcementBanner: String,
        maintenanceMode: Boolean,
        yearlyVipPricePkr: Long
    ) {
        viewModelScope.launch {
            val report = _apiHealth.value
            repository.saveAppConfig(
                announcementBanner = announcementBanner,
                maintenanceMode = maintenanceMode,
                yearlyVipPricePkr = yearlyVipPricePkr,
                paymentGatewayEnabled = false,
                lastApiSyncEpochMs = System.currentTimeMillis(),
                lastApiLatencyMs = report.responseLatencyMs,
                lastApiHealthy = report.isHealthy
            ).onSuccess {
                _statusMessage.value = "Global App Configuration updated"
            }.onFailure { e ->
                _statusMessage.value = "Failed to save App Config: ${e.localizedMessage}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    private fun unwrapAndLog(tag: String, error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w("DastanVM", "Stream error in $tag: ${(firestoreError ?: error).message}")
    }

    companion object {
        fun factory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                DastanViewModel(
                    repository = DastanRepository(db),
                    contentRepository = WordPressContentRepository(app),
                    dramaNitamRepository = DramaNitamContentRepository(app),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
