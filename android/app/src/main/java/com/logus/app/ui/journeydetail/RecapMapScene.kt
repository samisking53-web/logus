package com.logus.app.ui.journeydetail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.logus.app.journey.Member
import com.logus.app.journeydetail.RecapMapScene
import com.logus.app.journeydetail.RecapPin
import com.logus.app.journeydetail.distanceMeters
import com.logus.app.record.GeoPoint
import com.logus.app.ui.components.Avatar
import com.logus.app.ui.theme.MemberColors
import com.logus.app.ui.theme.PhotoFrame
import com.logus.app.ui.theme.PhotoScrim
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.coroutines.resume

/** 첫 지도 장면: 조금 멀리서 그 시간대 위치로 확대하는 시간 + 머무는 시간(밀리초) */
private const val FIRST_ZOOM_MS = 1_200
private const val FIRST_HOLD_MS = 800L

/** 다음 시간대로: 각자 이동하는 시간 → 도착한 곳으로 확대하는 시간 → 머무는 시간(밀리초) */
private const val MOVE_MS = 1_600
private const val ARRIVE_ZOOM_MS = 800
private const val ARRIVE_HOLD_MS = 600L

/** 지도 장면에서 가장 크게 확대하는 정도(같은 곳에 다 모여 있을 때, 골목이 보이는 정도) */
private const val MAX_ZOOM = 16.0

/** 지도 장면 한 개가 걸리는 시간(밀리초). 장면 진행 막대에 쓴다 */
internal fun mapSceneMillis(scene: RecapMapScene): Long =
    if (scene.fromMinute == null) FIRST_ZOOM_MS + FIRST_HOLD_MS else MOVE_MS + ARRIVE_ZOOM_MS + ARRIVE_HOLD_MS

/**
 * S17 DAY RECAP 2단계 지도 장면을 움직이는 리모컨. 재생기(RecapPlayer)가 장면 차례에 맞춰
 * show(멈춘 화면: 도착한 모습) · play(움직이는 화면)를 부르고, RecapMapView 가 이 상태를 보고 핀을 그린다.
 */
@Stable
class RecapMapController internal constructor(private val paddingPx: Int) {
    internal var map: MapLibreMap? = null

    /** 지도 그림(스타일)을 다 받아 카메라를 움직일 수 있는지 */
    internal var ready by mutableStateOf(false)

    /** 지금 보여 주는 지도 장면 */
    var scene by mutableStateOf<RecapMapScene?>(null)
        private set

    /** 이동 진행(0 = 출발 위치, 1 = 도착 위치) */
    internal var fraction by mutableFloatStateOf(1f)
        private set

    /** 도착했는지(도착하면 같은 건물 사람들을 말풍선 하나로 묶어 보여 준다) */
    internal var arrived by mutableStateOf(true)
        private set

    /** 지도 카메라가 움직일 때마다 바뀌는 값(핀 위치를 다시 계산하라는 신호) */
    internal var cameraTick by mutableIntStateOf(0)

    /** 멈춘 화면: 도착한 모습을 바로 보여 준다 */
    suspend fun show(scene: RecapMapScene) {
        this.scene = scene
        fraction = 1f
        arrived = true
        val m = awaitMap() ?: return
        m.cancelTransitions()
        cameraFor(m, scene.pins.mapNotNull { it.to })?.let { m.moveCamera(CameraUpdateFactory.newCameraPosition(it)) }
    }

    /** 움직이는 화면: 첫 장면은 확대, 그다음부터는 각자 이동 → 도착한 곳으로 확대. 다 끝나면 돌아온다 */
    suspend fun play(scene: RecapMapScene) {
        this.scene = scene
        val m = awaitMap()
        val arrivals = scene.pins.mapNotNull { it.to }
        if (scene.fromMinute == null) {
            fraction = 1f
            arrived = true
            val target = m?.let { cameraFor(it, arrivals) }
            if (m != null && target != null) {
                m.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(target).zoom(target.zoom - 2).build()))
                m.animateTo(target, FIRST_ZOOM_MS)
            } else {
                delay(FIRST_ZOOM_MS.toLong())
            }
            delay(FIRST_HOLD_MS)
        } else {
            fraction = 0f
            arrived = false
            coroutineScope {
                // 출발·도착이 모두 보이게 지도를 옮기면서, 핀은 각자 자기 길을 따라 움직인다
                val all = scene.pins.flatMap { listOfNotNull(it.from, it.to) }
                if (m != null) launch { cameraFor(m, all)?.let { m.animateTo(it, MOVE_MS) } }
                animate(0f, 1f, animationSpec = tween(MOVE_MS, easing = FastOutSlowInEasing)) { v, _ -> fraction = v }
            }
            arrived = true
            val target = m?.let { cameraFor(it, arrivals) }
            if (m != null && target != null) m.animateTo(target, ARRIVE_ZOOM_MS) else delay(ARRIVE_ZOOM_MS.toLong())
            delay(ARRIVE_HOLD_MS)
        }
    }

    /** 지도가 준비될 때까지 잠깐 기다린다(인터넷이 느려 지도를 못 받으면 null: 핀 없이 시간만 흐른다) */
    private suspend fun awaitMap(): MapLibreMap? {
        withTimeoutOrNull(5_000) { snapshotFlow { ready }.first { it } }
        return map.takeIf { ready }
    }

    /** 점들이 모두 보이는 카메라 위치(한 곳에 모여 있으면 가운데를 크게) */
    private fun cameraFor(m: MapLibreMap, points: List<GeoPoint>): CameraPosition? {
        if (points.isEmpty()) return null
        val first = points.first()
        if (points.all { distanceMeters(first, it) < 30.0 }) {
            return CameraPosition.Builder().target(LatLng(first.lat, first.lng)).zoom(MAX_ZOOM).build()
        }
        val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.lat, it.lng) }).build()
        val fit = m.getCameraForLatLngBounds(bounds, IntArray(4) { paddingPx }) ?: return null
        return if (fit.zoom > MAX_ZOOM) CameraPosition.Builder(fit).zoom(MAX_ZOOM).build() else fit
    }
}

/** 지도 카메라를 정해진 시간 동안 부드럽게 옮기고, 다 옮기면(또는 중간에 멈추면) 돌아온다 */
private suspend fun MapLibreMap.animateTo(position: CameraPosition, millis: Int) =
    suspendCancellableCoroutine { cont ->
        animateCamera(
            CameraUpdateFactory.newCameraPosition(position),
            millis,
            object : MapLibreMap.CancelableCallback {
                override fun onCancel() {
                    if (cont.isActive) cont.resume(Unit)
                }

                override fun onFinish() {
                    if (cont.isActive) cont.resume(Unit)
                }
            },
        )
        cont.invokeOnCancellation { cancelTransitions() }
    }

@Composable
fun rememberRecapMapController(): RecapMapController {
    val padding = with(LocalDensity.current) { 56.dp.roundToPx() } // 가장자리 핀이 잘리지 않게
    return remember { RecapMapController(padding) }
}

/**
 * S17 DAY RECAP 2단계 지도 장면 화면: MapLibre 지도(추억 지도와 같은 OpenFreeMap 타일, 손으로 움직이지 않음) 위에
 * 구성원 프로필 핀(구성원 색 테두리 + 이름 첫 글자)을 얹는다.
 * - 이동 중: 사람마다 따로, 출발 위치에서 지금 위치까지 점선. 새로 나타나는 사람은 서서히 나타나고, 위치가 없어진 사람은 흐려지며 사라진다.
 * - 도착하면: 같은 건물(50m 안)인 사람들은 흰 말풍선 하나에 프로필을 묶는다.
 */
@Composable
fun RecapMapView(controller: RecapMapController, members: Map<String, Member>, modifier: Modifier = Modifier) {
    val mapView = rememberLifecycleMapView()
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.setAllGesturesEnabled(false)
            m.uiSettings.isCompassEnabled = false
            mapView.addOnCameraIsChangingListener { controller.cameraTick++ }
            mapView.addOnCameraDidChangeListener { controller.cameraTick++ }
            m.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) {
                controller.map = m
                // 지도 크기가 정해진 뒤에 카메라를 맞추도록 한 박자 늦게 알린다
                mapView.post { controller.ready = true }
            }
        }
    }
    DisposableEffect(mapView) {
        onDispose {
            controller.ready = false
            controller.map = null
        }
    }
    val scene = controller.scene
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clearAndSetSemantics { contentDescription = scene?.let { describe(it, members) } ?: "지도" },
    ) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (scene != null) PinsOverlay(controller, scene, members)
        Text(
            "© OpenStreetMap",
            color = PhotoFrame,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .background(PhotoScrim, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PinsOverlay(controller: RecapMapController, scene: RecapMapScene, members: Map<String, Member>) {
    // 좌표 → 지도 위 화면 위치(픽셀). 카메라가 움직일 때마다 다시 계산한다
    fun screen(point: GeoPoint): Offset? {
        controller.cameraTick // 읽어 두면 카메라가 움직일 때 다시 그린다
        val p = controller.map?.projection?.toScreenLocation(LatLng(point.lat, point.lng)) ?: return null
        return Offset(p.x, p.y)
    }

    val density = LocalDensity.current
    if (!controller.arrived) {
        // 출발 위치에서 지금 위치까지 구성원 색 점선(각자의 길)
        Canvas(Modifier.fillMaxSize()) {
            scene.pins.forEach { pin ->
                val from = pin.from ?: return@forEach
                val to = pin.to ?: return@forEach
                val a = screen(from) ?: return@forEach
                val b = screen(lerp(from, to, controller.fraction)) ?: return@forEach
                drawLine(
                    color = MemberColors[pin.colorIndex % MemberColors.size],
                    start = a,
                    end = b,
                    strokeWidth = with(density) { 3.dp.toPx() },
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(with(density) { 2.dp.toPx() }, with(density) { 7.dp.toPx() })),
                )
            }
        }
        scene.pins.forEach { pin ->
            val point = when {
                pin.from == null -> pin.to
                pin.to == null -> pin.from
                else -> lerp(pin.from, pin.to, controller.fraction)
            } ?: return@forEach
            val alpha = when {
                pin.from == null -> controller.fraction
                pin.to == null -> 1f - controller.fraction
                else -> 1f
            }
            Box(Modifier.centerAt { screen(point) }.alpha(alpha)) { ProfilePin(pin, members[pin.uid]) }
        }
    } else {
        val byUid = scene.pins.associateBy { it.uid }
        scene.groups.forEach { uids ->
            val pins = uids.mapNotNull { byUid[it] }
            val points = pins.mapNotNull { it.to }
            if (points.isEmpty()) return@forEach
            val center = GeoPoint(points.map { it.lat }.average(), points.map { it.lng }.average())
            Box(Modifier.centerAt { screen(center) }) {
                if (pins.size == 1) {
                    ProfilePin(pins.first(), members[pins.first().uid])
                } else {
                    // 같은 건물: 흰 말풍선 하나에 프로필을 겹쳐 묶는다
                    Row(
                        Modifier
                            .background(PhotoFrame, RoundedCornerShape(24.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy((-8).dp),
                    ) {
                        pins.forEach { MemberFace(it, members[it.uid]) }
                    }
                }
            }
        }
    }
}

/** 혼자 있는 사람의 핀: 흰 테두리 동그라미 안 프로필 */
@Composable
private fun ProfilePin(pin: RecapPin, member: Member?) {
    Box(
        Modifier
            .background(PhotoFrame, CircleShape)
            .padding(3.dp),
    ) { MemberFace(pin, member) }
}

/** 프로필 사진(구성원 색 테두리) + 오른쪽 아래 이름 첫 글자(색만으로 구분하지 않게) */
@Composable
private fun MemberFace(pin: RecapPin, member: Member?) {
    val color = MemberColors[pin.colorIndex % MemberColors.size]
    Box(Modifier.size(38.dp)) {
        Avatar(photo = member?.photoUrl, size = 34.dp, ringWidth = 3.dp, ringColor = color)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(17.dp)
                .background(PhotoFrame, CircleShape)
                .padding(1.5.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                member?.nickname?.firstOrNull()?.toString() ?: "?",
                color = PhotoFrame,
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 내용의 가운데를 지도 위 화면 위치에 둔다(위치를 모르면 그리지 않는다). 위치는 그릴 때마다 다시 읽는다 */
private fun Modifier.centerAt(position: () -> Offset?): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) {
        val p = position() ?: return@layout
        placeable.place(p.x.toInt() - placeable.width / 2, p.y.toInt() - placeable.height / 2)
    }
}

private fun lerp(a: GeoPoint, b: GeoPoint, t: Float) =
    GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)

/** 화면 읽기 도우미용 설명: "13:00 지도. 함께 있음: 나, 성연. 따로: 지민" */
private fun describe(scene: RecapMapScene, members: Map<String, Member>): String {
    val name = { uid: String -> members[uid]?.nickname ?: "구성원" }
    val parts = scene.groups.map { g -> if (g.size > 1) "함께 있음: ${g.joinToString(", ") { name(it) }}" else name(g.first()) }
    return "구성원 위치 지도. " + parts.joinToString(". ")
}
