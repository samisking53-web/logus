package com.logus.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.logus.app.home.OngoingCard
import com.logus.app.home.RemoveJourneyState
import com.logus.app.journey.Journey
import com.logus.app.journey.Member
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.OnPrimary

// S01-A 여정 삭제·나가기 팝업 (2026-11-04 팀 그림: 여정 카드를 길게 누르면 뜬다)
/**
 * 내가 만든 여정(state.mine = true): "이 여정을 삭제할까요?"
 *   작은 대표 사진 · 여정 이름 · "9.24 ~ 9.26 · 내가 만든 여정" · X
 *   → 주의 상자 "구성원 4명이 올린 기록 8개가 모두 사라지고 추억 지도와 DAY RECAP도 만들 수 없어요. 삭제한 여정은 되돌릴 수 없어요."
 *   → [취소] [🗑 여정 삭제](빨강 채움·흰 글자). 누르면 구성원 모두에게서 여정이 지워진다(서버 deleteJourney, 5초 동안 되돌리기).
 * 초대받아 들어온 여정(mine = false): "이 여정에서 나갈까요?"
 *   → 주의 상자 "나가도 내가 올린 기록은 다른 구성원의 여정에 그대로 남아요 …"
 *   → [취소] [↪ 여정 나가기]. 나만 구성원에서 빠진다(서버 leaveJourney, 2026-11-04 팀 결정).
 * 받은 코인은 어느 쪽이든 그대로 남는다(2026-11-04 팀 결정). 닫기: X·취소·폰의 뒤로 가기·팝업 바깥 누르기
 */
@Composable
fun RemoveJourneyDialog(
    state: RemoveJourneyState,
    coverUrl: suspend (String) -> String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val journey = state.card.journey
    val ownerName = state.card.members.firstOrNull { it.uid == journey.ownerId }?.nickname
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp)),
        ) {
            Column(Modifier.padding(20.dp)) {
                // 작은 대표 사진 · 제목 · 여정 이름 · 기간(X 는 오른쪽 위 모서리에 따로 띄운다)
                Row(verticalAlignment = Alignment.Top) {
                    CoverThumbnail(state.card, coverUrl)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (state.mine) "이 여정을 삭제할까요?" else "이 여정에서 나갈까요?",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(end = 36.dp)
                                .semantics { heading() },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            journey.name,
                            color = LogUsColors.primaryStrong,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${journeyPeriod(journey.startDate, journey.endDate)} · " + when {
                                state.mine -> "내가 만든 여정"
                                ownerName != null -> "$ownerName 님이 만든 여정"
                                else -> "초대받은 여정"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // 주의 상자(연한 앰버 바탕 + 진한 앰버 글자, 대비 5.5:1. 다크는 바탕색 + 앰버 7.3:1)
                Text(
                    warningText(state),
                    color = LogUsColors.amberText,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LogUsColors.warningBox, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Spacer(Modifier.height(18.dp))

                // [취소] [🗑 여정 삭제] / [취소] [↪ 여정 나가기]
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(0.4f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, LogUsColors.border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = LogUsColors.card,
                            contentColor = LogUsColors.primaryStrong,
                        ),
                    ) {
                        Text("취소", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(0.6f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        // 지우기·나가기 버튼은 빨강 채움 + 흰 글자(대비 5.4:1, 라이트·다크 같음)
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = OnPrimary,
                        ),
                    ) {
                        Icon(
                            if (state.mine) Icons.Filled.Delete else Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.mine) "여정 삭제" else "여정 나가기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // 오른쪽 위 X
            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = LogUsColors.iconCircle,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, contentDescription = "닫기", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** 주의 상자 문구. 중요한 부분("기록 8개가 모두 사라지고")은 굵게 */
private fun warningText(state: RemoveJourneyState): AnnotatedString = buildAnnotatedString {
    fun bold(text: String) = withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
    if (!state.mine) {
        append("나가도 ")
        bold("내가 올린 기록은 다른 구성원의 여정에 그대로 남아요.")
        append(" 나간 뒤에는 이 여정을 볼 수 없고 내 기록을 지울 수도 없어요. 다시 들어오려면 초대 코드가 필요해요.")
        return@buildAnnotatedString
    }
    val people = state.card.journey.memberCount
    val count = state.logCount
    if (count == 0) {
        append(if (people > 1) "구성원 ${people}명 모두의 홈에서 " else "내 홈과 마이로그에서 ")
        bold("이 여정이 사라져요.")
    } else {
        append(if (people > 1) "구성원 ${people}명이 올린 " else "내가 올린 ")
        bold(if (count != null) "기록 ${count}개가 모두 사라지고" else "기록이 모두 사라지고")
        append(" 추억 지도와 DAY RECAP도 만들 수 없어요.")
    }
    append(" 삭제한 여정은 되돌릴 수 없어요.")
}

/** 팝업 왼쪽 위 작은 대표 사진(내가 고른 사진, 없으면 기본 그림) */
@Composable
private fun CoverThumbnail(card: OngoingCard, coverUrl: suspend (String) -> String?) {
    val url by produceState<String?>(initialValue = null, card.coverPath) {
        value = card.coverPath?.let { coverUrl(it) }
    }
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CoverPlaceholder(Modifier.fillMaxSize())
        }
    }
}

// ---------- 미리보기 ----------

private val previewCard = OngoingCard(
    Journey("j1", "우리의 포르투", "2026-09-24", "2026-09-26", listOf("a", "b", "c", "d"), 4, ownerId = "a"),
    listOf(Member("a", "성연"), Member("b", "민지"), Member("c", "수아"), Member("d", "현우")),
)

@Preview(showBackground = true)
@Composable
private fun RemoveJourneyDeletePreview() {
    LogUsTheme { RemoveJourneyDialog(RemoveJourneyState(previewCard, mine = true, logCount = 8), { null }, {}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun RemoveJourneyLeavePreview() {
    LogUsTheme { RemoveJourneyDialog(RemoveJourneyState(previewCard, mine = false), { null }, {}, {}) }
}
