package com.composepro.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.composepro.app.ui.CameraScreen
import com.composepro.app.ui.FirstOpenScreen
import com.composepro.app.ui.GalleryScreen
import com.composepro.app.ui.ReviewScreen
import com.composepro.app.ui.SettingsScreen
import com.composepro.app.ui.TourScreen
import com.composepro.app.ui.theme.CP
import com.composepro.app.ui.theme.CPType
import com.composepro.app.ui.theme.ComposeProTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val state = AppState(Prefs(applicationContext))
        setContent { ComposeProTheme { AppRoot(state) } }
    }
}

@Composable
fun AppRoot(state: AppState) {
    val context = LocalContext.current
    fun hasCamera() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    fun afterPermission() { state.screen = if (state.tourSeen) Screen.Camera else Screen.Tour }

    var refused by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        asking = false
        if (granted) { refused = false; afterPermission() } else refused = true
    }

    LaunchedEffect(Unit) { if (hasCamera()) afterPermission() }
    // Coming back from the phone's settings after turning the camera on.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state.screen == Screen.FirstOpen && refused && hasCamera()) { refused = false; afterPermission() }
    }

    BackHandler(enabled = state.screen in setOf(Screen.Review, Screen.Gallery, Screen.Settings)) { state.screen = Screen.Camera }

    AnimatedContent(
        targetState = state.screen,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.FirstOpen -> FirstOpenScreen(
                refused = refused,
                asking = asking,
                onAllow = { asking = true; askCamera.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                },
            )
            Screen.Tour -> TourScreen(onDone = {
                state.markTourSeen()
                state.screen = state.tourReturnsTo
                state.tourReturnsTo = Screen.Camera
            })
            Screen.Camera -> CameraScreen(state)
            Screen.Review -> ReviewScreen(state)
            Screen.Gallery -> GalleryScreen(state)
            Screen.Settings -> SettingsScreen(state)
        }
    }
}
