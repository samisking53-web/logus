package com.logus.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.logus.app.R
import com.logus.app.ui.theme.LogUsColors

/**
 * 동그란 프로필 사진. 사진이 없거나 불러오지 못하면 기본 프로필(지구본 로고)을 그린다.
 * photo: 사진 주소(String, 예: 구글·Storage 주소) 또는 폰 앨범 사진(Uri). null 이면 기본 프로필.
 * ringWidth·ringColor: 둘레 테두리(기본은 바탕색 4dp. 기록 보기 사진 위 작은 프로필은 흰색 2dp)
 */
@Composable
fun Avatar(
    photo: Any?,
    size: Dp,
    modifier: Modifier = Modifier,
    ringWidth: Dp = 4.dp,
    ringColor: Color = MaterialTheme.colorScheme.background,
) {
    var failed by remember(photo) { mutableStateOf(false) }

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(ringWidth, ringColor, CircleShape),
    ) {
        if (photo != null && !failed) {
            AsyncImage(
                model = photo,
                contentDescription = null, // 장식용(이름이 옆에 글자로 있음)
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            DefaultProfileGlobe(Modifier.fillMaxSize())
        }
    }
}

/**
 * 기본 프로필: 동그라미 가운데 LOG EARTH 지구본 로고(ic_globe, 강조 보라).
 * 사진을 고르지 않고 가입한 사람(photoURL 이 null)은 모두 이 그림을 쓴다(2026-10-09 팀 결정).
 * 바탕색은 감싸는 동그라미가 정한다(Avatar 는 연보라 surface, 홈 프로필 상자는 흰 카드).
 */
@Composable
fun DefaultProfileGlobe(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(
            painterResource(R.drawable.ic_globe),
            contentDescription = null, // 장식용(이름이 옆에 글자로 있음)
            tint = LogUsColors.primaryStrong,
            modifier = Modifier.fillMaxSize(0.52f),
        )
    }
}
