package com.logus.app.ui.explore

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.logus.app.R
import com.logus.app.explore.ExploreRoute
import com.logus.app.explore.ExploreUiState
import com.logus.app.explore.ExploreViewModel
import com.logus.app.explore.FeedTab
import com.logus.app.explore.MarkKind
import com.logus.app.explore.PublicRecap
import com.logus.app.explore.RecapListState
import com.logus.app.explore.RecapMark
import com.logus.app.journey.Member
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.components.ErrorMessage
import com.logus.app.ui.components.LogoMark
import com.logus.app.ui.components.ProfileDialog
import com.logus.app.ui.record.RecordTopBar
import com.logus.app.ui.theme.BackgroundDark
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.MemberColors
import com.logus.app.ui.theme.OnPrimary
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.PhotoScrim
import com.logus.app.ui.theme.Primary
import com.logus.app.ui.theme.PrimaryLight

// S08 공개 영상 피드 · S09 검색 결과 · 모아보기(좋아요·저장) (스토리보드 v5 21쪽, 모아보기는 2026-11-08 요청으로 추가)
/**
 * 하단 탭 "탐색".
 * - 맨 위 LOG EARTH → [‹] [🔍 지역 또는 테마 검색] [🔖 모아보기] → "BEST TRAVELER · 포르투"
 * - 공개된 DAY RECAP 을 위아래로 넘겨 본다(한 화면에 하나, 보이는 영상은 저절로 재생). 영상 아래 구성원 프로필,
 *   [♡ 좋아요] [🔖 저장] [📍 장소 보기]. 장소 보기 → 위치 정보 열람 팝업(20코인) → 공개 여정 상세.
 * - 검색: 지역·테마 낱말로 찾고 같은 모양(S09)으로 보여 준다.
 * - 🔖 모아보기: 좋아요·저장한 영상을 모아 본다.
 * onExit: S08 에서 ‹ 또는 폰의 뒤로 가기 → 홈 탭
 */
@Composable
fun ExploreScreen(
    uid: String,
    onExit: () -> Unit,
    viewModel: ExploreViewModel = viewModel(),
) {
    LaunchedEffect(uid) { viewModel.start(uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var profile by remember { mutableStateOf<Member?>(null) }
    // 영상을 누르면 여는 큰 화면(그동안 목록의 작은 재생기는 멈춰 둔다)
    var fullscreen by remember { mutableStateOf<PublicRecap?>(null) }
    // 목록 음소거(화면을 돌려도 유지). 큰 화면은 따로 가진다
    var muted by rememberSaveable { mutableStateOf(false) }

    // 폰의 뒤로 가기: 연 화면을 하나씩 닫는다(S08 이면 홈 탭으로, MainScreen 이 처리)
    BackHandler(enabled = state.routes.isNotEmpty() || state.unlock != null) { viewModel.back() }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val actions = RecapActions(
        liked = state.liked,
        saved = state.saved,
        onToggle = viewModel::toggleMark,
        onPlaces = viewModel::openPlaces,
        onProfile = { profile = it },
        publicUrl = viewModel::publicUrl,
        onOpenFull = { fullscreen = it },
        fullscreenOpen = fullscreen != null,
        muted = muted,
        onToggleMute = { muted = !muted },
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val route = state.route) {
            // S08: 맨 위 [전체] [내 영상](2026-11-08 요청, 내 영상은 꾹 눌러 탐색에서 내리기)
            null -> if (state.feedTab == FeedTab.ALL) FeedPage(
                title = "BEST TRAVELER",
                list = state.feed,
                query = "",
                actions = actions,
                onBack = onExit,
                onSearch = viewModel::search,
                onOpenCollection = viewModel::openCollection,
                onLoadMore = { viewModel.loadMore(search = false) },
                onRetry = viewModel::refreshFeed,
                emptyText = "아직 탐색에 올라온 DAY RECAP이 없어요\n여정의 DAY RECAP을 공개하면 여기에 보여요",
                onRefresh = viewModel::refreshFeed,
                header = { FeedTabs(state.feedTab, viewModel::selectFeedTab) },
            ) else FeedPage(
                title = "내 영상",
                list = state.mine,
                query = "",
                actions = actions,
                onBack = onExit,
                onSearch = viewModel::search,
                onOpenCollection = viewModel::openCollection,
                onLoadMore = viewModel::loadMoreMine,
                onRetry = viewModel::refreshMine,
                emptyText = "아직 탐색에 올린 DAY RECAP이 없어요\n마이로그 → DAY RECAP → 영상 추출에서 올릴 수 있어요",
                onRefresh = viewModel::refreshMine,
                header = {
                    FeedTabs(state.feedTab, viewModel::selectFeedTab)
                    if (state.mine.recaps.isNotEmpty()) LongPressHint()
                },
                onLongPress = viewModel::askUnpublish,
            )
            is ExploreRoute.Search -> FeedPage(
                title = "검색 결과",
                list = state.search,
                query = route.query,
                actions = actions,
                onBack = { viewModel.back() },
                onSearch = viewModel::search,
                onOpenCollection = viewModel::openCollection,
                onLoadMore = { viewModel.loadMore(search = true) },
                onRetry = viewModel::retrySearch,
                emptyText = "'${route.query}'에 맞는 영상이 없어요\n다른 지역이나 테마로 찾아보세요",
            )
            ExploreRoute.Collection -> CollectionPage(state, viewModel, onBack = { viewModel.back() })
            is ExploreRoute.Viewer -> ViewerPage(state, actions, onBack = { viewModel.back() })
            is ExploreRoute.Detail -> state.detail.recap?.let { recap ->
                PublicJourneyScreen(
                    state = state.detail,
                    recap = recap,
                    publicUrl = viewModel::publicUrl,
                    onBack = { viewModel.back() },
                    onRetry = viewModel::retryPlaces,
                    lookupAddress = viewModel::lookupAddress,
                    onProfile = { profile = it },
                )
            }
        }
    }

    state.unpublish?.let { dialog ->
        UnpublishDialog(state = dialog, onConfirm = viewModel::confirmUnpublish, onDismiss = viewModel::dismissUnpublish)
    }
    state.unlock?.let { dialog ->
        PlaceUnlockDialog(
            state = dialog,
            onConfirm = viewModel::confirmUnlock,
            onDismiss = viewModel::dismissUnlock,
        )
    }
    fullscreen?.let { recap ->
        FullscreenRecapDialog(
            recap = recap,
            publicUrl = viewModel::publicUrl,
            onProfile = { profile = it },
            onDismiss = { fullscreen = null },
        )
    }
    profile?.let { ProfileDialog(member = it, onDismiss = { profile = null }) }
}

/** 영상 아래 버튼들이 쓰는 값·동작 묶음 */
internal class RecapActions(
    val liked: Set<String>,
    val saved: Set<String>,
    val onToggle: (MarkKind, PublicRecap) -> Unit,
    val onPlaces: (PublicRecap) -> Unit,
    val onProfile: (Member) -> Unit,
    val publicUrl: (String) -> String,
    /** 영상을 누르면 큰 화면으로 */
    val onOpenFull: (PublicRecap) -> Unit = {},
    /** 큰 화면이 열려 있으면 목록 재생기는 멈춘다 */
    val fullscreenOpen: Boolean = false,
    /** 음소거(오른쪽 아래 소리 버튼) */
    val muted: Boolean = false,
    val onToggleMute: (() -> Unit)? = null,
)

/** 맨 위: LOG EARTH 로고 */
@Composable
internal fun ExploreLogo() {
    LogoMark(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 10.dp),
    )
}

/** S08·S09 공통: 위 검색 줄 + 제목 + 위아래로 넘기는 영상 */
@Composable
private fun FeedPage(
    title: String,
    list: RecapListState,
    query: String,
    actions: RecapActions,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onOpenCollection: () -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    emptyText: String,
    /** 빈 화면의 "새로고침"(S08 피드만) */
    onRefresh: (() -> Unit)? = null,
    /** 검색 줄 아래에 놓을 것([전체] [내 영상] 탭 등) */
    header: (@Composable () -> Unit)? = null,
    /** 영상을 꾹 눌렀을 때([내 영상]: 탐색에서 내리기) */
    onLongPress: ((PublicRecap) -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ExploreLogo()
        SearchRow(initial = query, onBack = onBack, onSearch = onSearch, onOpenCollection = onOpenCollection)
        Spacer(Modifier.height(10.dp))
        header?.invoke()
        when {
            list.recaps.isEmpty() && list.loading -> Centered { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            list.recaps.isEmpty() && list.error != null -> Centered {
                ErrorMessage(list.error)
                TextButton(onClick = onRetry) { Text("다시 시도", color = LogUsColors.primaryStrong) }
            }
            list.recaps.isEmpty() -> Centered {
                Text(
                    emptyText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
                if (onRefresh != null) {
                    TextButton(onClick = onRefresh) { Text("새로고침", color = LogUsColors.primaryStrong, fontWeight = FontWeight.Bold) }
                }
            }
            else -> RecapPager(title = title, recaps = list.recaps, actions = actions, onLoadMore = onLoadMore, onLongPress = onLongPress)
        }
    }
}

/** 위아래로 넘겨 보는 영상(한 화면에 하나). 끝에서 두 번째에 오면 다음 묶음을 읽는다 */
@Composable
private fun RecapPager(
    title: String,
    recaps: List<PublicRecap>,
    actions: RecapActions,
    onLoadMore: () -> Unit,
    onLongPress: ((PublicRecap) -> Unit)? = null,
) {
    val pager = rememberPagerState { recaps.size }
    LaunchedEffect(pager, recaps.size) {
        snapshotFlow { pager.currentPage }.collect { page -> if (page >= recaps.size - 2) onLoadMore() }
    }
    VerticalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        pageSpacing = 16.dp,
        key = { recaps[it].id },
    ) { page ->
        RecapPage(
            title = title,
            recap = recaps[page],
            active = pager.currentPage == page && !pager.isScrollInProgress,
            actions = actions,
            onLongPress = onLongPress,
        )
    }
}

/** 공개 리캡 하나: 제목 · 3분할 영상 · 구성원 프로필 · [좋아요] [저장] [장소 보기] */
@Composable
internal fun RecapPage(
    title: String,
    recap: PublicRecap,
    active: Boolean,
    actions: RecapActions,
    onLongPress: ((PublicRecap) -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
        Text(
            listOf(title, recap.city.ifEmpty { null }).filterNotNull().joinToString(" · "),
            color = LogUsColors.primaryStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            "${recap.journeyName} · ${shortDate(recap.date)}" +
                if (recap.themes.isNotEmpty()) "  " + recap.themes.take(3).joinToString(" ") { "#$it" } else "",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        PublicRecapPlayer(
            recap = recap,
            active = active && !actions.fullscreenOpen,
            publicUrl = actions.publicUrl,
            onProfileClick = actions.onProfile,
            onLongPress = onLongPress?.let { { it(recap) } },
            onClick = { actions.onOpenFull(recap) },
            muted = actions.muted,
            onToggleMute = actions.onToggleMute,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        Spacer(Modifier.height(10.dp))
        // 구성원 프로필(누르면 프로필 팝업). 색만으로 구분하지 않게 이름도 읽어 준다
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            recap.members.take(8).forEachIndexed { i, member ->
                Avatar(
                    photo = member.photoUrl,
                    size = 40.dp,
                    ringWidth = 2.dp,
                    ringColor = MemberColors[i % MemberColors.size],
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { actions.onProfile(member) }
                        .semantics { contentDescription = "${member.nickname} 님의 프로필 보기" },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val liked = recap.id in actions.liked
            val saved = recap.id in actions.saved
            ActionButton(
                label = "좋아요",
                count = recap.likeCount,
                vector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                selected = liked,
                onClick = { actions.onToggle(MarkKind.LIKE, recap) },
            )
            ActionButton(
                label = "저장",
                count = recap.saveCount,
                drawable = if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark,
                selected = saved,
                onClick = { actions.onToggle(MarkKind.SAVE, recap) },
            )
            ActionButton(label = "장소 보기", vector = Icons.Outlined.Place, onClick = { actions.onPlaces(recap) })
        }
    }
}

/** 영상 아래 글자 버튼(그림 + 글자, 강조 보라) */
@Composable
private fun ActionButton(
    label: String,
    onClick: () -> Unit,
    vector: ImageVector? = null,
    @DrawableRes drawable: Int? = null,
    selected: Boolean? = null,
    /** 좋아요·저장한 사람 수(있으면 글자 옆에 숫자) */
    count: Long? = null,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier.semantics {
            if (selected != null) {
                contentDescription = "$label " + (if (selected) "취소" else "하기") + (count?.let { ", ${it}명" } ?: "")
            }
        },
    ) {
        val tint = LogUsColors.primaryStrong
        if (vector != null) Icon(vector, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        if (drawable != null) Icon(painterResource(drawable), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

/** 탐색 맨 위 [전체] [내 영상] */
@Composable
private fun FeedTabs(selected: FeedTab, onSelect: (FeedTab) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FeedTab.entries.forEach { tab ->
            val on = tab == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (on) MaterialTheme.colorScheme.primary else LogUsColors.card,
                contentColor = if (on) OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                border = if (on) null else BorderStroke(1.dp, LogUsColors.line),
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) }),
            ) {
                Box(contentAlignment = Alignment.Center) { Text(tab.label, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** [내 영상] 위 안내 띠(N01 "길게 누르면 삭제" 안내와 같은 모양) */
@Composable
private fun LongPressHint() {
    Text(
        "ⓘ 내 영상을 꾹 누르면 탐색에서 내릴 수 있어요",
        color = LogUsColors.primaryStrong,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .background(LogUsColors.profileBox, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
    Spacer(Modifier.height(10.dp))
}

/** [‹] [🔍 지역 또는 테마 검색] [🔖] */
@Composable
private fun SearchRow(initial: String, onBack: () -> Unit, onSearch: (String) -> Unit, onOpenCollection: () -> Unit) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    val focus = LocalFocusManager.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircleButton(onClick = onBack, description = "뒤로 가기") {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(8.dp))
        TextField(
            value = text,
            onValueChange = { text = it.take(40) },
            singleLine = true,
            placeholder = { Text("지역 또는 테마 검색", fontSize = 15.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = LogUsColors.primaryStrong) },
            trailingIcon = if (text.isNotEmpty()) {
                { IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Clear, contentDescription = "검색어 지우기") } }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                onSearch(text)
            }),
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = LogUsColors.iconCircle,
                unfocusedContainerColor = LogUsColors.iconCircle,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier
                .weight(1f)
                .border(1.dp, LogUsColors.border, RoundedCornerShape(50)),
        )
        Spacer(Modifier.width(8.dp))
        // 모아보기(좋아요·저장한 영상) — 스토리보드에는 없고 2026-11-08 요청으로 돋보기 옆에 추가
        CircleButton(onClick = onOpenCollection, description = "좋아요·저장한 영상 모아보기") {
            Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null, tint = LogUsColors.primaryStrong)
        }
    }
}

@Composable
private fun CircleButton(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = LogUsColors.card,
        border = BorderStroke(1.dp, LogUsColors.line),
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** 모아보기: [좋아요] [저장] 탭 + 2열 격자(대표 화면 · 여정 이름 · 도시 · 날짜). 누르면 그 영상을 S08 모양으로 */
@Composable
private fun CollectionPage(state: ExploreUiState, viewModel: ExploreViewModel, onBack: () -> Unit) {
    val collection = state.collection
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ExploreLogo()
        RecordTopBar(title = "모아보기", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarkKind.entries.forEach { kind ->
                val selected = kind == collection.kind
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.primary else LogUsColors.card,
                    contentColor = if (selected) OnPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (selected) null else BorderStroke(1.dp, LogUsColors.line),
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .selectable(selected = selected, role = Role.Tab, onClick = { viewModel.loadCollection(kind) }),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(kind.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            collection.loading -> Centered { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            collection.error != null -> Centered {
                ErrorMessage(collection.error)
                TextButton(onClick = { viewModel.loadCollection(collection.kind) }) { Text("다시 시도", color = LogUsColors.primaryStrong) }
            }
            collection.items.isEmpty() -> Centered {
                Text(
                    when (collection.kind) {
                        MarkKind.PLACE -> "아직 코인을 써서 장소를 본 여정이 없어요\n탐색에서 📍 장소 보기를 눌러 보세요"
                        MarkKind.LIKE -> "아직 좋아요한 영상이 없어요\n탐색에서 ♡ 좋아요를 눌러 보세요"
                        MarkKind.SAVE -> "아직 저장한 영상이 없어요\n탐색에서 저장을 눌러 보세요"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(collection.items, key = { it.recapId }) { mark ->
                    MarkCard(
                        mark,
                        viewModel::publicUrl,
                        onClick = {
                            // 장소 보기한 여정은 바로 여정 상세(기록·추억 지도·DAY RECAP), 나머지는 영상 하나 보기
                            if (collection.kind == MarkKind.PLACE) viewModel.openUnlocked(mark.recapId)
                            else viewModel.openViewer(mark.recapId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkCard(mark: RecapMark, publicUrl: (String) -> String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LogUsColors.card)
            .border(1.dp, LogUsColors.border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(Brush.linearGradient(listOf(BackgroundDark, Primary, PrimaryLight))),
        ) {
            if (mark.thumbPath != null) {
                AsyncImage(
                    model = publicUrl(mark.thumbPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                shortDate(mark.date),
                color = PhotoFrame,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(PhotoScrim, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                mark.journeyName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                mark.city.ifEmpty { "도시 정보 없음" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 모아보기에서 고른 영상 하나(S08 과 같은 모양, 뒤로 가기 → 모아보기) */
@Composable
private fun ViewerPage(state: ExploreUiState, actions: RecapActions, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ExploreLogo()
        RecordTopBar(title = state.viewer?.journeyName ?: "모아보기", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        val recap = state.viewer
        when {
            recap != null -> RecapPage(title = "BEST TRAVELER", recap = recap, active = state.unlock == null, actions = actions)
            state.viewerError != null -> Centered { ErrorMessage(state.viewerError) }
            else -> Centered { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
internal fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** "2026-09-24" → "9.24" */
internal fun shortDate(date: String): String {
    val parts = date.split("-")
    if (parts.size != 3) return date
    return "${parts[1].toIntOrNull() ?: parts[1]}.${parts[2].toIntOrNull() ?: parts[2]}"
}
