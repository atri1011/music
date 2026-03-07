package com.music.myapplication.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.myapplication.domain.model.LyricLine
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    activeLineColor: Color = Color.White,
    inactiveLineColor: Color = Color.White.copy(alpha = 0.4f),
    translationColor: Color = Color.White.copy(alpha = 0.6f),
    scrimColor: Color = Color.Transparent,
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
    var lastAutoScrollIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(currentIndex, userDragging) {
        if (userDragging || currentIndex < 0 || currentIndex == lastAutoScrollIndex) return@LaunchedEffect
        val distance = if (lastAutoScrollIndex >= 0) abs(currentIndex - lastAutoScrollIndex) else 0
        if (distance <= 1) {
            listState.scrollToItem(index = currentIndex, scrollOffset = -300)
        } else {
            listState.animateScrollToItem(index = currentIndex, scrollOffset = -300)
        }
        lastAutoScrollIndex = currentIndex
    }

    LaunchedEffect(userDragging) {
        if (userDragging) {
            delay(3000)
            userDragging = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { userDragging = true },
                    onDrag = { _, _ -> }
                )
            }
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

                val targetAlpha = when {
                    isCurrent -> 1f
                    distance == 1 -> 0.5f
                    distance == 2 -> 0.35f
                    else -> 0.2f
                }
                val animatedAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(400),
                    label = "lyricAlpha_$index"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = animatedAlpha }
                        .padding(
                            top = if (isCurrent) 14.dp else 8.dp,
                            bottom = if (isCurrent) 14.dp else 8.dp
                        )
                ) {
                    // Original lyric text
                    Text(
                        text = line.text,
                        style = if (isCurrent) {
                            MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                lineHeight = 30.sp
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 22.sp
                            )
                        },
                        color = if (isCurrent) activeLineColor else inactiveLineColor,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Translation text (if available)
                    if (line.translation.isNotBlank()) {
                        Text(
                            text = line.translation,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = if (isCurrent) 14.sp else 13.sp,
                                lineHeight = 20.sp
                            ),
                            color = translationColor,
                            textAlign = TextAlign.Start,
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
