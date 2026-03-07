package com.music.myapplication.feature.player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    activeLineColor: Color = Color(0xFF161A1F),
    inactiveLineColor: Color = Color(0xFF7A828B),
    scrimColor: Color = MaterialTheme.colorScheme.background,
    modifier: Modifier = Modifier
) {
    if (lyrics.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "暂无歌词",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF6A727A)
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
            listState.scrollToItem(index = currentIndex, scrollOffset = -200)
        } else {
            listState.animateScrollToItem(index = currentIndex, scrollOffset = -200)
        }
        lastAutoScrollIndex = currentIndex
    }

    LaunchedEffect(userDragging) {
        if (userDragging) {
            delay(3000)
            userDragging = false
        }
    }

    // Use transparent scrim when in player (scrimColor == Transparent)
    val useTransparentScrim = scrimColor == Color.Transparent
    val topScrimColor = if (useTransparentScrim) Color(0xFFEDEFF2) else scrimColor
    val bottomScrimColor = if (useTransparentScrim) Color(0xFFE7EBEE) else scrimColor

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
            itemsIndexed(lyrics, key = { index, _ -> index }) { index, line ->
                val isCurrent = index == currentIndex
                Text(
                    text = line.text,
                    style = if (isCurrent) {
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (isCurrent) {
                        activeLineColor
                    } else {
                        inactiveLineColor
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = if (isCurrent) 12.dp else 8.dp,
                            horizontal = 24.dp
                        )
                )
            }
        }

        // Top fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.TopCenter)
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(topScrimColor, Color.Transparent)
                        )
                    )
                }
        )

        // Bottom fade
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomCenter)
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, bottomScrimColor)
                        )
                    )
                }
        )
    }
}
