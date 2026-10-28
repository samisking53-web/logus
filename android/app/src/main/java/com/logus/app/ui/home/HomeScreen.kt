package com.logus.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logus.app.R
import com.logus.app.auth.Profile
import com.logus.app.home.HomeUiState
import com.logus.app.home.OngoingCard
import com.logus.app.journey.Journey
import com.logus.app.journey.Member
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.components.LogoMark
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.PrimaryStrong

// S01 홈 / S01-A 여행 기간 중 홈
/**
 * 홈 화면. 진행 중인 여정이 없으면 S01, 오늘이 여행 기간 안인 여정이 있으면 S01-A 를 보여 준다.
 * - S01: 제목 → 프로필 상자 → "현재 진행중인 여정이 없어요!" → 새 여정 시작하기 / 초대 코드로 참여
 * - S01-A: 프로필 상자(작게) → 진행 중인 여정 카드 **모두**(여러 개면 "진행 중인 여정 N개"와 함께 아래로 이어진다)
 *   → 새 여정 시작하기(조금 밝은 보라) → 초대 코드로 참여(흰색). 화면이 길어지면 아래로 스크롤된다.
 *   "지금 기록하기" 버튼은 홈에 두지 않는다(2026-10-28 팀 결정): 여정 카드 → N01 기록 보기의 "지금 기록하기"로 찍는다.
 * "새 여정 시작하기"는 S02, "초대 코드로 참여"는 6자리 초대 코드 입력 팝업(링크는 쓰지 않음)을 연다.
 * 여정 카드: 누르면 N01 기록 보기, 사람+ 버튼은 "여정에 초대하기" 팝업, 사진 위 전환 버튼은 대표 사진 선택 팝업(MainScreen 이 연결).
 * coverUrl: 대표 사진 Storage 경로 → 내려받기 주소(HomeViewModel 이 한 번 받으면 기억한다).
 */
@Composable
fun HomeScreen(
    profile: Profile,
    state: HomeUiState,
    onRetry: () -> Unit,
    onEditPhoto: () -> Unit = {},
    onNewJourney: () -> Unit = {},
    onJoinWithCode: () -> Unit = {},
    onInviteToJourney: (Journey) -> Unit = {},
    onOpenJourney: (Journey) -> Unit = {},
    onChangeCover: (OngoingCard) -> Unit = {},
    coverUrl: suspend (String) -> String? = { null },
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        LogoHeader()

        when (state) {
            is HomeUiState.Ongoing -> {
                // S01-A
                Spacer(Modifier.height(8.dp))
                ProfileCard(profile, compact = true, onEditPhoto = onEditPhoto)
                Spacer(Modifier.height(if (state.journeys.size > 1) 24.dp else 32.dp))
                if (state.journeys.size > 1) {
                    Text(
                        "진행 중인 여정 ${state.journeys.size}개",
                        color = LogUsColors.primaryStrong,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                state.journeys.forEachIndexed { index, card ->
                    if (index > 0) Spacer(Modifier.height(16.dp))
                    key(card.journey.id) {
                        val cover by produceState<String?>(initialValue = null, card.coverPath) {
                            value = card.coverPath?.let { coverUrl(it) }
                        }
                        JourneyCard(
                            journey = card.journey,
                            members = card.members,
                            myNickname = profile.nickname,
                            coverPhoto = cover,
                            onInviteFriends = { onInviteToJourney(card.journey) }, // 여정에 초대하기 팝업(MainScreen)
                            onOpen = { onOpenJourney(card.journey) }, // 카드를 누르면 N01 기록 보기(MainScreen)
                            onChangeCover = { onChangeCover(card) }, // 대표 사진 선택 팝업(MainScreen)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                // S01 과 같은 두 버튼. 새 여정은 조금 밝은 보라(그림 상자는 기본 보라)
                ActionButton(
                    icon = rememberVectorPainter(Icons.Filled.Add),
                    title = "새 여정 시작하기",
                    subtitle = "이름과 날짜만 정하면 돼요",
                    filled = true,
                    onClick = onNewJourney,
                    containerColor = LogUsColors.primaryLight,
                    iconBoxColor = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                ActionButton(
                    icon = painterResource(R.drawable.ic_person_add),
                    title = "초대 코드로 참여",
                    subtitle = "받은 6자리 초대 코드로 들어가요",
                    filled = false,
                    onClick = onJoinWithCode,
                )
            }

            else -> {
                // S01 (여정을 찾는 중이거나 오류일 때도 같은 틀을 쓴다)
                Spacer(Modifier.height(24.dp))
                Text(
                    "어떤 순간을 남길까요?",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(20.dp))
                ProfileCard(profile, compact = false, onEditPhoto = onEditPhoto)

                when (state) {
                    HomeUiState.Loading -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }

                    is HomeUiState.Failed -> Column(Modifier.padding(vertical = 24.dp)) {
                        ErrorMessage(state.message)
                        TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("다시 시도", color = LogUsColors.primaryStrong)
                        }
                    }

                    else -> {
                        Spacer(Modifier.height(48.dp))
                        Text(
                            "현재 진행중인 여정이 없어요!",
                            color = LogUsColors.primaryStrong,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(48.dp))
                        ActionButton(
                            icon = rememberVectorPainter(Icons.Filled.Add),
                            title = "새 여정 시작하기",
                            subtitle = "이름과 날짜만 정하면 돼요",
                            filled = true,
                            onClick = onNewJourney,
                        )
                        Spacer(Modifier.height(12.dp))
                        ActionButton(
                            icon = painterResource(R.drawable.ic_person_add),
                            title = "초대 코드로 참여",
                            subtitle = "받은 6자리 초대 코드로 들어가요",
                            filled = false,
                            onClick = onJoinWithCode,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 화면 맨 위 로고: 지구 모양 + LOG EARTH (강조 보라) */
@Composable
internal fun LogoHeader() {
    LogoMark(
        Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        iconSize = 24.dp,
        fontSize = 20.sp,
        letterSpacing = 3.sp,
        gap = 10.dp,
    )
}

/**
 * 큰 상자 버튼: 왼쪽 그림 상자 + 제목(+설명) + 오른쪽 ›.
 * filled = true 면 보라 채움(흰 글자), false 면 흰 카드(본문 글자)
 * containerColor·iconBoxColor 로 채움색·그림 상자 색을 바꿀 수 있다(팔레트 색만, 채움이면 흰 글자 대비 4.5:1 이상)
 * 버튼 높이는 76dp 로 모두 같다(설명 글이 있어도 그림 상자 44dp + 위아래 여백 안에 들어간다)
 */
@Composable
internal fun ActionButton(
    icon: Painter,
    title: String,
    subtitle: String?,
    filled: Boolean,
    onClick: () -> Unit,
    containerColor: Color? = null,
    iconBoxColor: Color? = null,
) {
    val container = containerColor ?: if (filled) MaterialTheme.colorScheme.primary else LogUsColors.card
    val titleColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val subColor = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    // 보라 버튼 안 그림 상자는 한 단계 진한 보라, 흰 버튼 안은 연보라
    val iconBox = iconBoxColor ?: if (filled) PrimaryStrong else MaterialTheme.colorScheme.surface
    val iconTint = if (filled) MaterialTheme.colorScheme.onPrimary else LogUsColors.primaryStrong

    Surface(
        onClick = onClick,
        color = container,
        shape = RoundedCornerShape(18.dp),
        border = if (filled) null else BorderStroke(1.dp, LogUsColors.line),
        shadowElevation = if (filled) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(iconBox, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, color = subColor, fontSize = 12.sp)
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- 미리보기(Android Studio 의 Split/Design 화면에서 보인다) ----------

private val previewProfile = Profile(nickname = "성연", photoUrl = null, coins = 100)

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun HomeS01Preview() {
    LogUsTheme { HomeScreen(previewProfile, HomeUiState.NoJourney, onRetry = {}) }
}

@Preview(showBackground = true, heightDp = 760)
@Composable
private fun HomeS01APreview() {
    LogUsTheme {
        HomeScreen(
            previewProfile,
            HomeUiState.Ongoing(
                listOf(
                    OngoingCard(
                        Journey("j1", "우리의 포르투", "2026-09-24", "2026-09-26", listOf("a", "b", "c", "d"), 4),
                        listOf(Member("a", "성연"), Member("b", "민지"), Member("c", "수아"), Member("d", "현우")),
                    ),
                    OngoingCard(
                        Journey("j2", "리스본 산책", "2026-09-25", "2026-09-27", listOf("a", "b"), 2),
                        listOf(Member("a", "성연"), Member("b", "민지")),
                    ),
                ),
            ),
            onRetry = {},
        )
    }
}
