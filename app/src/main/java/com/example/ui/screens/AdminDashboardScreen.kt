package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ApiHealthReport
import com.example.data.model.AppConfig
import com.example.data.model.CatalogOverride
import com.example.data.model.DramaSeries
import com.example.data.model.EpisodeItem
import com.example.data.model.UserProfile
import com.example.ui.theme.AmberWarning
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

private enum class AdminSection(val title: String) {
    OVERVIEW("Overview"),
    DRAMAS("Dramas"),
    EPISODES("Episodes"),
    CATEGORIES("Categories"),
    BANNERS("Featured Banners"),
    USERS("Users & VIP"),
    API_SYNC("API Sync & Health"),
    SETTINGS("App Settings")
}

@Composable
fun AdminDashboardScreen(
    isAdmin: Boolean,
    dramas: List<DramaSeries>,
    latestEpisodes: List<EpisodeItem>,
    overrides: List<CatalogOverride>,
    users: List<UserProfile>,
    apiHealth: ApiHealthReport,
    appConfig: AppConfig,
    isLoadingSync: Boolean,
    onBackToUserApp: () -> Unit,
    onSaveOverride: (
        targetType: String,
        targetId: String,
        customTitle: String,
        customDescription: String,
        customPosterUrl: String,
        isHidden: Boolean,
        isFeatured: Boolean,
        isVipOnly: Boolean,
        priorityOrder: Int
    ) -> Unit,
    onUpdateUserMembership: (userId: String, plan: String, isBlocked: Boolean) -> Unit,
    onRunApiSync: () -> Unit,
    onSaveAppConfig: (announcement: String, maintenanceMode: Boolean, vipPrice: Long) -> Unit
) {
    BackHandler(onBack = onBackToUserApp)

    // Defense-in-depth UI guard: never render Admin UI if not verified Admin
    if (!isAdmin) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightBg)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "403 • Unauthorized Access",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CrimsonAlert
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Admin Console is strictly restricted to verified rdxkinglucky@gmail.com credentials.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryMutedText
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackToUserApp) {
                    Text("Return to Home")
                }
            }
        }
        return
    }

    var activeSection by remember { mutableStateOf(AdminSection.OVERVIEW) }
    var editingDrama by remember { mutableStateOf<DramaSeries?>(null) }
    var editingEpisode by remember { mutableStateOf<EpisodeItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("admin_dashboard_screen")
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToUserApp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to User App",
                        tint = PrimaryWhiteText
                    )
                }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = OttomanGoldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DASTAN ADMIN CONSOLE",
                            style = MaterialTheme.typography.titleLarge,
                            color = OttomanGoldAccent
                        )
                    }
                    Text(
                        text = "Role-Based Control Center • rdxkinglucky@gmail.com",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryMutedText
                    )
                }
            }

            IconButton(
                onClick = onRunApiSync,
                modifier = Modifier.testTag("admin_quick_sync_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync API",
                    tint = OttomanGoldAccent
                )
            }
        }

        // Section Navigation Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdminSection.entries.forEach { section ->
                FilterChip(
                    selected = activeSection == section,
                    onClick = { activeSection = section },
                    label = { Text(section.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = OttomanGoldAccent,
                        selectedLabelColor = MidnightBg,
                        containerColor = MidnightCardSurface,
                        labelColor = PrimaryWhiteText
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (activeSection) {
                AdminSection.OVERVIEW -> {
                    item {
                        AdminOverviewSection(
                            dramas = dramas,
                            episodes = latestEpisodes,
                            users = users,
                            overrides = overrides,
                            apiHealth = apiHealth,
                            onJumpSection = { activeSection = it }
                        )
                    }
                }

                AdminSection.DRAMAS -> {
                    item {
                        Text(
                            text = "Drama Management (${dramas.size})",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PrimaryWhiteText
                        )
                        Text(
                            text = "Show/hide dramas, feature on Home hero, mark VIP-only, or edit titles & descriptions.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryMutedText
                        )
                    }
                    items(dramas, key = { it.id }) { drama ->
                        AdminDramaRow(
                            drama = drama,
                            onToggleHidden = {
                                onSaveOverride(
                                    "DRAMA",
                                    drama.categoryId.toString(),
                                    drama.title,
                                    drama.description,
                                    drama.posterUrl,
                                    !drama.isHidden,
                                    drama.isFeatured,
                                    drama.isVipOnly,
                                    drama.priorityOrder
                                )
                            },
                            onToggleFeatured = {
                                onSaveOverride(
                                    "DRAMA",
                                    drama.categoryId.toString(),
                                    drama.title,
                                    drama.description,
                                    drama.posterUrl,
                                    drama.isHidden,
                                    !drama.isFeatured,
                                    drama.isVipOnly,
                                    drama.priorityOrder
                                )
                            },
                            onToggleVip = {
                                onSaveOverride(
                                    "DRAMA",
                                    drama.categoryId.toString(),
                                    drama.title,
                                    drama.description,
                                    drama.posterUrl,
                                    drama.isHidden,
                                    drama.isFeatured,
                                    !drama.isVipOnly,
                                    drama.priorityOrder
                                )
                            },
                            onEditMetadata = { editingDrama = drama }
                        )
                    }
                }

                AdminSection.EPISODES -> {
                    item {
                        Text(
                            text = "Episode Management (${latestEpisodes.size} recent)",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PrimaryWhiteText
                        )
                        Text(
                            text = "Control episode visibility, inspect video embed status, and edit episode titles/descriptions.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryMutedText
                        )
                    }
                    items(latestEpisodes, key = { it.id }) { ep ->
                        AdminEpisodeRow(
                            episode = ep,
                            onToggleHidden = {
                                onSaveOverride(
                                    "EPISODE",
                                    ep.postId.toString(),
                                    ep.title,
                                    ep.description,
                                    ep.posterUrl,
                                    !ep.isHidden,
                                    false,
                                    ep.isVipOnly,
                                    ep.episodeNumber
                                )
                            },
                            onToggleVip = {
                                onSaveOverride(
                                    "EPISODE",
                                    ep.postId.toString(),
                                    ep.title,
                                    ep.description,
                                    ep.posterUrl,
                                    ep.isHidden,
                                    false,
                                    !ep.isVipOnly,
                                    ep.episodeNumber
                                )
                            },
                            onEdit = { editingEpisode = ep }
                        )
                    }
                }

                AdminSection.CATEGORIES -> {
                    item {
                        Text(
                            text = "WordPress Category Ordering & Visibility",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PrimaryWhiteText
                        )
                        Text(
                            text = "Manage source categories synced from https://giveme5.co/wp-json/wp/v2/categories",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryMutedText
                        )
                    }
                    items(dramas, key = { "cat_${it.categoryId}" }) { drama ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MidnightBorder, RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Category #${drama.categoryId} • ${drama.title}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = PrimaryWhiteText
                                    )
                                    Text(
                                        text = "Slug: ${drama.slug} • ${drama.episodeCount} Posts",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SecondaryMutedText
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        onSaveOverride(
                                            "CATEGORY",
                                            drama.categoryId.toString(),
                                            drama.title,
                                            drama.description,
                                            drama.posterUrl,
                                            !drama.isHidden,
                                            drama.isFeatured,
                                            drama.isVipOnly,
                                            maxOf(0, drama.priorityOrder - 1)
                                        )
                                    }
                                ) {
                                    Text(if (drama.isHidden) "Show Category" else "Hide Category")
                                }
                            }
                        }
                    }
                }

                AdminSection.BANNERS -> {
                    item {
                        val featuredList = dramas.filter { it.isFeatured }
                        Text(
                            text = "Featured Hero Banners (${featuredList.size} active)",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PrimaryWhiteText
                        )
                        Text(
                            text = "Control which dramas appear in the Home screen Hero Spotlight carousel.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryMutedText
                        )
                    }
                    items(dramas, key = { "banner_${it.id}" }) { drama ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (drama.isFeatured) OttomanGoldAccent else MidnightBorder,
                                    RoundedCornerShape(14.dp)
                                ),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = drama.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = PrimaryWhiteText
                                    )
                                    Text(
                                        text = if (drama.isFeatured) "Promoted in Home Hero Spotlight" else "Standard Catalog Row",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (drama.isFeatured) OttomanGoldAccent else SecondaryMutedText
                                    )
                                }
                                Switch(
                                    checked = drama.isFeatured,
                                    onCheckedChange = { checked ->
                                        onSaveOverride(
                                            "DRAMA",
                                            drama.categoryId.toString(),
                                            drama.title,
                                            drama.description,
                                            drama.posterUrl,
                                            drama.isHidden,
                                            checked,
                                            drama.isVipOnly,
                                            drama.priorityOrder
                                        )
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = OttomanGoldAccent)
                                )
                            }
                        }
                    }
                }

                AdminSection.USERS -> {
                    item {
                        Text(
                            text = "User & Membership Management (${users.size})",
                            style = MaterialTheme.typography.headlineMedium,
                            color = PrimaryWhiteText
                        )
                        Text(
                            text = "View registered accounts, assign FREE or YEARLY_VIP memberships, and moderate access.",
                            style = MaterialTheme.typography.labelSmall,
                            color = SecondaryMutedText
                        )
                    }
                    items(users, key = { it.id.ifBlank { it.userId } }) { u ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MidnightBorder, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = u.displayName.ifBlank { "Dastan User" },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = PrimaryWhiteText
                                        )
                                        Text(
                                            text = u.email,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SecondaryMutedText
                                        )
                                    }
                                    Surface(
                                        color = if (u.membershipPlan == "YEARLY_VIP")
                                            OttomanGoldAccent else MidnightSecondaryBg,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = u.membershipPlan,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (u.membershipPlan == "YEARLY_VIP")
                                                MidnightBg else OttomanGoldAccent,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            val nextPlan =
                                                if (u.membershipPlan == "YEARLY_VIP") "FREE" else "YEARLY_VIP"
                                            onUpdateUserMembership(u.userId, nextPlan, u.isBlocked)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = RoyalVioletAccent,
                                            contentColor = MidnightBg
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (u.membershipPlan == "YEARLY_VIP")
                                                "Set Free Tier" else "Grant Yearly VIP",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onUpdateUserMembership(
                                                u.userId,
                                                u.membershipPlan,
                                                !u.isBlocked
                                            )
                                        }
                                    ) {
                                        Text(
                                            text = if (u.isBlocked) "Unblock" else "Block",
                                            color = if (u.isBlocked) EmeraldSuccess else CrimsonAlert
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                AdminSection.API_SYNC -> {
                    item {
                        AdminApiSyncAndHealthSection(
                            apiHealth = apiHealth,
                            isLoadingSync = isLoadingSync,
                            onRunApiSync = onRunApiSync
                        )
                    }
                }

                AdminSection.SETTINGS -> {
                    item {
                        AdminGlobalSettingsSection(
                            appConfig = appConfig,
                            onSaveAppConfig = onSaveAppConfig
                        )
                    }
                }
            }
        }
    }

    if (editingDrama != null) {
        val target = editingDrama!!
        var titleInput by remember(target) { mutableStateOf(target.title) }
        var descInput by remember(target) { mutableStateOf(target.description) }
        var posterInput by remember(target) { mutableStateOf(target.posterUrl) }

        AlertDialog(
            onDismissRequest = { editingDrama = null },
            containerColor = MidnightCardSurface,
            title = {
                Text("Edit Drama #${target.categoryId}", color = OttomanGoldAccent)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Drama Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = posterInput,
                        onValueChange = { posterInput = it },
                        label = { Text("Custom Poster URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("Synopsis / Description") },
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveOverride(
                            "DRAMA",
                            target.categoryId.toString(),
                            titleInput,
                            descInput,
                            posterInput,
                            target.isHidden,
                            target.isFeatured,
                            target.isVipOnly,
                            target.priorityOrder
                        )
                        editingDrama = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalVioletAccent,
                        contentColor = MidnightBg
                    )
                ) {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingDrama = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (editingEpisode != null) {
        val target = editingEpisode!!
        var titleInput by remember(target) { mutableStateOf(target.title) }
        var descInput by remember(target) { mutableStateOf(target.description) }
        var posterInput by remember(target) { mutableStateOf(target.posterUrl) }

        AlertDialog(
            onDismissRequest = { editingEpisode = null },
            containerColor = MidnightCardSurface,
            title = {
                Text("Edit Episode #${target.postId}", color = OttomanGoldAccent)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Episode Title") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = posterInput,
                        onValueChange = { posterInput = it },
                        label = { Text("Custom Thumbnail URL") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = descInput,
                        onValueChange = { descInput = it },
                        label = { Text("Episode Synopsis") },
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveOverride(
                            "EPISODE",
                            target.postId.toString(),
                            titleInput,
                            descInput,
                            posterInput,
                            target.isHidden,
                            false,
                            target.isVipOnly,
                            target.episodeNumber
                        )
                        editingEpisode = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalVioletAccent,
                        contentColor = MidnightBg
                    )
                ) {
                    Text("Save Episode", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingEpisode = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminOverviewSection(
    dramas: List<DramaSeries>,
    episodes: List<EpisodeItem>,
    users: List<UserProfile>,
    overrides: List<CatalogOverride>,
    apiHealth: ApiHealthReport,
    onJumpSection: (AdminSection) -> Unit
) {
    val vipCount = users.count { it.membershipPlan == "YEARLY_VIP" }
    val playableCount = episodes.count { it.isPlayable }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AdminMetricCard(
                title = "Total Dramas",
                value = "${dramas.size}",
                subtitle = "${dramas.count { it.isFeatured }} Featured",
                modifier = Modifier
                    .weight(1f)
                    .clickable { onJumpSection(AdminSection.DRAMAS) }
            )
            AdminMetricCard(
                title = "Recent Episodes",
                value = "${episodes.size}",
                subtitle = "$playableCount Playable Embeds",
                modifier = Modifier
                    .weight(1f)
                    .clickable { onJumpSection(AdminSection.EPISODES) }
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AdminMetricCard(
                title = "Registered Users",
                value = "${users.size}",
                subtitle = "$vipCount Yearly VIP",
                modifier = Modifier
                    .weight(1f)
                    .clickable { onJumpSection(AdminSection.USERS) }
            )
            AdminMetricCard(
                title = "API Health",
                value = if (apiHealth.isHealthy) "ONLINE" else "DEGRADED",
                subtitle = "${apiHealth.responseLatencyMs}ms • HTTP ${apiHealth.httpStatusCode}",
                modifier = Modifier
                    .weight(1f)
                    .clickable { onJumpSection(AdminSection.API_SYNC) }
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MidnightBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Active Firestore Overrides (${overrides.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = OttomanGoldAccent
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "All visibility, featuring, VIP-gating, and metadata edits are enforced in real-time via Firestore /catalog_overrides with zero-trust admin rules.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SecondaryMutedText
                )
            }
        }
    }
}

@Composable
private fun AdminMetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, MidnightBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = SecondaryMutedText)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, style = MaterialTheme.typography.headlineLarge, color = OttomanGoldAccent)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = RoyalVioletAccent)
        }
    }
}

@Composable
private fun AdminDramaRow(
    drama: DramaSeries,
    onToggleHidden: () -> Unit,
    onToggleFeatured: () -> Unit,
    onToggleVip: () -> Unit,
    onEditMetadata: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MidnightBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = drama.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (drama.isHidden) SecondaryMutedText else PrimaryWhiteText
                    )
                    Text(
                        text = "Cat #${drama.categoryId} • ${drama.episodeCount} Episodes • ${if (drama.hasMissingPoster) "Fallback Poster" else "Poster OK"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryMutedText
                    )
                }
                IconButton(onClick = onEditMetadata) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Drama",
                        tint = OttomanGoldAccent
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !drama.isHidden,
                    onClick = onToggleHidden,
                    label = { Text(if (drama.isHidden) "Hidden" else "Visible") }
                )
                FilterChip(
                    selected = drama.isFeatured,
                    onClick = onToggleFeatured,
                    label = { Text(if (drama.isFeatured) "Featured ★" else "Feature") }
                )
                FilterChip(
                    selected = drama.isVipOnly,
                    onClick = onToggleVip,
                    label = { Text(if (drama.isVipOnly) "VIP Only" else "Free Access") }
                )
            }
        }
    }
}

@Composable
private fun AdminEpisodeRow(
    episode: EpisodeItem,
    onToggleHidden: () -> Unit,
    onToggleVip: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MidnightBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (episode.isHidden) SecondaryMutedText else PrimaryWhiteText,
                        maxLines = 2
                    )
                    Text(
                        text = "Post #${episode.postId} • ${if (episode.isPlayable) "Embed Verified (${episode.backupEmbedUrls.size + 1} mirrors)" else "Missing Video Embed"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (episode.isPlayable) EmeraldSuccess else CrimsonAlert
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Episode",
                        tint = OttomanGoldAccent
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !episode.isHidden,
                    onClick = onToggleHidden,
                    label = { Text(if (episode.isHidden) "Hidden" else "Visible") }
                )
                FilterChip(
                    selected = episode.isVipOnly,
                    onClick = onToggleVip,
                    label = { Text(if (episode.isVipOnly) "VIP Only" else "Free") }
                )
            }
        }
    }
}

@Composable
private fun AdminApiSyncAndHealthSection(
    apiHealth: ApiHealthReport,
    isLoadingSync: Boolean,
    onRunApiSync: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, RoyalVioletAccent.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "WordPress API Synchronization & Health Telemetry",
                style = MaterialTheme.typography.titleLarge,
                color = OttomanGoldAccent
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Endpoint: ${apiHealth.endpointUrl}",
                style = MaterialTheme.typography.labelLarge,
                color = PrimaryWhiteText
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("• Status: ${if (apiHealth.isHealthy) "HEALTHY" else "ERROR"} (HTTP ${apiHealth.httpStatusCode})", color = if (apiHealth.isHealthy) EmeraldSuccess else CrimsonAlert)
            Text("• Response Latency: ${apiHealth.responseLatencyMs} ms", color = PrimaryWhiteText)
            Text("• Categories Synced: ${apiHealth.totalCategoriesFetched}", color = PrimaryWhiteText)
            Text("• Cached Episodes: ${apiHealth.totalEpisodesCached}", color = PrimaryWhiteText)
            Text("• Verified Playable Embeds: ${apiHealth.playableEpisodesCount}", color = EmeraldSuccess)
            Text("• Missing Poster Detections: ${apiHealth.missingPosterCount}", color = AmberWarning)
            Text("• Missing Video Detections: ${apiHealth.missingVideoCount}", color = AmberWarning)
            Text("• Duplicate Posts Filtered: ${apiHealth.duplicateSkippedCount}", color = SecondaryMutedText)

            if (apiHealth.errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Last Error: ${apiHealth.errorMessage}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CrimsonAlert
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRunApiSync,
                enabled = !isLoadingSync,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalVioletAccent,
                    contentColor = MidnightBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("admin_run_api_sync_button")
            ) {
                Icon(imageVector = Icons.Default.CloudSync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isLoadingSync) "Synchronizing with GiveMe5 API..." else "Refresh & Validate Content API Now",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AdminGlobalSettingsSection(
    appConfig: AppConfig,
    onSaveAppConfig: (announcement: String, maintenanceMode: Boolean, vipPrice: Long) -> Unit
) {
    var announcement by remember(appConfig.announcementBanner) {
        mutableStateOf(appConfig.announcementBanner)
    }
    var maintenanceMode by remember(appConfig.maintenanceMode) {
        mutableStateOf(appConfig.maintenanceMode)
    }
    var vipPriceText by remember(appConfig.yearlyVipPricePkr) {
        mutableStateOf(appConfig.yearlyVipPricePkr.toString())
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MidnightBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MidnightCardSurface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Global Application Configuration",
                style = MaterialTheme.typography.titleLarge,
                color = OttomanGoldAccent
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = announcement,
                onValueChange = { announcement = it },
                label = { Text("Home Screen Announcement Banner") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = vipPriceText,
                onValueChange = { vipPriceText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Yearly VIP Display Price (PKR)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Maintenance Mode", color = PrimaryWhiteText)
                    Text(
                        "Display maintenance notice to standard viewers",
                        style = MaterialTheme.typography.labelSmall,
                        color = SecondaryMutedText
                    )
                }
                Switch(
                    checked = maintenanceMode,
                    onCheckedChange = { maintenanceMode = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = OttomanGoldAccent)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onSaveAppConfig(
                        announcement,
                        maintenanceMode,
                        vipPriceText.toLongOrNull() ?: 2499L
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = OttomanGoldAccent,
                    contentColor = MidnightBg
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Publish Global Configuration", fontWeight = FontWeight.Bold)
            }
        }
    }
}
