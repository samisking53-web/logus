package com.logus.app.ui.journey

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.R
import com.logus.app.journey.City
import com.logus.app.journey.LocationStatus
import com.logus.app.journey.NewJourneyUiState
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.PrimaryStrong
import java.time.LocalDate

// S02 새 여정 만들기
/**
 * 여정 이름·국가와 도시·여행 기간·촬영 알림을 정하는 화면.
 * - 도시: ① 화면을 열면 현재 위치(GPS)의 도시를 먼저 추천 ② 입력하면 내장 목록(journey/Cities.kt)에서 바로 추천
 *   ③ "지도에서 찾기"로 오픈스트리트맵 지도 데이터에서 세계 도시를 찾는다. 어디에도 없으면 입력한 글자 그대로 저장한다.
 * - 여행 기간: 누르면 S03 달력이 열린다.
 * - 촬영 알림: 1·2·3시간 중 하나, 또는 "촬영 알림 받지 않기".
 * - 친구 초대하기: 초대 링크 공유 화면은 다음 작업에서 만든다. 지금은 버튼만 있다.
 * - 위쪽 "저장 →": 모두 채우면 켜진다. 누르면 서버 함수 createJourney 로 여정을 만든다.
 */
@Composable
fun NewJourneyScreen(
    state: NewJourneyUiState,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onCityQueryChange: (String) -> Unit,
    onCitySelect: (City) -> Unit,
    onCityClear: () -> Unit,
    onLocate: () -> Unit,
    onSearchMap: () -> Unit,
    onOpenCalendar: () -> Unit,
    onNotifyHours: (Int) -> Unit,
    onToggleNotifyOff: () -> Unit,
    onInviteFriends: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding(), // 키보드가 올라오면 내용이 가려지지 않게
    ) {
        TopBar(canSave = state.canSave, saving = state.saving, onCancel = onCancel, onSave = onSave)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { ErrorMessage(it) }

            // 여정 이름
            SectionCard(
                icon = rememberVectorPainter(Icons.Outlined.Edit),
                title = "여정 이름",
                trailing = {
                    Text(
                        "${state.name.length} / ${NewJourneyUiState.NAME_MAX}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                },
            ) {
                InputField(
                    value = state.name,
                    onValueChange = onNameChange,
                    placeholder = "예) 우리의 포르투",
                )
            }

            // 국가·도시: ① 현재 위치(GPS) 추천 ② 입력하면 내장 목록에서 바로 추천 ③ "지도에서 찾기"(OSM 지도 데이터)
            SectionCard(icon = rememberVectorPainter(Icons.Outlined.LocationOn), title = "국가·도시") {
                CurrentLocationRow(state = state, onLocate = onLocate, onSelect = onCitySelect)
                InputField(
                    value = state.cityQuery,
                    onValueChange = onCityQueryChange,
                    placeholder = "도시나 국가 이름을 입력해 주세요",
                    leading = Icons.Filled.Search,
                    onClear = if (state.cityQuery.isNotEmpty()) onCityClear else null,
                    imeAction = ImeAction.Search,
                    onImeAction = onSearchMap,
                )
                // 고른 도시(현재 위치 줄에서 이미 보이면 다시 그리지 않는다)
                state.selectedCity
                    ?.takeIf { it != state.currentCity }
                    ?.let { CityRow(it, selected = true, onClick = {}) }
                state.suggestions.forEach { city -> CityRow(city, selected = false, onClick = { onCitySelect(city) }) }
                if (state.selectedCity == null && state.cityQuery.isNotBlank()) {
                    MapSearchSection(state = state, onSearchMap = onSearchMap, onSelect = onCitySelect)
                }
            }

            // 여행 기간
            SectionCard(icon = rememberVectorPainter(Icons.Outlined.DateRange), title = "여행 기간") {
                PeriodField(start = state.startDate, end = state.endDate, onClick = onOpenCalendar)
            }

            // 촬영 알림
            SectionCard(
                icon = rememberVectorPainter(Icons.Outlined.Notifications),
                title = "촬영 알림",
                trailing = {
                    Text(
                        if (state.notifyOff) "받지 않음" else "${state.notifyHours}시간마다",
                        color = LogUsColors.primaryStrong,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3).forEach { h ->
                        IntervalChip(
                            label = "${h}시간",
                            selected = !state.notifyOff && state.notifyHours == h,
                            dimmed = state.notifyOff,
                            onClick = { onNotifyHours(h) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                NotifyOffRow(checked = state.notifyOff, onToggle = onToggleNotifyOff)
            }

            // 친구 초대하기(버튼만)
            InviteButton(onClick = onInviteFriends)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 맨 위: 취소 / 새 여정 만들기 / 저장 → */
@Composable
private fun TopBar(canSave: Boolean, saving: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 8.dp),
    ) {
        TextButton(onClick = onCancel, enabled = !saving, modifier = Modifier.align(Alignment.CenterStart)) {
            Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            "새 여정 만들기",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
        Surface(
            onClick = onSave,
            enabled = canSave,
            shape = RoundedCornerShape(50),
            color = if (canSave || saving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (canSave || saving) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .heightIn(min = 44.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("저장", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** 흰 카드 한 칸: 왼쪽 위 그림 + 제목(+오른쪽 위 보조 표시), 아래 내용 */
@Composable
private fun SectionCard(
    icon: Painter,
    title: String,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LogUsColors.card)
            .border(1.dp, LogUsColors.line, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = LogUsColors.primaryStrong, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            trailing()
        }
        content()
    }
}

/** 둥근 입력칸 */
@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: ImageVector? = null,
    onClear: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        leadingIcon = leading?.let { { Icon(it, contentDescription = null, tint = LogUsColors.primaryStrong) } },
        trailingIcon = onClear?.let {
            {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.Clear, contentDescription = "지우기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = onImeAction?.let { action -> KeyboardActions(onAny = { action() }) } ?: KeyboardActions.Default,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = LogUsColors.border,
            focusedContainerColor = MaterialTheme.colorScheme.background,
            unfocusedContainerColor = MaterialTheme.colorScheme.background,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 도시 한 줄: 이름 + 국가. 고른 도시는 연보라 바탕 + "선택됨" + 체크 */
@Composable
private fun CityRow(city: City, selected: Boolean, onClick: () -> Unit, badge: String? = null) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, LogUsColors.border, shape)
                } else {
                    Modifier
                },
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    city.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (selected || badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selected) "선택됨" else badge.orEmpty(),
                        color = LogUsColors.primaryStrong,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            // 고른 줄(연보라 바탕)에서는 흰 표시, 아닌 줄(흰 바탕)에서는 연보라 표시
                            .background(if (selected) LogUsColors.card else MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                listOf(badge?.takeIf { selected }, city.country, city.english)
                    .filterNot { it.isNullOrBlank() }
                    .joinToString(" · ")
                    .ifEmpty { "국가 정보 없음" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = LogUsColors.primaryStrong)
        }
    }
}

/**
 * 현재 위치 줄: 찾는 중 / 찾은 도시(누르면 선택) / "현재 위치로 찾기"(권한이 없거나 못 찾았을 때 다시 시도)
 */
@Composable
private fun CurrentLocationRow(state: NewJourneyUiState, onLocate: () -> Unit, onSelect: (City) -> Unit) {
    val found = state.currentCity
    when {
        state.locationStatus == LocationStatus.LOCATING -> Row(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("현재 위치의 도시를 찾는 중…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }

        state.locationStatus == LocationStatus.FOUND && found != null -> CityRow(
            city = found,
            selected = state.selectedCity == found,
            onClick = { onSelect(found) },
            badge = "현재 위치",
        )

        else -> Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, LogUsColors.border, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onLocate)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Place, contentDescription = null, tint = LogUsColors.primaryStrong)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("현재 위치로 찾기", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when (state.locationStatus) {
                        LocationStatus.DENIED -> "위치 권한을 허용하면 지금 있는 도시를 추천해요"
                        LocationStatus.FAILED -> "위치를 찾지 못했어요. 눌러서 다시 찾아요"
                        else -> "지금 있는 도시를 추천해요"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * 지도에서 찾기(OSM 지도 데이터). 무료 검색 서버 규칙상 글자를 칠 때마다가 아니라 버튼(또는 키보드 검색)을 누를 때만 찾는다.
 */
@Composable
private fun MapSearchSection(state: NewJourneyUiState, onSearchMap: () -> Unit, onSelect: (City) -> Unit) {
    val query = state.cityQuery.trim()
    if (state.showMapResults) {
        Text(
            "지도 검색 결과",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (state.mapResults.isEmpty()) {
            Text(
                "지도에서도 찾지 못했어요. 입력한 이름 그대로 저장돼요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        state.mapResults.forEach { city -> CityRow(city, selected = false, onClick = { onSelect(city) }) }
        Text(
            "지도 데이터 © OpenStreetMap 기여자",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !state.mapSearching, role = Role.Button, onClick = onSearchMap)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.mapSearching) {
            CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Search, contentDescription = null, tint = LogUsColors.primaryStrong)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (state.mapSearching) "지도에서 찾는 중…" else "지도에서 '$query' 찾기",
            color = LogUsColors.primaryStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    state.mapError?.let {
        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

/** 여행 기간 칸: 누르면 S03 달력 */
@Composable
private fun PeriodField(start: LocalDate?, end: LocalDate?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, LogUsColors.border, shape)
            .clickable(role = Role.Button, onClickLabel = "달력에서 여행 기간 고르기", onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.DateRange, contentDescription = null, tint = LogUsColors.primaryStrong)
        Spacer(Modifier.width(12.dp))
        Text(
            if (start != null && end != null) periodLabel(start, end) else "시작일과 종료일을 골라 주세요",
            color = if (start != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            fontWeight = if (start != null) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 알림 간격 칩(1시간·2시간·3시간) */
@Composable
private fun IntervalChip(
    label: String,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
            .border(
                BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else LogUsColors.line),
                shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .alpha(if (dimmed) 0.45f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) LogUsColors.primaryStrong else MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/** ○ 촬영 알림 받지 않기 (누르면 ● 로 바뀌고 알림 간격이 꺼진다) */
@Composable
private fun NotifyOffRow(checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.primary else LogUsColors.card),
        )
        Spacer(Modifier.width(10.dp))
        Text("촬영 알림 받지 않기", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
    }
}

/**
 * 친구 초대하기(초대 링크 만들기). 짙은 보라(primary-strong #4A2FB8) 채움 + 흰 글자(대비 8:1).
 * 다크 모드에서도 같은 짙은 보라를 쓴다(흰 글자가 잘 보이게). 공유 화면은 다음 작업에서 연결한다.
 */
@Composable
private fun InviteButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = PrimaryStrong,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_person_add),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("친구 초대하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("초대 링크를 만들어요", fontSize = 13.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** 10월 4일 ~ 10월 6일 (올해가 아니면 연도도 붙인다) */
internal fun periodLabel(start: LocalDate, end: LocalDate): String {
    val thisYear = LocalDate.now().year
    fun f(d: LocalDate) = if (d.year == thisYear) "${d.monthValue}월 ${d.dayOfMonth}일" else "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일"
    return if (start == end) f(start) else "${f(start)} ~ ${f(end)}"
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun NewJourneyScreenPreview() {
    LogUsTheme {
        NewJourneyScreen(
            state = NewJourneyUiState(
                name = "우리의 포르투",
                cityQuery = "포르",
                startDate = LocalDate.now(),
                endDate = LocalDate.now().plusDays(2),
            ),
            onCancel = {}, onSave = {}, onNameChange = {}, onCityQueryChange = {}, onCitySelect = {},
            onCityClear = {}, onLocate = {}, onSearchMap = {}, onOpenCalendar = {}, onNotifyHours = {}, onToggleNotifyOff = {},
        )
    }
}
