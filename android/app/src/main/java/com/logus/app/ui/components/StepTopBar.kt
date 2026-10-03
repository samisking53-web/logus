package com.logus.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 회원가입 화면 맨 위: ‹ 뒤로 가기 + "1 / 2 단계" 또는 화면 제목 */
@Composable
fun StepTopBar(onBack: () -> Unit, step: String? = null, title: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로 가기",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (step != null) {
            Text(step, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        }
        if (title != null) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
