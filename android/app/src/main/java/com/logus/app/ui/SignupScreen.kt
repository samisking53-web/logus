package com.logus.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.auth.AuthUiState
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme

/**
 * P01 회원가입 (스토리보드 v4 3쪽을 구글 가입에 맞게 바꿈)
 * 이메일·비밀번호는 구글 계정이 대신하므로 받지 않고, 닉네임과 프로필 사진만 정한다.
 * 사진을 직접 올리는 기능(P02)은 '사진 수정' 화면과 함께 다음 작업에서 만든다.
 */
@Composable
fun SignupScreen(
    state: AuthUiState.NeedsProfile,
    onSubmit: (nickname: String, useGooglePhoto: Boolean) -> Unit,
    onUseOtherAccount: () -> Unit,
) {
    var nickname by rememberSaveable { mutableStateOf(state.suggestedNickname) }
    var useDefaultPhoto by rememberSaveable { mutableStateOf(false) }
    val photoUrl = if (useDefaultPhoto) null else state.googlePhotoUrl

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("LOG EARTH", color = LogUsColors.primaryStrong, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "회원가입",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }

        Avatar(photoUrl = photoUrl, size = 112.dp)
        Text("프로필 사진 설정(선택)", color = LogUsColors.primaryStrong, fontWeight = FontWeight.SemiBold)
        if (state.googlePhotoUrl != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(role = Role.Checkbox) { useDefaultPhoto = !useDefaultPhoto },
            ) {
                Checkbox(
                    checked = useDefaultPhoto,
                    onCheckedChange = null, // 줄 전체를 눌러 바꾼다
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(8.dp))
                Text("기본 프로필 사용", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "사진은 가입 후 홈의 '사진 수정'에서 바꿀 수 있어요.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )

        OutlinedTextField(
            value = nickname,
            onValueChange = { if (it.length <= 40) nickname = it },
            label = { Text("닉네임") },
            supportingText = { Text("1~20자. 함께 여행하는 사람들에게 보여요.") },
            singleLine = true,
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit(nickname, !useDefaultPhoto) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = LogUsColors.border,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        state.email?.let {
            Text(
                "구글 계정 $it(으)로 가입해요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.error?.let { ErrorMessage(it) }

        Button(
            onClick = { onSubmit(nickname, !useDefaultPhoto) },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(if (state.saving) "가입하는 중…" else "가입 완료", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onUseOtherAccount) {
            Text("다른 구글 계정으로 하기", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SignupScreenPreview() {
    LogUsTheme {
        SignupScreen(
            state = AuthUiState.NeedsProfile(email = "seongyeon@gmail.com", suggestedNickname = "김성연", googlePhotoUrl = null),
            onSubmit = { _, _ -> },
            onUseOtherAccount = {},
        )
    }
}
