package com.logus.app.ui.signup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.auth.PhotoChoice
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.components.StepTopBar
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme

/**
 * P02 프로필 사진 (스토리보드 v4 3쪽)
 * "앨범에서 선택"으로 사진을 고르면 원형으로 미리 보여 주고, "프로필 저장"을 누르면 P01 로 돌아간다.
 * 앨범은 안드로이드 기본 사진 선택 창(Photo Picker)을 써서 저장공간 권한을 묻지 않는다.
 * 사진은 가입 완료를 누를 때 줄여서(최대 1024px JPEG) Storage 에 올린다.
 */
@Composable
fun ProfilePhotoScreen(
    draft: PhotoChoice,
    hasGooglePhoto: Boolean,
    onBack: () -> Unit,
    onPickAlbum: (Uri) -> Unit,
    onUseGooglePhoto: () -> Unit,
    onUseDefault: () -> Unit,
    onConfirm: () -> Unit,
) {
    val albumPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickAlbum(uri) // 창을 그냥 닫으면 null
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        StepTopBar(onBack = onBack, title = "프로필 사진")

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Avatar(
                photo = when (draft) {
                    is PhotoChoice.Google -> draft.url
                    is PhotoChoice.Album -> draft.uri
                    PhotoChoice.Default -> null // 기본 프로필 그림
                },
                size = 200.dp,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    albumPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, LogUsColors.border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) { Text("앨범에서 선택", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }

            Text("사진은 원형으로 표시돼요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)

            if (hasGooglePhoto) {
                TextButton(onClick = onUseGooglePhoto) {
                    Text("구글 프로필 사진 사용", color = LogUsColors.primaryStrong)
                }
            }
            TextButton(onClick = onUseDefault) {
                Text("기본 프로필 사용", color = LogUsColors.primaryStrong)
            }
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text("프로필 저장", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfilePhotoScreenPreview() {
    LogUsTheme {
        ProfilePhotoScreen(PhotoChoice.Default, hasGooglePhoto = true, {}, {}, {}, {}, {})
    }
}
