package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalCatalogCacheDatabase
import com.example.data.model.CatalogOverride
import com.example.data.model.ContentSource
import com.example.data.model.WatchHistoryItem
import com.example.data.remote.DramaNitamCategory
import com.example.data.remote.DramaNitamContentRepository
import com.example.data.remote.WordPressContentRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun streamUrlExtraction_filtersMaliciousAdIframesAndPrioritizesPlayableServers() {
        val repo = WordPressContentRepository()
        val rawWordPressHtml = """
            <p>Watch Sultan Mehmed Fateh Episode 75 in HD</p>
            <iframe src="https://bicea.org/popup-ad.html" width="600" height="400"></iframe>
            <iframe src="//ok.ru/videoembed/12358603934338" width="800" height="450" allowfullscreen></iframe>
            <iframe src="http://vidmoly.net/embed-da9y6q1gddrg.html" width="800" height="450" allowfullscreen></iframe>
        """.trimIndent()

        val extracted = repo.extractAuthorizedEmbedUrls(rawWordPressHtml)

        // bicea.org ad iframe must be filtered out; protocol-relative and http URLs normalized to https;
        // Vidmoly prioritized as primary Server 1 and OK.ru as backup Server 2
        assertEquals(2, extracted.size)
        assertEquals("https://vidmoly.net/embed-da9y6q1gddrg.html", extracted[0])
        assertEquals("https://ok.ru/videoembed/12358603934338", extracted[1])
    }

    @Test
    fun okRuEmbedParser_extractsHlsManifestMp4StreamsAndExactDuration() {
        val sampleOkRuHtml = """
            <div data-module="OKVideo" data-options="{&quot;flashvars&quot;:{&quot;metadata&quot;:&quot;{\&quot;movie\&quot;:{\&quot;id\&quot;:\&quot;12358603934338\&quot;,\&quot;duration\&quot;:4133},\&quot;hlsManifestUrl\&quot;:\&quot;https://vd427.okcdn.ru/video.m3u8?cmd=videoPlayerCdn\&quot;,\&quot;videos\&quot;:[{\&quot;name\&quot;:\&quot;sd\&quot;,\&quot;url\&quot;:\&quot;https://vd427.okcdn.ru/sd.mp4\&quot;},{\&quot;name\&quot;:\&quot;hd\&quot;,\&quot;url\&quot;:\&quot;https://vd427.okcdn.ru/hd.mp4\&quot;}]}&quot;}}"></div>
        """.trimIndent()

        val info = WordPressContentRepository.parseOkRuEmbedHtml(
            embedUrl = "https://ok.ru/videoembed/12358603934338",
            rawHtml = sampleOkRuHtml
        )

        assertNotNull(info)
        assertEquals("OK.ru HD", info!!.providerName)
        assertEquals(4133, info.durationSeconds)
        assertEquals(3, info.backupDirectUrls.size)
        assertEquals("https://vd427.okcdn.ru/video.m3u8?cmd=videoPlayerCdn", info.backupDirectUrls[0])
        assertEquals("https://vd427.okcdn.ru/hd.mp4", info.backupDirectUrls[1])
    }

    @Test
    fun automaticContentSync_detectsNewDramasNewSeasonsAndNewEpisodesWithoutDuplicates() {
        val repo = WordPressContentRepository()

        // Initial catalog: Mehmed S1 + S2
        val initialCategoriesJson = """
            [
              {"id": 129, "count": 15, "name": "Sultan Mehmed Fateh", "slug": "sultan-mehmed-fateh", "description": "Season 1"},
              {"id": 134, "count": 7, "name": "Sultan M Fateh S2", "slug": "sultan-m-fateh-s2", "description": "Season 2"}
            ]
        """.trimIndent()
        val initialDramas = repo.parseCategoriesJson(initialCategoriesJson, emptyMap(), emptyMap())
        assertEquals(1, initialDramas.size)
        assertEquals(listOf(1, 2), initialDramas.first().seasons.map { it.seasonNumber })

        // API subsequently adds Season 4 of Mehmed AND a brand new drama ("Kudus Fatihi Salahuddin Ayubi")
        val updatedCategoriesJson = """
            [
              {"id": 129, "count": 15, "name": "Sultan Mehmed Fateh", "slug": "sultan-mehmed-fateh", "description": "Season 1"},
              {"id": 134, "count": 8, "name": "Sultan M Fateh S2", "slug": "sultan-m-fateh-s2", "description": "Season 2"},
              {"id": 133, "count": 9, "name": "Sultan M Fateh S4", "slug": "sultan-m-fateh-s4", "description": "Season 4"},
              {"id": 125, "count": 28, "name": "Salahuddin Ayubi", "slug": "salahuddin-ayubi", "description": "Season 1"}
            ]
        """.trimIndent()
        val updatedDramas = repo.parseCategoriesJson(updatedCategoriesJson, emptyMap(), emptyMap())

        assertEquals(2, updatedDramas.size)
        val updatedMehmed = updatedDramas.first { it.title == "Sultan Mehmed Fateh" }
        assertEquals(listOf(1, 2, 4), updatedMehmed.seasons.map { it.seasonNumber })
        assertEquals(32, updatedMehmed.episodeCount)

        val newDrama = updatedDramas.first { it.title == "Salahuddin Ayubi" }
        assertEquals(1, newDrama.seasons.size)
        assertEquals(28, newDrama.episodeCount)
    }

    @Test
    fun dramaNitamParser_parsesHindiUrduDubbedPostsAndEpisodePortalAndIsolatesFromDramaCollection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LocalCatalogCacheDatabase.getInstance(context)
        db.clearAllLocalCaches(context)

        val nitamRepo = DramaNitamContentRepository(context)
        val catMap = mapOf(
            6 to DramaNitamCategory(id = 6, name = "Turkish Dramas", slug = "turkish-dramas", count = 43),
            5 to DramaNitamCategory(id = 5, name = "Korean Dramas", slug = "korean-dramas", count = 540)
        )

        val postJson = JSONObject(
            """
            {
              "id": 5322,
              "date": "2026-07-16T23:54:51",
              "slug": "arafta-bound-by-fate",
              "link": "https://www.dramanitam.com/arafta-bound-by-fate/",
              "title": {"rendered": "Arafta: Bound by Fate [Turkish Drama] in Hindi Dubbed &#8211; Episodes 12-18 Added &#8211; DramaNitam"},
              "excerpt": {"rendered": "<p>Mercan and Ates face a fateful reckoning in this gripping Turkish romance.</p>"},
              "content": {"rendered": "<ul><li>Drama: Turkish</li><li>Audio: Hindi Dubbed</li><li>Season: 02</li><li>Episodes: 18</li><li>Duration: 40 mint.</li></ul><ol><li>For Episode Wise Links ~ <a href=\"https://dramanitam04.blogspot.com/p/abbf-s02-163.html\">CLICK HERE</a></li></ol><img src=\"https://www.dramanitam.com/wp-content/uploads/2026/07/Arafta_-Bound-by-Fate.jpg\"/>"},
              "categories": [6]
            }
            """.trimIndent()
        )

        val parsed = nitamRepo.parseDramaPostObject(postJson, catMap)
        assertNotNull(parsed)
        assertEquals("Arafta: Bound by Fate", parsed!!.cleanDramaTitle)
        assertEquals(2, parsed.seasonNumber)
        assertEquals(18, parsed.episodeCount)
        assertEquals("Turkish Dramas", parsed.categoryName)
        assertEquals("Hindi Dubbed", parsed.audioLanguage)
        assertEquals("https://dramanitam04.blogspot.com/p/abbf-s02-163.html", parsed.episodePortalUrl)

        val nitamDramas = nitamRepo.groupPostsIntoMultiSeasonDramas(listOf(parsed))
        assertEquals(1, nitamDramas.size)
        val nitamDrama = nitamDramas.first()
        assertEquals(ContentSource.DRAMANITAM_DUBBED, nitamDrama.source)
        assertEquals("dramanitam_drama_5322", nitamDrama.id)

        // Verify episode-wise HTML parser extracts individual Episode items with server mirrors
        val samplePortalHtml = """
            <div>
              Episode: 01
              <a href="https://teraboxlinks.com/xXaxKIl">GDrive</a> |
              <a href="https://teraboxlinks.com/aHqma">XCloud</a> |
              <a href="https://teraboxlinks.com/Q1nC">Pixeldrain</a>
            </div>
            <div>
              Episode: 02
              <a href="https://teraboxlinks.com/ep2gdrive">GDrive</a> |
              <a href="https://teraboxlinks.com/ep2xcloud">XCloud</a>
            </div>
        """.trimIndent()

        val portalEpisodes = nitamRepo.parseEpisodeWiseHtmlPage(
            html = samplePortalHtml,
            seasonPostId = 5322,
            dramaTitle = nitamDrama.title,
            seasonNumber = 2,
            posterUrl = nitamDrama.posterUrl,
            durationLabel = "40m",
            audioLanguage = "Hindi Dubbed",
            portalUrl = parsed.episodePortalUrl
        )
        assertEquals(2, portalEpisodes.size)
        assertEquals(1, portalEpisodes[0].episodeNumber)
        assertEquals("https://teraboxlinks.com/xXaxKIl", portalEpisodes[0].primaryEmbedUrl)
        assertTrue(portalEpisodes[0].backupEmbedUrls.contains("https://teraboxlinks.com/aHqma"))
        assertEquals(ContentSource.DRAMANITAM_DUBBED, portalEpisodes[0].source)

        // Verify strict SQLite separation between ContentSource.DRAMA_COLLECTION and ContentSource.DRAMANITAM_DUBBED
        val giveMe5Repo = WordPressContentRepository(context)
        val giveMe5Dramas = giveMe5Repo.parseCategoriesJson(
            """[{"id": 129, "count": 15, "name": "Sultan Mehmed Fateh", "slug": "sultan-mehmed-fateh", "description": "Season 1"}]""",
            emptyMap(),
            emptyMap()
        )
        db.saveDramas(giveMe5Dramas, ContentSource.DRAMA_COLLECTION)
        db.saveDramas(nitamDramas, ContentSource.DRAMANITAM_DUBBED)

        val cachedCollection = db.getCachedDramas(ContentSource.DRAMA_COLLECTION)
        val cachedDubbed = db.getCachedDramas(ContentSource.DRAMANITAM_DUBBED)

        assertEquals(1, cachedCollection.size)
        assertEquals("Sultan Mehmed Fateh", cachedCollection.first().title)
        assertEquals(ContentSource.DRAMA_COLLECTION, cachedCollection.first().source)

        assertEquals(1, cachedDubbed.size)
        assertEquals("Arafta: Bound by Fate", cachedDubbed.first().title)
        assertEquals(ContentSource.DRAMANITAM_DUBBED, cachedDubbed.first().source)
        assertFalse(cachedCollection.any { it.source == ContentSource.DRAMANITAM_DUBBED })
        assertFalse(cachedDubbed.any { it.source == ContentSource.DRAMA_COLLECTION })

        db.clearAllLocalCaches(context)
    }
}
