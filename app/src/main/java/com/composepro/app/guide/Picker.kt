package com.composepro.app.guide

import com.composepro.app.camera.Thing

/** What the camera decided after its 3-second look: the guide, how many things it's laid out for, and a plain explanation. */
data class GuidePick(val guide: Guide, val slots: Int, val seen: String, val why: String)

private val animals = setOf("bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe")
private val round = setOf("bowl", "pizza", "cake", "donut", "clock", "orange", "apple", "frisbee", "cup", "sports ball")

/**
 * Picks a guide from what was seen (GUIDANCE.md §7, extended 2026-10-05 with grid, circle and front & back,
 * which are the flat-lay and product layouts that work for any object, not just food).
 * things are largest first.
 */
fun pickGuide(things: List<Thing>, fromAbove: Boolean): GuidePick? {
    if (things.isEmpty()) return null
    val n = things.size.coerceAtMost(6)
    val seen = describe(things.take(n))
    val main = things[0]
    val name = main.category
    fun pick(g: Guide, why: String) = GuidePick(g, n, seen, why)

    return when (n) {
        1 -> when {
            name in animals -> pick(Guide.Thirds, "Animals look best off-centre, with space on the side they're facing.")
            fromAbove && (name in round || main.box.width() / main.box.height() in 0.75f..1.33f) ->
                pick(Guide.Centre, "One round thing from above sits best in the middle.")
            fromAbove -> pick(Guide.Centre, "One thing from above sits best in the middle.")
            else -> pick(Guide.Thirds, "One thing looks more alive a little off-centre.")
        }
        2 -> if (main.area >= 2f * things[1].area) pick(Guide.FrontBack, "One big, one small: big in front, small behind for depth.")
        else pick(Guide.Diagonal, "Two things look balanced along a diagonal.")
        3 -> pick(Guide.Triangle, "Three things make a triangle, which leads the eye around the photo.")
        else -> {
            val common = things.take(n).groupingBy { it.category }.eachCount().maxOf { it.value }
            when {
                common >= n - 1 -> pick(Guide.Grid, "Lots of the same thing look neat in rows.")
                main.area >= 2f * things[1].area -> pick(Guide.Circle, "One big thing with the rest around it.")
                else -> pick(Guide.Spiral, "A mixed spread: the biggest in the spiral's eye, the rest along the curve.")
            }
        }
    }
}

/** "2 cups and a bowl", "a laptop", "3 things". */
fun describe(things: List<Thing>): String {
    val named = things.mapNotNull { it.category }
    if (named.size < things.size) return if (things.size == 1) "1 thing" else "${things.size} things"
    val groups = named.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    val parts = groups.map { (word, k) ->
        if (k == 1) (if (word.first() in "aeiou") "an $word" else "a $word") else "$k ${plural(word)}"
    }
    return if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}

private fun plural(word: String) = when {
    word == "knife" -> "knives"
    word == "mouse" -> "mice"
    word == "sheep" -> "sheep"
    word.endsWith("s") || word.endsWith("ch") || word.endsWith("sh") -> word + "es"
    else -> word + "s"
}
