package com.logus.app.ui.journeydetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.R
import com.logus.app.journey.Journey
import com.logus.app.journeydetail.JourneyDetailUiState
import com.logus.app.record.FeedLog
import com.logus.app.ui.feed.LogCard
import com.logus.app.ui.feed.WindowHeader
import com.logus.app.ui.home.LogoHeader
import com.logus.app.ui.record.RecordTopBar
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.OnPrimary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.LocalDate

/** 여정 상세의 탭 버튼 세 개(스토리보드 번호: 기록 S15 · 추억 지도 S16 · 추억 영상 S17) */
enum class DetailTab(val label: String) { RECORDS("기록"), MAP("추억 지도"), RECAP("추억 영상") }

// S15·S16·S17 여정 상세 (스토리보드 v5 17·23쪽 수정본, 2026-10-14 팀 결정 번호)
/**
 * 마이로그(S14)에서 여정 카드를 누르면 여는 화면. 하단 탭은 숨긴다.
 * - 위에 고정: LOG EARTH → 뒤로 가기 + 여정 이름 → 날짜·지역 상자("9.24 포르투 · 1일째 / 3일", 양옆 ‹ › 로 하루씩 넘김)
 *   → 탭 버튼 [기록] [추억 지도] [추억 영상](한 화면 안에서 버튼으로 바꿔 본다)
 * - S15 기록: 고른 날의 기록을 N01 기록 보기와 같은 시간대 카드로 보여 준다. 아래로 내리면 30개씩 더 읽는다.
 * - S16 추억 지도 · S17 추억 영상: 아직 만들지 않아 "준비 중" 안내만 보여 준다(다음 작업).
 */
@Composable
fun JourneyDetailScreen(
    state: JourneyDetailUiState,
    onBack: () -> Unit,
    onMoveDay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    previewUrl: suspend (FeedLog) -> String?,
) {
    var tab by rememberSaveable { mutableStateOf(DetailTab.RECORDS) }
    val listState = rememberLazyListState()

    // 날짜를 바꾸면 목록을 맨 위로
    LaunchedEffect(state.dayIndex) { listState.scrollToItem(0) }
    // 끝에서 3칸 안쪽까지 내려오면 그날의 다음 기록을 읽는다
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    JourneyDetailContent(
        state = state,
        tab = tab,
        listState = listState,
        onBack = onBack,
        onSelectTab = { tab = it },
        onMoveDay = onMoveDay,
        onRetry = onRetry,
        previewUrl = previewUrl,
    )
}

/** 화면 그리기만 하는 부분(미리보기에서도 쓰려고 나눴다) */
@Composable
private fun JourneyDetailContent(
    state: JourneyDetailUiState,
    tab: DetailTab,
    listState: LazyListState,
    onBack: () -> Unit,
    onSelectTab: (DetailTab) -> Unit,
    onMoveDay: (Int) -> Unit,
    onRetry: () -> Unit,
    previewUrl: suspend (FeedLog) -> String?,
) {
    val journey = state.journey ?: return
    Column(Modifier.fillMaxSize()) {
        // 위에 고정되는 부분
        Column(Modifier.padding(horizontal = 20.dp)) {
            LogoHeader()
            RecordTopBar(title = journey.name, onBack = onBack)
            Spacer(Modifier.height(12.dp))
            DayBox(
                day = state.days.getOrNull(state.dayIndex),
                city = journey.city,
                index = state.dayIndex,
                total = state.days.size,
                onMoveDay = onMoveDay,
            )
            Spacer(Modifier.height(12.dp))
            TabButtons(selected = tab, onSelect = onSelectTab)
            Spacer(Modifier.height(14.dp))
        }

        when (tab) {
            DetailTab.RECORDS -> RecordsTab(state = state, listState = listState, onRetry = onRetry, previewUrl = previewUrl)
            DetailTab.MAP -> ComingSoonTab(
                icon = R.drawable.ic_globe,
                text = "추억 지도는 준비 중이에요.\n구성원별 핀과 이동 경로가 여기에 나와요.",
            )
            DetailTab.RECAP -> ComingSoonTab(
                icon = R.drawable.ic_camera,
                text = "추억 영상은 준비 중이에요.\n그날의 DAY RECAP 영상이 여기에 나와요.",
            )
        }
    }
}

/** 날짜·지역 상자: ‹ "9.24 포르투  1일째 / 3일" › */
@Composable
private fun DayBox(day: LocalDate?, city: String, index: Int, total: Int, onMoveDay: (Int) -> Unit) {
    val arrowColors = IconButtonDefaults.iconButtonColors(
        contentColor = LogUsColors.primaryStrong,
        disabledContentColor = LogUsColors.primaryStrong.copy(alpha = 0.35f),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(LogUsColors.profileBox, RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val multiDay = total > 1
        if (multiDay) {
            IconButton(onClick = { onMoveDay(-1) }, enabled = index > 0, colors = arrowColors) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "전날 기록")
            }
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                listOfNotNull(day?.let { "${it.monthValue}.${it.dayOfMonth}" }, city.ifBlank { null }).joinToString(" "),
                color = LogUsColors.primaryStrong,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            if (multiDay) {
                Spacer(Modifier.width(6.dp))
                Text("${index + 1}일째 / ${total}일", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        if (multiDay) {
            IconButton(onClick = { onMoveDay(1) }, enabled = index < total - 1, colors = arrowColors) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 날 기록")
            }
        }
    }
}

/** 탭 버튼 [기록] [추억 지도] [추억 영상]: 고른 것은 보라 채움(흰 글자), 나머지는 흰 카드 */
@Composable
private fun TabButtons(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primary else LogUsColors.card,
                contentColor = if (isSelected) OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                border = if (isSelected) null else BorderStroke(1.dp, LogUsColors.line),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(tab.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** S15 기록: 그날 기록을 시간대별 카드로(N01 과 같은 카드) */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.RecordsTab(
    state: JourneyDetailUiState,
    listState: LazyListState,
    onRetry: () -> Unit,
    previewUrl: suspend (FeedLog) -> String?,
) {
    // 아래에 더 볼 기록이 있으면 안내를 보여 주고, 끝까지 내리면 숨긴다(N01 과 같다)
    val showHint by remember(state.windows, state.endReached) {
        derivedStateOf { state.windows.isNotEmpty() && (listState.canScrollForward || !state.endReached) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        when {
            state.loading -> item(key = "loading") {
                CenteredBox { CircularProgressIndicator(color = LogUsColors.primaryStrong) }
            }
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
                        "이날은 기록이 없어요.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
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

/** S16·S17 자리: 연보라 동그라미 그림 + 준비 중 안내 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ComingSoonTab(icon: Int, text: String) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .background(LogUsColors.iconCircle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = LogUsColors.primaryStrong, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
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

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun JourneyDetailPreview() {
    val journey = Journey(
        id = "j", name = "우리의 포르투", startDate = "2026-09-24", endDate = "2026-09-26",
        memberIds = emptyList(), memberCount = 3, city = "포르투",
    )
    LogUsTheme {
        JourneyDetailContent(
            state = JourneyDetailUiState(
                journey = journey,
                days = listOf(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25), LocalDate.of(2026, 9, 26)),
                loading = false,
                endReached = true,
            ),
            tab = DetailTab.RECORDS,
            listState = rememberLazyListState(),
            onBack = {},
            onSelectTab = {},
            onMoveDay = {},
            onRetry = {},
            previewUrl = { null },
        )
    }
}
