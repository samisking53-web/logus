package com.logus.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.R
import com.logus.app.auth.Profile
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.theme.Amber
import com.logus.app.ui.theme.AmberText
import com.logus.app.ui.theme.LogUsColors
import java.text.NumberFormat
import java.util.Locale

/**
 * 홈 위쪽 프로필 상자(S01·S01-A 공통): 동그란 프로필 + 카메라 표시, 닉네임, "사진 수정 ›", 오른쪽 위 보유 코인.
 * compact = true 면 S01-A 처럼 조금 작게 그린다(아래 여정 카드 자리를 넓히려고).
 * onEditPhoto: 프로필 사진 수정 화면은 나중에 만든다. 지금은 눌러도 아무 일도 없다.
 */
@Composable
fun ProfileCard(
    profile: Profile,
    compact: Boolean,
    onEditPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarSize = if (compact) 72.dp else 96.dp
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LogUsColors.profileBox)
            .border(1.dp, LogUsColors.border, RoundedCornerShape(24.dp)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = if (compact) 20.dp else 38.dp, bottom = if (compact) 18.dp else 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfilePhoto(profile.photoUrl, avatarSize)
            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
            Text(
                profile.nickname,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = "프로필 사진 수정", onClick = onEditPhoto)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("사진 수정", color = LogUsColors.primaryStrong, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = LogUsColors.primaryStrong,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        CoinChip(
            coins = profile.coins,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
        )
    }
}

/** 흰 동그라미 안 프로필 사진(없으면 사람 모양) + 오른쪽 아래 카메라 표시 */
@Composable
private fun ProfilePhoto(photoUrl: String?, size: Dp) {
    Box(Modifier.size(size)) {
        if (photoUrl != null) {
            Avatar(photo = photoUrl, size = size)
        } else {
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(LogUsColors.card),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    tint = LogUsColors.primaryStrong,
                    modifier = Modifier.size(size * 0.42f),
                )
            }
        }
        // 카메라 표시(사진을 바꿀 수 있다는 뜻). 보라 원 + 흰 테두리
        val badge = size * 0.32f
        Box(
            Modifier
                .size(badge)
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = (-2).dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, LogUsColors.card, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_camera),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(badge * 0.5f),
            )
        }
    }
}

/** 보유 코인 상자: "보유 코인" + 금색 동전 + 숫자 */
@Composable
private fun CoinChip(coins: Long, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LogUsColors.coinChip)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text("보유 코인", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            CoinIcon(Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                NumberFormat.getNumberInstance(Locale.KOREA).format(coins),
                color = LogUsColors.amberText,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 금색 동전 그림: 앰버 원 + 안쪽 테두리(앰버 글자색) */
@Composable
fun CoinIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Amber, radius = r, center = c)
        drawCircle(AmberText, radius = r * 0.62f, center = c, style = Stroke(width = r * 0.18f))
        drawLine(AmberText, Offset(c.x, c.y - r * 0.3f), Offset(c.x, c.y + r * 0.3f), strokeWidth = r * 0.18f)
    }
}
