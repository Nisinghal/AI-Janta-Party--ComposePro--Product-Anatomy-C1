package com.composepro.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composepro.app.AppState
import com.composepro.app.Screen
import com.composepro.app.data.PhotoStore
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPShape
import com.composepro.app.ui.theme.CPSpace
import com.composepro.app.ui.theme.CPType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = CPSpace.S1, end = CPSpace.S3, top = CPSpace.S2, bottom = CPSpace.S1), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(CPSpace.Tap).clip(CPShape.Pill).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to the camera", tint = CP.Ink)
        }
        Text(title, style = CPType.Display, color = CP.Ink)
    }
}

@Composable
fun GalleryScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photos = remember { mutableStateListOf<Uri>() }
    var loaded by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    var undo by remember { mutableStateOf<Pair<Int, Uri>?>(null) }
    var undoJob by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { PhotoStore.list(context) }
        photos.clear(); photos.addAll(list); loaded = true
    }
    LaunchedEffect(message) { if (message != null) { delay(3000); message = null } }
    BackHandler(enabled = viewing != null) { viewing = null }

    /** Delete waits 4 seconds so "Undo" can bring it back; only then is the file removed. */
    fun delete(index: Int) {
        val uri = photos.removeAt(index)
        undoJob?.cancel()
        undo?.let { (_, prev) -> scope.launch(Dispatchers.IO) { PhotoStore.delete(context, prev) } }
        undo = index to uri
        viewing = if (photos.isEmpty()) null else index.coerceAtMost(photos.lastIndex)
        undoJob = scope.launch {
            delay(4000)
            val ok = withContext(Dispatchers.IO) { PhotoStore.delete(context, uri) }
            if (!ok) {
                photos.add(index.coerceAtMost(photos.size), uri)
                message = "Couldn't delete that photo. Delete it from your phone's gallery instead."
            }
            undo = null
        }
    }

    Box(Modifier.fillMaxSize().background(CP.Surface)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            BackHeader("Your photos") { state.screen = Screen.Camera }
            when {
                !loaded -> Unit
                photos.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(CPSpace.S4),
                    verticalArrangement = Arrangement.spacedBy(CPSpace.S3, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (state.hadPhotos) "No photos here." else "Your photos will show up here.", style = CPType.Heading, color = CP.Ink, textAlign = TextAlign.Center)
                    if (!state.hadPhotos) PillButton("Take your first photo", { state.screen = Screen.Camera })
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = CPSpace.S3, end = CPSpace.S3, top = CPSpace.S1, bottom = CPSpace.S4),
                    horizontalArrangement = Arrangement.spacedBy(CPSpace.S1),
                    verticalArrangement = Arrangement.spacedBy(CPSpace.S1),
                ) {
                    itemsIndexed(photos, key = { _, u -> u.toString() }) { i, uri ->
                        PhotoImage(
                            uri, 360,
                            Modifier.aspectRatio(3f / 4f).clip(CPShape.Thumb).clickable(role = Role.Button) { viewing = i },
                            description = "Photo ${i + 1} of ${photos.size}",
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = viewing != null, enter = fadeIn() + scaleIn(initialScale = 0.98f), exit = fadeOut()) {
            val start = viewing ?: 0
            val pager = rememberPagerState(initialPage = start) { photos.size }
            LaunchedEffect(viewing) { viewing?.let { if (it != pager.currentPage && it in photos.indices) pager.scrollToPage(it) } }
            Box(Modifier.fillMaxSize().background(CP.CameraBar)) {
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    if (page in photos.indices) PhotoImage(photos[page], 2000, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, placeholder = CP.CameraBar, description = "Photo ${page + 1} of ${photos.size}")
                }
                Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(CPSpace.S2), horizontalArrangement = Arrangement.SpaceBetween) {
                    GlassRound("Back to your photos", { viewing = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = CP.OnDark, modifier = Modifier.size(20.dp)) }
                    GlassRound("Delete this photo", { if (pager.currentPage in photos.indices) delete(pager.currentPage) }) { Icon(Icons.Filled.Delete, null, tint = CP.OnDark, modifier = Modifier.size(20.dp)) }
                }
            }
        }

        undo?.let { (i, uri) ->
            Row(
                Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = CPSpace.S4).clip(CPShape.Pill).background(CP.Glass).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CPSpace.S2),
            ) {
                Text("Deleted", style = CPType.CaptionMedium, color = CP.OnDark)
                Box(Modifier.clip(CPShape.Pill).background(CP.OnDark).clickable(role = Role.Button) {
                    undoJob?.cancel(); photos.add(i.coerceAtMost(photos.size), uri); undo = null
                }.padding(horizontal = CPSpace.S2, vertical = 8.dp)) { Text("Undo", style = CPType.CaptionMedium, color = CP.Ink) }
            }
        }
        message?.let { GlassToast(it, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = CPSpace.S4)) }
    }
}

@Composable
fun GlassRound(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(CPSpace.Tap).clip(CPShape.Pill).background(CP.Glass).clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
