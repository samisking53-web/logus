package com.logus.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
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
import com.logus.app.ui.components.ProfileDialog
import com.logus.app.ui.home.ActionButton
import com.logus.app.ui.home.LogoHeader
import com.logus.app.ui.record.RecordTopBar
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.BorderDark
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.OnPrimary
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.PhotoScrim
import com.logus.app.ui.theme.Primary
import com.logus.app.ui.theme.PrimaryLight
import com.logus.app.ui.theme.SurfaceDark
import com.logus.app.ui.theme.TextDark
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// N01~N03 기록 보기 (스토리보드 v4 13쪽 N01 스크롤 전·N02 스크롤 후, 화면 목록의 S06 우리 기록)
/**
 * S01-A 여정 카드를 누르면 여는 화면. 하단 탭은 숨긴다.
 * - 위에 고정: LOG EARTH → 뒤로 가기 + 여정 이름 → "〈 10월 9일 · 전체 3명 〉"(보는 날짜·전체 인원) + 다운로드 모양 "여정 마치기" 버튼.
 *   〈 는 전날, 〉 는 다음날 기록으로 옮긴다(여정 시작일 ~ 오늘). 끝에 닿으면 그쪽 화살표는 흐려지고 눌리지 않는다.
 * - 아래로 스크롤되는 부분: "지금 기록하기"(→ S04, 오늘을 볼 때만) → 그날 기록을 공통 알림 간격으로 나눈 시간대
 *   ("09:20 ~ 11:20 시간" + 대표 화면 카드들). 아래로 내리면 "지금 기록하기"는 위로 올라가 사라지고 카드가 이어진다.
 * - 카드: 영상 대표 화면(첫 장면) 위 왼쪽 위에 기록한 사람의 작은 동그란 프로필, 오른쪽 위에 찍은 시각, 왼쪽 아래에 장소 이름.
 *   카드를 누르면 원본 영상 팝업(LogVideoDialog, 16:9 소리까지 재생, ‹ › 로 그날 영상 넘기기), 작은 프로필을 누르면 프로필 팝업.
 *   카드를 길게 누르면 삭제 팝업(DeleteLogDialog, 2026-11-02 팀 그림):
 *   내 기록이면 "이 기록을 삭제할까요?" → 삭제 → 아래에 5초 동안 "내 기록을 삭제했어요 · 되돌리기",
 *   남의 기록이면 "다른 사람의 기록이에요"(지울 수 없다는 안내만). 날짜 상자 아래 줄은 "기록 N개 · 내 기록 N개".
 * - 맨 아래 "↓ 아래로 밀면 다음 시간대" 안내. 끝까지 내리면 사라진다.
 * - 다운로드 모양 버튼: N03 "저장하시겠습니까?" 팝업(SaveJourneyDialog) → "저장" → 나에게만 여정을 끝내고 마이로그로 간다(MainScreen).
 */
@Composable
fun JourneyFeedScreen(
    journey: Journey,
    state: JourneyFeedUiState,
    onBack: () -> Unit,
    onRecordNow: () -> Unit,
    /** 날짜 상자 〈(-1) 〉(+1) */
    onMoveDay: (Long) -> Unit,
    onFinish: () -> Unit,
    /** 저장 팝업을 열 때(기록 수 세기) */
    onAskSave: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    previewUrl: suspend (FeedLog) -> String?,
    /** 원본 영상 팝업의 재생 주소(Storage) */
    videoUrl: suspend (FeedLog) -> String?,
    /** 로그인한 나(내 기록 표시·삭제할 수 있는 기록 가리기) */
    myUid: String,
    /** 삭제 팝업의 "삭제"(내 기록만) / 아래 안내의 "되돌리기" */
    onDeleteLog: (FeedLog) -> Unit,
    onUndoDelete: () -> Unit,
) {
    var askSave by rememberSaveable { mutableStateOf(false) }
    // 원본 영상 팝업으로 보고 있는 기록(카드를 누르면 연다)
    var videoOf by rememberSaveable { mutableStateOf<String?>(null) }
    // 삭제 팝업을 띄운 기록(카드를 길게 누르면 연다)
    var deleteOf by rememberSaveable { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val listState = rememberLazyListState()
    // 다른 날로 옮기면 목록 맨 위부터 보여 준다
    LaunchedEffect(state.day) { listState.scrollToItem(0) }

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
        onMoveDay = onMoveDay,
        onAskFinish = {
            onAskSave()
            askSave = true
        },
        onRetry = onRetry,
        previewUrl = previewUrl,
        onOpenVideo = { videoOf = it.id },
        myUid = myUid,
        onAskDelete = { deleteOf = it.id },
        onUndoDelete = onUndoDelete,
    )

    // 삭제 팝업: 내 기록이면 "이 기록을 삭제할까요?", 남의 기록이면 "다른 사람의 기록이에요"(안내만)
    deleteOf?.let { id ->
        val log = state.windows.firstNotNullOfOrNull { w -> w.logs.firstOrNull { it.id == id } }
        if (log == null) {
            LaunchedEffect(id) { deleteOf = null }
        } else {
            val mine = log.authorId == myUid
            DeleteLogDialog(
                log = log,
                author = state.members[log.authorId],
                mine = mine,
                previewUrl = previewUrl,
                onDelete = {
                    deleteOf = null
                    if (mine) onDeleteLog(log)
                },
                onDismiss = { deleteOf = null },
            )
        }
    }

    // 원본 영상 팝업: 그날 영상 기록 전체를 찍은 순서대로 ‹ › 로 넘겨 본다
    videoOf?.let { startId ->
        LogVideoDialog(
            logs = remember(state.windows) { dayVideos(state.windows) },
            startLogId = startId,
            members = state.members,
            canLoadMore = !state.endReached,
            onLoadMore = onLoadMore,
            videoUrl = videoUrl,
            previewUrl = previewUrl,
            onDismiss = { videoOf = null },
        )
    }

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
    onMoveDay: (Long) -> Unit,
    onAskFinish: () -> Unit,
    onRetry: () -> Unit,
    previewUrl: suspend (FeedLog) -> String?,
    /** 기록 카드를 누르면 원본 영상 팝업 */
    onOpenVideo: (FeedLog) -> Unit = {},
    myUid: String = "",
    /** 기록 카드를 길게 누르면 삭제 팝업 */
    onAskDelete: (FeedLog) -> Unit = {},
    onUndoDelete: () -> Unit = {},
) {
    val viewingToday = state.day == today
    // 이날 내가 올린 기록이 있으면 "길게 누르면 삭제" 안내를 보여 준다
    val hasMine = state.windows.any { w -> w.logs.any { it.authorId == myUid } }
    // 아래에 더 볼 기록이 있으면(스크롤할 수 있거나 아직 다 읽지 않음) 안내를 보여 주고, 끝까지 내리면 숨긴다
    val showHint by remember(state.windows, state.endReached) {
        derivedStateOf { state.windows.isNotEmpty() && (listState.canScrollForward || !state.endReached) }
    }
    // 기록 카드의 작은 프로필을 누르면 뜨는 프로필 팝업(누구의 것인지)
    var profileOf by remember { mutableStateOf<Member?>(null) }
    profileOf?.let { ProfileDialog(member = it, onDismiss = { profileOf = null }) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // 위에 고정되는 부분
            Column(Modifier.padding(horizontal = 20.dp)) {
                LogoHeader()
                RecordTopBar(title = journey.name, onBack = onBack)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 〈 보는 날짜 · 전체 인원 〉
                    Row(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .background(LogUsColors.profileBox, RoundedCornerShape(16.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DayArrow(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            label = "전날 기록 보기",
                            enabled = state.canGoPrev,
                            onClick = { onMoveDay(-1) },
                        )
                        // 날짜 / "기록 8개 · 내 기록 3개"(세기 전이거나 못 세면 "전체 3명")
                        Column(
                            Modifier
                                .weight(1f)
                                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "${state.day.monthValue}월 ${state.day.dayOfMonth}일",
                                color = LogUsColors.primaryStrong,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Text(
                                dayCountLabel(state, journey.memberCount),
                                color = LogUsColors.primaryStrong,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        DayArrow(
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            label = "다음날 기록 보기",
                            enabled = state.canGoNext,
                            onClick = { onMoveDay(1) },
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
                // 안내 띠: 이날 내가 올린 기록이 있을 때만
                if (hasMine) item(key = "deleteHint") {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        DeleteHint()
                        Spacer(Modifier.height(10.dp))
                    }
                }
                // 지금 기록하기는 오늘을 볼 때만(지난날 화면에서 찍으면 오늘 기록으로 올라가 헷갈리므로)
                if (viewingToday) item(key = "record") {
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
                                if (viewingToday) {
                                    "오늘은 아직 기록이 없어요.\n'지금 기록하기'로 첫 장면을 남겨 보세요."
                                } else {
                                    "이날은 기록이 없어요.\n〈 〉로 다른 날을 볼 수 있어요."
                                },
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
                            LogCard(
                                log = log,
                                author = state.members[log.authorId],
                                previewUrl = previewUrl,
                                onProfileClick = { profileOf = it },
                                onClick = { onOpenVideo(log) },
                                onLongClick = { onAskDelete(log) },
                                longClickLabel = if (log.authorId == myUid) "내 기록 삭제" else "삭제 안내 보기",
                            )
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

        // 내 기록을 지운 뒤 5초 동안: "✓ 내 기록을 삭제했어요 · 되돌리기"
        state.justDeleted?.let {
            UndoDeleteBar(
                onUndo = onUndoDelete,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

/** 날짜 상자 아래 줄: "기록 8개 · 내 기록 3개". 아직 세지 못했으면 "전체 3명" */
private fun dayCountLabel(state: JourneyFeedUiState, memberCount: Int): String = when {
    state.dayCount != null && state.myDayCount != null -> "기록 ${state.dayCount}개 · 내 기록 ${state.myDayCount}개"
    state.dayCount != null -> "기록 ${state.dayCount}개"
    else -> "전체 ${memberCount}명"
}

/** 안내 띠 "ⓘ 내가 올린 기록을 길게 누르면 삭제할 수 있어요" */
@Composable
private fun DeleteHint() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(LogUsColors.profileBox, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = LogUsColors.primaryStrong, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("내가 올린 기록을 길게 누르면 삭제할 수 있어요", color = LogUsColors.primaryStrong, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** 지운 뒤 아래에 잠깐 뜨는 안내와 "되돌리기"(5초가 지나면 저절로 사라지고 그때 진짜로 지운다) */
@Composable
private fun UndoDeleteBar(onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp))
            .background(SurfaceDark, RoundedCornerShape(18.dp))
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = TextDark, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("내 기록을 삭제했어요", color = TextDark, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Surface(
            onClick = onUndo,
            shape = RoundedCornerShape(50),
            color = BorderDark,
            contentColor = OnPrimary,
        ) {
            Text(
                "되돌리기",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/** 날짜 상자 양쪽 화살표. 더 갈 수 없으면 흐리게 하고 누를 수 없게 한다 */
@Composable
private fun DayArrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = LogUsColors.primaryStrong,
            modifier = Modifier
                .size(26.dp)
                .alpha(if (enabled) 1f else 0.3f),
        )
    }
}

/** "09:20 ~ 11:20 시간" + 오른쪽 "3개" */
@Composable
internal fun WindowHeader(window: FeedWindow) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${minuteLabel(window.startMinute)} ~ ${minuteLabel(window.endMinute)} 시간",
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
 * onProfileClick 이 있으면 작은 프로필을 눌러 프로필 팝업(ProfileDialog)을 연다(N01·S15 공통).
 * onClick 이 있으면 영상 기록 카드의 나머지 부분을 눌러 원본 영상 팝업(LogVideoDialog)을 연다(N01·S15 공통).
 * onLongClick 이 있으면 길게 눌러 삭제 팝업(DeleteLogDialog)을 연다(N01, 2026-11-02 팀 그림). longClickLabel 은 화면 읽기 프로그램의 "작업" 이름.
 * 내 기록이라고 따로 표시(테두리·배지)하지는 않는다(2026-11-02 팀 결정).
 */
@OptIn(ExperimentalFoundationApi::class) // combinedClickable(누르기 + 길게 누르기)
@Composable
internal fun LogCard(
    log: FeedLog,
    author: Member?,
    previewUrl: suspend (FeedLog) -> String?,
    onProfileClick: ((Member) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String = "삭제",
) {
    val url by produceState<String?>(initialValue = null, log.id) { value = previewUrl(log) }
    val time = clockLabel(log.capturedAtMillis)
    val name = author?.nickname ?: "구성원"
    // 원본 영상 팝업은 영상 기록만 연다
    val openVideo = onClick?.takeIf { log.mediaType == "video" && log.mediaPath != null }
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(3f)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(BackgroundDark, Primary, PrimaryLight)))
            .combinedClickable(
                enabled = openVideo != null || onLongClick != null,
                onClick = { openVideo?.invoke() },
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress) // 길게 눌렀다는 진동
                        longClick()
                    }
                },
            )
            // 화면 읽기 프로그램은 카드 하나를 한 문장으로 읽는다
            .clearAndSetSemantics {
                contentDescription = listOfNotNull("$name 님의 기록", time, log.placeName).joinToString(", ")
                // 화면 읽기 프로그램에서는 카드를 두 번 누르면 원본 영상 팝업이, 영상이 아니면 프로필 팝업이 열린다.
                // 프로필 팝업·삭제는 "작업" 메뉴로 연다
                val profileAction = if (author != null && onProfileClick != null) {
                    { onProfileClick(author); true }
                } else {
                    null
                }
                val actions = mutableListOf<CustomAccessibilityAction>()
                if (openVideo != null) {
                    onClick(label = "원본 영상 보기") { openVideo(); true }
                    profileAction?.let { actions += CustomAccessibilityAction("$name 님 프로필 보기", it) }
                } else if (profileAction != null) {
                    onClick(label = "$name 님 프로필 보기", action = profileAction)
                }
                onLongClick?.let { longClick ->
                    actions += CustomAccessibilityAction(longClickLabel) { longClick(); true }
                }
                if (actions.isNotEmpty()) customActions = actions
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
                .padding(12.dp)
                .clip(CircleShape)
                .clickable(enabled = author != null && onProfileClick != null) {
                    author?.let { onProfileClick?.invoke(it) }
                },
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

/** 원본 영상 팝업에서 ‹ › 로 넘겨 볼 그날 영상 기록(찍은 순서) */
internal fun dayVideos(windows: List<FeedWindow>): List<FeedLog> =
    windows.flatMap { it.logs }
        .filter { it.mediaType == "video" && it.mediaPath != null }
        .sortedBy { it.capturedAtMillis }

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
internal fun clockLabel(millis: Long): String {
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
                day = day,
                canGoPrev = false,
                canGoNext = true,
                loading = false,
                windows = groupByWindows(logs, 2, zone),
                members = mapOf("a" to Member("a", "성연"), "b" to Member("b", "민수"), "c" to Member("c", "지우")),
                endReached = true,
            ),
            today = day,
            listState = rememberLazyListState(),
            onBack = {},
            onRecordNow = {},
            onMoveDay = {},
            onAskFinish = {},
            onRetry = {},
            previewUrl = { null },
        )
    }
}
