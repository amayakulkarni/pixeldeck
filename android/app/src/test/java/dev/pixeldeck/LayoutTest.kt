package dev.pixeldeck

import dev.pixeldeck.data.Panels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private val start = Panels.DEFAULT_LAYOUT

    @Test fun sanitizeDropsUnknownDuplicatesAndPads() {
        val messy = listOf(listOf("clock", "bogus", "clock"), listOf("deck"))
        assertEquals(listOf(listOf("clock"), listOf("deck"), emptyList<String>()), Panels.sanitize(messy))
    }

    @Test fun sanitizeTrimsExtraColumns() {
        val out = Panels.sanitize(listOf(listOf("clock"), listOf("deck"), listOf("calendar"), listOf("notifications")))
        assertEquals(3, out.size)
        assertTrue(out.flatten().none { it == "notifications" })
    }

    @Test fun moveUpAndDown() {
        val down = Panels.move(start, "clock", 0, 1)
        assertEquals(listOf("timers", "clock", "calendar"), down[0])
        assertEquals(start, Panels.move(start, "clock", 0, -1))   // already at top
    }

    @Test fun moveAcrossColumns() {
        val moved = Panels.move(start, "calendar", 1, 0)
        assertEquals(listOf("clock", "timers"), moved[0])
        assertEquals(listOf("notifications", "calendar"), moved[1])
        assertEquals(start, Panels.move(start, "deck", 1, 0))     // already in the last column
    }

    @Test fun hideAndShow() {
        val hiddenLayout = Panels.setVisible(start, "calendar", false)
        assertEquals(listOf("calendar"), Panels.hidden(hiddenLayout))
        val shown = Panels.setVisible(hiddenLayout, "calendar", true)
        assertTrue(Panels.hidden(shown).isEmpty())
        // goes to the shortest column (the first one of equal length)
        assertEquals(listOf("notifications", "calendar"), shown[1])
    }

    @Test fun everyPanelIsInTheDefaultLayout() {
        assertTrue(Panels.hidden(start).isEmpty())
    }
}
