package com.logus.app.ui.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.logus.app.explore.PublicClip
import com.logus.app.explore.PublicDetailState
import com.logus.app.explore.PublicPlace
import com.logus.app.explore.PublicRecap
import com.logus.app.journey.Member
import com.logus.app.journeydetail.MemberRoute
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.journeydetail.RouteMap
import com.logus.app.ui.record.RecordTopBar
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.MemberColors
import com.logus.app.ui.theme.OnPrimary
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.PhotoScrim
import com.logus.app.ui.theme.Primary
import com.logus.app.ui.theme.PrimaryLight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 공개 여정 상세의 탭 */
private enum class PublicTab(val label: String) { RECORDS("기록"), MAP("추억 지도"), RECAP("DAY RECAP") }

// 탐색 → 위치 정보 열람(20코인) 뒤 여정 상세(스토리보드 v5 22쪽 "포인트 차감 후": [기록] [추억 지도] [추억 영상])
/**
 * 탐색에서 장소를 연 사람이 보는 여정 상세. 남의 여정 원본은 구성원만 읽을 수 있어서, 공개된 그날 DAY RECAP 에
 * 들어간 영상만 보여 준다(마이로그 여정 상세 S15~S17 과 같은 모양).
 * - 위: LOG EARTH → 뒤로 가기+여정 이름 → 날짜·지역 상자("9.24 포르투") → 탭 [기록] [추억 지도] [DAY RECAP]
 * - 기록: 장면(시간대)마다 영상 카드(대표 화면, 왼쪽 위 작은 프로필 → 프로필 팝업, 오른쪽 위 찍은 시각, 왼쪽 아래 장소 이름)
 * - 추억 지도: 마이로그 S16 과 같은 지도(MapLibre + OpenFreeMap)에 구성원 색 번호 핀과 선, 아래 범례
 * - DAY RECAP: 탐색과 같은 3분할 영상(누르면 재생·멈춤)
 */
@Composable
fun PublicJourneyScreen(
    state: PublicDetailState,
    recap: PublicRecap,
    publicUrl: (String) -> String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onProfile: (Member) -> Unit,
) {
    var tab by rememberSaveable(recap.id) { mutableStateOf(PublicTab.RECORDS) }
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        ExploreLogo()
        RecordTopBar(title = recap.journeyName, onBack = onBack)
        Spacer(Modifier.height(12.dp))
        // 날짜·지역 상자
        Box(
            Modifier
                .fillMaxWidth()
                .background(LogUsColors.profileBox, RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                listOf(shortDate(recap.date), recap.city).filter { it.isNotEmpty() }.joinToString(" "),
                color = LogUsColors.primaryStrong,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PublicTab.entries.forEach { t ->
                val selected = t == tab
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.primary else LogUsColors.card,
                    contentColor = if (selected) OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (selected) null else BorderStroke(1.dp, LogUsColors.line),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { tab = t }),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                PublicTab.RECORDS -> RecordsTab(state, recap, publicUrl, onProfile)
                PublicTab.MAP -> MapTab(state, recap, onRetry)
                PublicTab.RECAP -> Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
                    PublicRecapPlayer(
                        recap = recap,
                        active = true,
                        publicUrl = publicUrl,
                        onProfileClick = onProfile,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

/** 기록 탭: 장면(시간대)마다 제목 + 영상 카드 */
@Composable
private fun RecordsTab(state: PublicDetailState, recap: PublicRecap, publicUrl: (String) -> String, onProfile: (Member) -> Unit) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        recap.scenes.forEachIndexed { sceneIndex, scene ->
            item(key = "scene_$sceneIndex") {
                Text(
                    "${scene.label} 시간대",
                    color = LogUsColors.primaryStrong,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .semantics { heading() },
                )
            }
            items(scene.clips, key = { "${sceneIndex}_${it.logId}" }) { clip ->
                ClipCard(
                    clip = clip,
                    member = recap.member(clip.authorId),
                    colorIndex = recap.colorIndex(clip.authorId),
                    place = state.places[clip.logId],
                    placesLoading = state.loading,
                    publicUrl = publicUrl,
                    onProfile = onProfile,
                )
            }
        }
        item { Text("지도·주소 © OpenStreetMap", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
    }
}

@Composable
private fun ClipCard(
    clip: PublicClip,
    member: Member?,
    colorIndex: Int,
    place: PublicPlace?,
    placesLoading: Boolean,
    publicUrl: (String) -> String,
    onProfile: (Member) -> Unit,
) {
    val placeName = place?.placeName ?: if (placesLoading) null else "위치 없이 올린 기록"
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(BackgroundDark, Primary, PrimaryLight))),
    ) {
        if (clip.thumbPath != null) {
            AsyncImage(
                model = publicUrl(clip.thumbPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (placeName != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, PhotoScrim))),
            )
            Text(
                placeName,
                color = PhotoFrame,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            )
        }
        clockLabel(clip.capturedAtMillis)?.let { time ->
            Text(
                time,
                color = PhotoFrame,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(PhotoScrim, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Avatar(
            photo = member?.photoUrl,
            size = 30.dp,
            ringWidth = 2.dp,
            ringColor = MemberColors[colorIndex % MemberColors.size],
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(CircleShape)
                .clickable(enabled = member != null) { member?.let(onProfile) }
                .semantics { contentDescription = "${member?.nickname ?: "구성원"} 님의 프로필 보기" },
        )
    }
}

/** 추억 지도 탭: 위치를 남긴 영상만 구성원 색 번호 핀(찍은 순서)과 선으로 */
@Composable
private fun MapTab(state: PublicDetailState, recap: PublicRecap, onRetry: () -> Unit) {
    when {
        state.loading -> Centered { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        state.error != null -> Centered {
            ErrorMessage(state.error)
            TextButton(onClick = onRetry) { Text("다시 시도", color = LogUsColors.primaryStrong) }
        }
        else -> {
            val clips = recap.scenes.flatMap { it.clips }
            val located = clips.mapNotNull { clip -> state.places[clip.logId]?.takeIf { it.location != null } }
            val routes = located
                .groupBy { it.authorId }
                .map { (uid, places) ->
                    MemberRoute(
                        uid = uid,
                        colorIndex = recap.colorIndex(uid),
                        points = places.sortedBy { it.capturedAtMillis ?: 0L }.mapNotNull { it.location },
                    )
                }
                .sortedBy { it.colorIndex }
            val missing = clips.size - located.size
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(LogUsColors.iconCircle)
                    .border(1.dp, LogUsColors.border, RoundedCornerShape(20.dp))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${recap.city.ifEmpty { recap.journeyName }} · 기록 경로",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text("${located.size}곳", color = LogUsColors.primaryStrong, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                if (routes.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "이날 영상에는 위치가 남아 있지 않아요",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    RouteMap(
                        routes = routes,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp)),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("지도 © OpenStreetMap", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                // 범례: 색 동그라미 안 이름 첫 글자 + "이름 · N곳"(색만으로 구분하지 않게)
                routes.forEach { route ->
                    val member = recap.member(route.uid)
                    val name = member?.nickname ?: "구성원"
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .background(MemberColors[route.colorIndex % MemberColors.size], CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(name.take(1), color = OnPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("$name · ${route.points.size}곳", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                }
                if (missing > 0) {
                    Text(
                        "위치 없이 올린 기록 ${missing}개는 지도에 없어요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** 찍은 시각 "09:24"(폰 시간대) */
private fun clockLabel(millis: Long?): String? =
    millis?.let { DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
