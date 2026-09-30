package com.puretv.twitch.desktop.data

import com.puretv.twitch.core.model.StreamQuality
import com.puretv.twitch.desktop.discover.DiscoverFilters
import com.puretv.twitch.desktop.discover.UptimeFilter
import com.puretv.twitch.desktop.ui.isAbove480
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoundOneDataTest {
    private fun tmp(): File = Files.createTempDirectory("puretv").toFile()

    @Test fun history_moves_repeat_to_front_and_caps() {
        var h = emptyList<HistoryEntry>()
        repeat(HISTORY_LIMIT + 5) { i -> h = recordHistory(h, HistoryEntry("u$i", "U$i", watchedAt = i.toLong())) }
        assertEquals(HISTORY_LIMIT, h.size)
        h = recordHistory(h, HistoryEntry("U10", "again", watchedAt = 999))
        assertEquals("again", h.first().displayName)
        assertEquals(1, h.count { it.login.equals("u10", ignoreCase = true) })
    }

    @Test fun prefs_round_trip_everything() {
        val dir = tmp()
        ViewPrefsStore(dir).apply {
            setOneClickWatch(false)
            setMiniSize(MiniSize.LARGE)
            togglePinned(PinnedCategory("1", "Chess"))
            saveSearch("chill", DiscoverFilters(maxViewers = 50, hideFollowed = true))
            setIgnored("SomeTroll", true)
            setHighlightWords(listOf(" gg ", "GG", "clutch"))
            flush()
        }
        val p = ViewPrefsStore(dir).prefs.value
        assertFalse(p.oneClickWatch)
        assertEquals(MiniSize.LARGE, p.miniSizeEnum)
        assertEquals(listOf("1"), p.pinnedCategories.map { it.id })
        assertTrue(p.savedSearches.single().filters.hideFollowed)
        assertEquals(listOf("sometroll"), p.ignoredUsers)
        assertEquals(listOf("gg", "clutch"), p.highlightWords)
    }

    @Test fun pin_toggles_off() {
        val s = ViewPrefsStore(tmp())
        s.togglePinned(PinnedCategory("1", "Chess"))
        s.togglePinned(PinnedCategory("1", "Chess"))
        assertTrue(s.prefs.value.pinnedCategories.isEmpty())
    }

    @Test fun backup_export_then_staged_restore() {
        val src = tmp()
        File(src, "following.json").writeText("""[{"id":"1","login":"a","displayName":"A"}]""")
        File(src, "settings.json").writeText("""{"theme":"ember"}""")
        File(src, "tokens.enc").writeText("secret")
        val out = File(tmp(), "b.json")
        assertEquals(2, BackupManager(src).export(out))
        assertFalse(out.readText().contains("secret"))

        val dst = tmp()
        File(dst, "settings.json").writeText("""{"theme":"dark"}""")
        val mgr = BackupManager(dst)
        assertEquals(2, mgr.stageRestore(out).getOrThrow())
        assertTrue(mgr.hasPendingRestore)
        assertEquals("""{"theme":"dark"}""", File(dst, "settings.json").readText())
        mgr.applyPendingRestore()
        assertEquals("""{"theme":"ember"}""", File(dst, "settings.json").readText())
        assertFalse(mgr.hasPendingRestore)
    }

    @Test fun backup_rejects_foreign_files() {
        val f = File(tmp(), "x.json").apply { writeText("""{"hello":1}""") }
        assertTrue(BackupManager(tmp()).stageRestore(f).isFailure)
    }

    @Test fun quality_ladder() {
        assertTrue(isAbove480(StreamQuality.AUTO))
        assertTrue(isAbove480(StreamQuality.P720P60))
        assertFalse(isAbove480(StreamQuality.P480P))
        assertFalse(isAbove480(StreamQuality.AUDIO_ONLY))
    }

    @Test fun describe_filters() {
        assertEquals("Everything live", DiscoverFilters().describe())
        assertEquals("Chess, English, under 50 viewers, 1 to 4 hrs",
            DiscoverFilters(gameName = "Chess", language = "en", maxViewers = 49, uptime = UptimeFilter.MID).describe())
    }

    @Test fun mini_size_cycles() {
        assertEquals(MiniSize.LARGE, MiniSize.MEDIUM.next())
        assertEquals(MiniSize.SMALL, MiniSize.LARGE.next())
    }
}
