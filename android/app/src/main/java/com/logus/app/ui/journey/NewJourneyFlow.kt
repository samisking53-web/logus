package com.logus.app.ui.journey

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logus.app.journey.NewJourneyStep
import com.logus.app.journey.NewJourneyViewModel
import java.time.LocalDate

/**
 * 새 여정 만들기 흐름: S02 새 여정 만들기 ↔ S03 여행 기간 달력.
 * 폰의 뒤로 가기: 달력 → S02, S02 → 닫기(홈).
 */
@Composable
fun NewJourneyFlow(
    viewModel: NewJourneyViewModel,
    onClose: () -> Unit,
    onSaved: (startDate: LocalDate) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

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
            onOpenCalendar = viewModel::openCalendar,
            onNotifyHours = viewModel::selectNotifyHours,
            onToggleNotifyOff = viewModel::toggleNotifyOff,
        )

        NewJourneyStep.CALENDAR -> PeriodCalendarScreen(
            initialStart = state.startDate,
            initialEnd = state.endDate,
            onBack = viewModel::closeCalendar,
            onConfirm = viewModel::confirmPeriod,
        )
    }
}
