package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import com.example.data.model.FavoriteItem
import com.example.data.model.UserProfile
import com.example.data.model.WatchHistoryItem
import com.example.ui.theme.MidnightBg
import com.example.ui.theme.MidnightBorder
import com.example.ui.theme.MidnightCardSurface
import com.example.ui.theme.MidnightElevatedCard
import com.example.ui.theme.MidnightSecondaryBg
import com.example.ui.theme.OttomanGoldAccent
import com.example.ui.theme.PrimaryWhiteText
import com.example.ui.theme.RoyalVioletAccent
import com.example.ui.theme.SecondaryMutedText

@Composable
fun HomeScreen(
    dramas: List<DramaSeries>,
    latestEpisodes: List<EpisodeItem>,
    watchHistory: List<WatchHistoryItem>,
    favorites: List<FavoriteItem>,
    userProfile: UserProfile?,
    appConfig: AppConfig,
    isAdmin: Boolean,
    isLoading: Boolean,
    onSelectDrama: (DramaSeries) -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onToggleFavorite: (DramaSeries) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenWatchHistory: () -> Unit,
    onOpenMembership: () -> Unit,
    onOpenAdminConsole: () -> Unit,
    onRefresh: () -> Unit
) {
    val featuredDramas = remember(dramas) {
        val explicit = dramas.filter { it.isFeatured }
        if (explicit.isNotEmpty()) explicit.take(6) else dramas.take(5)
    }
    val trendingDramas = remember(dramas) { dramas.take(10) }
    val popularDramas = remember(dramas) { dramas.sortedByDescending { it.episodeCount }.take(10) }
    val recentlyAddedDramas = remember(dramas) { dramas.reversed().take(10) }
    val recommendedDramas = remember(dramas) { dramas.shuffled().take(10) }

    var heroIndex by remember { mutableIntStateOf(0) }
    val activeHeroDrama = featuredDramas.getOrNull(heroIndex % maxOf(1, featuredDramas.size))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Top App Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.img_launcher_foreground),
                        contentDescription = "Dastan TV",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, OttomanGoldAccent, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DASTAN TV",
                                style = MaterialTheme.typography.titleLarge,
                                color = OttomanGoldAccent
                            )
                            if (userProfile?.membershipPlan == "YEARLY_VIP") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = OttomanGoldAccent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "VIP",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OttomanGoldAccent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Urdu Dubbed & Subtitled Cinema",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryMutedText
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Admin Console Button: Strictly visible ONLY for verified Admin
                    if (isAdmin) {
                        Surface(
                            color = OttomanGoldAccent,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clickable(onClick = onOpenAdminConsole)
                                .testTag("admin_console_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = "Admin Console",
                                    tint = MidnightBg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Admin",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MidnightBg,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
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
                            contentDescription = "Refresh Catalog",
                            tint = SecondaryMutedText
                        )
                    }
                }
            }
        }

        // Announcement Banner from AppConfig
        if (appConfig.announcementBanner.isNotBlank()) {
            item {
                Surface(
                    color = MidnightSecondaryBg,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .border(1.dp, RoyalVioletAccent.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .clickable { onOpenMembership() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = OttomanGoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = appConfig.announcementBanner,
                            style = MaterialTheme.typography.labelLarge,
                            color = PrimaryWhiteText,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 1. Featured Turkish Dramas Hero Spotlight
        item {
            if (activeHeroDrama != null) {
                val isFav = favorites.any { it.dramaId == activeHeroDrama.id }
                HeroFeaturedCard(
                    drama = activeHeroDrama,
                    isFavorite = isFav,
                    totalFeatured = featuredDramas.size,
                    activeIndex = heroIndex % maxOf(1, featuredDramas.size),
                    onSelectIndex = { heroIndex = it },
                    onWatchClick = { onSelectDrama(activeHeroDrama) },
                    onToggleFavorite = { onToggleFavorite(activeHeroDrama) }
                )
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = RoyalVioletAccent)
                }
            }
        }

        // 2. Continue Watching Section (when user has watch history)
        if (watchHistory.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Continue Watching",
                    subtitle = "Pick up your episode right where you paused",
                    actionLabel = "History",
                    onActionClick = onOpenWatchHistory
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(watchHistory.take(10), key = { it.id.ifBlank { it.episodeId } }) { historyItem ->
                        ContinueWatchingCard(
                            item = historyItem,
                            onClick = {
                                val matchedEp = latestEpisodes.firstOrNull {
                                    it.id == historyItem.episodeId || "ep_${it.postId}" == historyItem.episodeId
                                } ?: EpisodeItem(
                                    id = historyItem.episodeId,
                                    postId = historyItem.episodeId.removePrefix("ep_").toIntOrNull() ?: 0,
                                    categoryId = historyItem.categoryId.toInt(),
                                    dramaTitle = historyItem.dramaTitle,
                                    title = historyItem.episodeTitle,
                                    episodeNumber = 1,
                                    seasonNumber = 1,
                                    description = "Resume ${historyItem.episodeTitle}",
                                    publishedDate = "",
                                    posterUrl = historyItem.posterUrl,
                                    primaryEmbedUrl = historyItem.embedUrl,
                                    backupEmbedUrls = emptyList(),
                                    sourcePostUrl = "",
                                    isPlayable = historyItem.embedUrl.isNotBlank()
                                )
                                onPlayEpisode(matchedEp)
                            }
                        )
                    }
                }
            }
        }

        // 3. Latest Episodes (Direct from WordPress API)
        if (latestEpisodes.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Latest Episodes",
                    subtitle = "Newly released episodes with Urdu Subtitles & Dubbing"
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(latestEpisodes.take(14), key = { it.id }) { ep ->
                        EpisodeHorizontalCard(
                            episode = ep,
                            onClick = { onPlayEpisode(ep) }
                        )
                    }
                }
            }
        }

        // 4. Trending Now
        if (trendingDramas.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Trending Now",
                    subtitle = "Most streamed Turkish dramas this week"
                )
                DramaRowList(
                    dramas = trendingDramas,
                    favorites = favorites,
                    onSelectDrama = onSelectDrama,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }

        // 5. Popular Dramas
        if (popularDramas.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Popular Dramas",
                    subtitle = "Complete multi-episode Ottoman & Seljuk sagas"
                )
                DramaRowList(
                    dramas = popularDramas,
                    favorites = favorites,
                    onSelectDrama = onSelectDrama,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }

        // 6. Recently Added
        if (recentlyAddedDramas.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently Added",
                    subtitle = "Freshly cataloged seasons and historical specials"
                )
                DramaRowList(
                    dramas = recentlyAddedDramas,
                    favorites = favorites,
                    onSelectDrama = onSelectDrama,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }

        // 7. Recommended for You
        if (recommendedDramas.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recommended for You",
                    subtitle = "Curated for fans of epic storytelling & Urdu dubbing"
                )
                DramaRowList(
                    dramas = recommendedDramas,
                    favorites = favorites,
                    onSelectDrama = onSelectDrama,
                    onToggleFavorite = onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun HeroFeaturedCard(
    drama: DramaSeries,
    isFavorite: Boolean,
    totalFeatured: Int,
    activeIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onWatchClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(310.dp)
            .border(1.dp, OttomanGoldAccent.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .clickable(onClick = onWatchClick)
            .testTag("hero_featured_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (drama.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = drama.posterUrl,
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
                                MidnightBg.copy(alpha = 0.25f),
                                MidnightBg.copy(alpha = 0.65f),
                                MidnightBg.copy(alpha = 0.96f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = OttomanGoldAccent,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "FEATURED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MidnightBg,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Surface(
                        color = RoyalVioletAccent.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${drama.episodeCount} EPISODES",
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryWhiteText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (drama.isVipOnly) {
                        Surface(
                            color = OttomanGoldAccent.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "YEARLY VIP",
                                style = MaterialTheme.typography.labelSmall,
                                color = OttomanGoldAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = drama.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = PrimaryWhiteText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = drama.genreTags.joinToString(" • "),
                    style = MaterialTheme.typography.labelLarge,
                    color = OttomanGoldAccent
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onWatchClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalVioletAccent,
                            contentColor = MidnightBg
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("hero_watch_now_button")
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Watch Now",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = onToggleFavorite,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("hero_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) OttomanGoldAccent else PrimaryWhiteText
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFavorite) "Saved" else "My List",
                            color = PrimaryWhiteText
                        )
                    }
                }

                if (totalFeatured > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        for (idx in 0 until totalFeatured) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .height(6.dp)
                                    .width(if (idx == activeIndex) 22.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (idx == activeIndex) OttomanGoldAccent
                                        else SecondaryMutedText.copy(alpha = 0.4f)
                                    )
                                    .clickable { onSelectIndex(idx) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = PrimaryWhiteText
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryMutedText
            )
        }
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = OttomanGoldAccent,
                modifier = Modifier
                    .clickable(onClick = onActionClick)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun DramaRowList(
    dramas: List<DramaSeries>,
    favorites: List<FavoriteItem>,
    onSelectDrama: (DramaSeries) -> Unit,
    onToggleFavorite: (DramaSeries) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(dramas, key = { it.id }) { drama ->
            val isFav = favorites.any { it.dramaId == drama.id }
            DramaPosterCard(
                drama = drama,
                isFavorite = isFav,
                onClick = { onSelectDrama(drama) },
                onToggleFavorite = { onToggleFavorite(drama) }
            )
        }
    }
}

@Composable
fun DramaPosterCard(
    drama: DramaSeries,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(156.dp)
            .clickable(onClick = onClick)
            .border(1.dp, MidnightBorder, RoundedCornerShape(16.dp))
            .testTag("drama_card_${drama.categoryId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                if (drama.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = drama.posterUrl,
                        contentDescription = drama.title,
                        placeholder = painterResource(id = R.drawable.img_onboarding_cinema),
                        error = painterResource(id = R.drawable.img_onboarding_cinema),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_onboarding_cinema),
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
                                    MidnightBg.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                Surface(
                    color = MidnightSecondaryBg.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "${drama.episodeCount} Eps",
                        style = MaterialTheme.typography.labelSmall,
                        color = OttomanGoldAccent,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (isFavorite) OttomanGoldAccent else PrimaryWhiteText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (drama.isVipOnly) {
                    Surface(
                        color = OttomanGoldAccent,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "VIP",
                            style = MaterialTheme.typography.labelSmall,
                            color = MidnightBg,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Season ${drama.seasonNumber} • Urdu",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryMutedText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EpisodeHorizontalCard(
    episode: EpisodeItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(240.dp)
            .clickable(onClick = onClick)
            .border(1.dp, MidnightBorder, RoundedCornerShape(16.dp))
            .testTag("latest_episode_card_${episode.postId}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(135.dp)
            ) {
                if (episode.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = episode.posterUrl,
                        contentDescription = episode.title,
                        placeholder = painterResource(id = R.drawable.img_hero_banner),
                        error = painterResource(id = R.drawable.img_hero_banner),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_banner),
                        contentDescription = episode.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MidnightBg.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = RoyalVioletAccent.copy(alpha = 0.9f),
                        shape = CircleShape,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Episode",
                                tint = MidnightBg
                            )
                        }
                    }
                }

                Surface(
                    color = MidnightBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "EP ${episode.episodeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OttomanGoldAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryWhiteText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.publishedDate.ifBlank { "Urdu Subtitled HD" },
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryMutedText
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
    val progressFraction = remember(item.progressMs, item.durationMs) {
        if (item.durationMs <= 0L) 0.25f
        else (item.progressMs.toFloat() / item.durationMs.toFloat()).coerceIn(0.05f, 1f)
    }

    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick)
            .border(1.dp, RoyalVioletAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
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
                        .background(MidnightBg.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Resume",
                        tint = OttomanGoldAccent,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = RoyalVioletAccent,
                trackColor = MidnightSecondaryBg
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.episodeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryWhiteText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${(progressFraction * 100).toInt()}% watched • Resume",
                    style = MaterialTheme.typography.labelSmall,
                    color = OttomanGoldAccent
                )
            }
        }
    }
}

@Composable
fun ExploreAndCategoriesScreen(
    dramas: List<DramaSeries>,
    latestEpisodes: List<EpisodeItem>,
    favorites: List<FavoriteItem>,
    onSelectDrama: (DramaSeries) -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onToggleFavorite: (DramaSeries) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("All") }
    val genres = remember {
        listOf("All", "Ottoman Empire", "Naval Action", "Epic Warfare", "Turkish Drama")
    }

    val filteredDramas = remember(dramas, searchQuery, selectedGenre) {
        dramas.filter { drama ->
            val matchesQuery = searchQuery.isBlank() ||
                drama.title.contains(searchQuery, ignoreCase = true) ||
                drama.description.contains(searchQuery, ignoreCase = true)
            val matchesGenre = selectedGenre == "All" ||
                drama.genreTags.any { it.equals(selectedGenre, ignoreCase = true) }
            matchesQuery && matchesGenre
        }
    }

    val matchingEpisodes = remember(latestEpisodes, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else latestEpisodes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.dramaTitle.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .padding(horizontal = 16.dp)
            .testTag("explore_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Explore & Categories",
            style = MaterialTheme.typography.headlineLarge,
            color = OttomanGoldAccent
        )
        Text(
            text = "Search across ${dramas.size} Turkish drama categories and Urdu-subtitled episodes",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryMutedText
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Sultan Fateh, Osman, Barbaroslar, Episode #...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = RoyalVioletAccent
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
            shape = RoundedCornerShape(16.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            genres.forEach { genre ->
                val selected = selectedGenre == genre
                FilterChip(
                    selected = selected,
                    onClick = { selectedGenre = genre },
                    label = { Text(genre) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = RoyalVioletAccent,
                        selectedLabelColor = MidnightBg,
                        containerColor = MidnightCardSurface,
                        labelColor = PrimaryWhiteText
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (matchingEpisodes.isNotEmpty()) {
            Text(
                text = "Matching Episodes (${matchingEpisodes.size})",
                style = MaterialTheme.typography.titleMedium,
                color = OttomanGoldAccent,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                items(matchingEpisodes, key = { it.id }) { ep ->
                    EpisodeHorizontalCard(
                        episode = ep,
                        onClick = { onPlayEpisode(ep) }
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 152.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredDramas, key = { it.id }) { drama ->
                val isFav = favorites.any { it.dramaId == drama.id }
                DramaPosterCard(
                    drama = drama,
                    isFavorite = isFav,
                    onClick = { onSelectDrama(drama) },
                    onToggleFavorite = { onToggleFavorite(drama) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
