package com.logus.app.ui.signup

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.legal.LegalDoc
import com.logus.app.ui.components.StepTopBar

/** 약관 전문 보기 (약관 동의 화면에서 › 를 눌렀을 때) */
@Composable
fun LegalDocScreen(doc: LegalDoc, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        StepTopBar(onBack = onBack, title = doc.title)
        Text(
            doc.body,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}
