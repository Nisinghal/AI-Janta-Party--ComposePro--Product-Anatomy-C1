package com.composepro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType

/** The same showcase as the first open, from Settings. One button back to the camera. */
@Composable
fun TourScreen(onDone: () -> Unit, autoShot: Boolean = false, onAutoShot: (Boolean) -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().background(CP.Accent).safeDrawingPadding().padding(top = CPSpace.S3, bottom = CPSpace.S4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Compose Pro", style = CPType.Heading, color = CP.OnDark)
        IntroShowcase(Modifier.weight(1f).fillMaxWidth().padding(top = CPSpace.S2))
        AutoShotRow(autoShot, onAutoShot, Modifier.padding(horizontal = CPSpace.S3).padding(top = CPSpace.S3))
        PillButton(
            "Start shooting", onDone, primary = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = CPSpace.S3).padding(top = CPSpace.S3),
        )
    }
}
