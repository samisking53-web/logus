package com.logus.app.ui.journey

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logus.app.R
import com.logus.app.journey.InviteCode
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import kotlinx.coroutines.delay

// S01-A 친구 추가 버튼 → 여정에 초대하기 팝업
/**
 * 여행 중에도 새 사람을 초대하는 팝업. 뒤의 S01-A 홈은 어둡게 보인다(Dialog 가 바탕을 덮는다).
 * - 위에서부터: "여정에 초대하기" → 여정 이름·현재 함께하는 사람 수 → 초대 코드 상자(복사) → 안내 → "초대 코드 공유"
 * - 초대 코드는 여정 문서(journeys.inviteCode)에 저장된 코드라서 같은 여정은 언제 열어도 같은 코드가 보인다(바뀌지 않음).
 *   여정이 끝날 때까지 쓸 수 있다(서버 previewInvite·joinJourney 가 여정 종료일로 확인한다).
 * - "초대 코드 공유"는 안드로이드 공유 창을 연다. Gmail(내 구글 계정)·메시지·카카오톡 등에서 골라 보낸다.
 *   보내는 글에는 여정 이름·초대 코드·참여 방법만 넣는다. 링크는 쓰지 않는다(팀 결정: 6자리 코드로만 초대).
 * - 닫기: X·폰의 뒤로 가기·팝업 바깥 누르기(잃을 내용이 없어서 바깥을 눌러도 닫힌다)
 */
@Composable
fun JourneyInviteDialog(
    journeyName: String,
    memberCount: Int,
    /** 여정의 6자리 초대 코드. 없으면(예전 데이터 등) 코드 대신 안내를 보여 주고 복사·공유를 막는다 */
    inviteCode: String?,
    /** 여정 종료일 "YYYY-MM-DD" (코드를 쓸 수 있는 마지막 날) */
    endDate: String,
    /** 공유 글에 넣을 초대한 사람(나)의 닉네임 */
    inviterName: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val code = inviteCode?.takeIf { InviteCode.isComplete(it) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp))
                .padding(bottom = 22.dp),
        ) {
            // 제목 + 닫기
            Row(
                Modifier.padding(start = 22.dp, end = 8.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "여정에 초대하기",
                    color = LogUsColors.primaryStrong,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(Modifier.padding(horizontal = 22.dp)) {
                Spacer(Modifier.height(10.dp))
                // 진행 중인 여정 이름 + 현재 인원
                Text(
                    journeyName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "현재 함께하는 사람 ${memberCount}명",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(18.dp))

                // 초대 코드 상자: 왼쪽 "초대 코드"·코드, 오른쪽 흰 "복사" 버튼
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(LogUsColors.iconCircle, RoundedCornerShape(18.dp))
                        .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("초대 코드", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            code ?: "------",
                            color = LogUsColors.primaryStrong,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            maxLines = 1,
                            // 화면 읽기 프로그램이 한 글자씩 읽게
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = code?.let { "초대 코드 " + it.toList().joinToString(" ") }
                                    ?: "초대 코드 없음"
                            },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            if (code != null) {
                                clipboard.setText(AnnotatedString(code))
                                copied = true
                            }
                        },
                        enabled = code != null,
                        shape = RoundedCornerShape(50),
                        color = LogUsColors.card,
                        contentColor = LogUsColors.primaryStrong,
                    ) {
                        Row(
                            Modifier
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (copied) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(painterResource(R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(if (copied) "복사됨" else "복사", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (code != null) {
                        "이 코드는 '$journeyName' 여정에만 쓰이고, 바뀌지 않아요.\n" +
                            "여정이 끝나는 ${monthDay(endDate)}까지 쓸 수 있어요."
                    } else {
                        "초대 코드를 불러오지 못했어요. 홈을 다시 열어 주세요."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(18.dp))

                // 초대 코드 공유: 안드로이드 공유 창(Gmail·메시지·카카오톡 등)
                Button(
                    onClick = {
                        if (code != null) {
                            shareInvite(
                                context,
                                subject = "LOG US 여정 초대: $journeyName",
                                text = inviteShareText(inviterName, journeyName, code, endDate),
                            )
                        }
                    },
                    enabled = code != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("초대 코드 공유", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * 공유할 글. 링크 없이 초대한 사람·여정 이름·초대 코드·참여 방법만 담는다.
 * 받은 사람은 LOG US 앱 홈의 "초대 코드로 참여"에 코드를 넣으면 된다.
 */
internal fun inviteShareText(inviterName: String, journeyName: String, code: String, endDate: String): String =
    "$inviterName 님이 LOG US '$journeyName' 여정에 초대했어요!\n\n" +
        "초대 코드: $code\n\n" +
        "LOG US 앱 홈에서 '초대 코드로 참여'를 누르고 코드 6자리를 넣어 주세요.\n" +
        "여정이 끝나는 ${monthDay(endDate)}까지 쓸 수 있어요."

/**
 * 안드로이드 공유 창을 연다. 사용자가 Gmail 을 고르면 내 구글 계정으로 메일이 가고(제목·본문이 채워진다),
 * 메시지·카카오톡 등을 고르면 본문이 그대로 들어간다. 공유 창은 안드로이드 기본 기능이라 라이브러리가 필요 없다.
 */
private fun shareInvite(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "초대 코드 보내기"))
}

/** "2026-09-26" → "9월 26일" (형식이 다르면 그대로) */
private fun monthDay(date: String): String {
    val parts = date.split("-").mapNotNull { it.toIntOrNull() }
    return if (parts.size == 3) "${parts[1]}월 ${parts[2]}일" else date
}

@Preview(showBackground = true)
@Composable
private fun JourneyInviteDialogPreview() {
    LogUsTheme {
        JourneyInviteDialog(
            journeyName = "우리의 포르투",
            memberCount = 3,
            inviteCode = "PT7924",
            endDate = "2026-09-26",
            inviterName = "성연",
            onClose = {},
        )
    }
}
