package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AppConfig
import com.example.data.model.ContentSource
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import com.example.data.model.FavoriteItem
import com.example.data.model.UserProfile
import com.example.data.model.WatchHistoryItem
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCardSurface
import com.example.ui.theme.MidnightSecondaryBg
import com.example.ui.theme.OttomanGoldAccent
import com.example.ui.theme.PrimaryWhiteText
import com.example.ui.theme.RoyalVioletAccent
import com.example.ui.theme.SecondaryMutedText

@Composable
fun HomeScreen(
    dramas: List<DramaSeries>,
    latestEpisodes: List<EpisodeItem>,
    hindiUrduDramas: List<DramaSeries>,
    hindiUrduLatestEpisodes: List<EpisodeItem>,
    hindiUrduCategories: List<String>,
    isLoadingHindiUrdu: Boolean,
    watchHistory: List<WatchHistoryItem>,
    favorites: List<FavoriteItem>,
    userProfile: UserProfile?,
    appConfig: AppConfig,
    isAdmin: Boolean,
    isLoading: Boolean,
    onSelectDrama: (DramaSeries) -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onToggleFavorite: (DramaSeries) -> Unit,
    onOpenSeeAllSection: (ContentSource) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenWatchHistory: () -> Unit,
    onOpenMembership: () -> Unit,
    onOpenAdminConsole: () -> Unit,
    onRefresh: () -> Unit
) {
    // Strictly isolate Drama Collection (giveme5.co) from Hindi & Urdu Dubbed (dramanitam.com)
    val dramaCollectionList = remember(dramas) {
        dramas.filter { it.source == ContentSource.DRAMA_COLLECTION }
    }
    val dubbedDramaList = remember(hindiUrduDramas) {
        hindiUrduDramas.filter { it.source == ContentSource.DRAMANITAM_DUBBED }
    }

    var selectedDubbedCategory by rememberSaveable { mutableStateOf("All") }
    val filteredDubbedDramas = remember(dubbedDramaList, selectedDubbedCategory) {
        if (selectedDubbedCategory == "All") {
            dubbedDramaList
        } else {
            dubbedDramaList.filter { drama ->
                drama.sourceCategoryName.equals(selectedDubbedCategory, ignoreCase = true) ||
                    drama.genreTags.any { it.contains(selectedDubbedCategory, ignoreCase = true) }
            }
        }
    }

    val featuredDrama = remember(dramaCollectionList) {
        dramaCollectionList.firstOrNull { it.isFeatured } ?: dramaCollectionList.firstOrNull()
    }
    val favoriteIds = remember(favorites) { favorites.map { it.dramaId }.toSet() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // Top App Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = RoyalVioletAccent.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "Dastan TV Logo",
                                tint = OttomanGoldAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "DASTAN TV",
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryWhiteText,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (userProfile?.membershipPlan == "YEARLY_VIP") {
                                "VIP Member • Ad-Free HD"
                            } else {
                                "Historical & Dubbed Streaming"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = OttomanGoldAccent
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAdmin) {
                        IconButton(
                            onClick = onOpenAdminConsole,
                            modifier = Modifier.testTag("home_admin_console_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin Dashboard",
                                tint = OttomanGoldAccent
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier.testTag("home_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Dramas",
                            tint = PrimaryWhiteText
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("home_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Content",
                            tint = SecondaryMutedText
                        )
                    }
                }
            }
        }

        // Announcement Banner
        if (appConfig.announcementBanner.isNotBlank()) {
            item {
                Surface(
                    color = MidnightSecondaryBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .border(1.dp, OttomanGoldAccent.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = OttomanGoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = appConfig.announcementBanner,
                            style = MaterialTheme.typography.labelMedium,
                            color = PrimaryWhiteText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 1. Existing Featured Banner (Preserved)
        item {
            if (featuredDrama != null) {
                FeaturedDramaBanner(
                    drama = featuredDrama,
                    isFavorite = favoriteIds.contains(featuredDrama.id),
                    onWatchClick = { onSelectDrama(featuredDrama) },
                    onToggleFavorite = { onToggleFavorite(featuredDrama) }
                )
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoyalVioletAccent)
                }
            }
        }

        // Continue Watching Section
        if (watchHistory.isNotEmpty()) {
            item {
                SectionHeaderRow(
                    title = "Continue Watching",
                    subtitle = "Resume exact Drama, Season & Episode",
                    actionLabel = "All History",
                    onActionClick = onOpenWatchHistory,
                    testTag = "home_continue_watching_see_all"
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(watchHistory.take(8), key = { it.episodeId }) { item ->
                        ContinueWatchingCard(
                            item = item,
                            onClick = {
                                val matchedEp = (latestEpisodes + hindiUrduLatestEpisodes).firstOrNull {
                                    it.id == item.episodeId || "ep_${it.postId}" == item.episodeId
                                } ?: EpisodeItem(
                                    id = item.episodeId,
                                    postId = item.episodeId.removePrefix("ep_").toIntOrNull() ?: 0,
                                    categoryId = item.categoryId.toInt(),
                                    dramaTitle = item.cleanDramaTitle,
                                    title = item.episodeTitle,
                                    episodeNumber = item.parsedEpisodeNumber,
                                    seasonNumber = item.parsedSeasonNumber,
                                    description = "Continue watching ${item.episodeTitle}",
                                    publishedDate = "",
                                    posterUrl = item.posterUrl,
                                    thumbnailUrl = item.posterUrl,
                                    primaryEmbedUrl = item.embedUrl,
                                    backupEmbedUrls = emptyList(),
                                    sourcePostUrl = "",
                                    isPlayable = item.embedUrl.isNotBlank(),
                                    source = if (item.episodeId.startsWith("dramanitam_")) {
                                        ContentSource.DRAMANITAM_DUBBED
                                    } else {
                                        ContentSource.DRAMA_COLLECTION
                                    }
                                )
                                onPlayEpisode(matchedEp)
                            }
                        )
                    }
                }
            }
        }

        // 2. SECTION 1: "Drama Collection" (giveme5.co) — Separate Horizontal Poster Carousel + See All
        item {
            SectionHeaderRow(
                title = "Drama Collection",
                subtitle = "Historical & Turkish Series • Multi-Season Catalog",
                badgeText = "COLLECTION",
                badgeColor = OttomanGoldAccent,
                actionLabel = "See All",
                onActionClick = { onOpenSeeAllSection(ContentSource.DRAMA_COLLECTION) },
                testTag = "see_all_drama_collection_button"
            )
            if (dramaCollectionList.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("drama_collection_carousel")
                ) {
                    items(dramaCollectionList, key = { it.id }) { drama ->
                        DramaPosterCard(
                            drama = drama,
                            isFavorite = favoriteIds.contains(drama.id),
                            onClick = { onSelectDrama(drama) },
                            onToggleFavorite = { onToggleFavorite(drama) }
                        )
                    }
                }
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = OttomanGoldAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // 3. SECTION 2: "Hindi & Urdu Dubbed" (dramanitam.com) — Separate Horizontal Poster Carousel + See All
        item {
            Spacer(modifier = Modifier.height(6.dp))
            SectionHeaderRow(
                title = "Hindi & Urdu Dubbed",
                subtitle = "Korean, Turkish & Asian Dramas • DramaNitam",
                badgeText = "DUBBED",
                badgeColor = RoyalVioletAccent,
                actionLabel = "See All",
                onActionClick = { onOpenSeeAllSection(ContentSource.DRAMANITAM_DUBBED) },
                testTag = "see_all_hindi_urdu_dubbed_button"
            )

            // Independent Category Filter Chips for DramaNitam
            val dubbedChipCategories = remember(hindiUrduCategories) {
                listOf("All") + hindiUrduCategories.ifEmpty {
                    listOf("Turkish Dramas", "Korean Dramas", "Chinese Dramas")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dubbedChipCategories.forEach { catName ->
                    val selected = selectedDubbedCategory == catName
                    FilterChip(
                        selected = selected,
                        onClick = { selectedDubbedCategory = catName },
                        label = {
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RoyalVioletAccent,
                            selectedLabelColor = MidnightBg,
                            containerColor = MidnightCardSurface,
                            labelColor = PrimaryWhiteText
                        ),
                        modifier = Modifier.testTag("dubbed_cat_chip_${catName.lowercase().replace(" ", "_")}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (filteredDubbedDramas.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("hindi_urdu_dubbed_carousel")
                ) {
                    items(filteredDubbedDramas, key = { it.id }) { drama ->
                        DramaPosterCard(
                            drama = drama,
                            isFavorite = favoriteIds.contains(drama.id),
                            onClick = { onSelectDrama(drama) },
                            onToggleFavorite = { onToggleFavorite(drama) }
                        )
                    }
                }
            } else if (isLoadingHindiUrdu) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = RoyalVioletAccent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading Hindi & Urdu Dubbed Series...",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryMutedText
                        )
                    }
                }
            } else {
                Surface(
                    color = MidnightCardSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.dp, MidnightBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "No dramas found in $selectedDubbedCategory",
                                style = MaterialTheme.typography.titleSmall,
                                color = PrimaryWhiteText
                            )
                            Text(
                                text = "Tap Refresh or switch category filter to view Hindi & Urdu Dubbed series.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryMutedText
                            )
                        }
                        TextButton(onClick = { selectedDubbedCategory = "All" }) {
                            Text("Show All", color = OttomanGoldAccent)
                        }
                    }
                }
            }
        }

        // Latest Episodes from Drama Collection (Source 1)
        if (latestEpisodes.isNotEmpty()) {
            item {
                SectionHeaderRow(
                    title = "Latest Episodes • Drama Collection",
                    subtitle = "Newly added episodes with Urdu Subtitles",
                    actionLabel = "See All",
                    onActionClick = { onOpenSeeAllSection(ContentSource.DRAMA_COLLECTION) },
                    testTag = "latest_episodes_collection_see_all"
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        latestEpisodes.filter { it.source == ContentSource.DRAMA_COLLECTION }.take(15),
                        key = { it.id }
                    ) { ep ->
                        LatestEpisodeHorizontalCard(
                            episode = ep,
                            onClick = { onPlayEpisode(ep) }
                        )
                    }
                }
            }
        }

        // Latest Added from Hindi & Urdu Dubbed (Source 2)
        if (hindiUrduLatestEpisodes.isNotEmpty()) {
            item {
                SectionHeaderRow(
                    title = "Latest Added • Hindi & Urdu Dubbed",
                    subtitle = "Recent Korean & Turkish dubbed episodes",
                    actionLabel = "See All",
                    onActionClick = { onOpenSeeAllSection(ContentSource.DRAMANITAM_DUBBED) },
                    testTag = "latest_episodes_dubbed_see_all"
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        hindiUrduLatestEpisodes.filter { it.source == ContentSource.DRAMANITAM_DUBBED }.take(15),
                        key = { it.id }
                    ) { ep ->
                        LatestEpisodeHorizontalCard(
                            episode = ep,
                            onClick = { onPlayEpisode(ep) }
                        )
                    }
                }
            }
        }

        // Membership Promo Banner
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onOpenMembership() }
                    .border(1.dp, OttomanGoldAccent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .testTag("home_vip_banner"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSecondaryBg)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = OttomanGoldAccent.copy(alpha = 0.16f),
                        shape = CircleShape,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = OttomanGoldAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Dastan TV Yearly VIP Pass",
                            style = MaterialTheme.typography.titleMedium,
                            color = OttomanGoldAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "PKR ${appConfig.yearlyVipPricePkr} / Year • Priority HD Servers & Early Access",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryMutedText
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = OttomanGoldAccent
                    )
                }
            }
        }
    }
}

@Composable
fun SectionSeeAllScreen(
    source: ContentSource,
    dramas: List<DramaSeries>,
    categories: List<String>,
    favorites: List<FavoriteItem>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSelectDrama: (DramaSeries) -> Unit,
    onToggleFavorite: (DramaSeries) -> Unit,
    onRefresh: () -> Unit
) {
    BackHandler(onBack = onBack)

    var searchQuery by rememberSaveable(source.sourceId) { mutableStateOf("") }
    var selectedCategory by rememberSaveable(source.sourceId) { mutableStateOf("All") }

    // Strictly enforce source isolation inside See All page
    val sourceIsolatedDramas = remember(dramas, source) {
        dramas.filter { it.source == source }
    }

    val availableCategories = remember(source, categories, sourceIsolatedDramas) {
        if (source == ContentSource.DRAMANITAM_DUBBED) {
            (listOf("All") + categories.ifEmpty {
                sourceIsolatedDramas.map { it.sourceCategoryName }.filter { it.isNotBlank() }.distinct()
            }).distinct()
        } else {
            val dynamicGenres = sourceIsolatedDramas
                .flatMap { it.genreTags }
                .filter { it.isNotBlank() && !it.contains("Seasons", ignoreCase = true) }
                .distinct()
            (listOf("All", "Multi-Season") + dynamicGenres).distinct()
        }
    }

    val filteredDramas = remember(sourceIsolatedDramas, searchQuery, selectedCategory, source) {
        sourceIsolatedDramas.filter { drama ->
            val matchesQuery = searchQuery.isBlank() ||
                drama.title.contains(searchQuery, ignoreCase = true) ||
                drama.originalTitle.contains(searchQuery, ignoreCase = true) ||
                drama.description.contains(searchQuery, ignoreCase = true)

            val matchesCat = when {
                selectedCategory == "All" -> true
                selectedCategory == "Multi-Season" -> drama.totalSeasonsCount > 1
                source == ContentSource.DRAMANITAM_DUBBED ->
                    drama.sourceCategoryName.equals(selectedCategory, ignoreCase = true) ||
                        drama.genreTags.any { it.contains(selectedCategory, ignoreCase = true) }
                else ->
                    drama.genreTags.any { it.equals(selectedCategory, ignoreCase = true) }
            }
            matchesQuery && matchesCat
        }
    }

    val favoriteIds = remember(favorites) { favorites.map { it.dramaId }.toSet() }
    val accentColor = if (source == ContentSource.DRAMANITAM_DUBBED) RoyalVioletAccent else OttomanGoldAccent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("section_see_all_screen_${source.sourceId}")
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("see_all_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryWhiteText
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = source.displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = PrimaryWhiteText,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = accentColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${filteredDramas.size} Series",
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = source.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryMutedText
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag("see_all_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Section",
                    tint = accentColor
                )
            }
        }

        // Section Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search in ${source.displayTitle}...",
                    color = SecondaryMutedText,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = accentColor
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = SecondaryMutedText
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                unfocusedBorderColor = MidnightBorder,
                focusedContainerColor = MidnightCardSurface,
                unfocusedContainerColor = MidnightCardSurface,
                focusedTextColor = PrimaryWhiteText,
                unfocusedTextColor = PrimaryWhiteText
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("see_all_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Section-Specific Category Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableCategories.forEach { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = category },
                    label = {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentColor,
                        selectedLabelColor = MidnightBg,
                        containerColor = MidnightCardSurface,
                        labelColor = PrimaryWhiteText
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading && filteredDramas.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentColor)
            }
        } else if (filteredDramas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No matching series in ${source.displayTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryWhiteText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Try clearing your search or selecting 'All' categories.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryMutedText
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("see_all_drama_grid")
            ) {
                items(filteredDramas, key = { it.id }) { drama ->
                    ExploreDramaGridCard(
                        drama = drama,
                        isFavorite = favoriteIds.contains(drama.id),
                        onClick = { onSelectDrama(drama) },
                        onToggleFavorite = { onToggleFavorite(drama) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedDramaBanner(
    drama: DramaSeries,
    isFavorite: Boolean,
    onWatchClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(276.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MidnightBorder, RoundedCornerShape(16.dp))
            .clickable { onWatchClick() }
            .testTag("featured_drama_banner")
    ) {
        val heroImage = drama.posterUrl.ifBlank { drama.thumbnailUrl }
        if (heroImage.isNotBlank()) {
            AsyncImage(
                model = heroImage,
                contentDescription = drama.title,
                placeholder = painterResource(id = R.drawable.img_hero_banner),
                error = painterResource(id = R.drawable.img_hero_banner),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_hero_banner),
                contentDescription = drama.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MidnightBg.copy(alpha = 0.55f),
                            MidnightBg.copy(alpha = 0.96f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = OttomanGoldAccent,
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Text(
                        text = "FEATURED EPIC",
                        style = MaterialTheme.typography.labelSmall,
                        color = MidnightBg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Surface(
                    color = MidnightSecondaryBg.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(5.dp)
                ) {
                    val seasonSummary = if (drama.totalSeasonsCount > 1) {
                        "${drama.totalSeasonsCount} Seasons • ${drama.episodeCount} Eps"
                    } else {
                        "Season ${drama.seasonNumber} • ${drama.episodeCount} Eps"
                    }
                    Text(
                        text = seasonSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = OttomanGoldAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = drama.title,
                style = MaterialTheme.typography.headlineMedium,
                color = PrimaryWhiteText,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = drama.description,
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onWatchClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalVioletAccent,
                        contentColor = MidnightBg
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("featured_watch_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Watch Series",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onToggleFavorite,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("featured_favorite_button")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) OttomanGoldAccent else PrimaryWhiteText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFavorite) "Saved" else "Watchlist",
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryWhiteText
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = OttomanGoldAccent,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryWhiteText,
                    fontWeight = FontWeight.Bold
                )
                if (!badgeText.isNullOrBlank()) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryMutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                modifier = if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = OttomanGoldAccent,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = OttomanGoldAccent,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: WatchHistoryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(216.dp)
            .clickable { onClick() }
            .border(1.dp, MidnightBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(106.dp)
            ) {
                if (item.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.episodeTitle,
                        placeholder = painterResource(id = R.drawable.img_hero_banner),
                        error = painterResource(id = R.drawable.img_hero_banner),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner),
                        contentDescription = item.episodeTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MidnightBg.copy(alpha = 0.35f))
                )
                Surface(
                    color = RoyalVioletAccent,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume",
                            tint = MidnightBg,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Surface(
                    color = MidnightBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "S${item.parsedSeasonNumber} • E${item.parsedEpisodeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OttomanGoldAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                LinearProgressIndicator(
                    progress = { item.progressFraction.coerceIn(0.08f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = OttomanGoldAccent,
                    trackColor = MidnightSecondaryBg
                )
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.cleanDramaTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = OttomanGoldAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.episodeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryWhiteText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DramaPosterCard(
    drama: DramaSeries,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val isDubbedSource = drama.source == ContentSource.DRAMANITAM_DUBBED
    Card(
        modifier = Modifier
            .width(138.dp)
            .clickable { onClick() }
            .border(1.dp, MidnightBorder, RoundedCornerShape(12.dp))
            .testTag("drama_card_${drama.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
            ) {
                val imgUrl = drama.thumbnailUrl.ifBlank { drama.posterUrl }
                if (imgUrl.isNotBlank()) {
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = drama.title,
                        placeholder = painterResource(id = R.drawable.img_hero_banner),
                        error = painterResource(id = R.drawable.img_hero_banner),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner),
                        contentDescription = drama.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Top-left Season / Audio Badge
                Surface(
                    color = MidnightBg.copy(alpha = 0.84f),
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    val badgeText = when {
                        drama.totalSeasonsCount > 1 -> "${drama.totalSeasonsCount} Seasons"
                        isDubbedSource -> drama.sourceCategoryName.ifBlank { "Dubbed" }.take(14)
                        else -> "Season ${drama.seasonNumber}"
                    }
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDubbedSource) RoyalVioletAccent else OttomanGoldAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Top-right Favorite button
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) OttomanGoldAccent else PrimaryWhiteText,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Bottom-right Episode count pill
                Surface(
                    color = MidnightBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(5.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "${drama.episodeCount} Eps",
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryWhiteText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
                Text(
                    text = drama.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryWhiteText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isDubbedSource) {
                        drama.audioLanguage
                    } else {
                        drama.genreTags.firstOrNull() ?: "Historical"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDubbedSource) OttomanGoldAccent else SecondaryMutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LatestEpisodeHorizontalCard(
    episode: EpisodeItem,
    onClick: () -> Unit
) {
    val isDubbed = episode.source == ContentSource.DRAMANITAM_DUBBED
    Card(
        modifier = Modifier
            .width(214.dp)
            .clickable { onClick() }
            .border(1.dp, MidnightBorder, RoundedCornerShape(12.dp))
            .testTag("latest_ep_card_${episode.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
            ) {
                val thumb = episode.thumbnailUrl.ifBlank { episode.posterUrl }
                if (thumb.isNotBlank()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = episode.cleanTitle,
                        placeholder = painterResource(id = R.drawable.img_hero_banner),
                        error = painterResource(id = R.drawable.img_hero_banner),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner),
                        contentDescription = episode.cleanTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MidnightBg.copy(alpha = 0.8f))
                            )
                        )
                )

                Surface(
                    color = if (isDubbed) RoyalVioletAccent else OttomanGoldAccent,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = "S${episode.seasonNumber} • EP ${episode.episodeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MidnightBg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    color = MidnightBg.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = episode.durationLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryWhiteText,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = episode.dramaTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDubbed) RoyalVioletAccent else OttomanGoldAccent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = episode.cleanTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryWhiteText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ExploreAndCategoriesScreen(
    dramas: List<DramaSeries>,
    latestEpisodes: List<EpisodeItem>,
    hindiUrduDramas: List<DramaSeries>,
    hindiUrduLatestEpisodes: List<EpisodeItem>,
    hindiUrduCategories: List<String>,
    favorites: List<FavoriteItem>,
    onSelectDrama: (DramaSeries) -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onToggleFavorite: (DramaSeries) -> Unit
) {
    // Source switcher in Explore keeps the two API catalogs strictly separated
    var activeSource by rememberSaveable { mutableStateOf(ContentSource.DRAMA_COLLECTION) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedGenre by rememberSaveable(activeSource) { mutableStateOf("All") }

    val activeSourceDramas = remember(activeSource, dramas, hindiUrduDramas) {
        when (activeSource) {
            ContentSource.DRAMA_COLLECTION -> dramas.filter { it.source == ContentSource.DRAMA_COLLECTION }
            ContentSource.DRAMANITAM_DUBBED -> hindiUrduDramas.filter { it.source == ContentSource.DRAMANITAM_DUBBED }
        }
    }

    val activeSourceEpisodes = remember(activeSource, latestEpisodes, hindiUrduLatestEpisodes) {
        when (activeSource) {
            ContentSource.DRAMA_COLLECTION -> latestEpisodes.filter { it.source == ContentSource.DRAMA_COLLECTION }
            ContentSource.DRAMANITAM_DUBBED -> hindiUrduLatestEpisodes.filter { it.source == ContentSource.DRAMANITAM_DUBBED }
        }
    }

    val allGenres = remember(activeSource, activeSourceDramas, hindiUrduCategories) {
        if (activeSource == ContentSource.DRAMANITAM_DUBBED) {
            (listOf("All") + hindiUrduCategories.ifEmpty {
                activeSourceDramas.map { it.sourceCategoryName }.filter { it.isNotBlank() }.distinct()
            }).distinct()
        } else {
            val dynamic = activeSourceDramas.flatMap { it.genreTags }
                .filter { it.isNotBlank() && !it.contains("Seasons", ignoreCase = true) }
                .distinct()
            (listOf("All", "Multi-Season") + dynamic).distinct()
        }
    }

    val filteredDramas = remember(activeSourceDramas, searchQuery, selectedGenre, activeSource) {
        activeSourceDramas.filter { drama ->
            val matchesQuery = searchQuery.isBlank() ||
                drama.title.contains(searchQuery, ignoreCase = true) ||
                drama.originalTitle.contains(searchQuery, ignoreCase = true) ||
                drama.description.contains(searchQuery, ignoreCase = true)

            val matchesGenre = when {
                selectedGenre == "All" -> true
                selectedGenre == "Multi-Season" -> drama.totalSeasonsCount > 1
                activeSource == ContentSource.DRAMANITAM_DUBBED ->
                    drama.sourceCategoryName.equals(selectedGenre, ignoreCase = true) ||
                        drama.genreTags.any { it.contains(selectedGenre, ignoreCase = true) }
                else ->
                    drama.genreTags.any { it.equals(selectedGenre, ignoreCase = true) }
            }
            matchesQuery && matchesGenre
        }
    }

    val matchingEpisodes = remember(activeSourceEpisodes, searchQuery) {
        if (searchQuery.length < 2) emptyList()
        else activeSourceEpisodes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.dramaTitle.contains(searchQuery, ignoreCase = true) ||
                "episode ${it.episodeNumber}".contains(searchQuery, ignoreCase = true)
        }.take(6)
    }

    val favoriteIds = remember(favorites) { favorites.map { it.dramaId }.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
            .testTag("explore_screen")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Explore & Categories",
            style = MaterialTheme.typography.headlineMedium,
            color = PrimaryWhiteText,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Switch between Drama Collection and Hindi & Urdu Dubbed catalogs",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryMutedText
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Strict Source Selector Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ContentSource.entries.forEach { sourceItem ->
                val isSelected = activeSource == sourceItem
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (sourceItem == ContentSource.DRAMANITAM_DUBBED) RoyalVioletAccent else OttomanGoldAccent
                    } else {
                        MidnightCardSurface
                    },
                    label = "sourceTabBg"
                )
                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            activeSource = sourceItem
                            selectedGenre = "All"
                        }
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color.Transparent else MidnightBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .testTag("explore_source_tab_${sourceItem.sourceId}")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = sourceItem.displayTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) MidnightBg else PrimaryWhiteText,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (sourceItem == ContentSource.DRAMA_COLLECTION) {
                                "Urdu Subtitles"
                            } else {
                                "Hindi/Urdu Dubbed"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MidnightBg.copy(alpha = 0.8f) else SecondaryMutedText
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "Search in ${activeSource.displayTitle}...",
                    color = SecondaryMutedText,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = OttomanGoldAccent
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = SecondaryMutedText
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = RoyalVioletAccent,
                unfocusedBorderColor = MidnightBorder,
                focusedContainerColor = MidnightCardSurface,
                unfocusedContainerColor = MidnightCardSurface,
                focusedTextColor = PrimaryWhiteText,
                unfocusedTextColor = PrimaryWhiteText
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("explore_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            allGenres.forEach { genre ->
                val selected = selectedGenre == genre
                FilterChip(
                    selected = selected,
                    onClick = { selectedGenre = genre },
                    label = {
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RoyalVioletAccent,
                        selectedLabelColor = MidnightBg,
                        containerColor = MidnightCardSurface,
                        labelColor = PrimaryWhiteText
                    )
                )
            }
        }

        if (matchingEpisodes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Matching Episodes (${matchingEpisodes.size})",
                style = MaterialTheme.typography.labelLarge,
                color = OttomanGoldAccent
            )
            Spacer(modifier = Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(matchingEpisodes, key = { it.id }) { ep ->
                    LatestEpisodeHorizontalCard(
                        episode = ep,
                        onClick = { onPlayEpisode(ep) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag("explore_drama_grid")
        ) {
            items(filteredDramas, key = { it.id }) { drama ->
                ExploreDramaGridCard(
                    drama = drama,
                    isFavorite = favoriteIds.contains(drama.id),
                    onClick = { onSelectDrama(drama) },
                    onToggleFavorite = { onToggleFavorite(drama) }
                )
            }
        }
    }
}

@Composable
private fun ExploreDramaGridCard(
    drama: DramaSeries,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val isDubbed = drama.source == ContentSource.DRAMANITAM_DUBBED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, MidnightBorder, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.74f)
            ) {
                val imgUrl = drama.thumbnailUrl.ifBlank { drama.posterUrl }
                if (imgUrl.isNotBlank()) {
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = drama.title,
                        placeholder = painterResource(id = R.drawable.img_hero_banner),
                        error = painterResource(id = R.drawable.img_hero_banner),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner),
                        contentDescription = drama.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Surface(
                    color = MidnightBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    val label = when {
                        drama.totalSeasonsCount > 1 -> "${drama.totalSeasonsCount} Seasons"
                        isDubbed -> drama.sourceCategoryName.ifBlank { "Dubbed" }.take(14)
                        else -> "Season ${drama.seasonNumber}"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDubbed) RoyalVioletAccent else OttomanGoldAccent,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) OttomanGoldAccent else PrimaryWhiteText,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = drama.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryWhiteText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isDubbed) {
                        "${drama.episodeCount} Episodes • ${drama.audioLanguage}"
                    } else {
                        "${drama.episodeCount} Episodes • Urdu Subtitles"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryMutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
