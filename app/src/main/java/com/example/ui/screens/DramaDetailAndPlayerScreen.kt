package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.DramaSeason
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import com.example.data.model.SavedEpisodeItem
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
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay

@Composable
fun DramaDetailScreen(
    drama: DramaSeries,
    selectedSeason: Int,
    onSelectSeason: (Int) -> Unit,
    episodes: List<EpisodeItem>,
    isFavorite: Boolean,
    savedEpisodes: List<SavedEpisodeItem>,
    isLoadingEpisodes: Boolean,
    isUserVipOrAdmin: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayEpisode: (EpisodeItem) -> Unit,
    onToggleSaveEpisode: (EpisodeItem) -> Unit,
    onLoadMoreEpisodes: () -> Unit,
    onRequestVipUpgrade: () -> Unit
) {
    BackHandler(onBack = onBack)

    val seasonsList = remember(drama.seasons, drama.seasonNumber, drama.categoryId, drama.episodeCount) {
        if (drama.seasons.isNotEmpty()) {
            drama.seasons.sortedBy { it.seasonNumber }
        } else {
            listOf(
                DramaSeason(
                    seasonNumber = drama.seasonNumber,
                    categoryId = drama.categoryId,
                    categoryName = drama.title,
                    slug = drama.slug,
                    episodeCount = drama.episodeCount
                )
            )
        }
    }

    val sortedSeasonEpisodes = remember(episodes, selectedSeason) {
        episodes
            .filter { it.seasonNumber == selectedSeason || seasonsList.size == 1 }
            .distinctBy { it.episodeNumber }
            .sortedBy { it.episodeNumber }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("drama_detail_screen"),
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        // Compact Commercial OTT Hero Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(236.dp)
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
                                    MidnightBg.copy(alpha = 0.25f),
                                    MidnightBg.copy(alpha = 0.68f),
                                    MidnightBg
                                )
                            )
                        )
                )

                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(38.dp)
                        .background(MidnightBg.copy(alpha = 0.78f), CircleShape)
                        .testTag("drama_detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryWhiteText,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        drama.genreTags.take(3).forEach { tag ->
                            Surface(
                                color = MidnightSecondaryBg.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OttomanGoldAccent,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = drama.title,
                        style = MaterialTheme.typography.displayMedium,
                        color = PrimaryWhiteText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    val seasonText = if (seasonsList.size > 1) {
                        "${seasonsList.size} Seasons"
                    } else {
                        "Season ${seasonsList.first().seasonNumber}"
                    }
                    Text(
                        text = "$seasonText • ${drama.episodeCount} Total Episodes • Urdu Subtitled/Dubbed",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryMutedText
                    )
                }
            }
        }

        // Action Bar, Synopsis & Separate Season Selector
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val firstEp = sortedSeasonEpisodes.firstOrNull()
                    Button(
                        onClick = {
                            if (drama.isVipOnly && !isUserVipOrAdmin) {
                                onRequestVipUpgrade()
                            } else if (firstEp != null) {
                                onPlayEpisode(firstEp)
                            }
                        },
                        enabled = firstEp != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalVioletAccent,
                            contentColor = MidnightBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("drama_play_first_episode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (drama.isVipOnly && !isUserVipOrAdmin) {
                                "Unlock with VIP"
                            } else if (firstEp != null) {
                                "Play S$selectedSeason • Ep ${firstEp.episodeNumber}"
                            } else {
                                "Play Season $selectedSeason"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = onToggleFavorite,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("drama_detail_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) OttomanGoldAccent else PrimaryWhiteText,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFavorite) "Saved" else "Watchlist",
                            style = MaterialTheme.typography.titleSmall,
                            color = PrimaryWhiteText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = drama.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryMutedText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Separate Season Selector Section
                Text(
                    text = "Select Season",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryWhiteText
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag("season_selector_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    seasonsList.forEach { seasonObj ->
                        val isSelected = selectedSeason == seasonObj.seasonNumber
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectSeason(seasonObj.seasonNumber) },
                            label = {
                                Text(
                                    text = "Season ${seasonObj.seasonNumber} (${seasonObj.episodeCount})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OttomanGoldAccent,
                                selectedLabelColor = MidnightBg,
                                containerColor = MidnightCardSurface,
                                labelColor = PrimaryWhiteText
                            ),
                            modifier = Modifier.testTag("season_chip_${seasonObj.seasonNumber}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Season $selectedSeason Episodes",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OttomanGoldAccent
                    )
                    Text(
                        text = "${sortedSeasonEpisodes.size} Loaded",
                        style = MaterialTheme.typography.labelMedium,
                        color = SecondaryMutedText
                    )
                }
            }
        }

        if (isLoadingEpisodes && sortedSeasonEpisodes.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = RoyalVioletAccent,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Loading Season $selectedSeason episodes...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryMutedText
                        )
                    }
                }
            }
        } else if (sortedSeasonEpisodes.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No episodes found for Season $selectedSeason.",
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryWhiteText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap Load More below or select another season tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryMutedText
                        )
                    }
                }
            }
        } else {
            items(sortedSeasonEpisodes, key = { "${it.seasonNumber}_${it.episodeNumber}_${it.id}" }) { ep ->
                val isSaved = savedEpisodes.any { it.episodeId == ep.id }
                EpisodeListRow(
                    episode = ep,
                    isSaved = isSaved,
                    onPlay = {
                        if ((drama.isVipOnly || ep.isVipOnly) && !isUserVipOrAdmin) {
                            onRequestVipUpgrade()
                        } else {
                            onPlayEpisode(ep)
                        }
                    },
                    onToggleSave = { onToggleSaveEpisode(ep) }
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OutlinedButton(
                        onClick = onLoadMoreEpisodes,
                        enabled = !isLoadingEpisodes,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("load_more_episodes_button")
                    ) {
                        if (isLoadingEpisodes) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = OttomanGoldAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Loading more Season $selectedSeason episodes...",
                                style = MaterialTheme.typography.labelLarge,
                                color = PrimaryWhiteText
                            )
                        } else {
                            Text(
                                text = "Load More Season $selectedSeason Episodes",
                                style = MaterialTheme.typography.labelLarge,
                                color = OttomanGoldAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeListRow(
    episode: EpisodeItem,
    isSaved: Boolean,
    onPlay: () -> Unit,
    onToggleSave: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onPlay)
            .border(1.dp, MidnightBorder, RoundedCornerShape(12.dp))
            .testTag("episode_row_${episode.postId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Thumbnail with Duration Badge
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(66.dp)
                    .clip(RoundedCornerShape(8.dp))
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
                        .background(MidnightBg.copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = OttomanGoldAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                if (episode.durationLabel.isNotBlank()) {
                    Surface(
                        color = MidnightBg.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = episode.durationLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = PrimaryWhiteText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = RoyalVioletAccent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "S${episode.seasonNumber} • EP ${episode.episodeNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoyalVioletAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = if (episode.isPlayable) {
                            val serverCount = 1 + episode.backupEmbedUrls.size
                            if (serverCount > 1) "HD • $serverCount Servers" else "HD Stream"
                        } else {
                            "Web Stream"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (episode.isPlayable) EmeraldSuccess else OttomanGoldAccent
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = episode.cleanTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryWhiteText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (episode.publishedDate.isNotBlank()) {
                        Text(
                            text = episode.publishedDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryMutedText
                        )
                    }
                    if (episode.durationLabel.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = SecondaryMutedText,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = episode.durationLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryMutedText
                        )
                    }
                }
            }

            IconButton(
                onClick = onToggleSave,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save Episode",
                    tint = if (isSaved) OttomanGoldAccent else SecondaryMutedText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VideoPlayerScreen(
    episode: EpisodeItem,
    siblingEpisodes: List<EpisodeItem>,
    subtitleEnabledByDefault: Boolean,
    onBack: () -> Unit,
    onSelectEpisode: (EpisodeItem) -> Unit,
    onSaveProgress: (progressMs: Long, durationMs: Long, completed: Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Ensure sibling episodes are strictly from the same drama & season and sorted by episodeNumber
    val orderedSeasonEpisodes = remember(episode, siblingEpisodes) {
        val sameSeason = siblingEpisodes
            .filter {
                it.seasonNumber == episode.seasonNumber &&
                    (it.categoryId == episode.categoryId || it.dramaTitle.equals(episode.dramaTitle, ignoreCase = true))
            }
            .distinctBy { it.episodeNumber }
            .sortedBy { it.episodeNumber }
        if (sameSeason.isNotEmpty()) sameSeason else listOf(episode)
    }

    val allServers = remember(episode) {
        buildList {
            if (episode.primaryEmbedUrl.isNotBlank()) add(episode.primaryEmbedUrl)
            addAll(episode.backupEmbedUrls.filter { it.isNotBlank() })
            if (isEmpty() && episode.sourcePostUrl.isNotBlank()) add(episode.sourcePostUrl)
        }.distinct()
    }

    var selectedServerIndex by remember(episode.id) { mutableIntStateOf(0) }
    val activeEmbedUrl = allServers.getOrNull(selectedServerIndex) ?: ""

    var isFullScreenLandscape by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var subtitlesVisible by remember { mutableStateOf(subtitleEnabledByDefault) }
    var isLoadingWebView by remember { mutableStateOf(true) }
    var playerError by remember { mutableStateOf<String?>(null) }
    var simulatedPositionSec by remember(episode.id) { mutableFloatStateOf(0f) }
    val estimatedDurationSec = 2700f
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val currentIndex = remember(episode.id, episode.episodeNumber, orderedSeasonEpisodes) {
        val byId = orderedSeasonEpisodes.indexOfFirst { it.id == episode.id }
        if (byId >= 0) byId
        else orderedSeasonEpisodes.indexOfFirst { it.episodeNumber == episode.episodeNumber }
    }
    val previousEpisode = if (currentIndex > 0) orderedSeasonEpisodes.getOrNull(currentIndex - 1) else null
    val nextEpisode = if (currentIndex >= 0 && currentIndex < orderedSeasonEpisodes.lastIndex) {
        orderedSeasonEpisodes.getOrNull(currentIndex + 1)
    } else null

    // Periodic progress tracking & auto-save to Firestore watch_history
    LaunchedEffect(episode.id, isPlaying) {
        while (true) {
            delay(5000L)
            if (isPlaying && !isLoadingWebView && playerError == null) {
                simulatedPositionSec = (simulatedPositionSec + 5f).coerceAtMost(estimatedDurationSec)
                onSaveProgress(
                    (simulatedPositionSec * 1000).toLong(),
                    (estimatedDurationSec * 1000).toLong(),
                    simulatedPositionSec >= estimatedDurationSec * 0.92f
                )
            }
        }
    }

    DisposableEffect(episode.id) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            onSaveProgress(
                (simulatedPositionSec * 1000).toLong(),
                (estimatedDurationSec * 1000).toLong(),
                simulatedPositionSec >= estimatedDurationSec * 0.92f
            )
        }
    }

    BackHandler {
        if (isFullScreenLandscape) {
            isFullScreenLandscape = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("video_player_screen")
    ) {
        // Top Player Header (Hidden in Fullscreen Landscape)
        if (!isFullScreenLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("player_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryWhiteText,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.cleanTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryWhiteText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${episode.dramaTitle} • Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OttomanGoldAccent
                    )
                }
                IconButton(
                    onClick = {
                        isFullScreenLandscape = !isFullScreenLandscape
                        activity?.requestedOrientation = if (isFullScreenLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("player_orientation_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = "Toggle Landscape",
                        tint = OttomanGoldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Video Embed Container (Evaluates Vidmoly / OK.ru iframe inside hardened WebView)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFullScreenLandscape) 330.dp else 230.dp)
                .background(Color.Black)
        ) {
            if (activeEmbedUrl.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = CrimsonAlert,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "No authorized video embed URL detected for this episode.",
                        style = MaterialTheme.typography.titleSmall,
                        color = PrimaryWhiteText
                    )
                }
            } else {
                val htmlWrapper = remember(activeEmbedUrl) {
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <style>
                            * { margin: 0; padding: 0; box-sizing: border-box; background: #000; }
                            html, body { width: 100%; height: 100%; overflow: hidden; background: #000; }
                            iframe { width: 100%; height: 100%; border: 0; display: block; }
                        </style>
                    </head>
                    <body>
                        <iframe id="ottFrame" src="$activeEmbedUrl" allowfullscreen="true" webkitallowfullscreen="true" mozallowfullscreen="true" allow="autoplay; fullscreen; encrypted-media"></iframe>
                    </body>
                    </html>
                    """.trimIndent()
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.BLACK)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    super.onShowCustomView(view, callback)
                                    isFullScreenLandscape = true
                                    activity?.requestedOrientation =
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                }

                                override fun onHideCustomView() {
                                    super.onHideCustomView()
                                    isFullScreenLandscape = false
                                    activity?.requestedOrientation =
                                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                private val blockedDomains = listOf(
                                    "bicea.org", "doubleclick.net", "googlesyndication.com",
                                    "adsterra", "popads", "onclickads"
                                )

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val url = request?.url?.toString()?.lowercase().orEmpty()
                                    if (blockedDomains.any { url.contains(it) }) {
                                        return WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val targetHost = request?.url?.host?.lowercase().orEmpty()
                                    val isAllowedPlayer = targetHost.contains("vidmoly") ||
                                        targetHost.contains("ok.ru") ||
                                        targetHost.contains("giveme5.co") ||
                                        targetHost.contains("odnoklassniki") ||
                                        targetHost.contains("vk.com") ||
                                        targetHost.contains("dailymotion")
                                    return !isAllowedPlayer
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoadingWebView = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request?.isForMainFrame == true) {
                                        isLoadingWebView = false
                                        playerError = "Stream server unreachable. Try switching to Backup Server."
                                    }
                                }
                            }
                            webViewRef = this
                            loadDataWithBaseURL(
                                "https://giveme5.co/",
                                htmlWrapper,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    },
                    update = { view ->
                        webViewRef = view
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (isLoadingWebView) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MidnightBg.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = OttomanGoldAccent,
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Loading Authorized HD Stream...",
                            style = MaterialTheme.typography.labelLarge,
                            color = PrimaryWhiteText
                        )
                    }
                }
            }

            if (playerError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MidnightBg.copy(alpha = 0.92f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = CrimsonAlert,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = playerError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryWhiteText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                playerError = null
                                isLoadingWebView = true
                                if (allServers.size > 1) {
                                    selectedServerIndex = (selectedServerIndex + 1) % allServers.size
                                } else {
                                    webViewRef?.reload()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoyalVioletAccent)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry / Switch Stream Server")
                        }
                    }
                }
            }

            // Subtitle Status Pill Overlay
            if (subtitlesVisible && !isLoadingWebView) {
                Surface(
                    color = MidnightBg.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Urdu Subtitles & Audio Stream Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = OttomanGoldAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Custom Transport & Episode Controls Bar
        Surface(
            color = MidnightCardSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                // Progress Scrubber
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatDurationSeconds(simulatedPositionSec.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = OttomanGoldAccent
                    )
                    Slider(
                        value = simulatedPositionSec,
                        onValueChange = { newSec ->
                            simulatedPositionSec = newSec
                            webViewRef?.evaluateJavascript(
                                "try { var v = document.querySelector('video'); if(v) v.currentTime = ${newSec.toInt()}; } catch(e){}",
                                null
                            )
                        },
                        valueRange = 0f..estimatedDurationSec,
                        colors = SliderDefaults.colors(
                            thumbColor = OttomanGoldAccent,
                            activeTrackColor = RoyalVioletAccent,
                            inactiveTrackColor = MidnightSecondaryBg
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    )
                    Text(
                        text = formatDurationSeconds(estimatedDurationSec.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryMutedText
                    )
                }

                // Transport Buttons: Prev Ep | -10s | Play/Pause | +10s | Next Ep | Mute | Subtitles | Fullscreen
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { previousEpisode?.let(onSelectEpisode) },
                        enabled = previousEpisode != null,
                        modifier = Modifier.testTag("player_prev_episode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous Episode",
                            tint = if (previousEpisode != null) PrimaryWhiteText else SecondaryMutedText.copy(alpha = 0.35f)
                        )
                    }

                    IconButton(
                        onClick = {
                            simulatedPositionSec = (simulatedPositionSec - 10f).coerceAtLeast(0f)
                            webViewRef?.evaluateJavascript(
                                "try { var v = document.querySelector('video'); if(v) v.currentTime -= 10; } catch(e){}",
                                null
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = PrimaryWhiteText
                        )
                    }

                    Surface(
                        color = RoyalVioletAccent,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                isPlaying = !isPlaying
                                webViewRef?.evaluateJavascript(
                                    "try { var v = document.querySelector('video'); if(v) { if(v.paused) v.play(); else v.pause(); } } catch(e){}",
                                    null
                                )
                            }
                            .testTag("player_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = MidnightBg
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            simulatedPositionSec = (simulatedPositionSec + 10f).coerceAtMost(estimatedDurationSec)
                            webViewRef?.evaluateJavascript(
                                "try { var v = document.querySelector('video'); if(v) v.currentTime += 10; } catch(e){}",
                                null
                            )
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = "Forward 10s",
                            tint = PrimaryWhiteText
                        )
                    }

                    IconButton(
                        onClick = { nextEpisode?.let(onSelectEpisode) },
                        enabled = nextEpisode != null,
                        modifier = Modifier.testTag("player_next_episode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next Episode",
                            tint = if (nextEpisode != null) PrimaryWhiteText else SecondaryMutedText.copy(alpha = 0.35f)
                        )
                    }

                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            webViewRef?.evaluateJavascript(
                                "try { var v = document.querySelector('video'); if(v) v.muted = $isMuted; } catch(e){}",
                                null
                            )
                        }
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Toggle Volume",
                            tint = if (isMuted) CrimsonAlert else PrimaryWhiteText
                        )
                    }

                    IconButton(onClick = { subtitlesVisible = !subtitlesVisible }) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = "Toggle Subtitles",
                            tint = if (subtitlesVisible) OttomanGoldAccent else SecondaryMutedText
                        )
                    }

                    IconButton(
                        onClick = {
                            isFullScreenLandscape = !isFullScreenLandscape
                            activity?.requestedOrientation = if (isFullScreenLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isFullScreenLandscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = OttomanGoldAccent
                        )
                    }
                }

                // Quick Previous / Next Episode Navigation Bar
                if (previousEpisode != null || nextEpisode != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { previousEpisode?.let(onSelectEpisode) },
                            enabled = previousEpisode != null,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (previousEpisode != null) {
                                    "Prev: S${previousEpisode.seasonNumber} Ep ${previousEpisode.episodeNumber}"
                                } else {
                                    "First Episode"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { nextEpisode?.let(onSelectEpisode) },
                            enabled = nextEpisode != null,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OttomanGoldAccent,
                                contentColor = MidnightBg
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                        ) {
                            Text(
                                text = if (nextEpisode != null) {
                                    "Next: S${nextEpisode.seasonNumber} Ep ${nextEpisode.episodeNumber}"
                                } else {
                                    "Latest Episode"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // Server Switcher & Season Episode Selector List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            item {
                // Server Switcher (Vidmoly / OK.ru)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = OttomanGoldAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Mirrors:",
                        style = MaterialTheme.typography.titleSmall,
                        color = PrimaryWhiteText
                    )
                    allServers.forEachIndexed { idx, url ->
                        val label = when {
                            url.contains("vidmoly", ignoreCase = true) -> "Server ${idx + 1} (Vidmoly HD)"
                            url.contains("ok.ru", ignoreCase = true) -> "Server ${idx + 1} (OK.ru)"
                            else -> "Server ${idx + 1}"
                        }
                        FilterChip(
                            selected = selectedServerIndex == idx,
                            onClick = {
                                selectedServerIndex = idx
                                playerError = null
                                isLoadingWebView = true
                                webViewRef?.loadUrl(url)
                            },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RoyalVioletAccent,
                                selectedLabelColor = MidnightBg
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = episode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryMutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${episode.dramaTitle} • Season ${episode.seasonNumber} Episodes (${orderedSeasonEpisodes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = OttomanGoldAccent
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            items(orderedSeasonEpisodes, key = { "${it.seasonNumber}_${it.episodeNumber}_${it.id}" }) { ep ->
                val isCurrent = ep.id == episode.id || ep.episodeNumber == episode.episodeNumber
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onSelectEpisode(ep) }
                        .border(
                            width = 1.dp,
                            color = if (isCurrent) OttomanGoldAccent else MidnightBorder,
                            shape = RoundedCornerShape(10.dp)
                        ),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) MidnightSecondaryBg else MidnightCardSurface
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isCurrent) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isCurrent) OttomanGoldAccent else RoyalVioletAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "S${ep.seasonNumber} • Episode ${ep.episodeNumber} — ${ep.cleanTitle}",
                                style = MaterialTheme.typography.titleSmall,
                                color = PrimaryWhiteText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isCurrent) {
                                    "Now Playing • ${ep.durationLabel}"
                                } else {
                                    listOf(ep.publishedDate, ep.durationLabel)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" • ")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCurrent) OttomanGoldAccent else SecondaryMutedText
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationSeconds(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return "%02d:%02d".format(mins, secs)
}
