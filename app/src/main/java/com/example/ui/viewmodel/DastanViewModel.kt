package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.model.ApiHealthReport
import com.example.data.model.AppConfig
import com.example.data.model.CatalogOverride
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import com.example.data.model.FavoriteItem
import com.example.data.model.SavedEpisodeItem
import com.example.data.model.UserProfile
import com.example.data.model.WatchHistoryItem
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
    val currentUserId: String
) : ViewModel() {

    private val _rawDramas = MutableStateFlow<List<DramaSeries>>(emptyList())
    private val _rawLatestEpisodes = MutableStateFlow<List<EpisodeItem>>(emptyList())
    private val _categoryEpisodesMap = MutableStateFlow<Map<Int, List<EpisodeItem>>>(emptyMap())
    private val _categoryPageMap = MutableStateFlow<Map<Int, Int>>(emptyMap())

    private val _isLoadingContent = MutableStateFlow(true)
    val isLoadingContent: StateFlow<Boolean> = _isLoadingContent.asStateFlow()

    private val _isLoadingEpisodes = MutableStateFlow(false)
    val isLoadingEpisodes: StateFlow<Boolean> = _isLoadingEpisodes.asStateFlow()

    private val _apiHealth = MutableStateFlow(ApiHealthReport())
    val apiHealth: StateFlow<ApiHealthReport> = _apiHealth.asStateFlow()

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

    // Visible Dramas for User UI (filters out hidden dramas)
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
        viewModelScope.launch {
            isAdmin.collect { adminFlag ->
                if (adminFlag) {
                    observeUsersForAdmin()
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
        viewModelScope.launch {
            _isLoadingContent.value = true
            val (dramas, latestEps) = contentRepository.fetchDramasAndLatestEpisodes(forceRefresh)
            _rawDramas.value = dramas
            _rawLatestEpisodes.value = latestEps
            _apiHealth.value = contentRepository.getLatestHealthReport()
            _isLoadingContent.value = false
            if (forceRefresh) {
                _statusMessage.value = "Synchronized ${dramas.size} dramas & ${latestEps.size} recent episodes from GiveMe5 API."
            }
        }
    }

    fun loadEpisodesForDrama(drama: DramaSeries, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoadingEpisodes.value = true
            val eps = contentRepository.fetchEpisodesForCategory(
                categoryId = drama.categoryId,
                dramaTitle = drama.title,
                page = 1,
                forceRefresh = forceRefresh
            )
            _categoryEpisodesMap.value = _categoryEpisodesMap.value + (drama.categoryId to eps)
            _categoryPageMap.value = _categoryPageMap.value + (drama.categoryId to 1)
            // Also refresh dramas in case poster was discovered
            val (updatedDramas, _) = contentRepository.fetchDramasAndLatestEpisodes(forceRefresh = false)
            if (updatedDramas.isNotEmpty()) {
                _rawDramas.value = updatedDramas
            }
            _isLoadingEpisodes.value = false
        }
    }

    fun loadNextEpisodePage(drama: DramaSeries) {
        viewModelScope.launch {
            if (_isLoadingEpisodes.value) return@launch
            _isLoadingEpisodes.value = true
            val nextPage = (_categoryPageMap.value[drama.categoryId] ?: 1) + 1
            val newPageEps = contentRepository.fetchEpisodesForCategory(
                categoryId = drama.categoryId,
                dramaTitle = drama.title,
                page = nextPage,
                forceRefresh = true
            )
            if (newPageEps.isNotEmpty()) {
                val existing = _categoryEpisodesMap.value[drama.categoryId].orEmpty()
                val merged = (existing + newPageEps).distinctBy { it.postId }.sortedBy { it.episodeNumber }
                _categoryEpisodesMap.value = _categoryEpisodesMap.value + (drama.categoryId to merged)
                _categoryPageMap.value = _categoryPageMap.value + (drama.categoryId to nextPage)
                _statusMessage.value = "Loaded page $nextPage (${newPageEps.size} additional episodes)"
            } else {
                _statusMessage.value = "All available episodes loaded for ${drama.title}"
            }
            _isLoadingEpisodes.value = false
        }
    }

    fun getEpisodesForCategory(categoryId: Int, includeHidden: Boolean = false): List<EpisodeItem> {
        val raw = _categoryEpisodesMap.value[categoryId]
            ?: _rawLatestEpisodes.value.filter { it.categoryId == categoryId }
        return contentRepository.applyAdminOverridesToEpisodes(
            episodes = raw,
            overrides = catalogOverrides.value,
            includeHiddenForAdmin = includeHidden
        )
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
                    posterUrl = drama.posterUrl,
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
            repository.saveWatchProgress(
                episodeId = episode.id,
                categoryId = episode.categoryId,
                episodeTitle = episode.title,
                dramaTitle = episode.dramaTitle,
                posterUrl = episode.posterUrl,
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
                repository.saveEpisodeToVault(
                    episodeId = episode.id,
                    categoryId = episode.categoryId,
                    title = episode.title,
                    dramaName = episode.dramaTitle,
                    posterUrl = episode.posterUrl,
                    embedUrl = episode.primaryEmbedUrl,
                    synopsis = episode.description
                )
                _statusMessage.value = "Saved ${episode.title} to Downloads Vault"
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
                paymentGatewayEnabled = false, // Disabled per requirement until payment gateway configured
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
                    contentRepository = WordPressContentRepository(),
                    currentUserId = currentUserId
                )
            }
        }
    }
}
