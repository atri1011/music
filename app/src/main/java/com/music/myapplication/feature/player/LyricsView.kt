package com.music.myapplication.feature.player

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.myapplication.domain.model.LyricLine
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    activeLineColor: Color = Color.White,
    inactiveLineColor: Color = Color.White,
    translationColor: Color = Color.White.copy(alpha = 0.7f),
    scrimColor: Color = Color.Transparent,
    onLineLongPress: (LyricLine) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
        return
    }

    val listState = rememberLazyListState()
    var userDragging by remember { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }
    var lastAutoScrollIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(currentIndex, userDragging) {
        if (userDragging || currentIndex < 0 || currentIndex == lastAutoScrollIndex) return@LaunchedEffect
        val distance = if (lastAutoScrollIndex >= 0) abs(currentIndex - lastAutoScrollIndex) else 0
        autoScrolling = true
        try {
            if (distance <= 1) {
                listState.scrollToItem(index = currentIndex, scrollOffset = -300)
            } else {
                listState.animateScrollToItem(index = currentIndex, scrollOffset = -300)
            }
            lastAutoScrollIndex = currentIndex
        } finally {
            autoScrolling = false
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress to autoScrolling }
            .collectLatest { (isScrolling, isAutoScrolling) ->
                if (isScrolling && !isAutoScrolling) {
                    userDragging = true
                } else if (!isScrolling && userDragging) {
                    delay(3000)
                    userDragging = false
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            // Top spacer for initial scroll offset
            item { Spacer(modifier = Modifier.height(40.dp)) }

            itemsIndexed(lyrics, key = { index, _ -> index }) { index, line ->
                val isCurrent = index == currentIndex
                val distance = abs(index - currentIndex)

                // NetEase-style smooth fade: 6-level gradient
                val targetAlpha = when {
                    isCurrent -> 1f
                    distance == 1 -> 0.50f
                    distance == 2 -> 0.38f
                    distance == 3 -> 0.28f
                    distance == 4 -> 0.20f
                    else -> 0.14f
                }
                val animatedAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "lyricAlpha_$index"
                )

                // Scale animation: current line larger, distant lines shrink
                val targetScale = when {
                    isCurrent -> 1.08f
                    distance == 1 -> 1.0f
                    distance == 2 -> 0.97f
                    else -> 0.95f
                }
                val animatedScale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "lyricScale_$index"
                )

                // Enhanced blur gradient for distant lines (API 31+)
                val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    when {
                        distance <= 1 -> Modifier
                        distance == 2 -> Modifier.blur(1.dp)
                        distance == 3 -> Modifier.blur(2.dp)
                        else -> Modifier.blur(3.dp)
                    }
                } else {
                    Modifier
                }

                // Enhanced shadow for current line: stronger glow
                val lyricShadow = if (isCurrent) {
                    Shadow(
                        color = activeLineColor.copy(alpha = 0.4f),
                        offset = Offset(0f, 1f),
                        blurRadius = 8f
                    )
                } else {
                    Shadow(
                        color = Color.Black.copy(alpha = 0.45f),
                        offset = Offset(0f, 1f),
                        blurRadius = 4f
                    )
                }

                // Animated vertical padding for rhythm feel
                val targetVerticalPadding = when {
                    isCurrent -> 20.dp
                    distance == 1 -> 14.dp
                    else -> 10.dp
                }
                val animatedVerticalPadding by animateDpAsState(
                    targetValue = targetVerticalPadding,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "lyricPadding_$index"
                )

                // Glow effect behind current line text
                val glowModifier = if (isCurrent) {
                    val glowColor = activeLineColor.copy(alpha = 0.3f)
                    Modifier.drawBehind {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().asFrameworkPaint().apply {
                                isAntiAlias = true
                                color = glowColor.toArgb()
                                maskFilter = android.graphics.BlurMaskFilter(
                                    48f,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                            }
                            canvas.nativeCanvas.drawRoundRect(
                                0f,
                                size.height * 0.2f,
                                size.width,
                                size.height * 0.8f,
                                size.height / 2f,
                                size.height / 2f,
                                paint
                            )
                        }
                    }
                } else {
                    Modifier
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(line.text, line.translation) {
                            detectTapGestures(
                                onLongPress = {
                                    if (line.text.isNotBlank()) {
                                        onLineLongPress(line)
                                    }
                                }
                            )
                        }
                        .graphicsLayer {
                            alpha = animatedAlpha
                            scaleX = animatedScale
                            scaleY = animatedScale
                        }
                        .then(blurModifier)
                        .then(glowModifier)
                        .padding(vertical = animatedVerticalPadding)
                ) {
                    // Main lyric text
                    Text(
                        text = line.text,
                        style = if (isCurrent) {
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 22.sp,
                                lineHeight = 30.sp,
                                shadow = lyricShadow
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                shadow = lyricShadow
                            )
                        },
                        color = if (isCurrent) activeLineColor else inactiveLineColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Translation text
                    if (line.translation.isNotBlank()) {
                        Text(
                            text = line.translation,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = if (isCurrent) 14.sp else 13.sp,
                                lineHeight = if (isCurrent) 20.sp else 18.sp,
                                shadow = lyricShadow
                            ),
                            color = translationColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                }
            }

            // Bottom spacer
            item { Spacer(modifier = Modifier.height(200.dp)) }
        }
    }
}
