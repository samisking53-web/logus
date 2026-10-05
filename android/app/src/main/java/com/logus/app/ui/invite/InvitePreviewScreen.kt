package com.logus.app.ui.invite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.R
import com.logus.app.journey.InvitePreview
import com.logus.app.ui.home.ActionButton
import com.logus.app.ui.home.CoverPlaceholder
import com.logus.app.ui.home.LogoHeader
import com.logus.app.ui.home.journeyPeriod
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme

// I01 초대 확인
/**
 * 초대 코드를 확인한 뒤 보이는 화면. 여정 진행 중 홈(S01-A)을 바탕으로 만들었다.
 * - 프로필 상자 대신 "○○ 님이 초대했습니다" 상자(연보라: 바탕보다 진하고 아래 보라 버튼보다 옅다)
 * - 가운데 여정 카드를 크게: 대표 사진 자리, 여정 이름, 기간·현재 인원, 촬영 알림 간격(여정을 만든 사람의 설정)
 * - "초대 수락하기"를 누르면 참여(joinJourney) 후 다음 화면으로 갈 예정이다. 그 화면은 다음 작업에서 만들고,
 *   지금은 onAccept 가 아무 일도 하지 않는다.
 * 폰의 뒤로 가기를 누르면 홈으로 돌아간다(MainScreen).
 */
@Composable
fun InvitePreviewScreen(
    preview: InvitePreview,
    onAccept: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        LogoHeader()
        Spacer(Modifier.height(8.dp))

        // ○○ 님이 초대했습니다
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(LogUsColors.profileBox)
                .border(1.dp, LogUsColors.border, RoundedCornerShape(20.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_person_add),
                contentDescription = null,
                tint = LogUsColors.primaryStrong,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${preview.inviterName} 님이 초대했습니다",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.height(16.dp))

        // 여정 카드(크게)
        val shape = RoundedCornerShape(20.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(LogUsColors.card)
                .border(1.dp, LogUsColors.line, shape),
        ) {
            // 대표 사진 자리(대표 사진 기능을 만들면 여기에 사진을 보여 준다)
            CoverPlaceholder(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(MaterialTheme.colorScheme.surface),
            )
            Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                Text(
                    preview.journeyName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${journeyPeriod(preview.startDate, preview.endDate)}  ·  현재 ${preview.memberCount}명",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(12.dp))
                // 촬영 알림 칩
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = LogUsColors.primaryStrong,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        preview.notifyIntervalHours?.let { "촬영 알림 · ${it}시간마다" } ?: "촬영 알림 · 받지 않음",
                        color = LogUsColors.primaryStrong,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))

        ActionButton(
            icon = painterResource(R.drawable.ic_login),
            title = "초대 수락하기",
            subtitle = null,
            filled = true,
            onClick = onAccept,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Preview(showBackground = true, heightDp = 820)
@Composable
private fun InvitePreviewScreenPreview() {
    LogUsTheme {
        InvitePreviewScreen(
            InvitePreview(
                code = "K7PQ2M",
                journeyName = "우리의 포르투",
                city = "포르투",
                startDate = "2026-09-24",
                endDate = "2026-09-26",
                memberCount = 2,
                inviterName = "성연",
                notifyIntervalHours = 2,
                alreadyMember = false,
            ),
        )
    }
}
