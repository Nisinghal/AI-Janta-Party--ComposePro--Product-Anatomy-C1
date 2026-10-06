package com.composepro.app.ui

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.composepro.app.AppState
import com.composepro.app.Screen
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(state: AppState) {
    var cleared by remember { mutableStateOf(false) }
    LaunchedEffect(cleared) { if (cleared) { delay(2000); cleared = false } }

    Box(Modifier.fillMaxSize().background(CP.Surface)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BackHeader("Settings") { state.screen = Screen.Camera }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = CPSpace.S3, end = CPSpace.S3, top = CPSpace.S1, bottom = CPSpace.S4),
                verticalArrangement = Arrangement.spacedBy(CPSpace.S4),
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CPShape.Card).background(CP.Raised)
                        .toggleable(value = state.tipsOn, role = Role.Switch) { state.setTips(it) }
                        .padding(start = 16.dp, end = CPSpace.S2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Tips", style = CPType.BodyMedium, color = CP.Ink)
                    Switch(state.tipsOn)
                }

                Column(verticalArrangement = Arrangement.spacedBy(CPSpace.S2)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Things you said weren't right", style = CPType.Heading, color = CP.Ink, modifier = Modifier.weight(1f))
                        if (state.notRight.isNotEmpty()) PillButton("Clear", { state.clearNotRight(); cleared = true }, primary = false, small = true)
                    }
                    if (state.notRight.isEmpty()) {
                        Text("Nothing here yet. When you tap \"Not right?\" on a tip, it shows up here.", style = CPType.Caption, color = CP.Muted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(CPSpace.S1)) {
                            state.notRight.forEach { n ->
                                Column(Modifier.fillMaxWidth().clip(CPShape.Card).background(CP.Raised).padding(horizontal = 16.dp, vertical = CPSpace.S2)) {
                                    Text(n.name, style = CPType.CardTitle, color = CP.Ink)
                                    Text(DateUtils.getRelativeTimeSpanString(n.atMillis).toString(), style = CPType.Caption, color = CP.Muted)
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CPShape.Card).background(CP.Raised)
                        .clickable(role = Role.Button) { state.tourReturnsTo = Screen.Settings; state.screen = Screen.Tour }
                        .padding(start = 16.dp, end = CPSpace.S2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Replay the quick tour", style = CPType.BodyMedium, color = CP.Ink)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = CP.Muted)
                }

                Text("Your photos stay on your phone. \"Ask photographer\" sends one camera view to Claude, only when you tap it.", style = CPType.Caption, color = CP.Muted)
            }
        }
        if (cleared) GlassToast("Cleared.", Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = CPSpace.S4))
    }
}

@Composable
private fun Switch(on: Boolean) {
    val track by animateColorAsState(if (on) CP.Accent else CP.Muted.copy(alpha = 0.35f), label = "track")
    val x by animateDpAsState(if (on) 20.dp else 0.dp, label = "thumb")
    Box(Modifier.size(width = 52.dp, height = 32.dp).clip(CPShape.Pill).background(track)) {
        Box(Modifier.offset(x = 3.dp + x, y = 3.dp).size(26.dp).clip(CPShape.Pill).background(CP.Surface))
    }
}
