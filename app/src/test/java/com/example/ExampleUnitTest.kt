package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalCatalogCacheDatabase
import com.example.data.model.CatalogOverride
import com.example.data.model.WatchHistoryItem
import com.example.data.remote.WordPressContentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    @Test
    fun mehmedMultiSeasonGrouping_groupsAllMehmedCategoriesUnderSingleDrama() {
        val repo = WordPressContentRepository()
        val categoriesJson = """
            [
              {"id": 129, "count": 15, "name": "Sultan Mehmed Fateh", "slug": "sultan-mehmed-fateh", "description": "Season 1"},
              {"id": 134, "count": 7, "name": "Sultan M Fateh S2", "slug": "sultan-m-fateh-s2", "description": "Season 2"},
              {"id": 133, "count": 8, "name": "Sultan M Fateh S4", "slug": "sultan-m-fateh-s4", "description": "Season 4"},
              {"id": 113, "count": 27, "name": "Kurulus Osman S1", "slug": "ko1", "description": ""},
              {"id": 114, "count": 37, "name": "Kurulus Osman S2", "slug": "ko2", "description": ""}
            ]
        """.trimIndent()

        val dramas = repo.parseCategoriesJson(categoriesJson, emptyMap(), emptyMap())

        // Mehmed should be unified into 1 drama with 3 seasons (S1, S2, S4)
        val mehmedDramas = dramas.filter { it.title.contains("Mehmed", ignoreCase = true) }
        assertEquals(1, mehmedDramas.size)

        val mehmed = mehmedDramas.first()
        assertEquals("Sultan Mehmed Fateh", mehmed.title)
        assertEquals(3, mehmed.totalSeasonsCount)
        assertEquals(listOf(1, 2, 4), mehmed.seasons.map { it.seasonNumber })
        assertEquals(129, mehmed.categoryIdForSeason(1))
        assertEquals(134, mehmed.categoryIdForSeason(2))
        assertEquals(133, mehmed.categoryIdForSeason(4))
        assertEquals(30, mehmed.episodeCount)

        // Osman should also be unified into 1 drama with 2 seasons (S1, S2)
        val osmanDramas = dramas.filter { it.title.contains("Osman", ignoreCase = true) }
        assertEquals(1, osmanDramas.size)
        assertEquals(listOf(1, 2), osmanDramas.first().seasons.map { it.seasonNumber })
    }

    @Test
    fun mehmedSeasonEpisodes_deduplicatesAndIsolatesBySeasonAndDrama() {
        val repo = WordPressContentRepository()
        val postsJson = """
            [
              {
                "id": 5157,
                "date": "2024-10-02T10:00:00",
                "link": "https://giveme5.co/mehmed-s2-ep17-v2",
                "title": {"rendered": "Sultan Muhammad Fateh Season 2 Episode 17 With Urdu Subtitles"},
                "excerpt": {"rendered": "<p>Mehmed prepares the siege.</p>"},
                "content": {"rendered": "<iframe src=\"https://vidmoly.net/embed-aaa111.html\"></iframe>"},
                "categories": [134]
              },
              {
                "id": 5006,
                "date": "2024-10-01T10:00:00",
                "link": "https://giveme5.co/mehmed-s2-ep17-v1",
                "title": {"rendered": "Sultan Muhammad Fateh Season 2 Episode 17 With Urdu Subtitles"},
                "excerpt": {"rendered": "<p>Mehmed prepares the siege.</p>"},
                "content": {"rendered": "<iframe src=\"https://ok.ru/videoembed/999888\"></iframe>"},
                "categories": [134]
              },
              {
                "id": 4951,
                "date": "2024-09-24T10:00:00",
                "link": "https://giveme5.co/mehmed-s2-ep16",
                "title": {"rendered": "Sultan Muhammad Fateh Season 2 Episode 16 With Urdu Subtitles"},
                "excerpt": {"rendered": "<p>Episode 16</p>"},
                "content": {"rendered": "<iframe src=\"https://vidmoly.net/embed-bbb222.html\"></iframe>"},
                "categories": [134]
              },
              {
                "id": 9999,
                "date": "2024-10-03T10:00:00",
                "link": "https://giveme5.co/orhan-ep1",
                "title": {"rendered": "Kurulus Orhan Episode 1 With Urdu Subtitles"},
                "excerpt": {"rendered": "<p>Unrelated drama</p>"},
                "content": {"rendered": "<iframe src=\"https://vidmoly.net/embed-ccc333.html\"></iframe>"},
                "categories": [134]
              }
            ]
        """.trimIndent()

        val parsedRaw = repo.parsePostsJson(
            jsonString = postsJson,
            defaultCategoryId = 134,
            defaultDramaName = "Sultan Mehmed Fateh",
            forcedSeasonNumber = 2
        ).first

        val isolated = parsedRaw.filter {
            WordPressContentRepository.doesPostBelongToDrama(it.title, "Sultan Mehmed Fateh")
        }
        val episodes = repo.deduplicateSeasonEpisodes(isolated)

        // Foreign drama (Kurulus Orhan) must be excluded, and duplicate Ep 17 posts merged
        assertEquals(2, episodes.size)
        assertEquals(listOf(16, 17), episodes.map { it.episodeNumber })
        assertTrue(episodes.all { it.seasonNumber == 2 })

        // Verify duplicate Ep 17 merged backup server URLs
        val ep17 = episodes.first { it.episodeNumber == 17 }
        assertEquals("https://vidmoly.net/embed-aaa111.html", ep17.primaryEmbedUrl)
        assertTrue(ep17.backupEmbedUrls.contains("https://ok.ru/videoembed/999888"))
    }

    @Test
    fun adminOverride_allowsSafeSeasonAndEpisodeCorrection() {
        val repo = WordPressContentRepository()
        val postsJson = """
            [
              {
                "id": 4729,
                "date": "2024-06-12T10:00:00",
                "link": "https://giveme5.co/mehmed-ep15",
                "title": {"rendered": "Sultan Muhammad Fateh Episode 15 With Urdu Subtitles"},
                "excerpt": {"rendered": "<p>Finale</p>"},
                "content": {"rendered": "<iframe src=\"https://vidmoly.net/embed-finale.html\"></iframe>"},
                "categories": [129]
              }
            ]
        """.trimIndent()

        val rawEpisodes = repo.parsePostsJson(
            jsonString = postsJson,
            defaultCategoryId = 129,
            defaultDramaName = "Sultan Mehmed Fateh",
            forcedSeasonNumber = 1
        ).first
        assertEquals(1, rawEpisodes.first().seasonNumber)
        assertEquals(15, rawEpisodes.first().episodeNumber)

        // Admin overrides Season to 2 and Episode to 1 via priorityOrder = 2001
        val override = CatalogOverride(
            id = "EPISODE_4729",
            targetType = "EPISODE",
            targetId = "4729",
            customTitle = "Sultan Mehmed Season 2 Premiere",
            customDescription = "Corrected by Admin",
            customPosterUrl = "",
            isHidden = false,
            isFeatured = false,
            isVipOnly = false,
            priorityOrder = 2001L
        )

        val corrected = repo.applyAdminOverridesToEpisodes(rawEpisodes, listOf(override), false)
        assertEquals(2, corrected.first().seasonNumber)
        assertEquals(1, corrected.first().episodeNumber)
        assertTrue(corrected.first().hasExplicitSeason)
    }

    @Test
    fun continueWatching_parsesDramaSeasonAndEpisodeCorrectly() {
        val item = WatchHistoryItem(
            id = "ep_5157",
            userId = "u1",
            episodeId = "ep_5157",
            categoryId = 134,
            episodeTitle = "S2 E17 • Episode 17 — Sultan Mehmed Fateh",
            dramaTitle = "Sultan Mehmed Fateh (S2 • E17)",
            posterUrl = "https://giveme5.co/thumb.jpg",
            embedUrl = "https://vidmoly.net/embed-aaa111.html",
            progressMs = 600_000L,
            durationMs = 2_400_000L
        )

        assertEquals("Sultan Mehmed Fateh", item.cleanDramaTitle)
        assertEquals(2, item.parsedSeasonNumber)
        assertEquals(17, item.parsedEpisodeNumber)
    }

    @Test
    fun localSqliteCache_savesAndReadsDramasSeasonsAndEpisodesOffline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LocalCatalogCacheDatabase.getInstance(context)
        db.clearAllLocalCaches(context)

        val repo = WordPressContentRepository(context)
        val categoriesJson = """
            [
              {"id": 129, "count": 15, "name": "Sultan Mehmed Fateh", "slug": "sultan-mehmed-fateh", "description": "Season 1"},
              {"id": 134, "count": 7, "name": "Sultan M Fateh S2", "slug": "sultan-m-fateh-s2", "description": "Season 2"}
            ]
        """.trimIndent()
        val dramas = repo.parseCategoriesJson(categoriesJson, emptyMap(), emptyMap())
        db.saveDramas(dramas)

        val cachedDramas = db.getCachedDramas()
        assertEquals(1, cachedDramas.size)
        assertEquals(2, cachedDramas.first().seasons.size)
        assertTrue(db.isCacheKeyFresh(LocalCatalogCacheDatabase.KEY_CATALOG_DRAMAS, 60_000L))

        val stats = db.getCacheStats(context)
        assertNotNull(stats)
        assertEquals(1, stats.cachedDramasCount)
        assertEquals(2, stats.cachedSeasonsCount)

        db.clearAllLocalCaches(context)
        assertEquals(0, db.getCachedDramas().size)
    }
}
