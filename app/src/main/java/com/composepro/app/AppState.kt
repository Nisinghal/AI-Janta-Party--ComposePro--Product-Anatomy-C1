package com.composepro.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen { Splash, FirstOpen, Tour, Camera, Review, Gallery, Settings }

/** A thing the person said the camera got wrong ("Not right?"). */
data class NotRight(val name: String, val atMillis: Long)

/** The photo on the Review screen, plus the tip that was showing when it was taken. */
data class PendingPhoto(val uri: Uri, val name: String, val tipReminder: String?)

/** Small things the app remembers between uses. Nothing here leaves the phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("composepro", Context.MODE_PRIVATE)

    var tourSeen: Boolean
        get() = sp.getBoolean("tourSeen", false)
        set(v) = sp.edit().putBoolean("tourSeen", v).apply()

    var tipsOn: Boolean
        get() = sp.getBoolean("tipsOn", true)
        set(v) = sp.edit().putBoolean("tipsOn", v).apply()

    /** Auto shot: the camera adjusts and takes the photo once the steps are done and the phone is held still. */
    var autoShot: Boolean
        get() = sp.getBoolean("autoShot", false)
        set(v) = sp.edit().putBoolean("autoShot", v).apply()

    /** Has this person ever kept a photo? Decides the empty Gallery's words (day one vs. emptied). */
    var hadPhotos: Boolean
        get() = sp.getBoolean("hadPhotos", false)
        set(v) = sp.edit().putBoolean("hadPhotos", v).apply()

    var notRight: List<NotRight>
        get() = (sp.getString("notRight", "") ?: "").lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val i = line.lastIndexOf('|')
            if (i <= 0) null else NotRight(line.substring(0, i), line.substring(i + 1).toLongOrNull() ?: 0L)
        }
        set(v) = sp.edit().putString("notRight", v.joinToString("\n") { "${it.name.replace("\n", " ")}|${it.atMillis}" }).apply()
}

class AppState(private val prefs: Prefs) {
    var screen by mutableStateOf(Screen.Splash)
    var tourSeen by mutableStateOf(prefs.tourSeen)
        private set
    var tipsOn by mutableStateOf(prefs.tipsOn)
        private set
    val notRight = mutableStateListOf<NotRight>().apply { addAll(prefs.notRight) }
    var pending by mutableStateOf<PendingPhoto?>(null)
    var hadPhotos by mutableStateOf(prefs.hadPhotos)
        private set
    var autoShot by mutableStateOf(prefs.autoShot)
        private set
    /** Where to go after the Quick tour: the first run goes to the Camera, a replay goes back to Settings. */
    var tourReturnsTo by mutableStateOf(Screen.Camera)

    fun markTourSeen() { tourSeen = true; prefs.tourSeen = true }
    fun markHadPhotos() { if (!hadPhotos) { hadPhotos = true; prefs.hadPhotos = true } }
    fun setTips(on: Boolean) { tipsOn = on; prefs.tipsOn = on }
    fun switchAutoShot(on: Boolean) { autoShot = on; prefs.autoShot = on }
    fun addNotRight(name: String) { notRight.add(0, NotRight(name, System.currentTimeMillis())); prefs.notRight = notRight.toList() }
    fun clearNotRight() { notRight.clear(); prefs.notRight = emptyList() }
}
