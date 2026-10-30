package com.logus.app.ui.journeydetail

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.logus.app.feed.minuteLabel
import com.logus.app.journey.Member
import com.logus.app.journeydetail.RECAP_MAX_CLIPS
import com.logus.app.journeydetail.RecapClip
import com.logus.app.journeydetail.RecapMapScene
import com.logus.app.journeydetail.RecapScene
import com.logus.app.journeydetail.RecapStep
import com.logus.app.record.FeedLog
import com.logus.app.record.VideoCache
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.MemberColors
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.PhotoScrim
import com.logus.app.ui.theme.Primary
import com.logus.app.ui.theme.PrimaryLight
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 영상이 늦게 받아져도 한 장면에서 이만큼(밀리초)이 지나면 다음 장면으로 넘어간다 */
private const val SCENE_TIMEOUT_MS = 15_000L

/** 영상을 하나도 못 받은 장면은 대표 화면만 이만큼 보여 주고 넘어간다(촬영 길이 3초와 같게) */
private const val POSTER_ONLY_MS = 3_000L

/**
 * S17 DAY RECAP 재생기: 지도 장면(2단계, RecapMapView)과 영상 3분할 장면을 차례대로 튼다.
 * - 위 "11:00"(이동 장면은 "11:00 → 13:00")·"장면 1 / 4" → 지도 또는 영상 칸(구성원 수만큼 위·가운데·아래로 나눔, 1명이면 가득) → 장면 진행 막대.
 * - 지도 장면은 정해진 시간(약 2~3초) 동안 움직이고 다음 장면으로 넘어간다. 멈춰 있으면 도착한 모습을 보여 준다.
 * - ▶ 를 누르면 그 장면 영상들을 동시에 틀고, 모두 끝나면 다음 장면으로 저절로 넘어간다. 마지막 장면이 끝나면 처음으로.
 * - 재생 중 영상을 누르면 멈춘다(다시 ▶ 를 누르면 그 장면 처음부터). 앱을 내리면 멈춘다.
 * - 소리는 맨 위 영상만 낸다(셋이 겹치면 시끄러워서).
 * - 날짜(dayKey)가 바뀌면 첫 장면으로 돌아가 멈춘다.
 */
@Composable
fun RecapPlayer(
    scenes: List<RecapStep>,
    members: Map<String, Member>,
    dayKey: Int,
    videoUrl: suspend (FeedLog) -> String?,
    previewUrl: suspend (FeedLog) -> String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var sceneIndex by remember(dayKey) { mutableIntStateOf(0) }
    var playing by remember(dayKey) { mutableStateOf(false) }
    var progress by remember(dayKey) { mutableFloatStateOf(0f) }
    val mapController = rememberRecapMapController()
    val hasMap = scenes.any { it is RecapMapScene }

    // 영상 칸마다 재생기 하나(최대 3개). 처음 영상 장면을 틀 때 만들고, 화면을 떠나면 모두 정리한다
    var players by remember { mutableStateOf<List<ExoPlayer>>(emptyList()) }
    DisposableEffect(Unit) {
        onDispose { players.forEach { it.release() } }
    }
    // 앱을 내리면 멈춘다
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) playing = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(playing, sceneIndex, dayKey, scenes) {
        val step = scenes.getOrNull(sceneIndex)
        if (!playing) {
            players.forEach { it.pause() }
            // 멈춘 지도 장면은 도착한 모습으로
            if (step is RecapMapScene) mapController.show(step)
            return@LaunchedEffect
        }
        if (step == null) {
            playing = false
            return@LaunchedEffect
        }
        progress = 0f
        players.forEach {
            it.stop()
            it.clearMediaItems()
        }
        when (step) {
            is RecapMapScene -> coroutineScope {
                // 지도 장면: 정해진 시간만큼 움직이고, 그동안 진행 막대를 채운다
                val total = mapSceneMillis(step).toFloat()
                val startedAt = System.currentTimeMillis()
                val ticker = launch {
                    while (true) {
                        progress = ((System.currentTimeMillis() - startedAt) / total).coerceIn(0f, 1f)
                        delay(50)
                    }
                }
                mapController.play(step)
                ticker.cancel()
            }
            is RecapScene -> {
                // 폰 안 영상 캐시를 쓰는 재생기: 한 번 본 영상은 다시 받지 않는다(VideoCache)
                if (players.isEmpty()) players = List(RECAP_MAX_CLIPS) { VideoCache.newPlayer(context) }
                playVideoScene(step, players, videoUrl) { progress = it }
            }
        }
        if (sceneIndex < scenes.lastIndex) {
            sceneIndex += 1
        } else {
            // 끝까지 봤으면 처음 장면으로 돌아가 멈춘다(▶ 로 다시 보기)
            playing = false
            sceneIndex = 0
            progress = 0f
        }
    }

    val step = scenes.getOrNull(sceneIndex) ?: return
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val from = (step as? RecapMapScene)?.fromMinute
            Text(
                if (from != null) "${minuteLabel(from)} → ${minuteLabel(step.startMinute)}" else minuteLabel(step.startMinute),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text("장면 ${sceneIndex + 1} / ${scenes.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            // 지도는 장면이 바뀌어도 계속 두고(다시 받지 않게), 영상 장면일 때는 영상 칸이 위를 덮는다
            if (hasMap) RecapMapView(mapController, members, Modifier.fillMaxSize())
            if (step is RecapScene) {
                // 구성원 수만큼 위·가운데·아래로 나눈다. 칸 사이 틈 없이 한 덩어리(바깥 모서리만 둥글게, 칸 사이는 얇은 흰 선)
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BackgroundDark),
                ) {
                    step.clips.forEachIndexed { i, clip ->
                        if (i > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(PhotoFrame),
                            )
                        }
                        RecapClipView(
                            clip = clip,
                            author = members[clip.uid],
                            player = players.getOrNull(i),
                            showVideo = playing,
                            previewUrl = previewUrl,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
            if (playing) {
                // 재생 중 화면(지도·영상)을 누르면 멈춘다
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(role = Role.Button, onClickLabel = "DAY RECAP 멈추기") { playing = false },
                )
            } else {
                Surface(
                    onClick = { playing = true },
                    shape = CircleShape,
                    color = PhotoFrame,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(58.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "DAY RECAP 재생", tint = Primary, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
        SceneProgressBar(count = scenes.size, current = sceneIndex, progress = if (playing) progress else 0f)
    }
}

/**
 * 영상 장면 하나를 튼다: 칸마다 영상을 동시에 틀고 모두 끝나면(또는 너무 오래 걸리면) 돌아온다.
 * 영상이 없는 칸(검은 화면)은 건너뛰고, 영상을 하나도 못 받으면 대표 화면만 잠깐 보여 준다.
 */
private suspend fun playVideoScene(
    scene: RecapScene,
    players: List<ExoPlayer>,
    videoUrl: suspend (FeedLog) -> String?,
    onProgress: (Float) -> Unit,
) {
    // 이 장면 영상 주소를 한꺼번에 받는다(못 받은 영상은 대표 화면만 보인다)
    val urls = coroutineScope { scene.clips.map { clip -> async { clip.log?.let { videoUrl(it) } } }.awaitAll() }
    var soundGiven = false
    val active = scene.clips.indices.mapNotNull { i ->
        val url = urls[i] ?: return@mapNotNull null
        players[i].apply {
            setMediaItem(VideoCache.mediaItem(url, scene.clips[i].log?.mediaPath))
            // 소리는 영상이 있는 칸 중 맨 위 하나만
            volume = if (soundGiven) 0f else 1f
            soundGiven = true
            prepare()
        }
    }
    if (active.isEmpty()) {
        delay(POSTER_ONLY_MS)
        return
    }
    // 모두 준비되면(또는 조금 기다려도 안 되면) 한꺼번에 튼다
    withTimeoutOrNull(5_000) {
        while (active.any { it.playbackState == Player.STATE_BUFFERING }) delay(50)
    }
    active.forEach { it.play() }
    val startedAt = System.currentTimeMillis()
    while (true) {
        delay(100)
        val duration = active.maxOf { it.duration.coerceAtLeast(1L) }
        onProgress((active.maxOf { it.currentPosition }.toFloat() / duration).coerceIn(0f, 1f))
        // 모두 끝났거나(오류로 멈춘 것도 끝난 것으로) 너무 오래 걸리면 다음 장면으로
        val done = active.all { it.playbackState == Player.STATE_ENDED || it.playbackState == Player.STATE_IDLE }
        if (done || System.currentTimeMillis() - startedAt > SCENE_TIMEOUT_MS) break
    }
}

/** 장면 진행 막대: 지난 장면은 채움, 지금 장면은 재생한 만큼 */
@Composable
private fun SceneProgressBar(count: Int, current: Int, progress: Float) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { i ->
            val fill = when {
                i < current -> 1f
                i == current -> progress
                else -> 0f
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LogUsColors.border),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fill)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * 영상 칸 하나: 대표 화면(첫 장면) 위에 재생 중이면 영상, 왼쪽 위 작은 프로필(구성원 색 테두리), 왼쪽 아래 장소 이름.
 * 영상은 16:9 로 찍혀 있어서 칸을 꽉 채우도록 가운데를 잘라 보여 준다.
 * 이 차례에 그 사람 영상이 없으면(clip.log == null) 검은 화면에 작은 프로필만 둔다(누구 자리인지 알 수 있게).
 */
@Composable
private fun RecapClipView(
    clip: RecapClip,
    author: Member?,
    player: ExoPlayer?,
    showVideo: Boolean,
    previewUrl: suspend (FeedLog) -> String?,
    modifier: Modifier = Modifier,
) {
    val log = clip.log
    val poster by produceState<String?>(initialValue = null, log?.id) { value = log?.let { previewUrl(it) } }
    val name = author?.nickname ?: "구성원"
    Box(
        modifier
            .clipToBounds()
            .background(
                if (log == null) Brush.linearGradient(listOf(BackgroundDark, BackgroundDark))
                else Brush.linearGradient(listOf(BackgroundDark, Primary, PrimaryLight)),
            )
            .clearAndSetSemantics {
                contentDescription = if (log == null) "$name 님은 이 차례에 영상이 없어요"
                else listOfNotNull("$name 님의 영상", log.placeName).joinToString(", ")
            },
    ) {
        if (poster != null) {
            AsyncImage(model = poster, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (showVideo && player != null && log != null) {
            // 16:9 영상을 칸보다 크거나 같게 키워 가운데에 두면, 둥근 칸 밖으로 나간 부분이 잘려 "꽉 채우기"가 된다
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val byWidth = maxWidth * 9f / 16f
                val (w, h) = if (byWidth >= maxHeight) maxWidth to byWidth else (maxHeight * 16f / 9f) to maxHeight
                AndroidView(
                    factory = { TextureView(it).also { view -> player.setVideoTextureView(view) } },
                    update = { player.setVideoTextureView(it) },
                    onRelease = { player.clearVideoTextureView(it) },
                    modifier = Modifier.requiredSize(w, h),
                )
            }
        }
        if (log?.placeName != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, PhotoScrim))),
            )
            Text(
                log.placeName,
                color = PhotoFrame,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
            )
        }
        Avatar(
            photo = author?.photoUrl,
            size = 30.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            ringWidth = 2.dp,
            ringColor = MemberColors[clip.colorIndex % MemberColors.size],
        )
    }
}
