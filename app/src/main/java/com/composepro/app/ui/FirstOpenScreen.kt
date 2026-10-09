package com.composepro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType

/** The onboarding: the auto-playing showcase (IntroShowcase), then "Allow camera". Shown only until the camera is allowed. */
@Composable
fun FirstOpenScreen(refused: Boolean, asking: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(CP.Accent).safeDrawingPadding().padding(top = CPSpace.S3, bottom = CPSpace.S3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Compose Pro", style = CPType.Heading, color = CP.OnDark)
        IntroShowcase(Modifier.weight(1f).fillMaxWidth().padding(top = CPSpace.S2))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = CPSpace.S3).padding(top = CPSpace.S3),
            verticalArrangement = Arrangement.spacedBy(CPSpace.S2),
        ) {
            if (refused) {
                ErrorLine("Compose Pro needs the camera to work. Turn it on in your phone's settings.")
                PillButton("Open settings", onOpenSettings, Modifier.fillMaxWidth(), primary = false)
            } else {
                PillButton("Allow camera", onAllow, Modifier.fillMaxWidth(), primary = false, loading = asking)
            }
            Text(
                "Your photos stay on your phone. To suggest steps, the camera view is sent to Google Gemini while the camera is open.",
                style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.55f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
