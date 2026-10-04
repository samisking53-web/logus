package com.logus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.logus.app.auth.Profile
import com.logus.app.home.HomeViewModel
import com.logus.app.ui.components.BottomTabBar
import com.logus.app.ui.components.MainTab
import com.logus.app.ui.explore.ExploreScreen
import com.logus.app.ui.home.HomeScreen
import com.logus.app.ui.mylog.MyLogScreen

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
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    BackHandler(enabled = tab != MainTab.HOME) { tab = MainTab.HOME }

    // 진행 중인 여정 확인(로그인한 사람이 바뀔 때만 다시 읽는다)
    LaunchedEffect(uid) { homeViewModel.load(uid) }
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            when (tab) {
                MainTab.HOME -> HomeScreen(
                    profile = profile,
                    state = homeState,
                    onRetry = { homeViewModel.load(uid, force = true) },
                )
                MainTab.EXPLORE -> ExploreScreen()
                MainTab.MY_LOG -> MyLogScreen(onSignOut = onSignOut)
            }
        }
        BottomTabBar(selected = tab, onSelect = { tab = it })
    }
}
