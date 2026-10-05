package com.logus.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.logus.app.R
import com.logus.app.journey.InviteCode
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.PrimaryStrong
import com.logus.app.ui.theme.Success

// S01 "초대 코드로 참여" → 초대 코드 입력 팝업
/**
 * 홈(S01) 위에 뜨는 초대 코드 입력 팝업. 뒤의 홈 화면은 어둡게 보인다.
 * - 6자리를 다 넣기 전: 입력칸 연한 테두리, 아래 "n / 6자리", 회색 버튼 "코드 6자리를 넣어주세요"(눌리지 않음)
 * - 6자리를 다 넣으면: 입력칸 테두리가 보라로 진해지고, "6자리를 모두 넣었어요", 짙은 보라 "여정 확인하기" 버튼
 * - 소문자로 쳐도 대문자로 바뀌고, 코드에 없는 글자(0·O·1·I·공백·기호)는 들어가지 않는다(안내 문구를 보여 준다).
 * - "여정 확인하기"를 누른 뒤 나오는 화면(코드 확인 → 참여)은 다음 작업에서 만든다. 지금은 onConfirm 이 아무 일도 하지 않는다.
 *   (코드가 실제로 있는지는 서버만 알 수 있어서, 버튼을 누를 때 joinJourney 로 확인할 예정)
 */
@Composable
fun JoinCodeDialog(
    onClose: () -> Unit,
    onConfirm: (code: String) -> Unit = {},
) {
    var code by rememberSaveable { mutableStateOf("") }
    var droppedChar by rememberSaveable { mutableStateOf(false) }
    val complete = InviteCode.isComplete(code)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() } // 팝업이 뜨면 바로 키보드

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .background(LogUsColors.card, RoundedCornerShape(28.dp)),
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 열쇠 그림
                Box(
                    Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_key),
                        contentDescription = null,
                        tint = LogUsColors.primaryStrong,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "초대 코드 입력",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "친구에게 받은 6자리 코드를\n넣으면 여정을 확인할 수 있어요",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))

                // 코드 입력칸: 6자리가 되면 테두리가 보라로 진해진다
                val shape = RoundedCornerShape(16.dp)
                BasicTextField(
                    value = code,
                    onValueChange = { typed ->
                        val (cleaned, dropped) = InviteCode.clean(typed)
                        code = cleaned
                        droppedChar = dropped
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = LogUsColors.primaryStrong,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 10.sp,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .semantics { contentDescription = "초대 코드 6자리 입력칸" },
                    decorationBox = { inner ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .background(MaterialTheme.colorScheme.background, shape)
                                .border(
                                    width = if (complete) 2.dp else 1.dp,
                                    color = if (complete) MaterialTheme.colorScheme.primary else LogUsColors.border,
                                    shape = shape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (code.isEmpty()) {
                                Text(
                                    "- - - - - -",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 4.sp,
                                )
                            }
                            inner()
                        }
                    },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    when {
                        complete -> "6자리를 모두 넣었어요"
                        droppedChar -> "코드에는 0·O·1·I 와 기호가 없어요 (${code.length} / 6자리)"
                        else -> "${code.length} / 6자리"
                    },
                    color = if (complete) Success else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (complete) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(Modifier.height(18.dp))

                // 6자리 전: 회색(누를 수 없음) / 6자리: 짙은 보라 "여정 확인하기"
                Button(
                    onClick = { onConfirm(code) },
                    enabled = complete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryStrong, // 라이트·다크 모두 짙은 보라 + 흰 글자(대비 8:1)
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surface,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(
                        if (complete) "여정 확인하기" else "코드 6자리를 넣어주세요",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun JoinCodeDialogPreview() {
    LogUsTheme { JoinCodeDialog(onClose = {}) }
}
