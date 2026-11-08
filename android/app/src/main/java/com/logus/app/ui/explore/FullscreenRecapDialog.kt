package com.logus.app.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logus.app.explore.PublicRecap
import com.logus.app.journey.Member
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.TextSecondaryDark

// 탐색 영상 큰 화면(2026-11-08 요청: 탐색에서 영상을 누르면 크게 보기)
/**
 * 어두운 전체 화면에 공개 DAY RECAP 을 크게 재생한다(탐색 피드·검색·내 영상·모아보기 공통).
 * 아래 재생 막대: [❚❚/▶](일시정지·재생) · 진행 막대 · "장면 1 / N". 그 위 오른쪽에 동그란 소리 버튼(음소거, 목록과 따로).
 * 위: 여정 이름 · "11.4 · 포르투" · X(닫기). 가운데: 같은 3분할 재생기(화면을 꽉 채움, 누르면 멈춤·다시 재생).
 * 닫기: X · 폰의 뒤로 가기. 작은 프로필을 누르면 프로필 팝업(그 위에 뜬다).
 */
@Composable
fun FullscreenRecapDialog(
    recap: PublicRecap,
    publicUrl: (String) -> String,
    onProfile: (Member) -> Unit,
    onDismiss: () -> Unit,
) {
    // 큰 화면의 음소거는 목록과 따로(열 때마다 소리 켜진 채로 시작, 2026-11-08 요청)
    var muted by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BackgroundDark),
        ) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            recap.journeyName,
                            color = PhotoFrame,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            listOf(shortDate(recap.date), recap.city).filter { it.isNotEmpty() }.joinToString(" · "),
                            color = TextSecondaryDark,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = PhotoFrame,
                        contentColor = BackgroundDark,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = "큰 화면 닫기", modifier = Modifier.size(22.dp))
                        }
                    }
                }
                PublicRecapPlayer(
                    recap = recap,
                    active = true,
                    publicUrl = publicUrl,
                    onProfileClick = onProfile,
                    cornerRadius = 0.dp,
                    muted = muted,
                    onToggleMute = { muted = !muted },
                    showControls = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
