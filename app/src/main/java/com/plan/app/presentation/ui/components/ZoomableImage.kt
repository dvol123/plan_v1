package com.plan.app.presentation.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.max
import kotlin.math.min

/**
 * A zoomable image composable that supports pinch-to-zoom and panning.
 */
@Composable
fun ZoomableImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onSizeChanged: (IntSize) -> Unit = {},
    onDoubleTap: ((Offset) -> Unit)? = null,
    onSingleTap: ((Offset) -> Unit)? = null,
    isZoomEnabled: Boolean = true
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isZoomEnabled) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = max(0.5f, min(5f, scale * zoom))
                            
                            // Limit panning based on scale
                            val maxOffsetX = (size.width * (scale - 1) / 2)
                            val maxOffsetY = (size.height * (scale - 1) / 2)
                            
                            offsetX = max(-maxOffsetX, min(maxOffsetX, offsetX + pan.x))
                            offsetY = max(-maxOffsetY, min(maxOffsetY, offsetY + pan.y))
                        }
                    }
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (onDoubleTap != null) {
                            onDoubleTap(offset)
                        } else if (isZoomEnabled) {
                            // Toggle zoom on double tap
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2f
                            }
                        }
                    },
                    onTap = onSingleTap
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(model)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = contentScale
        )
    }
}

/**
 * Reset zoom to default values.
 */
fun resetZoom(scale: MutableFloatState, offsetX: MutableFloatState, offsetY: MutableFloatState) {
    scale.floatValue = 1f
    offsetX.floatValue = 0f
    offsetY.floatValue = 0f
}
