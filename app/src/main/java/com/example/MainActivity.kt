package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.auth.GoogleAuthHelper
import com.example.data.local.LocalCatalogCacheDatabase
import com.example.data.model.ContentSource
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DownloadsVaultScreen
import com.example.ui.screens.DramaDetailScreen
import com.example.ui.screens.ExploreAndCategoriesScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MembershipPlansScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.screens.SectionSeeAllScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.WatchHistoryScreen
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightCardSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OttomanGoldAccent
import com.example.ui.theme.RoyalVioletAccent
import com.example.ui.theme.SecondaryMutedText
import com.example.ui.viewmodel.DastanViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalCatalogCacheDatabase.ensureCoilConfigured(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                DastanRootApp(
                    onUpdateFlagSecure = { enabled ->
                        if (enabled) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                )
            }
        }
    }
}

private enum class BottomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    EXPLORE("Explore", Icons.Filled.Explore, Icons.Outlined.Explore, "nav_explore"),
    FAVORITES("Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "nav_favorites"),
    DOWNLOADS("Downloads", Icons.Filled.Download, Icons.Outlined.Download, "nav_downloads"),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person, "nav_profile")
}

private sealed interface ActiveOverlayScreen {
    data object None : ActiveOverlayScreen
    data class SectionSeeAll(
        val source: ContentSource
    ) : ActiveOverlayScreen
    data class DramaDetail(
        val drama: DramaSeries,
        val selectedSeason: Int = drama.seasonNumber,
        val returnToSeeAllSource: ContentSource? = null
    ) : ActiveOverlayScreen
    data class VideoPlayer(
        val episode: EpisodeItem,
        val siblingEpisodes: List<EpisodeItem>
    ) : ActiveOverlayScreen
    data object WatchHistory : ActiveOverlayScreen
    data object MembershipPlans : ActiveOverlayScreen
    data object AdminDashboard : ActiveOverlayScreen
}

@Composable
fun DastanRootApp(
    onUpdateFlagSecure: (Boolean) -> Unit
) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var hasCompletedOnboarding by rememberSaveable { mutableStateOf(false) }
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val updatedUser = auth.currentUser
            currentUser = updatedUser
            if (updatedUser != null) {
                hasCompletedOnboarding = true
                showSplash = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    when {
        showSplash -> {
            SplashScreen(onSplashFinished = { showSplash = false })
        }
        !hasCompletedOnboarding && currentUser == null -> {
            OnboardingScreen(onFinishOnboarding = { hasCompletedOnboarding = true })
        }
        currentUser == null -> {
            AuthScreen(
                onAuthSuccess = {
                    hasCompletedOnboarding = true
                    showSplash = false
                    currentUser = Firebase.auth.currentUser
                }
            )
        }
        else -> {
            val uid = currentUser!!.uid
            key(uid) {
                AuthenticatedDastanWorkspace(
                    currentUserId = uid,
                    onUpdateFlagSecure = onUpdateFlagSecure,
                    onSignedOut = { currentUser = null }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedDastanWorkspace(
    currentUserId: String,
    onUpdateFlagSecure: (Boolean) -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: DastanViewModel = viewModel(
        key = currentUserId,
        factory = DastanViewModel.factory(currentUserId)
    )

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val userDramas by viewModel.userDramas.collectAsStateWithLifecycle()
    val adminDramas by viewModel.adminDramas.collectAsStateWithLifecycle()
    val userLatestEpisodes by viewModel.userLatestEpisodes.collectAsStateWithLifecycle()
    val adminLatestEpisodes by viewModel.adminLatestEpisodes.collectAsStateWithLifecycle()
    val hindiUrduDramas by viewModel.hindiUrduDramas.collectAsStateWithLifecycle()
    val hindiUrduLatestEpisodes by viewModel.hindiUrduLatestEpisodes.collectAsStateWithLifecycle()
    val hindiUrduCategories by viewModel.hindiUrduCategories.collectAsStateWithLifecycle()
    val isLoadingHindiUrdu by viewModel.isLoadingHindiUrdu.collectAsStateWithLifecycle()
    val scopedEpisodesMap by viewModel.scopedEpisodesMap.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val savedEpisodes by viewModel.savedEpisodes.collectAsStateWithLifecycle()
    val catalogOverrides by viewModel.catalogOverrides.collectAsStateWithLifecycle()
    val appConfig by viewModel.appConfig.collectAsStateWithLifecycle()
    val adminUsers by viewModel.adminUsers.collectAsStateWithLifecycle()
    val apiHealth by viewModel.apiHealth.collectAsStateWithLifecycle()
    val cacheStats by viewModel.cacheStats.collectAsStateWithLifecycle()
    val isLoadingContent by viewModel.isLoadingContent.collectAsStateWithLifecycle()
    val isLoadingEpisodes by viewModel.isLoadingEpisodes.collectAsStateWithLifecycle()
    val flagSecureEnabled by viewModel.flagSecureEnabled.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(flagSecureEnabled) {
        onUpdateFlagSecure(flagSecureEnabled)
    }

    var selectedTab by rememberSaveable(currentUserId) { mutableStateOf(BottomNavTab.HOME) }
    var overlayScreen by remember(currentUserId) { mutableStateOf<ActiveOverlayScreen>(ActiveOverlayScreen.None) }

    val isUserVipOrAdmin = isAdmin || userProfile?.membershipPlan == "YEARLY_VIP"

    when (val currentOverlay = overlayScreen) {
        is ActiveOverlayScreen.SectionSeeAll -> {
            val sectionSource = currentOverlay.source
            val sectionDramas = if (sectionSource == ContentSource.DRAMANITAM_DUBBED) {
                hindiUrduDramas
            } else {
                userDramas
            }
            val sectionLoading = if (sectionSource == ContentSource.DRAMANITAM_DUBBED) {
                isLoadingHindiUrdu
            } else {
                isLoadingContent
            }
            SectionSeeAllScreen(
                source = sectionSource,
                dramas = sectionDramas,
                categories = if (sectionSource == ContentSource.DRAMANITAM_DUBBED) hindiUrduCategories else emptyList(),
                favorites = favorites,
                isLoading = sectionLoading,
                onBack = { overlayScreen = ActiveOverlayScreen.None },
                onSelectDrama = { drama ->
                    val firstSeason = drama.seasons.firstOrNull()?.seasonNumber ?: drama.seasonNumber
                    viewModel.loadEpisodesForDrama(drama, seasonNumber = firstSeason)
                    overlayScreen = ActiveOverlayScreen.DramaDetail(
                        drama = drama,
                        selectedSeason = firstSeason,
                        returnToSeeAllSource = sectionSource
                    )
                },
                onToggleFavorite = { drama -> viewModel.toggleFavorite(drama) },
                onRefresh = { viewModel.refreshContent(forceRefresh = true) }
            )
        }

        is ActiveOverlayScreen.DramaDetail -> {
            // Look up active drama ONLY in its own source list to guarantee strict separation
            val sourceDramas = if (currentOverlay.drama.source == ContentSource.DRAMANITAM_DUBBED) {
                hindiUrduDramas
            } else {
                userDramas
            }
            val activeDrama = sourceDramas.firstOrNull { it.id == currentOverlay.drama.id }
                ?: currentOverlay.drama
            val activeSeason = currentOverlay.selectedSeason
            val seasonEps = remember(
                activeDrama,
                activeSeason,
                scopedEpisodesMap,
                catalogOverrides,
                userLatestEpisodes,
                hindiUrduLatestEpisodes
            ) {
                viewModel.getEpisodesForDramaSeason(
                    drama = activeDrama,
                    seasonNumber = activeSeason,
                    includeHidden = false
                )
            }
            val isFav = favorites.any { it.dramaId == activeDrama.id }

            DramaDetailScreen(
                drama = activeDrama,
                selectedSeason = activeSeason,
                onSelectSeason = { newSeason ->
                    viewModel.loadEpisodesForDrama(activeDrama, seasonNumber = newSeason)
                    overlayScreen = currentOverlay.copy(selectedSeason = newSeason)
                },
                episodes = seasonEps,
                isFavorite = isFav,
                savedEpisodes = savedEpisodes,
                isLoadingEpisodes = isLoadingEpisodes,
                isUserVipOrAdmin = isUserVipOrAdmin,
                onBack = {
                    overlayScreen = currentOverlay.returnToSeeAllSource?.let {
                        ActiveOverlayScreen.SectionSeeAll(it)
                    } ?: ActiveOverlayScreen.None
                },
                onToggleFavorite = { viewModel.toggleFavorite(activeDrama) },
                onPlayEpisode = { ep ->
                    overlayScreen = ActiveOverlayScreen.VideoPlayer(
                        episode = ep,
                        siblingEpisodes = seasonEps.ifEmpty { listOf(ep) }
                    )
                },
                onToggleSaveEpisode = { ep -> viewModel.toggleSaveEpisodeToVault(ep) },
                onLoadMoreEpisodes = {
                    viewModel.loadNextEpisodePage(activeDrama, seasonNumber = activeSeason)
                },
                onRequestVipUpgrade = { overlayScreen = ActiveOverlayScreen.MembershipPlans }
            )
        }

        is ActiveOverlayScreen.VideoPlayer -> {
            val sameSourceDramas = if (currentOverlay.episode.source == ContentSource.DRAMANITAM_DUBBED) {
                hindiUrduDramas
            } else {
                userDramas
            }
            val parentDrama = remember(currentOverlay.episode, sameSourceDramas) {
                sameSourceDramas.firstOrNull { drama ->
                    drama.source == currentOverlay.episode.source &&
                        (drama.allCategoryIds.contains(currentOverlay.episode.categoryId) ||
                            drama.title.equals(currentOverlay.episode.dramaTitle, ignoreCase = true))
                }
            }
            val nextSeasonObj = remember(currentOverlay.episode, parentDrama) {
                parentDrama?.seasons
                    ?.sortedBy { it.seasonNumber }
                    ?.firstOrNull { it.seasonNumber > currentOverlay.episode.seasonNumber }
            }

            VideoPlayerScreen(
                episode = currentOverlay.episode,
                siblingEpisodes = currentOverlay.siblingEpisodes,
                subtitleEnabledByDefault = userProfile?.subtitleEnabled ?: true,
                onBack = { overlayScreen = ActiveOverlayScreen.None },
                onSelectEpisode = { nextEp ->
                    overlayScreen = ActiveOverlayScreen.VideoPlayer(
                        episode = nextEp,
                        siblingEpisodes = currentOverlay.siblingEpisodes
                    )
                },
                onSaveProgress = { progressMs, durationMs, completed ->
                    viewModel.recordPlaybackProgress(
                        episode = currentOverlay.episode,
                        progressMs = progressMs,
                        durationMs = durationMs,
                        completed = completed
                    )
                },
                nextSeasonNumber = nextSeasonObj?.seasonNumber,
                onPlayNextSeason = if (parentDrama != null && nextSeasonObj != null) {
                    {
                        val targetSeason = nextSeasonObj.seasonNumber
                        val cachedNextSeasonEps = viewModel.getEpisodesForDramaSeason(
                            drama = parentDrama,
                            seasonNumber = targetSeason,
                            includeHidden = false
                        )
                        if (cachedNextSeasonEps.isNotEmpty()) {
                            overlayScreen = ActiveOverlayScreen.VideoPlayer(
                                episode = cachedNextSeasonEps.first(),
                                siblingEpisodes = cachedNextSeasonEps
                            )
                        } else {
                            viewModel.loadEpisodesForDrama(parentDrama, seasonNumber = targetSeason)
                            overlayScreen = ActiveOverlayScreen.DramaDetail(
                                drama = parentDrama,
                                selectedSeason = targetSeason
                            )
                        }
                    }
                } else null
            )
        }

        ActiveOverlayScreen.WatchHistory -> {
            WatchHistoryScreen(
                watchHistory = watchHistory,
                onBack = { overlayScreen = ActiveOverlayScreen.None },
                onResumeEpisode = { item ->
                    val resolvedSource = if (item.episodeId.startsWith("dramanitam_")) {
                        ContentSource.DRAMANITAM_DUBBED
                    } else {
                        ContentSource.DRAMA_COLLECTION
                    }
                    val sourceLatestList = if (resolvedSource == ContentSource.DRAMANITAM_DUBBED) {
                        hindiUrduLatestEpisodes
                    } else {
                        userLatestEpisodes
                    }
                    val matchedEp = sourceLatestList.firstOrNull {
                        it.id == item.episodeId || "ep_${it.postId}" == item.episodeId
                    } ?: EpisodeItem(
                        id = item.episodeId,
                        postId = item.episodeId.removePrefix("ep_").toIntOrNull() ?: 0,
                        categoryId = item.categoryId.toInt(),
                        dramaTitle = item.cleanDramaTitle,
                        title = item.episodeTitle,
                        episodeNumber = item.parsedEpisodeNumber,
                        seasonNumber = item.parsedSeasonNumber,
                        description = "Resumed from Watch History",
                        publishedDate = "",
                        posterUrl = item.posterUrl,
                        thumbnailUrl = item.posterUrl,
                        primaryEmbedUrl = item.embedUrl,
                        backupEmbedUrls = emptyList(),
                        sourcePostUrl = "",
                        isPlayable = item.embedUrl.isNotBlank(),
                        source = resolvedSource
                    )
                    val seasonSiblings = viewModel.getEpisodesForCategory(
                        categoryId = matchedEp.categoryId,
                        source = matchedEp.source,
                        includeHidden = false
                    ).ifEmpty { listOf(matchedEp) }
                    overlayScreen = ActiveOverlayScreen.VideoPlayer(
                        episode = matchedEp,
                        siblingEpisodes = seasonSiblings
                    )
                },
                onRemoveHistoryItem = { epId -> viewModel.removeWatchHistory(epId) }
            )
        }

        ActiveOverlayScreen.MembershipPlans -> {
            MembershipPlansScreen(
                userProfile = userProfile,
                appConfig = appConfig,
                onBack = { overlayScreen = ActiveOverlayScreen.None }
            )
        }

        ActiveOverlayScreen.AdminDashboard -> {
            AdminDashboardScreen(
                isAdmin = isAdmin,
                dramas = adminDramas,
                latestEpisodes = adminLatestEpisodes,
                overrides = catalogOverrides,
                users = adminUsers,
                apiHealth = apiHealth,
                appConfig = appConfig,
                isLoadingSync = isLoadingContent,
                onBackToUserApp = { overlayScreen = ActiveOverlayScreen.None },
                onSaveOverride = { type, id, title, desc, poster, hidden, featured, vip, order ->
                    viewModel.adminSaveCatalogOverride(
                        targetType = type,
                        targetId = id,
                        customTitle = title,
                        customDescription = desc,
                        customPosterUrl = poster,
                        isHidden = hidden,
                        isFeatured = featured,
                        isVipOnly = vip,
                        priorityOrder = order
                    )
                },
                onUpdateUserMembership = { uid, plan, blocked ->
                    viewModel.adminUpdateUserMembership(uid, plan, blocked)
                },
                onRunApiSync = { viewModel.adminRunSyncAndHealthCheck() },
                onSaveAppConfig = { banner, maintenance, price ->
                    viewModel.adminUpdateAppConfig(banner, maintenance, price)
                }
            )
        }

        ActiveOverlayScreen.None -> {
            Scaffold(
                containerColor = MidnightBg,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(
                        containerColor = MidnightCardSurface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        BottomNavTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (tab == BottomNavTab.PROFILE) {
                                        viewModel.refreshCacheStats()
                                    }
                                    selectedTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MidnightBg,
                                    selectedTextColor = OttomanGoldAccent,
                                    indicatorColor = RoyalVioletAccent,
                                    unselectedIconColor = SecondaryMutedText,
                                    unselectedTextColor = SecondaryMutedText
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        BottomNavTab.HOME -> {
                            HomeScreen(
                                dramas = userDramas,
                                latestEpisodes = userLatestEpisodes,
                                hindiUrduDramas = hindiUrduDramas,
                                hindiUrduLatestEpisodes = hindiUrduLatestEpisodes,
                                hindiUrduCategories = hindiUrduCategories,
                                isLoadingHindiUrdu = isLoadingHindiUrdu,
                                watchHistory = watchHistory,
                                favorites = favorites,
                                userProfile = userProfile,
                                appConfig = appConfig,
                                isAdmin = isAdmin,
                                isLoading = isLoadingContent,
                                onSelectDrama = { drama ->
                                    val firstSeason = drama.seasons.firstOrNull()?.seasonNumber ?: drama.seasonNumber
                                    viewModel.loadEpisodesForDrama(drama, seasonNumber = firstSeason)
                                    overlayScreen = ActiveOverlayScreen.DramaDetail(
                                        drama = drama,
                                        selectedSeason = firstSeason
                                    )
                                },
                                onPlayEpisode = { ep ->
                                    val sameSourceLatest = if (ep.source == ContentSource.DRAMANITAM_DUBBED) {
                                        hindiUrduLatestEpisodes
                                    } else {
                                        userLatestEpisodes
                                    }
                                    val siblings = viewModel.getEpisodesForCategory(
                                        categoryId = ep.categoryId,
                                        source = ep.source,
                                        includeHidden = false
                                    ).ifEmpty {
                                        sameSourceLatest.filter {
                                            it.source == ep.source &&
                                                it.categoryId == ep.categoryId &&
                                                it.seasonNumber == ep.seasonNumber
                                        }.ifEmpty { listOf(ep) }
                                    }
                                    overlayScreen = ActiveOverlayScreen.VideoPlayer(
                                        episode = ep,
                                        siblingEpisodes = siblings
                                    )
                                },
                                onToggleFavorite = { drama -> viewModel.toggleFavorite(drama) },
                                onOpenSeeAllSection = { sectionSource ->
                                    overlayScreen = ActiveOverlayScreen.SectionSeeAll(sectionSource)
                                },
                                onOpenSearch = { selectedTab = BottomNavTab.EXPLORE },
                                onOpenWatchHistory = { overlayScreen = ActiveOverlayScreen.WatchHistory },
                                onOpenMembership = { overlayScreen = ActiveOverlayScreen.MembershipPlans },
                                onOpenAdminConsole = {
                                    if (isAdmin) overlayScreen = ActiveOverlayScreen.AdminDashboard
                                },
                                onRefresh = { viewModel.refreshContent(forceRefresh = true) }
                            )
                        }

                        BottomNavTab.EXPLORE -> {
                            ExploreAndCategoriesScreen(
                                dramas = userDramas,
                                latestEpisodes = userLatestEpisodes,
                                hindiUrduDramas = hindiUrduDramas,
                                hindiUrduLatestEpisodes = hindiUrduLatestEpisodes,
                                hindiUrduCategories = hindiUrduCategories,
                                favorites = favorites,
                                onSelectDrama = { drama ->
                                    val firstSeason = drama.seasons.firstOrNull()?.seasonNumber ?: drama.seasonNumber
                                    viewModel.loadEpisodesForDrama(drama, seasonNumber = firstSeason)
                                    overlayScreen = ActiveOverlayScreen.DramaDetail(
                                        drama = drama,
                                        selectedSeason = firstSeason
                                    )
                                },
                                onPlayEpisode = { ep ->
                                    val siblings = viewModel.getEpisodesForCategory(
                                        categoryId = ep.categoryId,
                                        source = ep.source,
                                        includeHidden = false
                                    ).ifEmpty { listOf(ep) }
                                    overlayScreen = ActiveOverlayScreen.VideoPlayer(
                                        episode = ep,
                                        siblingEpisodes = siblings
                                    )
                                },
                                onToggleFavorite = { drama -> viewModel.toggleFavorite(drama) }
                            )
                        }

                        BottomNavTab.FAVORITES -> {
                            FavoritesScreen(
                                favorites = favorites,
                                dramas = userDramas + hindiUrduDramas,
                                onSelectDrama = { drama ->
                                    val firstSeason = drama.seasons.firstOrNull()?.seasonNumber ?: drama.seasonNumber
                                    viewModel.loadEpisodesForDrama(drama, seasonNumber = firstSeason)
                                    overlayScreen = ActiveOverlayScreen.DramaDetail(
                                        drama = drama,
                                        selectedSeason = firstSeason
                                    )
                                },
                                onRemoveFavorite = { dramaId ->
                                    val dummy = DramaSeries(
                                        id = dramaId,
                                        categoryId = 0,
                                        title = "Drama",
                                        originalTitle = "Drama",
                                        slug = "",
                                        description = "",
                                        posterUrl = "",
                                        episodeCount = 0,
                                        seasonNumber = 1,
                                        genreTags = emptyList(),
                                        source = if (dramaId.startsWith("dramanitam_")) {
                                            ContentSource.DRAMANITAM_DUBBED
                                        } else {
                                            ContentSource.DRAMA_COLLECTION
                                        }
                                    )
                                    viewModel.toggleFavorite(dummy)
                                }
                            )
                        }

                        BottomNavTab.DOWNLOADS -> {
                            DownloadsVaultScreen(
                                savedEpisodes = savedEpisodes,
                                onPlaySavedEpisode = { ep ->
                                    val siblings = viewModel.getEpisodesForCategory(
                                        categoryId = ep.categoryId,
                                        source = ep.source,
                                        includeHidden = false
                                    ).ifEmpty { listOf(ep) }
                                    overlayScreen = ActiveOverlayScreen.VideoPlayer(
                                        episode = ep,
                                        siblingEpisodes = siblings
                                    )
                                },
                                onRemoveSavedEpisode = { epId ->
                                    val dummyEp = EpisodeItem(
                                        id = epId,
                                        postId = 0,
                                        categoryId = 0,
                                        dramaTitle = "",
                                        title = "",
                                        episodeNumber = 1,
                                        seasonNumber = 1,
                                        description = "",
                                        publishedDate = "",
                                        posterUrl = "",
                                        primaryEmbedUrl = "",
                                        backupEmbedUrls = emptyList(),
                                        sourcePostUrl = "",
                                        isPlayable = true
                                    )
                                    viewModel.toggleSaveEpisodeToVault(dummyEp)
                                }
                            )
                        }

                        BottomNavTab.PROFILE -> {
                            ProfileAndSettingsScreen(
                                userProfile = userProfile,
                                appConfig = appConfig,
                                cacheStats = cacheStats,
                                isAdmin = isAdmin,
                                flagSecureEnabled = flagSecureEnabled,
                                watchHistoryCount = watchHistory.size,
                                favoritesCount = favorites.size,
                                onOpenWatchHistory = { overlayScreen = ActiveOverlayScreen.WatchHistory },
                                onOpenMembershipPlans = { overlayScreen = ActiveOverlayScreen.MembershipPlans },
                                onOpenAdminConsole = {
                                    if (isAdmin) overlayScreen = ActiveOverlayScreen.AdminDashboard
                                },
                                onSaveSettings = { name, quality, subs, secure ->
                                    viewModel.updateUserSettings(name, quality, subs, secure)
                                },
                                onClearCache = { viewModel.clearLocalCache() },
                                onSignOut = {
                                    GoogleAuthHelper.signOut(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        onSignOutComplete = onSignedOut
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
