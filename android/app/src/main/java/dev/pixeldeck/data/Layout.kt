package dev.pixeldeck.data

/**
 * The panels that can be placed on the Home screen, and the pure helpers behind the layout organizer.
 * The layout is three columns; a panel that is in no column is hidden.
 */
object Panels {
    const val COLUMNS = 3

    val ALL: List<Pair<String, String>> = listOf(
        "clock" to "Clock",
        "timers" to "Timers and alarms",
        "calendar" to "Calendar",
        "notifications" to "Notifications",
        "deck" to "Stream deck",
    )

    /** Panels that only need as much height as their content; the rest share the remaining space. */
    val COMPACT = setOf("clock", "timers")

    val DEFAULT_LAYOUT: List<List<String>> = listOf(
        listOf("clock", "timers", "calendar"),
        listOf("notifications"),
        listOf("deck"),
    )

    fun label(key: String): String = ALL.firstOrNull { it.first == key }?.second ?: key

    private val KNOWN = ALL.map { it.first }.toSet()

    /** Drops unknown and duplicate keys and pads or trims to exactly [COLUMNS] columns. */
    fun sanitize(layout: List<List<String>>): List<List<String>> {
        val seen = HashSet<String>()
        val cleaned = layout.take(COLUMNS).map { column ->
            column.filter { it in KNOWN && seen.add(it) }
        }
        return cleaned + List(COLUMNS - cleaned.size) { emptyList() }
    }

    fun hidden(layout: List<List<String>>): List<String> {
        val visible = layout.flatten().toSet()
        return ALL.map { it.first }.filter { it !in visible }
    }

    /** Moves [key] one step: [dx] columns left/right and [dy] rows up/down. Returns the layout unchanged if impossible. */
    fun move(layout: List<List<String>>, key: String, dx: Int, dy: Int): List<List<String>> {
        val clean = sanitize(layout)
        val col = clean.indexOfFirst { key in it }
        if (col < 0) return clean
        val row = clean[col].indexOf(key)
        val out = clean.map { it.toMutableList() }
        if (dy != 0) {
            val target = row + dy
            if (target !in out[col].indices) return clean
            out[col][row] = out[col][target]
            out[col][target] = key
        }
        if (dx != 0) {
            val targetCol = col + dx
            if (targetCol !in out.indices) return clean
            out[col].removeAt(row)
            out[targetCol].add(minOf(row, out[targetCol].size), key)
        }
        return out
    }

    fun setVisible(layout: List<List<String>>, key: String, visible: Boolean): List<List<String>> {
        val clean = sanitize(layout)
        val present = clean.any { key in it }
        return when {
            visible && !present -> {
                // Put it at the end of the shortest column.
                val target = clean.indices.minByOrNull { clean[it].size } ?: 0
                clean.mapIndexed { i, c -> if (i == target) c + key else c }
            }
            !visible && present -> clean.map { c -> c.filter { it != key } }
            else -> clean
        }
    }
}
