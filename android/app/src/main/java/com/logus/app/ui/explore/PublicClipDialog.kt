package com.logus.app.ui.explore

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Place
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.logus.app.R
import com.logus.app.explore.PlaceAddress
import com.logus.app.explore.PublicClip
import com.logus.app.explore.PublicPlace
import com.logus.app.explore.PublicRecap
import com.logus.app.journey.Member
import com.logus.app.record.GeoPoint
import com.logus.app.record.VideoCache
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.Border
import com.logus.app.ui.theme.MemberColors
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.PhotoScrim
import com.logus.app.ui.theme.Primary
import com.logus.app.ui.theme.PrimaryLight
import com.logus.app.ui.theme.SurfaceDark
import com.logus.app.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay

// 탐색 공개 여정 상세 [기록] 영상 팝업(2026-11-08 요청: N01 원본 영상 팝업처럼 재생 + 위에 넓은 지역 주소)
/**
 * 어두운 판 안에 X · 넓은 지역 주소(예: "포르투 · 히베이라", 큰 글자) · "성연 · 09:24 기록"
 * → 16:9 영상(왼쪽 위 작은 프로필·시각, 가운데 ❚❚/▶, 왼쪽 아래 장소 이름) → 진행 막대 "0:00 / 0:03"
 * → [‹] "1 / 8 · 원본 영상" [›](그날 공개된 영상을 찍은 순서로) → "주소 © OpenStreetMap".
 * 열면 소리까지 재생, 영상을 누르면 멈춤·다시 재생, 끝나면 첫 장면으로, 앱을 내리면 멈춤. 영상은 VideoCache 를 거친다.
 * 주소는 영상을 열 때 그 위치로 한 번 묻는다(Nominatim, 위치 없으면 묻지 않음).
 */
@Composable
fun PublicClipDialog(
    recap: PublicRecap,
    clips: List<PublicClip>,
    startIndex: Int,
    places: Map<String, PublicPlace>,
    publicUrl: (String) -> String,
    lookupAddress: suspend (GeoPoint) -> PlaceAddress,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex.coerceIn(0, (clips.size - 1).coerceAtLeast(0))) }
    val clip = clips.getOrNull(index) ?: return
    val member = recap.member(clip.authorId)
    val place = places[clip.logId]
    val address by produceState<PlaceAddress?>(null, clip.logId) {
        value = place?.location?.let { runCatching { lookupAddress(it) }.getOrNull() }
    }
    val player = remember { VideoCache.newPlayer(context) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var progress by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) { onDispose { player.release() } }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) playing = false }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(clip.logId) {
        player.setMediaItem(VideoCache.mediaItem(publicUrl(clip.videoPath), clip.videoPath))
        player.prepare()
        player.seekTo(0)
        playing = true
    }
    LaunchedEffect(playing) { player.playWhenReady = playing }
    LaunchedEffect(clip.logId) {
        while (true) {
            if (player.duration > 0) duration = player.duration
            position = player.currentPosition
            progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
                playing = false
            }
            delay(150)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BackgroundDark)
                .padding(14.dp),
        ) {
            // 위: X · 넓은 지역 주소(큰 글자) · "성연 · 09:24 기록"
            Box(Modifier.fillMaxWidth()) {
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = SurfaceDark,
                    contentColor = PhotoFrame,
                    modifier = Modifier.size(40.dp).align(Alignment.CenterStart),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, contentDescription = "닫기", modifier = Modifier.size(20.dp))
                    }
                }
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        address?.area ?: place?.placeName ?: if (place?.location == null) "위치 없이 올린 기록" else "주소를 찾는 중…",
                        color = PhotoFrame,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        listOfNotNull(member?.nickname ?: "구성원", clockOf(clip.capturedAtMillis)?.let { "$it 기록" }).joinToString(" · "),
                        color = TextSecondaryDark,
                        fontSize = 12.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 16:9 영상
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(BackgroundDark, Primary, PrimaryLight)))
                    .clickable { playing = !playing }
                    .semantics { contentDescription = if (playing) "영상 멈추기" else "영상 재생" },
            ) {
                if (clip.thumbPath != null) {
                    AsyncImage(
                        model = publicUrl(clip.thumbPath),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                AndroidView(
                    factory = { TextureView(it).also { v -> player.setVideoTextureView(v) } },
                    update = { player.setVideoTextureView(it) },
                    onRelease = { player.clearVideoTextureView(it) },
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    Modifier.align(Alignment.TopStart).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(
                        photo = member?.photoUrl,
                        size = 30.dp,
                        ringWidth = 2.dp,
                        ringColor = MemberColors[recap.colorIndex(clip.authorId) % MemberColors.size],
                    )
                    clockOf(clip.capturedAtMillis)?.let {
                        Spacer(Modifier.width(6.dp))
                        Chip(it)
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { Chip("16:9 · ${timeLabel(duration)}") }
                Box(
                    Modifier.align(Alignment.Center).size(56.dp).background(PhotoScrim, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (playing) {
                        Icon(painterResource(R.drawable.ic_pause), contentDescription = null, tint = PhotoFrame, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = PhotoFrame, modifier = Modifier.size(32.dp))
                    }
                }
                place?.placeName?.let { name ->
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(PhotoScrim)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Place, contentDescription = null, tint = PhotoFrame, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(name, color = PhotoFrame, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // 진행 막대 · 시간
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SurfaceDark),
            ) {
                Box(Modifier.fillMaxWidth(progress).height(4.dp).background(Border))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeLabel(position), color = TextSecondaryDark, fontSize = 12.sp)
                Text(timeLabel(duration), color = TextSecondaryDark, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            // [‹] 1 / 8 · 원본 영상 [›]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavButton(enabled = index > 0, next = false) { index-- }
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceDark),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${index + 1} / ${clips.size} · 원본 영상", color = PhotoFrame, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                NavButton(enabled = index < clips.lastIndex, next = true) { index++ }
            }
            if (place?.location != null) {
                Text(
                    "주소 © OpenStreetMap",
                    color = TextSecondaryDark,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Chip(text: String) {
    Text(
        text,
        color = PhotoFrame,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(PhotoScrim, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun NavButton(enabled: Boolean, next: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = SurfaceDark,
        contentColor = if (enabled) PhotoFrame else TextSecondaryDark,
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (next) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = if (next) "다음 영상" else "이전 영상",
                tint = if (enabled) PhotoFrame else TextSecondaryDark,
            )
        }
    }
}

/** 밀리초 → "0:03" */
private fun timeLabel(millis: Long): String {
    val s = (millis / 1000).coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

private fun clockOf(millis: Long?): String? =
    millis?.let { java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault())) }
