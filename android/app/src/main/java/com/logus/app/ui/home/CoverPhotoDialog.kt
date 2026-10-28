package com.logus.app.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.logus.app.R
import com.logus.app.home.CoverPhotoUiState
import com.logus.app.home.GalleryAlbum
import com.logus.app.home.GalleryPhoto
import com.logus.app.home.GalleryRepository
import com.logus.app.home.PhotoAccess
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import java.time.Instant
import java.time.ZoneId

// 대표 사진 선택 팝업(S01-A 여정 카드 사진 오른쪽 위 전환 버튼, 2026-10-28 팀 그림)
/**
 * 내 홈의 여정 카드 사진을 폰 사진첩에서 골라 바꾸는 팝업. 뒤의 홈(S01-A)은 어둡게 보인다.
 * - 위에서부터: "대표 사진 선택"·X → "휴대폰 갤러리에서 사진을 골라요" → 앨범 고르기("최근 항목" · 사진 N장 ▾)
 *   → 사진 3칸 격자(고른 사진은 보라 테두리 + 체크) → 고른 사진 이름·날짜("IMG_2381 · 9월 24일") → [취소] [✓ 이 사진으로 변경]
 * - 처음 열 때 사진 접근 권한을 묻는다. 안드로이드 14 이상에서 "사진 선택"(일부만 허용)을 고르면 고른 사진만 보이고 "사진 더 고르기"가 생긴다.
 * - "이 사진으로 변경"을 눌러야 올라간다. 내 홈에서만 바뀐다(다른 구성원의 홈은 그대로). 저장하는 동안은 닫히지 않는다.
 */
@Composable
fun CoverPhotoDialog(
    state: CoverPhotoUiState,
    onPermissionResult: () -> Unit,
    onSelectAlbum: (String?) -> Unit,
    onSelect: (GalleryPhoto) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onPermissionResult()
    }
    // 처음 열었는데 권한이 없으면 바로 묻는다
    LaunchedEffect(state.journeyId) {
        if (state.access == PhotoAccess.NONE && !state.asked) {
            permissionLauncher.launch(GalleryRepository.permissionsToRequest())
        }
    }
    val askPermission = { permissionLauncher.launch(GalleryRepository.permissionsToRequest()) }
    val openSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    val close = { if (!state.saving) onClose() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CoverPhotoContent(
            state = state,
            onAskPermission = askPermission,
            onOpenSettings = openSettings,
            onSelectAlbum = onSelectAlbum,
            onSelect = onSelect,
            onSave = onSave,
            onClose = close,
        )
    }
}

/** 팝업 안쪽(미리보기에서도 쓰려고 나눴다) */
@Composable
private fun CoverPhotoContent(
    state: CoverPhotoUiState,
    onAskPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectAlbum: (String?) -> Unit,
    onSelect: (GalleryPhoto) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(LogUsColors.card, RoundedCornerShape(28.dp))
            .padding(18.dp),
    ) {
        // 제목 + X
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 4.dp)) {
                Text(
                    "대표 사진 선택",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(4.dp))
                Text("휴대폰 갤러리에서 사진을 골라요", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            IconButton(
                onClick = onClose,
                enabled = !state.saving,
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))

        when {
            state.access == PhotoAccess.NONE -> PermissionBox(asked = state.asked, onAsk = onAskPermission, onOpenSettings = onOpenSettings)
            else -> {
                AlbumBar(state = state, onSelectAlbum = onSelectAlbum)
                Spacer(Modifier.height(10.dp))
                PhotoGrid(state = state, onSelect = onSelect)
                if (state.access == PhotoAccess.PARTIAL) {
                    // 안드로이드 14 이상 "사진 선택"으로 일부만 허용했을 때
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "허용한 사진만 보여요",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onAskPermission) {
                            Text("사진 더 고르기", color = LogUsColors.primaryStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Spacer(Modifier.height(10.dp))
                }
                // 고른 사진 이름 · 날짜
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_image),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    val selected = state.selected
                    Text(
                        if (selected != null) "${selected.name} · ${dayLabel(selected.takenMillis)}" else "바꿀 사진을 하나 골라 주세요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        state.error?.let {
            Spacer(Modifier.height(10.dp))
            // 오류 글자는 본문색(다크 모드 대비 규칙)
            Text(it, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onClose,
                enabled = !state.saving,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, LogUsColors.border),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = LogUsColors.card,
                    contentColor = LogUsColors.primaryStrong,
                ),
            ) { Text("취소", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = onSave,
                enabled = state.selected != null && !state.saving,
                modifier = Modifier
                    .weight(1.6f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // 저장하는 동안에도 보라 그대로(도는 표시)
                    disabledContainerColor = if (state.saving) MaterialTheme.colorScheme.primary else LogUsColors.border,
                    disabledContentColor = if (state.saving) MaterialTheme.colorScheme.onPrimary else LogUsColors.primaryStrong,
                ),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("이 사진으로 변경", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/** 앨범 고르기 줄: 사진 모양 + 앨범 이름(굵게) … 오른쪽 "사진 N장 ▾". 누르면 앨범 목록 */
@Composable
private fun AlbumBar(state: CoverPhotoUiState, onSelectAlbum: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(enabled = state.albums.size > 1, role = Role.Button, onClickLabel = "앨범 고르기") { expanded = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_image),
                contentDescription = null,
                tint = LogUsColors.primaryStrong,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                state.albumName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (state.loading) "읽는 중…" else "사진 ${state.visiblePhotos.size}장",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.albums.forEach { album: GalleryAlbum ->
                DropdownMenuItem(
                    text = { Text("${album.name} · ${album.count}장", fontSize = 14.sp) },
                    onClick = {
                        expanded = false
                        onSelectAlbum(album.id)
                    },
                )
            }
        }
    }
}

/** 사진 3칸 격자(3줄쯤 보이고 그 안에서 스크롤). 고른 사진은 보라 테두리 + 오른쪽 위 체크 */
@Composable
private fun PhotoGrid(state: CoverPhotoUiState, onSelect: (GalleryPhoto) -> Unit) {
    val photos = state.visiblePhotos
    Box(
        Modifier
            .fillMaxWidth()
            .height(312.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            photos.isEmpty() -> Text(
                "보여 줄 사진이 없어요",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(photos, key = { it.uri.toString() }) { photo ->
                    val isSelected = state.selected?.uri == photo.uri
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(shape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else LogUsColors.border,
                                shape = shape,
                            )
                            .clickable(role = Role.Button, enabled = !state.saving) { onSelect(photo) }
                            .semantics {
                                selected = isSelected
                                contentDescription = "${photo.name}, ${dayLabel(photo.takenMillis)}"
                            },
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (isSelected) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .border(1.5.dp, LogUsColors.card, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 권한이 없을 때: 안내 + "사진 접근 허용"(한 번 거절한 뒤에는 "설정에서 허용하기"도) */
@Composable
private fun PermissionBox(asked: Boolean, onAsk: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(312.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_image),
            contentDescription = null,
            tint = LogUsColors.primaryStrong,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "대표 사진을 고르려면\n사진 접근을 허용해 주세요",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "고른 사진 한 장만 올라가요",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onAsk,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) { Text("사진 접근 허용", fontWeight = FontWeight.Bold) }
        if (asked) {
            TextButton(onClick = onOpenSettings) {
                Text("허용 창이 안 뜨면 설정에서 허용하기", color = LogUsColors.primaryStrong, fontSize = 13.sp)
            }
        }
    }
}

/** 1727145600000 → "9월 24일" */
private fun dayLabel(millis: Long): String {
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${date.monthValue}월 ${date.dayOfMonth}일"
}

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun CoverPhotoContentPreview() {
    LogUsTheme {
        CoverPhotoContent(
            state = CoverPhotoUiState(journeyId = "j1", access = PhotoAccess.NONE, asked = true),
            onAskPermission = {},
            onOpenSettings = {},
            onSelectAlbum = {},
            onSelect = {},
            onSave = {},
            onClose = {},
        )
    }
}
