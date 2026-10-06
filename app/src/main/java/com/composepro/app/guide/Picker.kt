package com.composepro.app.guide

import com.composepro.app.camera.Thing

/** The phone angle a subject looks best from (GUIDANCE.md §3). Any = no angle advice. */
enum class Angle { Above, Diner, Eye, Any }

/**
 * What the camera decided after its 3-second look: the guide, how many things it's laid out for,
 * the best phone angle for the main subject, and a plain explanation.
 */
data class GuidePick(val guide: Guide, val slots: Int, val seen: String, val why: String, val angle: Angle, val subject: String?) {
    /** Two or more separate things: there's an arrangement to make as well as a phone position. */
    val arranging get() = slots >= 2
}

val animals = setOf("bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe")
private val round = setOf("bowl", "pizza", "cake", "donut", "clock", "orange", "apple", "frisbee", "cup", "sports ball")

/** Flat things show their shape best from straight above. */
private val flat = setOf("pizza", "bowl", "laptop", "keyboard", "book", "phone", "remote", "sandwich", "donut", "scissors", "mouse", "orange", "apple", "banana", "frisbee")

/** Tall things lose their shape from above: shoot from the side, at their height. */
private val tall = setOf(
    "bottle", "wine glass", "vase", "plant", "chair", "fire hydrant", "parking meter", "stop sign", "traffic light",
    "umbrella", "clock", "TV", "refrigerator", "bicycle", "motorcycle", "car", "bus", "truck", "boat", "train", "bench", "teddy bear",
)

private fun angleFor(name: String?): Angle = when (name) {
    in animals, in tall -> Angle.Eye
    in flat -> Angle.Above
    "cup", "cake", "hot dog", "broccoli", "carrot" -> Angle.Diner
    else -> Angle.Any
}

/**
 * Picks a guide from what was seen (GUIDANCE.md §7 and §9). things are largest first.
 * One thing: a framing guide (centre or thirds) you reach by moving the phone.
 * Two or more: an arrangement guide (front & back, diagonal, triangle, grid, circle, spiral) plus phone tips.
 */
fun pickGuide(things: List<Thing>, fromAbove: Boolean): GuidePick? {
    if (things.isEmpty()) return null
    val n = things.size.coerceAtMost(6)
    val seen = describe(things.take(n))
    val main = things[0]
    val name = main.category
    // A group: mostly tall things → from the side; otherwise a flat lay from above, like the reference images.
    val angle = if (n == 1) angleFor(name) else if (things.take(n).count { it.category in tall } * 2 > n) Angle.Eye else Angle.Above
    fun pick(g: Guide, why: String) = GuidePick(g, n, seen, why, angle, name)

    return when (n) {
        1 -> when {
            name in animals -> pick(Guide.Thirds, "Animals look best off-centre, with space on the side they're facing.")
            name in tall -> pick(Guide.Thirds, "Tall things look best from the side, a little off-centre.")
            angle == Angle.Above || fromAbove -> pick(Guide.Centre, if (name in round) "One round thing from above sits best in the middle." else "One thing from above sits best in the middle.")
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
