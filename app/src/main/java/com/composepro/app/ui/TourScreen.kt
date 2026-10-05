package com.composepro.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType
import kotlinx.coroutines.launch

private data class TourCard(val text: String, val edge: EdgeState, val warm: Boolean, val tip: Boolean)

private val cards = listOf(
    TourCard("Point at your food. If something's off, a tip appears.", EdgeState.Off, warm = true, tip = false),
    TourCard("Red edge: something's off, read the tip. Green edge with ✓: you've got it.", EdgeState.Off, warm = true, tip = true),
    TourCard("Tips are just tips. The shutter always works.", EdgeState.Right, warm = false, tip = false),
)

@Composable
fun TourScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(CP.Surface).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(top = CPSpace.S2, end = CPSpace.S2), horizontalArrangement = Arrangement.End) {
            LinkButton("Skip", onDone)
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val card = cards[page]
            Column(Modifier.fillMaxSize().padding(horizontal = CPSpace.S3, vertical = CPSpace.S1), verticalArrangement = Arrangement.spacedBy(CPSpace.S3)) {
                Box(Modifier.weight(1f).fillMaxWidth().clip(CPShape.Sheet).background(CP.Raised)) {
                    PlateIllustration(Modifier.fillMaxSize(), card.edge, card.warm)
                    if (card.tip) {
                        Column(
                            Modifier.align(Alignment.TopCenter).padding(top = CPSpace.S2).clip(CPShape.Pill).background(CP.Glass).padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Tilt toward the window.", style = CPType.CaptionMedium, color = CP.OnDark)
                            Text("Light's coming from above", style = CPType.Caption, color = CP.OnDark.copy(alpha = 0.75f))
                        }
                    }
                }
                Text(card.text, style = CPType.Heading, color = CP.Ink, modifier = Modifier.heightIn(min = 72.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = CPSpace.S3), horizontalArrangement = Arrangement.spacedBy(CPSpace.S1, Alignment.CenterHorizontally)) {
            repeat(cards.size) { i ->
                val on = pager.currentPage == i
                val w by animateDpAsState(if (on) 20.dp else 8.dp, label = "dot")
                val c by animateColorAsState(if (on) CP.Accent else CP.Muted.copy(alpha = 0.3f), label = "dotColor")
                Box(Modifier.height(8.dp).width(w).clip(CPShape.Pill).background(c))
            }
        }
        val last = pager.currentPage == cards.lastIndex
        PillButton(
            text = if (last) "Start shooting" else "Next",
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth().padding(start = CPSpace.S3, end = CPSpace.S3, bottom = CPSpace.S4),
        )
    }
}
