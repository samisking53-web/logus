package com.logus.app.ui.feed

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.logus.app.R
import com.logus.app.journey.Member
import com.logus.app.record.FeedLog
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.BorderDark
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.PhotoScrim
import com.logus.app.ui.theme.PrimaryStrongDark
import com.logus.app.ui.theme.SurfaceDark
import com.logus.app.ui.theme.TextDark
import com.logus.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay

// 원본 영상 팝업 (N01 기록 보기·S15 여정 상세 기록 탭 공통, 2026-10-30 팀 그림)
/**
 * 기록 카드(LogCard)를 누르면 뜨는 팝업. 뒤 화면은 그대로 두고 어둡게 덮는다. 바깥을 누르거나 X·뒤로 가기로 닫는다.
 * - 위: X · 장소 이름(위치 없이 올렸으면 "위치 없이 올린 기록") · "성연 · 09:24 기록"
 * - 영상 칸(가로 16:9): 찍은 원본을 자르지 않고 비율 그대로 보여 준다(2026-10-30 팀 결정).
 *   왼쪽 위 작은 프로필·찍은 시각, 오른쪽 위 "16:9 · 0:03", 가운데 ❚❚(재생 중)·▶(멈춤), 왼쪽 아래 장소 이름.
 *   열면 바로 소리까지 재생하고, 영상을 누르면 멈춤·다시 재생. 끝나면 첫 장면으로 돌아가 ▶. 앱을 내리면 멈춘다.
 * - 진행 막대와 "0:00 / 0:03"
 * - [‹] "1 / 8 · 원본 영상" [›]: 그날 영상 기록 전체를 찍은 순서대로 넘겨 본다(2026-10-30 팀 결정).
 *   아직 안 읽은 그날 기록이 있으면 마지막 영상에 왔을 때 더 읽는다(onLoadMore).
 * 영상은 Storage 주소에서 바로 재생한다(Media3 ExoPlayer, 폰에 받아 두지 않음). 팝업을 닫으면 재생기를 정리한다.
 */
@Composable
fun LogVideoDialog(
    /** 그날 영상 기록(찍은 순서) */
    logs: List<FeedLog>,
    /** 처음 보여 줄 기록(누른 카드) */
    startLogId: String,
    members: Map<String, Member>,
    /** 그날 기록을 아직 다 읽지 않았는지 */
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    videoUrl: suspend (FeedLog) -> String?,
    previewUrl: suspend (FeedLog) -> String?,
    onDismiss: () -> Unit,
) {
    var currentId by rememberSaveable { mutableStateOf(startLogId) }
    val index = logs.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val log = logs.getOrNull(index)
    // 보여 줄 영상이 없어지면(다시 읽는 중 등) 닫는다
    LaunchedEffect(log == null) { if (log == null) onDismiss() }
    // 마지막 영상까지 왔는데 그날 기록이 더 있으면 다음 쪽을 읽는다(› 로 이어서 넘길 수 있게)
    LaunchedEffect(index, logs.size, canLoadMore) {
        if (canLoadMore && index >= logs.lastIndex) onLoadMore()
    }
    if (log == null) return

    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var playing by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    // 첫 장면이 그려지기 전에는 대표 화면(썸네일)을 위에 덮어 둔다(검은 화면 대신)
    var frameShown by remember { mutableStateOf(false) }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var attempt by remember { mutableIntStateOf(0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) durationMs = player.duration.coerceAtLeast(0L)
                if (playbackState == Player.STATE_ENDED) {
                    // 끝나면 첫 장면으로 돌아가 멈춘다(다시 누르면 처음부터)
                    player.pause()
                    player.seekTo(0)
                    positionMs = 0L
                }
            }

            override fun onRenderedFirstFrame() {
                frameShown = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    videoAspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                failed = true
                buffering = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // 앱을 내리면 멈춘다
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 기록을 바꾸거나(‹ ›) 다시 시도하면: 그 영상의 Storage 주소를 받아 처음부터 바로 재생한다
    LaunchedEffect(log.id, attempt) {
        failed = false
        frameShown = false
        buffering = true
        durationMs = 0L
        positionMs = 0L
        player.stop()
        player.clearMediaItems()
        val url = videoUrl(log)
        if (url == null) {
            failed = true
            buffering = false
            return@LaunchedEffect
        }
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    // 재생 중에는 진행 막대를 자주 고친다
    LaunchedEffect(playing) {
        while (playing) {
            positionMs = player.currentPosition
            delay(50)
        }
        positionMs = player.currentPosition
    }

    fun togglePlay() {
        when {
            failed -> attempt++
            player.isPlaying -> player.pause()
            else -> player.play()
        }
    }

    val author = members[log.authorId]
    val name = author?.nickname ?: "구성원"
    val time = clockLabel(log.capturedAtMillis)
    val place = log.placeName

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .background(BackgroundDark, RoundedCornerShape(28.dp))
                .padding(16.dp),
        ) {
            // X · 장소 이름 · "성연 · 09:24 기록"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = SurfaceDark,
                    contentColor = TextDark,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "닫기", modifier = Modifier.size(22.dp))
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        place ?: "위치 없이 올린 기록",
                        color = TextDark,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        "$name · $time 기록",
                        color = TextSecondaryDark,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 제목이 가운데에 오도록 X 와 같은 너비를 비워 둔다
                Spacer(Modifier.width(44.dp))
            }
            Spacer(Modifier.height(14.dp))

            // 영상 칸(가로 16:9, 원본 비율 그대로)
            val poster by produceState<String?>(initialValue = null, log.id) { value = previewUrl(log) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BackgroundDark)
                    .clickable { togglePlay() }
                    .clearAndSetSemantics {
                        contentDescription = listOfNotNull("$name 님의 원본 영상", time, place).joinToString(", ")
                        onClick(label = if (failed) "다시 불러오기" else if (playing) "멈추기" else "재생하기") {
                            togglePlay()
                            true
                        }
                    },
            ) {
                // 영상 비율대로 칸 안에 맞춘다(16:9 영상이면 칸을 꽉 채우고, 다르면 남는 곳은 검게)
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val boxAspect = maxWidth / maxHeight
                    val w = if (videoAspect >= boxAspect) maxWidth else maxHeight * videoAspect
                    val h = if (videoAspect >= boxAspect) maxWidth / videoAspect else maxHeight
                    AndroidView(
                        factory = { TextureView(it).also { view -> player.setVideoTextureView(view) } },
                        update = { player.setVideoTextureView(it) },
                        onRelease = { player.clearVideoTextureView(it) },
                        modifier = Modifier.size(w, h),
                    )
                }
                if (!frameShown) {
                    // 새 영상의 첫 장면이 나오기 전: 앞 영상의 마지막 장면이 남아 보이지 않게 덮고, 대표 화면이 있으면 보여 준다
                    Box(Modifier.fillMaxSize().background(BackgroundDark))
                    poster?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (place != null) {
                    // 장소 이름이 영상 위에서도 잘 읽히도록 아래쪽을 어둡게
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, PhotoScrim))),
                    )
                }
                // 왼쪽 위: 작은 프로필 + 찍은 시각
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(photo = author?.photoUrl, size = 32.dp, ringWidth = 2.dp, ringColor = PhotoFrame)
                    Spacer(Modifier.width(8.dp))
                    VideoPill(time)
                }
                // 오른쪽 위: "16:9 · 0:03"
                VideoPill(
                    if (durationMs > 0) "16:9 · ${durationLabel(durationMs)}" else "16:9",
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                )
                // 가운데: 불러오는 중 / 못 불러옴 / ❚❚ / ▶
                Box(Modifier.align(Alignment.Center)) {
                    when {
                        failed -> Text(
                            "영상을 불러오지 못했어요\n누르면 다시 불러와요",
                            color = PhotoFrame,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(PhotoScrim, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                        buffering && !playing -> CircularProgressIndicator(color = PhotoFrame, modifier = Modifier.size(36.dp))
                        else -> Box(
                            Modifier
                                .size(60.dp)
                                .background(PhotoScrim, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (playing) {
                                Icon(painterResource(R.drawable.ic_pause), contentDescription = null, tint = PhotoFrame, modifier = Modifier.size(28.dp))
                            } else {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PhotoFrame, modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                }
                // 왼쪽 아래: 장소 이름
                if (place != null) {
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = PhotoFrame, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            place,
                            color = PhotoFrame,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            // 진행 막대와 "0:00 / 0:03"
            val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(BorderDark),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(PrimaryStrongDark),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(durationLabel(positionMs), color = TextSecondaryDark, fontSize = 12.sp)
                Text(if (durationMs > 0) durationLabel(durationMs) else "-:--", color = TextSecondaryDark, fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))

            // [‹] "1 / 8 · 원본 영상" [›]
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    label = "이전 영상",
                    enabled = index > 0,
                    onClick = { logs.getOrNull(index - 1)?.let { currentId = it.id } },
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .background(SurfaceDark, RoundedCornerShape(16.dp))
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1} / ${logs.size} · 원본 영상",
                        color = TextDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                StepButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    label = "다음 영상",
                    enabled = index < logs.lastIndex,
                    onClick = { logs.getOrNull(index + 1)?.let { currentId = it.id } },
                )
            }
        }
    }
}

/** 영상 위 작은 글자 상자(시각·"16:9 · 0:03") */
@Composable
private fun VideoPill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = PhotoFrame,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .background(PhotoScrim, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** 이전·다음 영상 버튼. 더 갈 수 없으면 흐리게 하고 누를 수 없게 한다 */
@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDark,
        contentColor = TextDark,
        modifier = Modifier
            .size(52.dp)
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp))
        }
    }
}

/** 영상 길이·위치 "0:03" */
private fun durationLabel(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
