package com.logus.app.ui.feed

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.logus.app.journey.Member
import com.logus.app.record.FeedLog
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.OnPrimary
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.Primary
import com.logus.app.ui.theme.PrimaryLight

// N01 기록 삭제 팝업 (2026-11-02 팀 그림: 기록 카드를 길게 누르면 뜬다)
/**
 * 내 기록(mine = true): "이 기록을 삭제할까요?"
 *   대표 화면(작은 프로필) · 장소 이름 · "나 · 09:24 기록 · 3초 영상" · X
 *   → 안내 상자 "삭제하면 구성원 모두의 여정에서 사라지고, 추억 지도와 DAY RECAP에도 포함되지 않아요."
 *   → [취소] [🗑 삭제](빨강 채움·흰 글자, 대비 5.4:1). "삭제"를 누르면 지우고 5초 동안 되돌릴 수 있다(JourneyFeedViewModel).
 * 남의 기록(mine = false): "다른 사람의 기록이에요" → "민지 님이 올린 기록이라 삭제하거나 수정할 수 없어요 …" → [확인] 하나.
 *   남의 기록은 어떤 경로로도 지우지 않는다(2026-11-02 팀 결정, 보안 규칙도 작성자만 지울 수 있다).
 * 닫기: X·취소·확인·폰의 뒤로 가기·팝업 바깥 누르기
 */
@Composable
fun DeleteLogDialog(
    log: FeedLog,
    author: Member?,
    mine: Boolean,
    previewUrl: suspend (FeedLog) -> String?,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = author?.nickname ?: "구성원"
    val time = clockLabel(log.capturedAtMillis)
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
                // 대표 화면 · 제목 · 장소 · "나 · 09:24 기록 · 3초 영상"(X 는 오른쪽 위 모서리에 따로 띄워 글자 줄을 넓게 쓴다)
                Row(verticalAlignment = Alignment.Top) {
                    LogThumbnail(log = log, author = author, previewUrl = previewUrl)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (mine) "이 기록을 삭제할까요?" else "다른 사람의 기록이에요",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(end = 36.dp)
                                .semantics { heading() },
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            log.placeName ?: "위치 없이 올린 기록",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${if (mine) "나" else name} · $time 기록 · 3초 영상",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // 안내 상자
                Text(
                    if (mine) {
                        "삭제하면 구성원 모두의 여정에서 사라지고, 추억 지도와 DAY RECAP에도 포함되지 않아요."
                    } else {
                        "$name 님이 올린 기록이라 삭제하거나 수정할 수 없어요. 내가 올린 기록만 삭제할 수 있고, 다른 사람의 기록은 그대로 남아요."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(LogUsColors.iconCircle, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
                Spacer(Modifier.height(18.dp))

                if (mine) {
                    // [취소] [🗑 삭제]
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
                            onClick = onDelete,
                            modifier = Modifier
                                .weight(0.6f)
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            // 지우는 버튼은 빨강 채움 + 흰 글자(대비 5.4:1, 라이트·다크 같음)
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = OnPrimary,
                            ),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("삭제", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Text("확인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

/** 팝업 왼쪽 위 작은 대표 화면(정사각형) + 왼쪽 위 작은 프로필 */
@Composable
private fun LogThumbnail(log: FeedLog, author: Member?, previewUrl: suspend (FeedLog) -> String?) {
    val url by produceState<String?>(initialValue = null, log.id) { value = previewUrl(log) }
    Box(
        Modifier
            .size(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(BackgroundDark, Primary, PrimaryLight))),
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Avatar(
            photo = author?.photoUrl,
            size = 26.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
            ringWidth = 2.dp,
            ringColor = PhotoFrame,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DeleteLogDialogPreview() {
    LogUsTheme {
        DeleteLogDialog(
            log = FeedLog("1", "a", "video", null, 0L, "히베이라 강변"),
            author = Member("a", "성연"),
            mine = true,
            previewUrl = { null },
            onDelete = {},
            onDismiss = {},
        )
    }
}
