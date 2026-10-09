package com.logus.app.ui.journey

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logus.app.journey.NewJourneyStep
import com.logus.app.journey.NewJourneyViewModel
import java.time.LocalDate

/**
 * 새 여정 만들기 흐름: S02 새 여정 만들기 ↔ S03 여행 기간 달력, S02 위의 초대 코드 팝업.
 * 폰의 뒤로 가기: 달력 → S02, S02 → 닫기(홈).
 */
@Composable
fun NewJourneyFlow(
    viewModel: NewJourneyViewModel,
    onClose: () -> Unit,
    onSaved: (startDate: LocalDate) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 위치 권한(대략적인 위치) 묻기 → 허용하면 현재 위치의 도시를 찾는다
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.locate(context) else viewModel.onLocationDenied()
    }
    fun requestLocation() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.locate(context)
        } else {
            viewModel.markLocationAsked()
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // 화면을 열면 먼저 현재 위치로 도시를 잡아 본다(한 번만. 달력에 다녀와도 다시 묻지 않는다)
    LaunchedEffect(Unit) {
        if (!state.locationAsked) requestLocation()
    }

    // 친구 초대하기 → 초대 코드 팝업(S02 위에 뜬다). 닫으면 저장과 똑같이 홈으로 간다
    val code = state.inviteCode
    if (state.showInviteDialog && code != null) {
        InviteCodeDialog(code = code, onClose = { viewModel.closeInviteDialog(onSaved) })
    }

    BackHandler(enabled = !state.saving) {
        if (state.step == NewJourneyStep.CALENDAR) viewModel.closeCalendar() else onClose()
    }

    when (state.step) {
        NewJourneyStep.FORM -> NewJourneyScreen(
            state = state,
            onCancel = onClose,
            onSave = { viewModel.save(onSaved) },
            onNameChange = viewModel::updateName,
            onCityQueryChange = viewModel::updateCityQuery,
            onCitySelect = viewModel::selectCity,
            onCityClear = viewModel::clearCity,
            onLocate = ::requestLocation,
            onSearchMap = viewModel::searchMap,
            onOpenCalendar = viewModel::openCalendar,
            onNotifyHours = viewModel::selectNotifyHours,
            onToggleNotifyOff = viewModel::toggleNotifyOff,
            onInviteFriends = viewModel::inviteFriends,
        )

        NewJourneyStep.CALENDAR -> PeriodCalendarScreen(
            initialStart = state.startDate,
            initialEnd = state.endDate,
            onBack = viewModel::closeCalendar,
            onConfirm = viewModel::confirmPeriod,
        )
    }
}
