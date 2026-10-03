package com.logus.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * 동그란 프로필 사진. 사진이 없거나 불러오지 못하면 기본 프로필 그림을 그린다.
 * photo: 사진 주소(String, 예: 구글·Storage 주소) 또는 폰 앨범 사진(Uri). null 이면 기본 그림.
 * (웹 components/home/Avatar.tsx 와 같은 그림·색)
 */
@Composable
fun Avatar(photo: Any?, size: Dp, modifier: Modifier = Modifier) {
    var failed by remember(photo) { mutableStateOf(false) }

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(4.dp, MaterialTheme.colorScheme.background, CircleShape),
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
            DefaultAvatar(Modifier.fillMaxSize())
        }
    }
}

/** 기본 프로필 그림: 팔레트의 앰버 배경(얼굴)·본문색(머리)·프라이머리(몸) */
@Composable
private fun DefaultAvatar(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        // 몸
        drawArc(
            color = Color(0xFF5B3DD6),
            startAngle = 180f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(w * 0.16f, w * 0.69f), size = Size(w * 0.68f, w * 0.62f),
        )
        // 머리카락
        drawCircle(Color(0xFF1A1725), radius = w * 0.2f, center = Offset(w * 0.5f, w * 0.37f))
        // 얼굴
        drawCircle(Color(0xFFFDF0DC), radius = w * 0.18f, center = Offset(w * 0.5f, w * 0.41f))
    }
}
