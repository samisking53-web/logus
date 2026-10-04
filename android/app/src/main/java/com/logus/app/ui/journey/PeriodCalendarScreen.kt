package com.logus.app.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.ui.components.StepTopBar
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import java.time.LocalDate
import java.time.YearMonth

// S03 여행 기간 달력
/**
 * 한 달 전체가 보이는 달력에서 여행 기간을 고른다.
 * - 끌어서 고르기: 시작일을 누른 채 종료일까지 손가락을 끌면 그 사이가 모두 선택된다.
 * - 눌러서 고르기: 시작일을 누르고, 이어서 종료일을 누른다(같은 날을 두 번 누르면 하루 여행).
 * - 지난 날짜는 고를 수 없다. 위쪽 ‹ › 로 달을 넘긴다.
 * 외부 달력 라이브러리 없이 직접 그렸다(색은 팀 팔레트만 사용).
 */
@Composable
fun PeriodCalendarScreen(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onBack: () -> Unit,
    onConfirm: (start: LocalDate, end: LocalDate) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    // 화면을 돌려도 고른 날짜가 남도록 글자("YYYY-MM-DD")로 기억한다
    var startText by rememberSaveable { mutableStateOf(initialStart?.toString()) }
    var endText by rememberSaveable { mutableStateOf(initialEnd?.toString()) }
    var pickingEnd by rememberSaveable { mutableStateOf(false) } // 시작일만 누르고 종료일을 기다리는 중
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(initialStart ?: today).toString()) }

    val start = startText?.let(LocalDate::parse)
    val end = endText?.let(LocalDate::parse)
    val month = YearMonth.parse(monthText)
    val thisMonth = YearMonth.from(today)

    fun setRange(a: LocalDate, b: LocalDate) {
        startText = minOf(a, b).toString()
        endText = maxOf(a, b).toString()
    }

    /** 눌러서 고르기 */
    fun tap(day: LocalDate) {
        if (pickingEnd && start != null && !day.isBefore(start)) {
            endText = day.toString()
            pickingEnd = false
        } else {
            startText = day.toString()
            endText = day.toString()
            pickingEnd = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        StepTopBar(onBack = onBack, title = "여행 기간 선택")

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            // 달 이동: ‹ 2026년 10월 ›
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { monthText = month.minusMonths(1).toString() },
                    enabled = month.isAfter(thisMonth), // 지난 달로는 가지 않는다
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "이전 달",
                        tint = LogUsColors.primaryStrong,
                        modifier = Modifier.alpha(if (month.isAfter(thisMonth)) 1f else 0.3f),
                    )
                }
                Text(
                    "${month.year}년 ${month.monthValue}월",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "다음 달", tint = LogUsColors.primaryStrong)
                }
            }
            Spacer(Modifier.height(16.dp))

            // 요일
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            MonthGrid(
                month = month,
                today = today,
                start = start,
                end = end,
                onTap = ::tap,
                onDragRange = { a, b ->
                    setRange(a, b)
                    pickingEnd = false
                },
            )

            Spacer(Modifier.weight(1f))
            Text(
                "시작일을 누른 채 종료일까지 끌거나, 시작일과 종료일을 차례로 눌러 주세요",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            // 고른 기간
            Text(
                if (start != null && end != null) periodLabel(start, end) else "아직 고르지 않았어요",
                color = if (start != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, LogUsColors.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            )
        }

        Button(
            onClick = { if (start != null && end != null) onConfirm(start, end) },
            enabled = start != null && end != null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Text("기간 선택 완료", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 한 달 칸(6줄까지). 손가락 위치 → 몇째 줄·몇째 칸 → 날짜로 바꿔서 누르기·끌기를 처리한다.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    start: LocalDate?,
    end: LocalDate?,
    onTap: (LocalDate) -> Unit,
    onDragRange: (LocalDate, LocalDate) -> Unit,
) {
    // 1일이 무슨 요일인지(일요일 = 0칸)
    val firstOffset = month.atDay(1).dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()
    val weeks = (firstOffset + daysInMonth + 6) / 7
    val cellHeight = 52.dp

    val latestTap by rememberUpdatedState(onTap)
    val latestDrag by rememberUpdatedState(onDragRange)
    var dragAnchor by remember { mutableStateOf<LocalDate?>(null) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val cellWidthPx = with(density) { maxWidth.toPx() } / 7f
        val cellHeightPx = with(density) { cellHeight.toPx() }

        /** 손가락 위치의 날짜. 칸 밖이거나 지난 날짜면 null */
        fun dayAt(offset: Offset): LocalDate? {
            val col = (offset.x / cellWidthPx).toInt()
            val row = (offset.y / cellHeightPx).toInt()
            if (offset.x < 0 || offset.y < 0 || col !in 0..6 || row !in 0 until weeks) return null
            val dayNumber = row * 7 + col - firstOffset + 1
            if (dayNumber !in 1..daysInMonth) return null
            return month.atDay(dayNumber).takeIf { !it.isBefore(today) }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(month, weeks, cellWidthPx) {
                    detectTapGestures(onTap = { offset -> dayAt(offset)?.let(latestTap) })
                }
                .pointerInput(month, weeks, cellWidthPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragAnchor = dayAt(offset)
                            dragAnchor?.let { latestDrag(it, it) }
                        },
                        onDrag = { change, _ ->
                            val anchor = dragAnchor ?: dayAt(change.position)?.also { dragAnchor = it }
                            val current = dayAt(change.position)
                            if (anchor != null && current != null) latestDrag(anchor, current)
                        },
                        onDragEnd = { dragAnchor = null },
                        onDragCancel = { dragAnchor = null },
                    )
                },
        ) {
            for (row in 0 until weeks) {
                Row(Modifier.fillMaxWidth().height(cellHeight)) {
                    for (col in 0..6) {
                        val dayNumber = row * 7 + col - firstOffset + 1
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (dayNumber in 1..daysInMonth) {
                                DayCell(month.atDay(dayNumber), today, start, end)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 날짜 한 칸: 기간 사이는 연보라 띠, 시작·종료일은 보라 동그라미(흰 글자), 오늘은 테두리 */
@Composable
private fun DayCell(day: LocalDate, today: LocalDate, start: LocalDate?, end: LocalDate?) {
    val past = day.isBefore(today)
    val inRange = start != null && end != null && !day.isBefore(start) && !day.isAfter(end)
    val isEdge = day == start || day == end
    val band = MaterialTheme.colorScheme.surface

    Box(
        Modifier
            .fillMaxSize()
            .semantics {
                contentDescription = buildString {
                    append("${day.monthValue}월 ${day.dayOfMonth}일")
                    if (day == today) append(", 오늘")
                    if (isEdge) append(if (day == start) ", 시작일" else ", 종료일") else if (inRange) append(", 선택됨")
                    if (past) append(", 고를 수 없음")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 기간 띠(시작일은 오른쪽 반, 종료일은 왼쪽 반, 사이는 전체)
        if (inRange && start != end) {
            Row(Modifier.fillMaxWidth().height(40.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(if (day == start) band.copy(alpha = 0f) else band))
                Box(Modifier.weight(1f).fillMaxHeight().background(if (day == end) band.copy(alpha = 0f) else band))
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .then(
                    when {
                        isEdge -> Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        day == today -> Modifier.border(1.5.dp, LogUsColors.primaryStrong, CircleShape)
                        else -> Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                color = when {
                    isEdge -> MaterialTheme.colorScheme.onPrimary
                    inRange -> LogUsColors.primaryStrong
                    else -> MaterialTheme.colorScheme.onBackground
                },
                fontSize = 15.sp,
                fontWeight = if (isEdge || inRange || day == today) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.alpha(if (past) 0.35f else 1f),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun PeriodCalendarPreview() {
    val today = LocalDate.of(2026, 9, 20)
    LogUsTheme {
        PeriodCalendarScreen(
            initialStart = LocalDate.of(2026, 9, 24),
            initialEnd = LocalDate.of(2026, 9, 26),
            onBack = {},
            onConfirm = { _, _ -> },
            today = today,
        )
    }
}
