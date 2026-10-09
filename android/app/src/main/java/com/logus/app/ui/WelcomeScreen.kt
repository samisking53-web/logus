package com.logus.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.R
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.components.LogoMark
import com.logus.app.ui.theme.GoogleButton
import com.logus.app.ui.theme.GoogleLabel
import com.logus.app.ui.theme.GoogleOutline
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.PhotoFrame

/**
 * 첫 화면 (앱을 처음 연 사람 / 로그아웃한 사람)
 * LOG EARTH 로고 → 앱 소개 사진(겹친 카드 3장) → "Google 계정으로 계속하기".
 * 처음 온 사람은 로그인 뒤 약관 동의 → 프로필 설정으로, 이미 가입한 사람은 바로 홈으로 간다.
 */
@Composable
fun WelcomeScreen(
    busy: Boolean,
    error: String?,
    onGoogleClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp), // 아래 여백을 줄여 버튼을 화면 아래쪽에 둔다
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 로고
        LogoMark()
        Spacer(Modifier.height(16.dp))

        // 앱 소개 사진: 흰 테두리 카드 3장을 살짝 겹쳐 부채꼴로 놓는다(글자 없음)
        IntroPhotoStack(Modifier.fillMaxWidth().weight(1f))
        Spacer(Modifier.height(24.dp))

        // 구글 로그인 버튼: 구글 브랜드 가이드(흰 배경, 회색 테두리, 4색 G 로고) — 팔레트 예외
        OutlinedButton(
            onClick = onGoogleClick,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoogleOutline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = GoogleButton,
                contentColor = GoogleLabel,
                disabledContainerColor = GoogleButton,
                disabledContentColor = GoogleLabel,
            ),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), color = GoogleLabel, strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("로그인하는 중…", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Image(painterResource(R.drawable.ic_google_logo), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Google 계정으로 계속하기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            ErrorMessage(error)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "계속하면 이용약관과 개인정보 처리방침에\n동의하는 절차로 넘어가요",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
    }
}

/**
 * 앱 소개 사진 3장. 가운데 사진이 맨 앞에 크게, 양옆 사진은 조금 작게 기울여 뒤에 겹친다.
 * 사진은 res/drawable-nodpi/intro_*.webp. 사진을 바꾸려면 같은 이름으로 파일만 바꾸면 된다.
 * 화면 높이가 낮은 폰에서도 잘리지 않게, 남은 공간 크기에 맞춰 카드 크기를 정한다.
 */
@Composable
private fun IntroPhotoStack(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        // 카드 비율 3:4(세로). 가운데 카드 너비는 화면 너비의 50%, 단 높이가 남은 공간의 88%를 넘지 않게
        val centerWidth = minOf(maxWidth * 0.5f, maxHeight * 0.88f * 3f / 4f)
        val sideWidth = centerWidth * 0.82f
        val sideShift = centerWidth * 0.52f // 양옆 카드가 가운데에서 떨어진 거리(겹치는 정도). 화면 밖으로 나가지 않는 값

        // 뒤에 있는 양옆 카드를 먼저 그리고, 가운데 카드를 마지막에 그려 맨 앞에 오게 한다
        PhotoCard(
            R.drawable.intro_oreum_trail,
            Modifier.width(sideWidth).offset(x = -sideShift, y = centerWidth * 0.06f).rotate(-8f),
        )
        PhotoCard(
            R.drawable.intro_tangerine_cafe,
            Modifier.width(sideWidth).offset(x = sideShift, y = centerWidth * 0.06f).rotate(8f),
        )
        PhotoCard(R.drawable.intro_sea_cafe, Modifier.width(centerWidth))
    }
}

/** 흰 테두리와 그림자가 있는 사진 카드 한 장 */
@Composable
private fun PhotoCard(@DrawableRes photo: Int, modifier: Modifier = Modifier) {
    val outer = RoundedCornerShape(20.dp)
    Box(
        modifier
            .aspectRatio(3f / 4f)
            .shadow(10.dp, outer)
            .background(PhotoFrame, outer)
            .padding(6.dp),
    ) {
        Image(
            painterResource(photo),
            contentDescription = null, // 꾸밈용 사진이라 화면 읽기에서는 건너뛴다
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp)),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    LogUsTheme { WelcomeScreen(busy = false, error = null, onGoogleClick = {}) }
}
