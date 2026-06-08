package com.music.myapplication.feature.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.music.myapplication.MainActivity
import com.music.myapplication.R
import com.music.myapplication.media.service.MusicPlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MusicPlaybackWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(width = 250.dp, height = 56.dp),
            DpSize(width = 250.dp, height = 120.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = MusicPlaybackWidgetStateStore.read(context)
        provideContent {
            MusicPlaybackWidgetContent(snapshot = snapshot)
        }
    }
}

class MusicPlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicPlaybackWidget()
}

@Composable
private fun MusicPlaybackWidgetContent(snapshot: MusicPlaybackWidgetSnapshot) {
    val context = LocalContext.current
    val widgetSize = LocalSize.current
    val isTall = widgetSize.height >= 96.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(R.color.widget_background))
            .appWidgetBackground()
            .cornerRadius(18.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(horizontal = 14.dp, vertical = if (isTall) 12.dp else 8.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        if (isTall) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                WidgetArtworkPlaceholder()
                Spacer(modifier = GlanceModifier.width(12.dp))
                TrackTexts(
                    snapshot = snapshot,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
            WidgetControls(
                snapshot = snapshot,
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.End
            )
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                WidgetArtworkPlaceholder(size = 44)
                Spacer(modifier = GlanceModifier.width(10.dp))
                TrackTexts(
                    snapshot = snapshot,
                    modifier = GlanceModifier.defaultWeight()
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                WidgetControls(snapshot = snapshot)
            }
        }
    }
}

@Composable
private fun WidgetArtworkPlaceholder(size: Int = 52) {
    Box(
        modifier = GlanceModifier
            .size(size.dp)
            .cornerRadius(12.dp)
            .background(ColorProvider(R.color.widget_surface)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_music_note),
            contentDescription = "音乐",
            modifier = GlanceModifier.size((size - 20).coerceAtLeast(20).dp)
        )
    }
}

@Composable
private fun TrackTexts(
    snapshot: MusicPlaybackWidgetSnapshot,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(modifier = modifier) {
        Text(
            text = snapshot.title,
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(R.color.widget_on_surface),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(3.dp))
        Text(
            text = if (snapshot.hasTrack) snapshot.artist else "打开应用开始播放",
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(R.color.widget_on_surface_variant),
                fontSize = 12.sp
            )
        )
    }
}

@Composable
private fun WidgetControls(
    snapshot: MusicPlaybackWidgetSnapshot,
    modifier: GlanceModifier = GlanceModifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Horizontal.CenterHorizontally
) {
    Row(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        WidgetControlButton(
            icon = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            contentDescription = if (snapshot.isPlaying) "暂停" else "播放",
            action = MusicPlaybackWidgetAction.TogglePlayPause
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        WidgetControlButton(
            icon = R.drawable.ic_widget_next,
            contentDescription = "下一首",
            action = MusicPlaybackWidgetAction.Next
        )
    }
}

@Composable
private fun WidgetControlButton(
    icon: Int,
    contentDescription: String,
    action: MusicPlaybackWidgetAction
) {
    Box(
        modifier = GlanceModifier
            .size(44.dp)
            .cornerRadius(22.dp)
            .background(ColorProvider(Color(0xFF22C55E)))
            .clickable(
                actionRunCallback<MusicPlaybackWidgetActionCallback>(
                    parameters = actionParametersOf(WidgetActionKey to action.name)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(22.dp)
        )
    }
}

private enum class MusicPlaybackWidgetAction {
    TogglePlayPause,
    Next
}

private val WidgetActionKey = ActionParameters.Key<String>("music_playback_widget_action")

class MusicPlaybackWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val action = parameters[WidgetActionKey]
            ?.let { runCatching { MusicPlaybackWidgetAction.valueOf(it) }.getOrNull() }
            ?: return

        withMediaController(context.applicationContext) { controller ->
            when (action) {
                MusicPlaybackWidgetAction.TogglePlayPause -> {
                    if (controller.isPlaying) controller.pause() else controller.play()
                }
                MusicPlaybackWidgetAction.Next -> {
                    if (controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
                        controller.seekToNextMediaItem()
                    } else {
                        controller.seekToNext()
                    }
                }
            }
        }
    }

    private suspend fun withMediaController(
        context: Context,
        block: (MediaController) -> Unit
    ) = withContext(Dispatchers.IO) {
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        try {
            val controller = future.get(3, TimeUnit.SECONDS)
            block(controller)
        } finally {
            MediaController.releaseFuture(future)
        }
    }
}
