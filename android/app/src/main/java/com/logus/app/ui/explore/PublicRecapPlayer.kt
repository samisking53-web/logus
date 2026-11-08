package com.logus.app.ui.explore

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.logus.app.explore.PublicClip
import com.logus.app.explore.PublicRecap
import com.logus.app.journey.Member
import com.logus.app.record.VideoCache
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.MemberColors
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.PhotoScrim
import com.logus.app.ui.theme.Primary
import com.logus.app.ui.theme.PrimaryLight
import com.logus.app.ui.theme.PrimaryStrong
import kotlinx.coroutines.delay

/** 한 장면 칸 수 상한(DAY RECAP 과 같은 4칸) */
private const val MAX_CLIPS = 4

/**
 * S08·S09 공개 DAY RECAP 재생(스토리보드 v5 21쪽: 같은 시간대 구성원 기록을 위·가운데·아래로 나눠 재생).
 * - active(지금 화면에 보이는 영상)이면 저절로 재생하고, 장면이 끝나면 다음 장면, 마지막 장면 뒤에는 처음부터 다시(자동 재생).
 * - 칸마다 그 사람의 영상을 동시에 재생한다. 소리는 맨 위 칸만. 영상을 누르면 멈춤·다시 재생.
 * - 재생기는 active 일 때만 만들고, 다른 영상으로 넘기거나 화면을 닫으면 정리한다(동시에 여러 영상 디코더를 잡지 않게).
 * - 영상은 폰 안 캐시(VideoCache)를 거쳐 받아서 한 번 본 영상은 다시 받지 않는다.
 */
@Composable
fun PublicRecapPlayer(
    recap: PublicRecap,
    active: Boolean,
    publicUrl: (String) -> String,
    onProfileClick: (Member) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var sceneIndex by remember(recap.id) { mutableIntStateOf(0) }
    var paused by remember(recap.id) { mutableStateOf(false) }
    // 장면을 넘길 때마다 1씩 늘린다(장면이 하나뿐이어도 처음부터 다시 재생되게)
    var round by remember(recap.id) { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var players by remember { mutableStateOf<List<ExoPlayer>>(emptyList()) }
    val scene = recap.scenes[sceneIndex.coerceIn(0, recap.scenes.lastIndex)]
    val clips = scene.clips.take(MAX_CLIPS)
    val playing = active && !paused && players.isNotEmpty()

    // 보이는 동안만 재생기를 만든다
    DisposableEffect(active, recap.id) {
        if (active) players = List(MAX_CLIPS) { VideoCache.newPlayer(context) }
        onDispose {
            players.forEach { it.release() }
            players = emptyList()
            progress = 0f
        }
    }

    // 앱을 내리면 멈춘다
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) paused = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 장면이 바뀌면 칸마다 영상을 넣고 함께 재생한다
    LaunchedEffect(players, sceneIndex, round) {
        if (players.isEmpty()) return@LaunchedEffect
        progress = 0f
        players.forEachIndexed { i, player ->
            val clip = clips.getOrNull(i)
            if (clip == null) {
                player.stop()
                player.clearMediaItems()
            } else {
                player.setMediaItem(VideoCache.mediaItem(publicUrl(clip.videoPath), clip.videoPath))
                player.volume = if (i == 0) 1f else 0f
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.prepare()
                player.seekTo(0)
            }
        }
    }

    // 재생·멈춤
    LaunchedEffect(players, playing, sceneIndex, round) {
        players.take(clips.size).forEach { it.playWhenReady = playing }
    }

    // 모두 끝나면 다음 장면(마지막이면 처음으로). 진행 막대는 맨 위 영상 기준
    LaunchedEffect(players, sceneIndex, round, playing) {
        if (!playing) return@LaunchedEffect
        val used = players.take(clips.size)
        delay(300)
        while (true) {
            val top = used.firstOrNull()
            if (top != null && top.duration > 0) progress = (top.currentPosition.toFloat() / top.duration).coerceIn(0f, 1f)
            val done = used.all {
                it.playbackState == Player.STATE_ENDED || it.playerError != null ||
                    (it.playbackState == Player.STATE_IDLE && it.mediaItemCount == 0)
            }
            if (done) {
                sceneIndex = (sceneIndex + 1) % recap.scenes.size
                round++
                break
            }
            delay(150)
        }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BackgroundDark)
            .clickable(enabled = active) { paused = !paused }
            .semantics {
                contentDescription = "${recap.journeyName} DAY RECAP, ${scene.label} 장면 ${sceneIndex + 1} / ${recap.scenes.size}. " +
                    if (playing) "누르면 멈춰요" else "누르면 재생해요"
            },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            clips.forEachIndexed { i, clip ->
                ClipPane(
                    clip = clip,
                    member = recap.member(clip.authorId),
                    colorIndex = recap.colorIndex(clip.authorId),
                    player = players.getOrNull(i)?.takeIf { active },
                    // 혼자면 영상을 자르지 않고 원래 비율 그대로(DAY RECAP 과 같은 규칙)
                    fit = clips.size == 1,
                    publicUrl = publicUrl,
                    onProfileClick = onProfileClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        }
        // 오른쪽 위: 시각 · 장면 번호
        Text(
            "${scene.label} · ${sceneIndex + 1} / ${recap.scenes.size}",
            color = PhotoFrame,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .background(PhotoScrim, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        // 가운데 ▶(멈췄을 때)
        if (!playing) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .background(PhotoFrame, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PrimaryStrong, modifier = Modifier.size(36.dp))
            }
        }
        // 아래 진행 막대
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(PhotoScrim),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (active) progress else 0f)
                    .height(3.dp)
                    .background(PhotoFrame),
            )
        }
    }
}

/** 영상 한 칸: 대표 화면 위에 영상(칸을 꽉 채우게 가운데를 잘라서), 왼쪽 위 작은 프로필(구성원 색 테두리) */
@Composable
private fun ClipPane(
    clip: PublicClip,
    member: Member?,
    colorIndex: Int,
    player: ExoPlayer?,
    fit: Boolean,
    publicUrl: (String) -> String,
    onProfileClick: (Member) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clipToBounds()
            .background(
                if (fit) Brush.linearGradient(listOf(BackgroundDark, BackgroundDark))
                else Brush.linearGradient(listOf(BackgroundDark, Primary, PrimaryLight)),
            ),
    ) {
        if (clip.thumbPath != null) {
            AsyncImage(
                model = publicUrl(clip.thumbPath),
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (player != null) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val byWidth = maxWidth * 9f / 16f
                val useWidth = if (fit) byWidth <= maxHeight else byWidth >= maxHeight
                val (w, h) = if (useWidth) maxWidth to byWidth else (maxHeight * 16f / 9f) to maxHeight
                AndroidView(
                    factory = { TextureView(it).also { view -> player.setVideoTextureView(view) } },
                    update = { player.setVideoTextureView(it) },
                    onRelease = { player.clearVideoTextureView(it) },
                    modifier = Modifier.requiredSize(w, h),
                )
            }
        }
        val name = member?.nickname ?: "구성원"
        Avatar(
            photo = member?.photoUrl,
            size = 32.dp,
            ringWidth = 2.dp,
            ringColor = MemberColors[colorIndex % MemberColors.size],
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(CircleShape)
                .clickable(enabled = member != null) { member?.let(onProfileClick) }
                .semantics { contentDescription = "$name 님의 프로필 보기" },
        )
    }
}
