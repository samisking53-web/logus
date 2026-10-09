package com.logus.app.ui.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.auth.AuthUiState
import com.logus.app.auth.PhotoChoice
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.components.DefaultProfileGlobe
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.components.LogoMark
import com.logus.app.ui.components.StepTopBar
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme

/**
 * P01 회원가입(프로필 설정) — 2 / 2 단계 (스토리보드 v4 3쪽)
 * 사진 원(+)을 누르면 P02 프로필 사진 화면이 열린다. 사진은 선택이라 기본 프로필로도 가입할 수 있다.
 * 이메일·비밀번호는 구글 계정이 대신하므로 받지 않는다.
 */
@Composable
fun ProfileSetupScreen(
    state: AuthUiState.Signup,
    onBack: () -> Unit,
    onPhotoClick: () -> Unit,
    onNicknameChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        StepTopBar(onBack = onBack, step = "2 / 2 단계")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LogoMark()
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

            // 프로필 사진(누르면 P02). 고른 사진이 없으면 기본 프로필(지구본 로고) + 오른쪽 아래 작은 + 표시.
            // 사진을 고르지 않고 가입하면 이 지구본이 프로필이 된다.
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 60.dp), // 동그라미 모양으로 눌림 표시
                        role = Role.Button,
                        onClickLabel = "프로필 사진 고르기",
                        onClick = onPhotoClick,
                    ),
            ) {
                when (val photo = state.photo) {
                    PhotoChoice.Default -> {
                        DefaultProfileGlobe(
                            Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, LogUsColors.border, CircleShape),
                        )
                        // 사진을 바꿀 수 있다는 표시: 보라 원 + 흰 +(홈 프로필 상자의 카메라 표시와 같은 모양)
                        Box(
                            Modifier
                                .size(34.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 2.dp, y = (-2).dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    is PhotoChoice.Google -> Avatar(photo = photo.url, size = 112.dp)
                    is PhotoChoice.Album -> Avatar(photo = photo.uri, size = 112.dp)
                }
            }
            Text(
                "프로필 사진 설정(선택)",
                color = LogUsColors.primaryStrong,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(role = Role.Button, onClick = onPhotoClick).padding(4.dp),
            )

            OutlinedTextField(
                value = state.nickname,
                onValueChange = onNicknameChange,
                label = { Text("닉네임") },
                supportingText = { Text("1~20자. 함께 여행하는 사람들에게 보여요.") },
                singleLine = true,
                isError = state.error != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
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
        }

        Button(
            onClick = onSubmit,
            enabled = !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (state.saving) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Text("  가입하는 중…", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else {
                Text("가입 완료", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileSetupScreenPreview() {
    LogUsTheme {
        ProfileSetupScreen(
            state = AuthUiState.Signup(
                email = "seongyeon@gmail.com",
                googlePhotoUrl = null,
                nickname = "김성연",
                photo = PhotoChoice.Default,
            ),
            onBack = {},
            onPhotoClick = {},
            onNicknameChange = {},
            onSubmit = {},
        )
    }
}

