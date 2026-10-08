package com.logus.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logus.app.auth.Profile
import com.logus.app.home.HomeUiState
import com.logus.app.home.HomeViewModel
import com.logus.app.home.JoinViewModel
import com.logus.app.journey.Journey
import com.logus.app.journey.NewJourneyViewModel
import com.logus.app.journey.StartDayCamera
import com.logus.app.record.CaptureViewModel
import com.logus.app.ui.components.BottomTabBar
import com.logus.app.ui.components.MainTab
import com.logus.app.ui.explore.ExploreScreen
import com.logus.app.ui.home.HomeScreen
import com.logus.app.ui.home.JoinCodeDialog
import com.logus.app.ui.invite.InviteAcceptedDialog
import com.logus.app.ui.invite.InvitePreviewScreen
import com.logus.app.ui.journey.JourneyInviteDialog
import com.logus.app.ui.journey.NewJourneyFlow
import com.logus.app.ui.mylog.MyLogScreen
import com.logus.app.ui.record.CaptureScreen
import java.time.LocalDate

/**
 * 가입한 사람이 보는 메인 화면: 위쪽은 고른 탭의 화면, 아래쪽은 항상 하단 탭(홈 / 탐색 / 마이로그).
 * 고른 탭은 화면을 돌려도 유지된다. 홈이 아닌 탭에서 폰의 뒤로 가기를 누르면 홈으로 돌아간다.
 */
@Composable
fun MainScreen(
    uid: String,
    profile: Profile,
    onSignOut: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    newJourneyViewModel: NewJourneyViewModel = viewModel(),
    joinViewModel: JoinViewModel = viewModel(),
    captureViewModel: CaptureViewModel = viewModel(),
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    // S02·S03 새 여정 만들기를 보여 주는 중인지(이때는 하단 탭을 숨긴다)
    var creatingJourney by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = !creatingJourney && tab != MainTab.HOME) { tab = MainTab.HOME }

    // 초대 코드로 참여: 입력 팝업 → (서버 확인) → I01 초대 확인 화면(홈 탭 자리에 보인다)
    // → "초대 수락하기"(서버 참여) → 수락 완료 팝업 → "홈으로 가기"(참여한 여정의 S01-A)
    val joinState by joinViewModel.state.collectAsStateWithLifecycle()
    val invitePreview = joinState.preview
    BackHandler(enabled = !creatingJourney && tab == MainTab.HOME && invitePreview != null) {
        joinViewModel.closePreview()
    }

    // 진행 중인 여정 확인(로그인한 사람이 바뀔 때만 다시 읽는다)
    LaunchedEffect(uid) { homeViewModel.load(uid) }
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    val ongoing = homeState as? HomeUiState.Ongoing

    // S01-A 여정 카드의 친구 추가 버튼 → 여정에 초대하기 팝업(뒤는 S01-A 그대로)
    var invitingToJourney by rememberSaveable { mutableStateOf(false) }
    // S04 앱 내 카메라를 보여 주는 중인지(이때는 하단 탭을 숨긴다)
    var capturing by rememberSaveable { mutableStateOf(false) }
    val captureState by captureViewModel.state.collectAsStateWithLifecycle()
    fun openCapture(journey: Journey) {
        captureViewModel.open(journey) // 상태를 처음으로 되돌리고 공통 알림 간격을 읽는다
        capturing = true
    }
    // 홈을 다시 읽는 중이거나 진행 중인 여정이 없어지면 팝업·카메라를 닫는다(나중에 갑자기 다시 뜨지 않게)
    LaunchedEffect(homeState) {
        if (homeState !is HomeUiState.Ongoing) {
            invitingToJourney = false
            capturing = false
        }
    }

    // 여정 시작일이면 S04 앱 내 카메라를 연다(오늘 시작하는 여정을 방금 만들었거나, 미리 만든 여정의 시작일에 앱을 열었을 때).
    // 같은 여정은 이 폰에서 한 번만 자동으로 연다.
    LaunchedEffect(homeState) {
        val current = homeState as? HomeUiState.Ongoing ?: return@LaunchedEffect
        if (current.journey.startDate == LocalDate.now().toString() &&
            StartDayCamera.claimAutoOpen(context, current.journey.id)
        ) {
            openCapture(current.journey)
        }
    }

    if (creatingJourney) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            NewJourneyFlow(
                viewModel = newJourneyViewModel,
                onClose = { creatingJourney = false },
                onSaved = { startDate ->
                    creatingJourney = false
                    tab = MainTab.HOME
                    if (startDate.isAfter(LocalDate.now())) {
                        Toast.makeText(
                            context,
                            "여정을 만들었어요. ${startDate.monthValue}월 ${startDate.dayOfMonth}일에 앱을 열면 카메라가 열려요.",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    // 홈을 다시 읽는다: 오늘 시작하는 여정이면 S01-A 가 되고 S04 카메라가 열린다
                    homeViewModel.load(uid, force = true)
                },
            )
        }
    } else if (capturing && ongoing != null) {
        // S04 앱 내 카메라(전체 화면, 하단 탭 없음). 뒤로 가기 → 찍던·찍은 영상은 버리고 홈(S01-A)으로
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            CaptureScreen(
                journeyName = ongoing.journey.name,
                state = captureState,
                onBack = {
                    captureViewModel.discardAndStop()
                    capturing = false
                },
                onStart = { controller, withAudio -> captureViewModel.startRecording(context, controller, withAudio) },
                onStop = captureViewModel::stopRecording,
            )
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding(),
            ) {
                when (tab) {
                    // I01 초대 확인. "초대 수락하기" → 서버 참여 → 아래 수락 완료 팝업
                    MainTab.HOME -> if (invitePreview != null) InvitePreviewScreen(
                        preview = invitePreview,
                        onAccept = joinViewModel::accept,
                        accepting = joinState.accepting,
                        error = joinState.acceptError,
                    ) else HomeScreen(
                        profile = profile,
                        state = homeState,
                        onRetry = { homeViewModel.load(uid, force = true) },
                        onNewJourney = {
                            newJourneyViewModel.reset()
                            creatingJourney = true
                        },
                        onJoinWithCode = joinViewModel::openDialog,
                        // S01-A "지금 기록하기" → S04 앱 내 카메라
                        onRecordNow = { ongoing?.let { openCapture(it.journey) } },
                        onInviteToJourney = {
                            invitingToJourney = true
                            homeViewModel.refreshOngoing() // 그사이 누가 참여했을 수 있으니 인원수를 새로 읽는다
                        },
                    )
                    MainTab.EXPLORE -> ExploreScreen()
                    MainTab.MY_LOG -> MyLogScreen(onSignOut = onSignOut)
                }
            }
            BottomTabBar(selected = tab, onSelect = { tab = it })
        }
        if (joinState.dialogOpen) {
            JoinCodeDialog(
                onClose = joinViewModel::closeDialog,
                onConfirm = joinViewModel::check, // 서버(previewInvite)가 코드를 확인 → 맞으면 I01 화면
                checking = joinState.checking,
                error = joinState.error,
                onEdit = joinViewModel::clearError,
            )
        }
        // 여정에 초대하기 팝업(S01-A 위): 여정 이름·현재 인원·여정의 초대 코드(늘 같은 코드)·복사·공유
        if (invitingToJourney && ongoing != null && tab == MainTab.HOME && invitePreview == null) {
            JourneyInviteDialog(
                journeyName = ongoing.journey.name,
                memberCount = ongoing.journey.memberCount,
                inviteCode = ongoing.journey.inviteCode,
                endDate = ongoing.journey.endDate,
                inviterName = profile.nickname,
                onClose = { invitingToJourney = false },
            )
        }
        // 초대 수락 완료 팝업(I01 위). "홈으로 가기" → 방금 참여한 여정을 S01-A 로 보여 준다("지금 기록하기")
        val joinedJourneyId = joinState.joinedJourneyId
        if (joinedJourneyId != null && invitePreview != null) {
            InviteAcceptedDialog(
                inviterName = invitePreview.inviterName,
                onGoHome = {
                    joinViewModel.finish()
                    tab = MainTab.HOME
                    homeViewModel.showJoined(uid, joinedJourneyId)
                },
            )
        }
    }
}
