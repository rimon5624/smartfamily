package com.rimon.smartfamily

import com.rimon.smartfamily.smart.Cities
import com.rimon.smartfamily.smart.HebrewDateSearch
import com.rimon.smartfamily.smart.Ics
import com.rimon.smartfamily.smart.JewishDays
import com.rimon.smartfamily.smart.QuickAdd
import com.rimon.smartfamily.smart.Zmanim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Plain JVM checks of the app's logic (no phone needed); run by CI on every build. */
class CoreLogicTest {

    @Test fun localitiesLoadAndAreInIsrael() {
        val all = Cities.all
        assertTrue("expected all localities, got ${all.size}", all.size > 1200)
        assertEquals("keys must be unique", all.size, all.map { it.key }.toSet().size)
        all.forEach { c -> assertTrue("${c.he} outside Israel", c.lat in 29.4..33.4 && c.lon in 34.2..35.95) }
    }

    @Test fun oldCityKeysStillWork() {
        listOf("jerusalem", "kiryat_malachi", "haifa", "eilat", "ariel").forEach { assertEquals(it, Cities.byKey(it).key) }
        assertEquals("jerusalem", Cities.byKey("no-such-key").key)
    }

    @Test fun searchFindsPlacesWithAnySpelling() {
        assertTrue(Cities.search("קרית מלאכי").any { it.key == "kiryat_malachi" })
        assertTrue(Cities.search("קריית מלאכי").first().key == "kiryat_malachi")
        assertTrue(Cities.search("אפרת").isNotEmpty())
        assertTrue(Cities.search("efrat").isNotEmpty())
        assertTrue(Cities.search("שדה עוזיהו").isNotEmpty())
        assertTrue(Cities.search("ביתר").isNotEmpty())
        // a city appears once: the hand-checked entry replaces the same place from the big list
        assertEquals(1, Cities.search("קרית מלאכי").size)
    }

    @Test fun nearestPicksTheRightTown() {
        assertEquals("kiryat_malachi", Cities.nearest(31.731, 34.746).key)
        assertEquals("jerusalem", Cities.nearest(31.779, 35.233).key)
    }

    @Test fun shabbatTimesAreSane() {
        val t = Zmanim.nextShabbat(Cities.byKey("jerusalem"))
        val gap = t.havdalah.time - t.candleLighting.time
        assertTrue("Shabbat should last ~25h, got ${gap / 3_600_000.0}h", gap in 24 * 3_600_000L..26 * 3_600_000L)
    }

    @Test fun hebrewDateSearch() {
        val r = HebrewDateSearch.parse("א׳ תשרי תשפ״ז")
        assertNotNull(r); assertEquals(LocalDate.of(2026, 9, 12), r!!.date)
        val r2 = HebrewDateSearch.parse("ט״ו בשבט תשפ״ז")
        assertNotNull(r2); assertEquals(15, r2!!.day); assertEquals(5787, r2.year)
        assertEquals(LocalDate.of(2027, 1, 23), r2.date)
    }

    @Test fun hebrewDayInfo() {
        val i = JewishDays.info(LocalDate.of(2026, 9, 12), Cities.byKey("jerusalem"))
        assertEquals(1, i.hDay); assertEquals(5787, i.hYear); assertTrue(i.yomTov)
    }

    @Test fun quickAddGuesses() {
        val today = LocalDate.of(2026, 11, 20)
        assertEquals("SHOPPING", QuickAdd.guess("לקנות חלב", today).kind.name)
        val e = QuickAdd.guess("פגישה עם המורה מחר 17:30", today)
        assertEquals("EVENT", e.kind.name); assertEquals(today.plusDays(1), e.date); assertEquals(17, e.time?.hour)
    }

    @Test fun icsRoundTrip() {
        val ev = com.rimon.smartfamily.data.db.EventEntity(id = "e1", familyId = "f", title = "חוג, שחייה; מים", startAt = 1_790_000_000_000L,
            endAt = 1_790_003_600_000L, ownerId = "u", recurrence = "WEEKLY", location = "בריכה", note = "שורה\nשנייה")
        val text = Ics.export(listOf(ev), { emptyList() }, emptyList())
        val back = Ics.parse(text).single()
        assertEquals(ev.title, back.title); assertEquals(ev.startAt, back.start); assertEquals(ev.endAt, back.end)
        assertEquals("WEEKLY", back.recurrence); assertEquals(ev.note, back.note)
    }
}
