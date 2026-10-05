package com.composepro.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.composepro.app.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a photo off the main thread. Shows [placeholder] (a grey square) until it arrives. */
@Composable
fun PhotoImage(
    uri: Uri,
    maxSide: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Color = Color(0xFFF2F2F2),
    description: String? = null,
) {
    val context = LocalContext.current
    var bmp by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, maxSide) { bmp = withContext(Dispatchers.IO) { PhotoStore.load(context, uri, maxSide) } }
    val b = bmp
    if (b == null) Box(modifier.background(placeholder))
    else Image(b.asImageBitmap(), description, modifier, contentScale = contentScale)
}
