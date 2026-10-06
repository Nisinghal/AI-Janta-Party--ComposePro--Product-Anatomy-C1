package com.composepro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType

@Composable
fun FirstOpenScreen(refused: Boolean, asking: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CP.Surface).safeDrawingPadding().padding(horizontal = CPSpace.S3, vertical = CPSpace.S4),
        verticalArrangement = Arrangement.spacedBy(CPSpace.S3),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(CPShape.Sheet).background(CP.Raised)) {
            PlateIllustration(Modifier.fillMaxSize(), EdgeState.Right)
        }
        Column(verticalArrangement = Arrangement.spacedBy(CPSpace.S1)) {
            Text("Compose Pro", style = CPType.Display, color = CP.Ink)
            Text("Point at your food. If something's off, you'll get a tip.", style = CPType.Body, color = CP.Ink)
        }
        Column(verticalArrangement = Arrangement.spacedBy(CPSpace.S2)) {
            if (refused) {
                ErrorLine("Compose Pro needs the camera to work. Turn it on in your phone's settings.")
                PillButton("Open settings", onOpenSettings, Modifier.fillMaxWidth(), primary = false)
            } else {
                PillButton("Allow camera", onAllow, Modifier.fillMaxWidth(), loading = asking)
            }
            Text("Your photos stay on your phone. \"Ask photographer\" sends one camera view to Google Gemini, only when you tap it.", style = CPType.Caption, color = CP.Muted)
        }
    }
}
