package com.logus.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.logus.app.R
import com.logus.app.journey.Journey
import com.logus.app.journey.Member
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.MemberColors
import com.logus.app.ui.theme.OnPrimary
import com.logus.app.ui.theme.Success

/**
 * S01-A 진행 중인 여정 카드: 위쪽 대표 사진 + "진행 중" 표시, 아래쪽 여정 이름·구성원·기간.
 * coverPhoto: 여행 중 기록한 사진·영상 장면 중 대표로 고른 사진(주소). 대표 사진을 고르는 기능을 만들면
 * 여기에 넘긴다. 지금은 null 이라 "사진이 들어갈 자리" 그림을 보여 준다.
 * 여정 이름 오른쪽의 사람+ 버튼(onInviteFriends)은 여행 중에도 새 사람을 초대하는
 * "여정에 초대하기" 팝업(ui/journey/JourneyInviteDialog.kt)을 연다(MainScreen 이 연결).
 * 카드의 나머지 부분을 누르면(onOpen) N01 기록 보기(ui/feed/JourneyFeedScreen.kt)를 연다.
 */
@Composable
fun JourneyCard(
    journey: Journey,
    members: List<Member>,
    myNickname: String,
    coverPhoto: Any? = null,
    onInviteFriends: () -> Unit = {},
    onOpen: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LogUsColors.card)
            .border(1.dp, LogUsColors.line, shape)
            .clickable(role = Role.Button, onClickLabel = "오늘 기록 보기", onClick = onOpen),
    ) {
        // 대표 사진 자리
        Box(
            Modifier
                .fillMaxWidth()
                .height(176.dp)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (coverPhoto != null) {
                AsyncImage(
                    model = coverPhoto,
                    contentDescription = "${journey.name} 대표 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CoverPlaceholder(Modifier.fillMaxSize())
            }
            OngoingBadge(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
            )
        }

        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
            // 여정 이름 + 오른쪽 위 친구 추가 버튼
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    journey.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = onInviteFriends,
                    shape = RoundedCornerShape(10.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = LogUsColors.iconCircle, // 라이트 연보라, 다크는 바탕색(카드와 구분)
                        contentColor = LogUsColors.primaryStrong,
                    ),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_person_add),
                        contentDescription = "이 여정에 친구 초대하기",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberDots(members)
                Spacer(Modifier.width(10.dp))
                val others = journey.memberCount - 1
                Text(
                    "${journeyPeriod(journey.startDate, journey.endDate)}  ·  " +
                        if (others > 0) "$myNickname 외 ${others}명" else myNickname,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "● 진행 중" 흰 알약 표시 */
@Composable
private fun OngoingBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(LogUsColors.card)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(Success),
        )
        Spacer(Modifier.width(6.dp))
        Text("진행 중", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 구성원 동그라미(겹쳐 놓음). 색만으로 구분하지 않게 이름 첫 글자를 넣는다 */
@Composable
private fun MemberDots(members: List<Member>) {
    val names = members.joinToString(", ") { it.nickname }
    Box(Modifier.clearAndSetSemantics { contentDescription = "함께하는 사람: $names" }) {
        members.forEachIndexed { i, member ->
            Box(
                Modifier
                    .offset(x = (i * 18).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MemberColors[i % MemberColors.size])
                    .border(2.dp, LogUsColors.card, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    member.nickname.firstLetter(),
                    color = OnPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // 겹쳐 놓은 만큼 자리를 차지하게 한다
        Spacer(Modifier.width((26 + (members.size - 1).coerceAtLeast(0) * 18).dp).height(26.dp))
    }
}

/**
 * 대표 사진이 아직 없을 때 보여 주는 그림: 연보라 바탕에 건물 모양 상자와 물결.
 * "여기에 대표 사진이 들어간다"는 자리 표시용이다.
 */
@Composable
internal fun CoverPlaceholder(modifier: Modifier = Modifier) {
    val fill = LogUsColors.card
    val wave = LogUsColors.border
    Canvas(modifier.semantics { contentDescription = "대표 사진이 들어갈 자리" }) {
        val w = size.width
        val h = size.height
        val ground = h * 0.78f
        // (왼쪽 위치, 너비, 높이) 비율. 건물 실루엣처럼 들쭉날쭉하게
        listOf(
            Triple(0.00f, 0.09f, 0.40f), Triple(0.10f, 0.09f, 0.52f), Triple(0.21f, 0.13f, 0.36f),
            Triple(0.36f, 0.08f, 0.56f), Triple(0.46f, 0.15f, 0.38f), Triple(0.63f, 0.10f, 0.47f),
            Triple(0.76f, 0.13f, 0.32f), Triple(0.91f, 0.09f, 0.42f),
        ).forEach { (x, bw, bh) ->
            drawRoundRect(
                color = fill.copy(alpha = 0.75f),
                topLeft = Offset(w * x, ground - h * bh),
                size = Size(w * bw, h * bh),
                cornerRadius = CornerRadius(4f, 4f),
            )
        }
        // 땅(물결)
        val path = Path().apply {
            moveTo(0f, ground + h * 0.04f)
            cubicTo(w * 0.3f, ground - h * 0.06f, w * 0.55f, ground - h * 0.02f, w * 0.7f, ground - h * 0.02f)
            cubicTo(w * 0.85f, ground - h * 0.02f, w * 0.95f, ground + h * 0.06f, w, ground + h * 0.06f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(path, fill)
        drawPath(path, wave, style = Stroke(width = 3f))
    }
}

/** "2026-09-24", "2026-09-26" → "9.24 ~ 9.26" (해가 다르면 "2026.12.30 ~ 2027.1.2") */
internal fun journeyPeriod(start: String, end: String): String {
    fun parts(d: String) = d.split("-").mapNotNull { it.toIntOrNull() }
    val s = parts(start)
    val e = parts(end)
    if (s.size != 3 || e.size != 3) return "$start ~ $end"
    return if (s[0] == e[0]) {
        "${s[1]}.${s[2]} ~ ${e[1]}.${e[2]}"
    } else {
        "${s[0]}.${s[1]}.${s[2]} ~ ${e[0]}.${e[1]}.${e[2]}"
    }
}

/** 이름의 첫 글자(한글·영문·이모지 모두 한 글자로) */
private fun String.firstLetter(): String {
    val t = trim()
    if (t.isEmpty()) return "?"
    return String(Character.toChars(t.codePointAt(0)))
}
