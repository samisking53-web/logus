package com.logus.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.logus.app.R
import com.logus.app.ui.theme.LogUsColors

/** 하단 탭 3개. 글자 대신 그림(픽토그램)만 보이고, 화면 읽기 프로그램에는 이름(label)을 읽어 준다. */
enum class MainTab(val label: String, @DrawableRes val icon: Int) {
    HOME("홈", R.drawable.ic_nav_home),
    EXPLORE("탐색", R.drawable.ic_nav_explore),
    MY_LOG("마이로그", R.drawable.ic_nav_mylog),
}

/**
 * 모든 메인 화면 아래에 붙는 탭 바(홈 / 탐색 / 마이로그).
 * 고른 탭은 강조 보라 그림 + 연보라 둥근 바탕, 나머지는 보조 글자색 그림.
 */
@Composable
fun BottomTabBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(LogUsColors.card)
            .navigationBarsPadding(), // 폰 아래 제스처 막대와 겹치지 않게
    ) {
        HorizontalDivider(color = LogUsColors.line)
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Box(
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                        .semantics { contentDescription = tab.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 36.dp)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surface else LogUsColors.card,
                                RoundedCornerShape(18.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(tab.icon),
                            contentDescription = null, // 이름은 바깥 Box 가 읽어 준다
                            tint = if (isSelected) LogUsColors.primaryStrong else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    }
}
