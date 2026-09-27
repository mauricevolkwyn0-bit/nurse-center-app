package com.nursecenter.nurse.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Downloads small remote images (profile photos), scaled down and kept in memory for the session. */
private object ImageLoader {
    private const val MAX_SIZE_PX = 512

    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    // Sized in kilobytes; a few dozen avatars at most.
    private val cache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String): Bitmap? = cache.get(url) ?: withContext(Dispatchers.IO) {
        runCatching {
            val bytes = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body?.bytes()
            } ?: return@runCatching null
            // Decode at the smallest power-of-two scale that keeps the image at least MAX_SIZE_PX, so large photos don't waste memory.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_SIZE_PX && bounds.outHeight / (sample * 2) >= MAX_SIZE_PX) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()?.also { cache.put(url, it) }
    }
}

/**
 * Shows the image at [url] filling its parent, fading in once downloaded. Shows nothing while loading or if the
 * download fails, so place it over a fallback such as initials.
 */
@Composable
fun RemoteImage(url: String?, description: String?, modifier: Modifier = Modifier) {
    if (url == null) return
    val bitmap by produceState(ImageLoader.cached(url), url) { value = ImageLoader.load(url) }
    val image = bitmap?.let { remember(it) { it.asImageBitmap() } } ?: return
    val shown = remember(url) { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(visibleState = shown, enter = fadeIn(tween(250))) {
        Image(image, description, modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
