package com.logus.app.ui.signup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.R
import com.logus.app.auth.AgreementItem
import com.logus.app.auth.Agreements
import com.logus.app.legal.LegalDoc
import com.logus.app.ui.components.StepTopBar
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme

/**
 * 회원가입 1 / 2 단계: 약관 동의
 * 필수 3개(만 14세 이상·서비스 이용약관·위치기반서비스 이용약관)를 모두 체크해야 아래 버튼이 켜진다.
 * 동의 기록(동의 시각·약관 버전)은 가입 완료 때 users/{uid}/agreements/{약관 버전}에 저장된다.
 */
@Composable
fun TermsScreen(
    agreements: Agreements,
    onBack: () -> Unit,
    onToggleAll: () -> Unit,
    onToggle: (AgreementItem) -> Unit,
    onOpenDoc: (LegalDoc) -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        StepTopBar(onBack = onBack, step = "1 / 2 단계")

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Text(
                "시작하기 전에\n확인할 내용이 있어요",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 36.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "LOG EARTH는 사진에 붙은 장소를 지도에 기록해요. 그래서 위치정보 약관 동의가 필요합니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(24.dp))

            // 약관 전체 동의
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, LogUsColors.border),
            ) {
                AgreementRow(
                    checked = agreements.allChecked,
                    onToggle = onToggleAll,
                    tag = null,
                    label = "약관 전체에 동의합니다",
                    bold = true,
                )
            }
            Spacer(Modifier.height(8.dp))

            AgreementRow(agreements.ageOver14, { onToggle(AgreementItem.AGE_OVER_14) }, "(필수)", "만 14세 이상입니다")
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            AgreementRow(agreements.terms, { onToggle(AgreementItem.TERMS) }, "(필수)", LegalDoc.SERVICE.title) {
                onOpenDoc(LegalDoc.SERVICE)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            AgreementRow(agreements.location, { onToggle(AgreementItem.LOCATION) }, "(필수)", LegalDoc.LOCATION.title) {
                onOpenDoc(LegalDoc.LOCATION)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            AgreementRow(
                agreements.notifyNewLogs,
                { onToggle(AgreementItem.NOTIFY_NEW_LOGS) },
                "(선택)",
                "새 기록 알림 받기",
                required = false,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            // 개인정보 처리방침(동의 체크 없이 확인만)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { onOpenDoc(LegalDoc.PRIVACY) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_document),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "${LegalDoc.PRIVACY.title} 확인",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                "동의한 시각과 약관 버전을 함께 저장해 두어, 나중에 어떤 내용에 동의했는지 확인할 수 있어요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onContinue,
                enabled = agreements.requiredDone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // 꺼진 버튼: 카드 배경 + 보조 글자(대비 6.0)
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Text(
                    if (agreements.requiredDone) "동의하고 계속하기" else "필수 항목에 동의해 주세요",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 체크 한 줄: 줄 전체를 누르면 체크가 바뀌고, 오른쪽 › 를 누르면 약관 전문을 연다 */
@Composable
private fun AgreementRow(
    checked: Boolean,
    onToggle: () -> Unit,
    tag: String?,
    label: String,
    required: Boolean = true,
    bold: Boolean = false,
    onOpenDetail: (() -> Unit)? = null,
) {
    // (필수)는 강조 보라, (선택)은 보조 글자색
    val tagColor = if (required) LogUsColors.primaryStrong else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null, // 줄 전체를 눌러 바꾼다
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Text(
            buildAnnotatedString {
                if (tag != null) {
                    withStyle(SpanStyle(color = tagColor)) { append(tag) }
                    append(" ")
                }
                append(label)
            },
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (onOpenDetail != null) {
            IconButton(onClick = onOpenDetail) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "$label 보기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TermsScreenPreview() {
    LogUsTheme {
        TermsScreen(Agreements(ageOver14 = true), {}, {}, {}, {}, {})
    }
}
