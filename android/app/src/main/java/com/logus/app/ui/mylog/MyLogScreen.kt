package com.logus.app.ui.mylog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.logus.app.journey.Journey
import com.logus.app.mylog.MyLogUiState
import com.logus.app.ui.home.CoverPlaceholder
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.OnPrimary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

// S14 마이로그 여정 목록 (스토리보드 v5 23쪽 수정본. 2026-10-14 팀 결정으로 S10 → S14)
/**
 * 하단 탭 "마이로그". 나에게 끝난 지난 여정을 최근 시작한 순서로 보여 준다(하단 탭은 그대로 보인다).
 * - 위: 가운데 "마이로그" 제목(2026-10-15 팀 결정, 예전 준비 중 화면과 같은 모양). 폰의 뒤로 가기는 홈 탭으로(MainScreen)
 * - 여정 카드: 왼쪽 대표 사진(가장 먼저 찍은 기록의 대표 화면, 없으면 보라 그림) + 여정 이름 · "9.24~26 · 3명" · "지난 여정 ›"
 *   카드를 누르면 onOpenJourney(여정 상세 S15~S17, MainScreen 이 연결)
 * - 아래로 내려 끝에 가까워지면 다음 여정을 읽는다(10개씩)
 */
@Composable
fun MyLogScreen(
    state: MyLogUiState,
    onOpenJourney: (Journey) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    coverUrl: suspend (String) -> String?,
    /** 휴지통 확인 팝업에서 "지우기"(내 마이로그에서만 지움) */
    onHide: (Journey) -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val listState = rememberLazyListState()
    val context = LocalContext.current
    // 휴지통을 누른 여정(확인 팝업을 띄운다)
    var confirming by remember { mutableStateOf<Journey?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onMessageShown()
        }
    }
    confirming?.let { journey ->
        HideJourneyDialog(
            journey = journey,
            onDismiss = { confirming = null },
            onConfirm = {
                confirming = null
                onHide(journey)
            },
        )
    }

    // 끝에서 2칸 안쪽까지 내려오면 다음 여정을 읽는다(한 번에 다 읽지 않는다)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 2
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { onLoadMore() }
    }

    Column(Modifier.fillMaxSize()) {
        // 위에 고정되는 제목
        Text(
            "마이로그",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = 20.dp)
                .semantics { heading() },
        )

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
                state.journeys.isEmpty() -> item(key = "empty") {
                    CenteredBox {
                        Text(
                            "아직 지난 여정이 없어요.\n여정이 끝나면 여기에 모여요.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> items(state.journeys, key = { it.id }) { journey ->
                    PastJourneyCard(
                        journey = journey,
                        coverUrl = coverUrl,
                        deleting = state.deletingId == journey.id,
                        onClick = { onOpenJourney(journey) },
                        onDelete = { confirming = journey },
                    )
                    Spacer(Modifier.height(12.dp))
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
    }
}

/** 지난 여정 카드: 대표 사진 + 여정 이름 · 기간·인원 · "지난 여정 ›" + 오른쪽 휴지통(2026-10-28 팀 요청) */
@Composable
private fun PastJourneyCard(
    journey: Journey,
    coverUrl: suspend (String) -> String?,
    deleting: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val url by produceState<String?>(initialValue = null, journey.id) { value = coverUrl(journey.id) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, LogUsColors.border, shape)
            .clickable(role = Role.Button, onClickLabel = "여정 다시 보기", onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LogUsColors.border),
        ) {
            // 보라 그림을 먼저 깔고, 대표 사진을 받으면 그 위에 덮는다(받는 중이거나 사진이 없어도 빈 칸으로 보이지 않게)
            CoverPlaceholder(Modifier.fillMaxSize())
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = "${journey.name} 대표 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                journey.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${shortPeriod(journey.startDate, journey.endDate)} · ${journey.memberCount}명",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "지난 여정 ›",
                color = LogUsColors.primaryStrong,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        // 휴지통(누르기 쉬운 48dp). 지우는 중이면 동그라미가 돈다
        if (deleting) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LogUsColors.primaryStrong, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "${journey.name} 마이로그에서 지우기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 휴지통 확인 팝업: 휴지통 그림 → "마이로그에서 지울까요?" → 무엇이 사라지고 무엇이 남는지 → [취소] [지우기].
 * "지우기"는 내 마이로그에서만 지운다(2026-10-28 팀 결정 (가)).
 */
@Composable
private fun HideJourneyDialog(journey: Journey, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp))
                .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(LogUsColors.iconCircle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = LogUsColors.amberText, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "마이로그에서 지울까요?",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "\"${journey.name}\"이(가) 내 마이로그에서 사라져요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text("· 다른 구성원의 마이로그와 기록은 그대로 남아요", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 19.sp)
                Text("· 내가 올린 기록도 지워지지 않아요", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, lineHeight = 19.sp)
                Text(
                    "· 지운 여정은 앱에서 다시 볼 수 없어요",
                    color = LogUsColors.warning,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text("취소", color = LogUsColors.primaryStrong, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = OnPrimary),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("지우기", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * 여정 목록 카드의 짧은 기간 표시(스토리보드 "9.24~26").
 * 같은 날 "9.12" / 같은 달 "9.24~26" / 다른 달 "9.30~10.2" / 다른 해 "2026.12.30~2027.1.2"
 */
internal fun shortPeriod(start: String, end: String): String {
    fun parts(d: String) = d.split("-").mapNotNull { it.toIntOrNull() }
    val s = parts(start)
    val e = parts(end)
    if (s.size != 3 || e.size != 3) return "$start~$end"
    return when {
        s == e -> "${s[1]}.${s[2]}"
        s[0] != e[0] -> "${s[0]}.${s[1]}.${s[2]}~${e[0]}.${e[1]}.${e[2]}"
        s[1] != e[1] -> "${s[1]}.${s[2]}~${e[1]}.${e[2]}"
        else -> "${s[1]}.${s[2]}~${e[2]}"
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun MyLogScreenPreview() {
    fun journey(id: String, name: String, start: String, end: String) =
        Journey(id = id, name = name, startDate = start, endDate = end, memberIds = emptyList(), memberCount = 3)
    LogUsTheme {
        MyLogScreen(
            state = MyLogUiState(
                loading = false,
                endReached = true,
                journeys = listOf(
                    journey("a", "우리의 포르투", "2026-09-24", "2026-09-26"),
                    journey("b", "포르투 산책", "2026-09-12", "2026-09-12"),
                ),
            ),
            onOpenJourney = {},
            onLoadMore = {},
            onRetry = {},
            coverUrl = { null },
        )
    }
}
