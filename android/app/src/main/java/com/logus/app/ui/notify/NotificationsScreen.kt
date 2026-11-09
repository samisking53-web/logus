package com.logus.app.ui.notify

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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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
import com.logus.app.R
import com.logus.app.notify.AppNotification
import com.logus.app.notify.NotificationsUiState
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.home.CoinIcon
import com.logus.app.ui.home.LogoHeader
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.OnPrimary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

// 알림 화면(스토리보드에 없음, 2026-11-09 팀 그림: S01·S01-A 홈 오른쪽 위 종 버튼을 누르면 열린다)
/**
 * LOG EARTH → [‹](홈으로) [알림 ③](안 읽은 수) [모두 읽음] → "오늘"·"어제"·"11월 6일 (목)"로 나눈 알림 카드.
 * 카드: 왼쪽 그림 상자(종류마다 그림, 코인은 연한 앰버) · 제목 · 내용 · 오른쪽 시각과 안 읽음 보라 점.
 * 안 읽은 알림은 흰 카드 + 연보라 테두리, 읽은 알림은 바탕색 카드 + 옅은 테두리(제목 글자도 덜 굵게).
 * 카드를 누르면 읽음으로 바뀌고 관련 화면을 연다(MainScreen 이 연결). 하단 탭은 그대로 보인다.
 */
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpen: (AppNotification) -> Unit,
    onRetry: () -> Unit,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            LogoHeader()
            TopBar(unread = state.unreadCount, onBack = onBack, onMarkAllRead = onMarkAllRead)
        }
        when {
            state.loading && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            state.error != null && state.items.isEmpty() -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ErrorMessage(state.error)
                TextButton(onClick = onRetry) { Text("다시 시도", color = LogUsColors.primaryStrong) }
            }

            state.items.isEmpty() -> EmptyNotifications()

            else -> {
                val groups = state.items.groupBy { Instant.ofEpochMilli(it.createdAtMillis).atZone(zone).toLocalDate() }
                LazyColumn(
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    groups.forEach { (day, items) ->
                        item(key = "h$day") {
                            Text(
                                dayLabel(day, today),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(top = 12.dp, bottom = 2.dp)
                                    .semantics { heading() },
                            )
                        }
                        items(items, key = { it.id }) { item ->
                            NotificationCard(item, zone, onClick = { onOpen(item) })
                        }
                    }
                }
            }
        }
    }
}

/** [‹] [알림 ③] [모두 읽음] */
@Composable
private fun TopBar(unread: Int, onBack: () -> Unit, onMarkAllRead: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            shape = CircleShape,
            color = LogUsColors.card,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, LogUsColors.line),
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "홈으로 돌아가기", modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(LogUsColors.card)
                .semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("알림", color = MaterialTheme.colorScheme.onSurface, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (unread > 0) {
                Spacer(Modifier.width(8.dp))
                CountBadge(unread, size = 24)
            }
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            onClick = onMarkAllRead,
            enabled = unread > 0,
            shape = RoundedCornerShape(26.dp),
            color = LogUsColors.card,
            contentColor = LogUsColors.primaryStrong,
            border = BorderStroke(1.dp, LogUsColors.line),
            modifier = Modifier
                .height(52.dp)
                .alpha(if (unread > 0) 1f else 0.45f),
        ) {
            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text("모두 읽음", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 알림 카드 하나 */
@Composable
private fun NotificationCard(item: AppNotification, zone: ZoneId, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val time = Instant.ofEpochMilli(item.createdAtMillis).atZone(zone).toLocalTime()
    val timeText = "%02d:%02d".format(time.hour, time.minute)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (item.read) MaterialTheme.colorScheme.background else LogUsColors.card)
            .border(1.dp, if (item.read) LogUsColors.line else LogUsColors.border, shape)
            .clickable(role = Role.Button, onClickLabel = "알림 열기", onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = (if (item.read) "" else "안 읽음, ") + "${item.title}, ${item.body}, $timeText"
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeIcon(item.type)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = if (item.read) FontWeight.Medium else FontWeight.Bold,
                lineHeight = 21.sp,
                maxLines = 2, // 폰 폭이 좁아도 "○○ 님이 여정에 참여했어요"가 잘리지 않게 두 줄까지
                overflow = TextOverflow.Ellipsis,
            )
            if (item.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(timeText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            if (!item.read) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/** 왼쪽 그림 상자: 종류마다 그림. 코인은 연한 앰버 상자에 동전 */
@Composable
private fun TypeIcon(type: String) {
    val amber = type == "coins"
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (amber) LogUsColors.warningBox else LogUsColors.iconCircle),
        contentAlignment = Alignment.Center,
    ) {
        if (amber) {
            CoinIcon(Modifier.size(24.dp))
        } else {
            Icon(typePainter(type), contentDescription = null, tint = LogUsColors.primaryStrong, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun typePainter(type: String): Painter = when (type) {
    "captureReminder" -> painterResource(R.drawable.ic_camera)
    "newLog" -> painterResource(R.drawable.ic_image)
    "memberJoined" -> painterResource(R.drawable.ic_person_add)
    "recapConsent" -> rememberVectorPainter(Icons.Filled.PlayArrow)
    "recapPublished" -> painterResource(R.drawable.ic_nav_explore)
    "recapRejected" -> rememberVectorPainter(Icons.Outlined.Info)
    else -> rememberVectorPainter(Icons.Outlined.Notifications)
}

/** 빨간 동그라미 숫자(안 읽은 알림 수). 10개 이상은 "9+" */
@Composable
internal fun CountBadge(count: Int, size: Int = 20, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = size.dp, minHeight = size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 빨강 채움 + 흰 글자(대비 5.4:1, 라이트·다크 같음)
        Text(
            if (count > 9) "9+" else count.toString(),
            color = OnPrimary,
            fontSize = (size * 0.55f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

/** 알림이 하나도 없을 때 */
@Composable
private fun EmptyNotifications() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 80.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Notifications, contentDescription = null, tint = LogUsColors.primaryStrong, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(14.dp))
        Text("아직 알림이 없어요", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "촬영 시간이 되거나 구성원이 기록을 올리면 여기에 모여요",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** 오늘 → "오늘", 어제 → "어제", 그 밖 → "11월 6일 (목)" */
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "오늘"
    today.minusDays(1) -> "어제"
    else -> "${day.monthValue}월 ${day.dayOfMonth}일 (${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})"
}

// ---------- 미리보기 ----------

@Preview(showBackground = true, heightDp = 860)
@Composable
private fun NotificationsPreview() {
    val zone = ZoneId.of("Asia/Seoul")
    val today = LocalDate.of(2026, 9, 24)
    fun at(day: LocalDate, h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
    val y = today.minusDays(1)
    LogUsTheme {
        NotificationsScreen(
            state = NotificationsUiState(
                unreadCount = 3,
                items = listOf(
                    AppNotification("1", "captureReminder", "지금 기록할 시간이에요", "우리의 포르투 · 2시간 주기 알림", "j", null, false, at(today, 14, 30)),
                    AppNotification("2", "newLog", "민지 님이 기록을 올렸어요", "우리의 포르투 · 11:20 시간대", "j", null, false, at(today, 13, 8)),
                    AppNotification("3", "coins", "+10코인을 받았어요", "위치와 함께 저장한 기록 1개", "j", null, false, at(today, 12, 35)),
                    AppNotification("4", "memberJoined", "수현 님이 여정에 참여했어요", "우리의 포르투 · 구성원 4명", "j", null, true, at(today, 10, 12)),
                    AppNotification("5", "recapPublished", "DAY RECAP을 탐색에 올렸어요", "우리의 포르투 · 9.23 · 모두 동의해서 전체 공개로 올라갔어요", null, "r", true, at(y, 21, 0)),
                ),
            ),
            onBack = {},
            onMarkAllRead = {},
            onOpen = {},
            onRetry = {},
            today = today,
            zone = zone,
        )
    }
}
