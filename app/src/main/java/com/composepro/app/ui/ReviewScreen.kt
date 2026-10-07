package com.composepro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import com.composepro.app.AppState
import com.composepro.app.Screen
import com.composepro.app.data.PhotoStore
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType

/** Keep or take again. Waits for a tap (decided 2026-10-04). The photo is already saved; "Take again" removes it. */
@Composable
fun ReviewScreen(state: AppState) {
    val context = LocalContext.current
    val photo = state.pending
    if (photo == null) { LaunchedEffect(Unit) { state.screen = Screen.Camera }; return }
    Column(
        Modifier.fillMaxSize().background(CP.Surface).safeDrawingPadding().padding(start = CPSpace.S3, end = CPSpace.S3, top = CPSpace.S3, bottom = CPSpace.S4),
        verticalArrangement = Arrangement.spacedBy(CPSpace.S3),
    ) {
        // The whole photo, never trimmed: it used to be cropped to fill its box, so the subject looked bigger than on
        // the camera screen (group feedback 2026-10-07, watch screenshots). Same white space before and after.
        PhotoImage(
            photo.uri, 1600, Modifier.weight(1f).fillMaxWidth(),
            contentScale = ContentScale.Fit, description = "The photo you just took",
        )
        Column(verticalArrangement = Arrangement.spacedBy(CPSpace.S2)) {
            photo.tipReminder?.let { Text(it, style = CPType.Caption, color = CP.Muted) }
            Row(horizontalArrangement = Arrangement.spacedBy(CPSpace.S2)) {
                PillButton("Keep", { state.markHadPhotos(); state.pending = null; state.screen = Screen.Camera }, Modifier.weight(1f))
                PillButton("Take again", {
                    PhotoStore.delete(context, photo.uri)
                    state.pending = null
                    state.screen = Screen.Camera
                }, Modifier.weight(1f), primary = false)
            }
        }
    }
}
