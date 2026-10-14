package com.logus.app.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
import com.logus.app.R
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import java.time.LocalDate

// N03 여정 저장 확인 팝업(기록 보기의 다운로드 모양 버튼 → "저장하시겠습니까?")
/**
 * 기록 보기(N01) 위에 뜨는 확인 팝업. 뒤의 기록 보기는 어둡게 보인다(Dialog 가 바탕을 덮는다).
 * - 위에서부터: X(닫기) → 연보라 동그라미 안 다운로드 모양 → "저장하시겠습니까?" → "저장하면 여정이 끝나고 마이로그로 옮겨져요"
 *   → 여정 요약 상자(여정 이름 / 오늘 날짜 · 기록 N개 · 전체 N명) → 주의 문구 → [취소] [저장]
 * - "저장"을 눌러야만 여정을 저장(나에게만 여정 끝내기)하고 마이로그로 간다(MainScreen). 저장하는 동안은 닫을 수 없다.
 * - 닫기: X·취소·폰의 뒤로 가기·팝업 바깥 누르기
 */
@Composable
fun SaveJourneyDialog(
    journeyName: String,
    today: LocalDate,
    /** 이 여정에 올라온 기록 수(전체 구성원). 세는 중이면 null */
    recordCount: Int?,
    memberCount: Int,
    saving: Boolean,
    /** 저장하지 못했을 때 보여 줄 문구 */
    error: String?,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismiss = { if (!saving) onDismiss() }
    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp)),
        ) {
            IconButton(
                onClick = dismiss,
                enabled = !saving,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 연보라 동그라미 안의 다운로드 모양(기록 보기의 저장 버튼과 같은 그림)
                Box(
                    Modifier
                        .size(60.dp)
                        .background(LogUsColors.iconCircle, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_download),
                        contentDescription = null,
                        tint = LogUsColors.primaryStrong,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "저장하시겠습니까?",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "저장하면 여정이 끝나고\n마이로그로 옮겨져요",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                // 여정 요약: 이름 / 날짜 · 기록 수 · 인원
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
                        .border(1.dp, LogUsColors.line, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        journeyName,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${today.monthValue}월 ${today.dayOfMonth}일  ·  기록 ${recordCount?.toString() ?: "…"}개  ·  전체 ${memberCount}명",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "저장한 뒤에는 이 여정에 기록을 더 올릴 수 없어요",
                    color = LogUsColors.warning,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    // 오류 글자는 본문색(다크 모드 대비 규칙)
                    Text(error, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = dismiss,
                        enabled = !saving,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, LogUsColors.line),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = LogUsColors.card,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Text("취소", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onSave,
                        enabled = !saving,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text("저장", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SaveJourneyDialogPreview() {
    LogUsTheme {
        SaveJourneyDialog(
            journeyName = "우리의 포르투",
            today = LocalDate.of(2026, 9, 24),
            recordCount = 8,
            memberCount = 3,
            saving = false,
            error = null,
            onSave = {},
            onDismiss = {},
        )
    }
}
