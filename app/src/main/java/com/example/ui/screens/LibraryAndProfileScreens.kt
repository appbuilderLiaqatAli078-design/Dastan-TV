package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AppConfig
import com.example.data.model.CacheStats
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import com.example.data.model.FavoriteItem
import com.example.data.model.SavedEpisodeItem
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
fun FavoritesScreen(
    favorites: List<FavoriteItem>,
    dramas: List<DramaSeries>,
    onSelectDrama: (DramaSeries) -> Unit,
    onRemoveFavorite: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .padding(horizontal = 16.dp)
            .testTag("favorites_screen")
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "My Favorites",
            style = MaterialTheme.typography.headlineLarge,
            color = OttomanGoldAccent
        )
        Text(
            text = "Cloud-synced Turkish dramas saved to your personal watchlist",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryMutedText
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (favorites.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp)
                    .border(1.dp, MidnightBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = RoyalVioletAccent,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Your Watchlist is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryWhiteText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the heart icon on any Turkish drama poster to bookmark it here for instant access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryMutedText
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(favorites, key = { it.id.ifBlank { it.dramaId } }) { fav ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val matched = dramas.firstOrNull {
                                    it.id == fav.dramaId || "drama_${it.categoryId}" == fav.dramaId
                                } ?: DramaSeries(
                                    id = fav.dramaId,
                                    categoryId = fav.dramaId.removePrefix("drama_").toIntOrNull() ?: 95,
                                    title = fav.title,
                                    originalTitle = fav.title,
                                    slug = fav.dramaId,
                                    description = "Saved drama in your personal Favorites.",
                                    posterUrl = fav.posterUrl,
                                    thumbnailUrl = fav.posterUrl,
                                    episodeCount = fav.episodeCount.toInt(),
                                    seasonNumber = 1,
                                    genreTags = listOf("Historical", "Urdu Sub/Dub")
                                )
                                onSelectDrama(matched)
                            }
                            .border(1.dp, MidnightBorder, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(72.dp)
                                    .height(92.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                if (fav.posterUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = fav.posterUrl,
                                        contentDescription = fav.title,
                                        placeholder = painterResource(id = R.drawable.img_onboarding_cinema),
                                        error = painterResource(id = R.drawable.img_onboarding_cinema),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_onboarding_cinema),
                                        contentDescription = fav.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = fav.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = PrimaryWhiteText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${fav.episodeCount} Episodes • Urdu Dubbed/Subtitled",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OttomanGoldAccent
                                )
                            }
                            IconButton(
                                onClick = { onRemoveFavorite(fav.dramaId) },
                                modifier = Modifier.testTag("remove_favorite_${fav.dramaId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Remove Favorite",
                                    tint = CrimsonAlert
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadsVaultScreen(
    savedEpisodes: List<SavedEpisodeItem>,
    onPlaySavedEpisode: (EpisodeItem) -> Unit,
    onRemoveSavedEpisode: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .padding(horizontal = 16.dp)
            .testTag("downloads_screen")
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Saved Episodes Vault",
            style = MaterialTheme.typography.headlineLarge,
            color = OttomanGoldAccent
        )
        Text(
            text = "Offline episode synopses & instant authorized stream launcher",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryMutedText
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = MidnightSecondaryBg,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = OttomanGoldAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Compliance Note: Third-party video streams are never downloaded or rehosted without authorization. Saved episodes cache synopses offline and launch the authorized stream directly.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryMutedText
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (savedEpisodes.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .border(1.dp, MidnightBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = null,
                        tint = RoyalVioletAccent,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Saved Episodes Yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryWhiteText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap the bookmark icon on any episode in Drama Details to save its synopsis and direct stream shortcut here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryMutedText
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(savedEpisodes, key = { it.id.ifBlank { it.episodeId } }) { saved ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MidnightBorder, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = saved.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = PrimaryWhiteText
                                    )
                                    Text(
                                        text = saved.dramaName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = OttomanGoldAccent
                                    )
                                }
                                IconButton(onClick = { onRemoveSavedEpisode(saved.episodeId) }) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkRemove,
                                        contentDescription = "Remove Saved Episode",
                                        tint = CrimsonAlert
                                    )
                                }
                            }
                            if (saved.synopsis.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = saved.synopsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SecondaryMutedText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    onPlaySavedEpisode(
                                        EpisodeItem(
                                            id = saved.episodeId,
                                            postId = saved.episodeId.removePrefix("ep_").toIntOrNull() ?: 0,
                                            categoryId = saved.categoryId.toInt(),
                                            dramaTitle = saved.dramaName.substringBefore(" (").trim(),
                                            title = saved.title,
                                            episodeNumber = 1,
                                            seasonNumber = 1,
                                            description = saved.synopsis,
                                            publishedDate = "",
                                            posterUrl = saved.posterUrl,
                                            thumbnailUrl = saved.posterUrl,
                                            primaryEmbedUrl = saved.embedUrl,
                                            backupEmbedUrls = emptyList(),
                                            sourcePostUrl = "",
                                            isPlayable = saved.embedUrl.isNotBlank()
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = RoyalVioletAccent,
                                    contentColor = MidnightBg
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Launch Authorized Stream",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchHistoryScreen(
    watchHistory: List<WatchHistoryItem>,
    onBack: () -> Unit,
    onResumeEpisode: (WatchHistoryItem) -> Unit,
    onRemoveHistoryItem: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
            .testTag("watch_history_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = PrimaryWhiteText
                )
            }
            Column {
                Text(
                    text = "Watch History & Continue Watching",
                    style = MaterialTheme.typography.titleLarge,
                    color = OttomanGoldAccent
                )
                Text(
                    text = "${watchHistory.size} episodes tracked with Season & Episode position",
                    style = MaterialTheme.typography.labelSmall,
                    color = SecondaryMutedText
                )
            }
        }

        if (watchHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No playback history yet. Start watching any episode!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryMutedText
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(watchHistory, key = { it.id.ifBlank { it.episodeId } }) { item ->
                    val progress = if (item.durationMs > 0) {
                        (item.progressMs.toFloat() / item.durationMs.toFloat()).coerceIn(0.05f, 1f)
                    } else 0.2f

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onResumeEpisode(item) }
                            .border(1.dp, MidnightBorder, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.cleanDramaTitle} • Season ${item.parsedSeasonNumber} • Episode ${item.parsedEpisodeNumber}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = OttomanGoldAccent
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = item.episodeTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = PrimaryWhiteText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onRemoveHistoryItem(item.episodeId) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Remove from History",
                                        tint = CrimsonAlert
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = RoyalVioletAccent,
                                trackColor = MidnightSecondaryBg
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileAndSettingsScreen(
    userProfile: UserProfile?,
    appConfig: AppConfig,
    cacheStats: CacheStats = CacheStats(),
    isAdmin: Boolean,
    flagSecureEnabled: Boolean,
    watchHistoryCount: Int,
    favoritesCount: Int,
    onOpenWatchHistory: () -> Unit,
    onOpenMembershipPlans: () -> Unit,
    onOpenAdminConsole: () -> Unit,
    onSaveSettings: (displayName: String, quality: String, subtitles: Boolean, flagSecure: Boolean) -> Unit,
    onClearCache: () -> Unit = {},
    onSignOut: () -> Unit
) {
    var displayName by remember(userProfile?.displayName) {
        mutableStateOf(userProfile?.displayName ?: "Dastan Viewer")
    }
    var preferredQuality by remember(userProfile?.preferredQuality) {
        mutableStateOf(userProfile?.preferredQuality ?: "AUTO")
    }
    var subtitleEnabled by remember(userProfile?.subtitleEnabled) {
        mutableStateOf(userProfile?.subtitleEnabled ?: true)
    }
    var localFlagSecure by remember(flagSecureEnabled) {
        mutableStateOf(flagSecureEnabled)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .padding(horizontal = 16.dp)
            .testTag("profile_screen"),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Profile & App Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = OttomanGoldAccent
            )
            Text(
                text = "Manage your account, local cache, and playback preferences",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryMutedText
            )
        }

        // User Identity Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, RoyalVioletAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MidnightSecondaryBg,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .border(1.5.dp, OttomanGoldAccent, CircleShape)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (userProfile?.displayName?.firstOrNull()?.uppercase() ?: "D"),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = OttomanGoldAccent
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.displayName?.ifBlank { "Dastan Viewer" } ?: "Dastan Viewer",
                                style = MaterialTheme.typography.titleLarge,
                                color = PrimaryWhiteText
                            )
                            Text(
                                text = userProfile?.email.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = SecondaryMutedText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = if (userProfile?.membershipPlan == "YEARLY_VIP")
                                        OttomanGoldAccent else MidnightSecondaryBg,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (userProfile?.membershipPlan == "YEARLY_VIP")
                                            "YEARLY VIP" else "FREE PLAN",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (userProfile?.membershipPlan == "YEARLY_VIP")
                                            MidnightBg else OttomanGoldAccent,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (isAdmin) {
                                    Surface(
                                        color = RoyalVioletAccent,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "AUTHORIZED ADMIN",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MidnightBg,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenWatchHistory,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("open_watch_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "History ($watchHistoryCount)",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Button(
                            onClick = onOpenMembershipPlans,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OttomanGoldAccent,
                                contentColor = MidnightBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("open_membership_plans_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Membership",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Admin Mode Workspace Entry (Strictly hidden for normal users)
                    if (isAdmin) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onOpenAdminConsole,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RoyalVioletAccent,
                                contentColor = MidnightBg
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("profile_admin_dashboard_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Launch Admin Dashboard Console",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Local SQLite & Image Cache Management Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MidnightBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = OttomanGoldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline Catalog & Image Cache",
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryWhiteText
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Dramas: ${cacheStats.cachedDramasCount} • Seasons: ${cacheStats.cachedSeasonsCount} • Episodes: ${cacheStats.cachedEpisodesCount} • Disk Size: ${cacheStats.formattedDiskSize}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryMutedText
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onClearCache,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("clear_cache_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = OttomanGoldAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Clear Metadata & Image Cache",
                            style = MaterialTheme.typography.titleSmall,
                            color = OttomanGoldAccent
                        )
                    }
                }
            }
        }

        // App & Playback Settings Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MidnightBorder, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Playback & Security Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryWhiteText
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoyalVioletAccent,
                            unfocusedBorderColor = MidnightBorder,
                            focusedTextColor = PrimaryWhiteText,
                            unfocusedTextColor = PrimaryWhiteText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Default Stream Quality",
                        style = MaterialTheme.typography.titleSmall,
                        color = PrimaryWhiteText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("AUTO", "1080P", "720P", "480P").forEach { q ->
                            FilterChip(
                                selected = preferredQuality == q,
                                onClick = { preferredQuality = q },
                                label = { Text(q, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RoyalVioletAccent,
                                    selectedLabelColor = MidnightBg
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Urdu Subtitles Overlay",
                                style = MaterialTheme.typography.titleSmall,
                                color = PrimaryWhiteText
                            )
                            Text(
                                text = "Display Urdu subtitle indicator on video streams by default",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryMutedText
                            )
                        }
                        Switch(
                            checked = subtitleEnabled,
                            onCheckedChange = { subtitleEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = OttomanGoldAccent)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Window Security (FLAG_SECURE)",
                                style = MaterialTheme.typography.titleSmall,
                                color = PrimaryWhiteText
                            )
                            Text(
                                text = "Deters standard Android screenshots/recording on supported OS surfaces",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryMutedText
                            )
                        }
                        Switch(
                            checked = localFlagSecure,
                            onCheckedChange = { localFlagSecure = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = OttomanGoldAccent)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            onSaveSettings(
                                displayName,
                                preferredQuality,
                                subtitleEnabled,
                                localFlagSecure
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalVioletAccent,
                            contentColor = MidnightBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("save_profile_settings_button")
                    ) {
                        Text(
                            text = "Save Preferences",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Sign Out & Credits
        item {
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Sign Out",
                    tint = CrimsonAlert,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign Out of Dastan TV",
                    style = MaterialTheme.typography.titleSmall,
                    color = CrimsonAlert,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Dastan TV v1.1 • Developer: Liaquat Ali • Content API: giveme5.co",
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryMutedText
            )
        }
    }
}

@Composable
fun MembershipPlansScreen(
    userProfile: UserProfile?,
    appConfig: AppConfig,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val currentPlan = userProfile?.membershipPlan ?: "FREE"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp)
            .testTag("membership_plans_screen"),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryWhiteText
                    )
                }
                Column {
                    Text(
                        text = "Membership Plans",
                        style = MaterialTheme.typography.headlineMedium,
                        color = OttomanGoldAccent
                    )
                    Text(
                        text = "Compare Free vs Yearly VIP Cinema Entitlements",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryMutedText
                    )
                }
            }
        }

        // Free Tier Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (currentPlan == "FREE") RoyalVioletAccent else MidnightBorder,
                        shape = RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Free Cinema Tier",
                            style = MaterialTheme.typography.headlineSmall,
                            color = PrimaryWhiteText
                        )
                        if (currentPlan == "FREE") {
                            Surface(
                                color = RoyalVioletAccent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "ACTIVE PLAN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RoyalVioletAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PKR 0 / Forever",
                        style = MaterialTheme.typography.titleLarge,
                        color = OttomanGoldAccent
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("• Standard Turkish Drama Catalog access", style = MaterialTheme.typography.bodyMedium, color = SecondaryMutedText)
                    Text("• Urdu Subtitled & Dubbed Episode Streams", style = MaterialTheme.typography.bodyMedium, color = SecondaryMutedText)
                    Text("• Cloud Watchlist & Continue Watching sync", style = MaterialTheme.typography.bodyMedium, color = SecondaryMutedText)
                }
            }
        }

        // Yearly VIP Tier Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        color = OttomanGoldAccent,
                        shape = RoundedCornerShape(14.dp)
                    ),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MidnightSecondaryBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = null,
                                tint = OttomanGoldAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Yearly VIP Pass",
                                style = MaterialTheme.typography.headlineSmall,
                                color = OttomanGoldAccent
                            )
                        }
                        if (currentPlan == "YEARLY_VIP") {
                            Surface(
                                color = EmeraldSuccess.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "UNLOCKED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldSuccess,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PKR ${appConfig.yearlyVipPricePkr} / Year",
                        style = MaterialTheme.typography.titleLarge,
                        color = PrimaryWhiteText
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("• Everything in Free Cinema Tier", style = MaterialTheme.typography.bodyMedium, color = PrimaryWhiteText)
                    Text("• Early access to VIP-marked historical finales & specials", style = MaterialTheme.typography.bodyMedium, color = PrimaryWhiteText)
                    Text("• Priority multi-mirror 1080p stream selection", style = MaterialTheme.typography.bodyMedium, color = PrimaryWhiteText)
                    Text("• Golden VIP Profile Badge across Dastan TV", style = MaterialTheme.typography.bodyMedium, color = PrimaryWhiteText)

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        color = MidnightBg.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = OttomanGoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentPlan == "YEARLY_VIP") {
                                    "Your account has active Yearly VIP entitlement granted by Admin."
                                } else {
                                    "Online Payment Gateway is currently disabled until billing configuration is finalized. Yearly VIP entitlements are assigned via Admin Membership Management."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryMutedText
                            )
                        }
                    }
                }
            }
        }
    }
}
