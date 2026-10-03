package com.logus.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 오류 문구. 오류 빨강은 다크 바탕에서 글자 대비가 부족해(3.4) 테두리에만 쓰고 글자는 본문색으로 쓴다.
 */
@Composable
fun ErrorMessage(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(12.dp))
            .padding(12.dp)
            // 화면 읽기 프로그램이 오류 문구를 바로 읽어 준다
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
