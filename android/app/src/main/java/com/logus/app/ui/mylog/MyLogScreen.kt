package com.logus.app.ui.mylog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.ui.components.ComingSoon
import com.logus.app.ui.theme.LogUsColors

// S10 마이로그(여정 목록) — 다음 작업에서 만든다
/**
 * 하단 탭 "마이로그". 지금은 준비 중 안내와 로그아웃 버튼만 있다
 * (로그아웃은 원래 임시 홈에 있던 것을 옮겨 왔다. 설정 화면을 만들면 그쪽으로 옮긴다).
 */
@Composable
fun MyLogScreen(onSignOut: () -> Unit) {
    ComingSoon(title = "마이로그", message = "내가 함께한 여정 목록이\n곧 여기에 모여요") {
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            border = BorderStroke(1.dp, LogUsColors.border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = LogUsColors.primaryStrong),
        ) { Text("로그아웃", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}
