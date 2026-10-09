package com.logus.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.logus.app.R
import com.logus.app.feed.FeedWindow
import com.logus.app.feed.JourneyFeedUiState
import com.logus.app.feed.groupByWindows
import com.logus.app.feed.minuteLabel
import com.logus.app.journey.Journey
import com.logus.app.journey.Member
import com.logus.app.record.FeedLog
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.home.ActionButton
import com.logus.app.ui.home.LogoHeader
import com.logus.app.ui.record.RecordTopBar
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.PhotoScrim
import com.logus.app.ui.theme.Primary
import com.logus.app.ui.theme.PrimaryLight
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// N01~N03 기록 보기 (스토리보드 v4 13쪽 N01 스크롤 전·N02 스크롤 후, 화면 목록의 S06 우리 기록)
/**
 * S01-A 여정 카드를 누르면 여는 화면. 하단 탭은 숨긴다.
 * - 위에 고정: LOG EARTH → 뒤로 가기 + 여정 이름 → "10월 9일 · 전체 3명"(오늘 날짜·전체 인원) + 다운로드 모양 "여정 마치기" 버튼
 * - 아래로 스크롤되는 부분: "지금 기록하기"(→ S04) → 오늘 기록을 공통 알림 간격으로 나눈 시간대
 *   ("09:20 ~ 11:20 시간대" + 대표 화면 카드들). 아래로 내리면 "지금 기록하기"는 위로 올라가 사라지고 카드가 이어진다.
 * - 카드: 영상 대표 화면(첫 장면) 위 왼쪽 위에 기록한 사람의 작은 동그란 프로필, 오른쪽 위에 찍은 시각, 왼쪽 아래에 장소 이름.
 * - 맨 아래 "↓ 아래로 밀면 다음 시간대" 안내. 끝까지 내리면 사라진다.
 * - 다운로드 모양 버튼: N03 "저장하시겠습니까?" 팝업(SaveJourneyDialog) → "저장" → 나에게만 여정을 끝내고 마이로그로 간다(MainScreen).
 */
@Composable
fun JourneyFeedScreen(
    journey: Journey,
    state: JourneyFeedUiState,
    onBack: () -> Unit,
    onRecordNow: () -> Unit,
    onFinish: () -> Unit,
    /** 저장 팝업을 열 때(기록 수 세기) */
    onAskSave: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    previewUrl: suspend (FeedLog) -> String?,
) {
    var askSave by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    val listState = rememberLazyListState()

    // 끝에서 3칸 안쪽까지 내려오면 다음 기록을 읽는다(한 번에 다 읽지 않는다)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    JourneyFeedContent(
        journey = journey,
        state = state,
        today = today,
        listState = listState,
        onBack = onBack,
        onRecordNow = onRecordNow,
        onAskFinish = {
            onAskSave()
            askSave = true
        },
        onRetry = onRetry,
        previewUrl = previewUrl,
    )

    // N03 "저장하시겠습니까?" 팝업. "저장"을 눌러야만 여정을 저장하고 마이로그로 간다
    if (askSave || state.finishing) {
        SaveJourneyDialog(
            journeyName = journey.name,
            today = today,
            recordCount = state.recordCount,
            memberCount = journey.memberCount,
            saving = state.finishing,
            error = state.finishError,
            onSave = onFinish,
            onDismiss = { askSave = false },
        )
    }
}

/** 화면 그리기만 하는 부분(미리보기에서도 쓰려고 나눴다) */
@Composable
private fun JourneyFeedContent(
    journey: Journey,
    state: JourneyFeedUiState,
    today: LocalDate,
    listState: LazyListState,
    onBack: () -> Unit,
    onRecordNow: () -> Unit,
    onAskFinish: () -> Unit,
    onRetry: () -> Unit,
    previewUrl: suspend (FeedLog) -> String?,
) {
    // 아래에 더 볼 기록이 있으면(스크롤할 수 있거나 아직 다 읽지 않음) 안내를 보여 주고, 끝까지 내리면 숨긴다
    val showHint by remember(state.windows, state.endReached) {
        derivedStateOf { state.windows.isNotEmpty() && (listState.canScrollForward || !state.endReached) }
    }

    Column(Modifier.fillMaxSize()) {
        // 위에 고정되는 부분
        Column(Modifier.padding(horizontal = 20.dp)) {
            LogoHeader()
            RecordTopBar(title = journey.name, onBack = onBack)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 오늘 날짜 · 전체 인원
                Box(
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .background(LogUsColors.profileBox, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${today.monthValue}월 ${today.dayOfMonth}일 · 전체 ${journey.memberCount}명",
                        color = LogUsColors.primaryStrong,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                // 여정 마치기(다운로드 모양, 팔레트 강조 보라)
                Surface(
                    onClick = onAskFinish,
                    shape = RoundedCornerShape(16.dp),
                    color = LogUsColors.profileBox,
                    contentColor = LogUsColors.primaryStrong,
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_download),
                            contentDescription = "여정 저장하기",
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 아래로 스크롤되는 부분: 지금 기록하기 → 시간대별 기록
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            item(key = "record") {
                Column {
                    Spacer(Modifier.height(4.dp))
                    ActionButton(
                        icon = painterResource(R.drawable.ic_camera),
                        title = "지금 기록하기",
                        subtitle = null,
                        filled = true,
                        onClick = onRecordNow,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
            when {
                state.loading -> item(key = "loading") { CenteredBox { CircularProgressIndicator(color = LogUsColors.primaryStrong) } }
                state.error != null -> item(key = "error") {
                    CenteredBox {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                            TextButton(onClick = onRetry) { Text("다시 불러오기", color = LogUsColors.primaryStrong) }
                        }
                    }
                }
                state.windows.isEmpty() -> item(key = "empty") {
                    CenteredBox {
                        Text(
                            "오늘은 아직 기록이 없어요.\n'지금 기록하기'로 첫 장면을 남겨 보세요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> state.windows.forEach { window ->
                    item(key = "w${window.startMinute}") { WindowHeader(window) }
                    items(window.logs, key = { it.id }) { log ->
                        LogCard(log = log, author = state.members[log.authorId], previewUrl = previewUrl)
                        Spacer(Modifier.height(10.dp))
                    }
                    item(key = "gap${window.startMinute}") { Spacer(Modifier.height(18.dp)) }
                }
            }
            if (state.loadingMore) {
                item(key = "more") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LogUsColors.primaryStrong, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // 맨 아래 안내(끝까지 내리면 사라진다)
        if (showHint) {
            HorizontalDivider(color = LogUsColors.line)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_arrow_down),
                    contentDescription = null,
                    tint = LogUsColors.primaryStrong,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("아래로 밀면 다음 시간대", color = LogUsColors.primaryStrong, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "09:20 ~ 11:20 시간대" + 오른쪽 "3개" */
@Composable
private fun WindowHeader(window: FeedWindow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${minuteLabel(window.startMinute)} ~ ${minuteLabel(window.endMinute)} 시간대",
            color = LogUsColors.primaryStrong,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        Text("${window.logs.size}개", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

/**
 * 기록 카드: 대표 화면(영상 첫 장면)을 가로로 길게 잘라 보여 준다. 대표 화면이 없으면(예전 기록·글 기록) 보라 그라데이션.
 * 왼쪽 위 작은 동그란 프로필(사진이 없으면 지구본), 오른쪽 위 찍은 시각, 왼쪽 아래 장소 이름(있을 때).
 */
@Composable
private fun LogCard(log: FeedLog, author: Member?, previewUrl: suspend (FeedLog) -> String?) {
    val url by produceState<String?>(initialValue = null, log.id) { value = previewUrl(log) }
    val time = clockLabel(log.capturedAtMillis)
    val name = author?.nickname ?: "구성원"
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(BackgroundDark, Primary, PrimaryLight)))
            // 화면 읽기 프로그램은 카드 하나를 한 문장으로 읽는다
            .clearAndSetSemantics {
                contentDescription = listOfNotNull("$name 님의 기록", time, log.placeName).joinToString(", ")
            },
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (log.placeName != null) {
            // 장소 이름이 사진 위에서도 잘 읽히도록 아래쪽을 어둡게
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, PhotoScrim))),
            )
        }
        Avatar(
            photo = author?.photoUrl,
            size = 36.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            ringWidth = 2.dp,
            ringColor = PhotoFrame,
        )
        Text(
            time,
            color = PhotoFrame,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(PhotoScrim, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        log.placeName?.let {
            Text(
                it,
                color = PhotoFrame,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 찍은 시각 "09:24"(폰 시간대) */
private fun clockLabel(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
    return "%02d:%02d".format(t.hour, t.minute)
}

@Preview(showBackground = true, heightDp = 860)
@Composable
private fun JourneyFeedPreview() {
    val zone = ZoneId.systemDefault()
    val day = LocalDate.of(2026, 9, 24)
    fun at(h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    val logs = listOf(
        FeedLog("1", "a", "video", null, at(9, 24), "히베이라 강변"),
        FeedLog("2", "b", "video", null, at(10, 5), "포르투 대성당"),
        FeedLog("3", "c", "video", null, at(11, 44), null),
    )
    LogUsTheme {
        JourneyFeedContent(
            journey = Journey("j", "우리의 포르투", "2026-09-24", "2026-09-26", listOf("a", "b", "c"), 3),
            state = JourneyFeedUiState(
                journeyId = "j",
                loading = false,
                windows = groupByWindows(logs, 2, zone),
                members = mapOf("a" to Member("a", "성연"), "b" to Member("b", "민수"), "c" to Member("c", "지우")),
                endReached = true,
            ),
            today = day,
            listState = rememberLazyListState(),
            onBack = {},
            onRecordNow = {},
            onAskFinish = {},
            onRetry = {},
            previewUrl = { null },
        )
    }
}
