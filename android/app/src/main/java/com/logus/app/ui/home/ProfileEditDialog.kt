package com.logus.app.ui.home

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.logus.app.R
import com.logus.app.auth.NICKNAME_MAX
import com.logus.app.profile.PhotoSource
import com.logus.app.profile.ProfileEditUiState
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import java.io.File

// P03 프로필 사진 수정 팝업(홈 S01·S01-A 프로필 상자의 "사진 수정 ›")
/**
 * 가입할 때(P01·P02) 정한 사진과 닉네임을 홈에서 바꾸는 팝업. 뒤의 홈(S01 또는 S01-A)은 어둡게 보인다.
 * - 위에서부터: "프로필 사진 수정"·X → "가입할 때 정한 사진과 이름을 바꿀 수 있어요" → 동그란 사진(+ 카메라 표시)
 *   → [사진 찍기] [앨범에서] [기본 이미지](고른 것은 보라 채움) → 닉네임 입력칸·"그룹 멤버에게만 보여요"·"2 / 20" → [취소] [저장]
 * - 사진 찍기: 폰의 카메라 앱으로 한 장 찍는다(처음엔 카메라 권한을 묻는다). 앨범에서: 안드로이드 사진 선택 창.
 *   기본 이미지: 지구본 로고. 사진은 "저장"을 눌러야 올라간다.
 * - 닫기: X·취소·폰의 뒤로 가기·팝업 바깥(저장하는 동안은 닫히지 않는다)
 */
@Composable
fun ProfileEditDialog(
    state: ProfileEditUiState,
    onNicknameChange: (String) -> Unit,
    onTookPhoto: (Uri) -> Unit,
    onPickAlbum: (Uri) -> Unit,
    onUseDefault: () -> Unit,
    onError: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    // 카메라 앱이 찍은 사진을 넣을 자리(앱 전용 임시 폴더). 화면을 돌려도 잃지 않게 문자열로 기억한다
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri?.let(Uri::parse)
        if (ok && uri != null) onTookPhoto(uri)
    }
    fun launchCamera() {
        val uri = newCameraUri(context)
        pendingCameraUri = uri.toString()
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            onError("이 폰에서 카메라 앱을 찾지 못했어요. '앨범에서'로 골라 주세요.")
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else onError("카메라 권한을 허용해야 사진을 찍을 수 있어요.")
    }
    val albumPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPickAlbum(uri) // 창을 그냥 닫으면 null
    }

    val close = { if (!state.saving) onClose() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()) // 키보드가 올라와 좁아지면 스크롤
                    .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 22.dp),
            ) {
                Text(
                    "프로필 사진 수정",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(end = 40.dp)
                        .semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "가입할 때 정한 사진과 이름을 바꿀 수 있어요",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(20.dp))

                // 동그란 사진 + 오른쪽 아래 카메라 표시
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    Avatar(photo = state.previewPhoto, size = 96.dp)
                    Box(
                        Modifier
                            .size(30.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(2.dp, LogUsColors.card, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_camera),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))

                // 사진 찍기 · 앨범에서 · 기본 이미지
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceButton("사진 찍기", state.selectedSource == PhotoSource.CAMERA, !state.saving, Modifier.weight(1f)) {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                    SourceButton("앨범에서", state.selectedSource == PhotoSource.ALBUM, !state.saving, Modifier.weight(1f)) {
                        albumPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    SourceButton("기본 이미지", state.selectedSource == PhotoSource.DEFAULT, !state.saving, Modifier.weight(1f), onUseDefault)
                }
                Spacer(Modifier.height(22.dp))

                // 닉네임
                Text("닉네임", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.nickname,
                    onValueChange = onNicknameChange,
                    singleLine = true,
                    enabled = !state.saving,
                    isError = state.error != null,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = LogUsColors.border,
                        focusedContainerColor = LogUsColors.card,
                        unfocusedContainerColor = LogUsColors.card,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "그룹 멤버에게만 보여요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    val length = state.nickname.trim().let { it.codePointCount(0, it.length) }
                    Text(
                        "$length / $NICKNAME_MAX",
                        color = LogUsColors.primaryStrong,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    // 오류 글자는 본문색(다크 모드 대비 규칙). 빨강은 입력칸 테두리에만
                    Text(state.error, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                }
                Spacer(Modifier.height(22.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = close,
                        enabled = !state.saving,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, LogUsColors.line),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = LogUsColors.card,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { Text("취소", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    Button(
                        onClick = onSave,
                        enabled = !state.saving,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.primary,
                            disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text("저장", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            IconButton(
                onClick = close,
                enabled = !state.saving,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 사진 고르기 버튼(알약 모양). 고른 버튼은 보라 채움 + 흰 글자, 나머지는 흰 바탕 + 테두리 */
@Composable
private fun SourceButton(label: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val semanticsModifier = modifier
        .height(44.dp)
        .semantics {
            this.selected = selected
            role = Role.Button
        }
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = semanticsModifier,
        ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            contentPadding = PaddingValues(horizontal = 4.dp),
            border = BorderStroke(1.dp, LogUsColors.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = LogUsColors.card,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = semanticsModifier,
        ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
    }
}

/**
 * 카메라 앱이 찍은 사진을 저장할 주소. 앱 전용 임시 폴더(cacheDir/profile_camera)에 매번 새 이름으로 만들고,
 * 예전에 찍은 파일은 지운다. FileProvider 로 카메라 앱에 이 파일에 쓸 권한만 잠깐 준다(AndroidManifest·res/xml/file_paths.xml).
 */
private fun newCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "profile_camera").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, "profile_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Preview(showBackground = true)
@Composable
private fun ProfileEditDialogPreview() {
    LogUsTheme {
        ProfileEditDialog(
            state = ProfileEditUiState(open = true, nickname = "성연"),
            onNicknameChange = {},
            onTookPhoto = {},
            onPickAlbum = {},
            onUseDefault = {},
            onError = {},
            onSave = {},
            onClose = {},
        )
    }
}
