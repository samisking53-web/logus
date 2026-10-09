package com.logus.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.R
import com.logus.app.ui.theme.LogUsColors

/**
 * LOG EARTH 로고: 지구 그림(ic_globe, 팀이 정한 로고) + "LOG EARTH" 글자, 둘 다 강조 보라(primary-strong).
 * 앱 시작 화면·첫 화면·P01·홈 위쪽(LogoHeader)이 모두 이 로고를 크기만 바꿔 쓴다.
 * 넓은 칸(fillMaxWidth)에 놓으면 가운데에 선다.
 */
@Composable
fun LogoMark(
    modifier: Modifier = Modifier,
    iconSize: Dp = 22.dp,
    fontSize: TextUnit = 18.sp,
    letterSpacing: TextUnit = 2.sp,
    gap: Dp = 8.dp,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_globe),
            contentDescription = null, // 옆 글자 "LOG EARTH"를 읽어 주므로 그림 설명은 넣지 않는다
            tint = LogUsColors.primaryStrong,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(gap))
        Text(
            "LOG EARTH",
            color = LogUsColors.primaryStrong,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = letterSpacing,
        )
    }
}
