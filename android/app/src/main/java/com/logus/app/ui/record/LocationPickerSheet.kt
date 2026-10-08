package com.logus.app.ui.record

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.logus.app.record.GeoPoint
import com.logus.app.record.LocationPickerUiState
import com.logus.app.record.PickedPlace
import com.logus.app.ui.theme.LogUsColors
import com.logus.app.ui.theme.LogUsTheme
import com.logus.app.ui.theme.Success
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/** 지도 그림(OpenFreeMap 무료 타일, 오픈스트리트맵 데이터). 키가 필요 없다 */
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** 처음 보여 줄 지도 확대 정도(골목·가게가 보이는 정도) */
private const val START_ZOOM = 16.0

// L01 위치 확인(지도 팝업)
/**
 * S05 "위치 확인 · 지도 열기"를 누르면 영상 아래부터 화면 맨 아래까지 올라오는 팝업.
 * - 처음 열 때 위치 권한(정확한·대략적인)을 묻고, 폰 GPS 위치로 지도를 연다. 핀은 지도 가운데에 고정되어 있다.
 * - 지도를 끌어 움직이면 핀 자리가 바뀌고, 멈추면 장소 이름·주소를 오픈스트리트맵에서 다시 찾는다.
 * - "GPS 현재 위치"를 누르면 폰 위치로 돌아간다.
 * - "이 위치 사용" → 고른 장소(좌표·이름·주소)를 S05 로 넘기고 팝업을 닫는다. 실제 저장은 S05 "여정에 올리기"에서 한다.
 * - 지도·주소 데이터 출처(© OpenStreetMap)를 표시한다(지도 오른쪽 아래 ⓘ, 주소 아래 글자).
 */
@Composable
fun LocationPickerSheet(
    state: LocationPickerUiState,
    /** 위치 권한을 확인한 뒤 지도를 연다(MainScreen 이 S05 에서 이미 고른 장소와 여정 도시를 함께 넘긴다) */
    onOpen: (context: Context, hasFine: Boolean, hasCoarse: Boolean) -> Unit,
    onClose: () -> Unit,
    onRecenter: () -> Unit,
    onCameraIdle: (GeoPoint) -> Unit,
    onUse: (PickedPlace) -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        onOpen(
            context,
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true || context.granted(Manifest.permission.ACCESS_FINE_LOCATION),
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || context.granted(Manifest.permission.ACCESS_COARSE_LOCATION),
        )
    }
    // 팝업이 뜰 때 한 번: 권한이 있으면 바로 지도를 열고, 없으면 먼저 묻는다
    LaunchedEffect(Unit) {
        val fine = context.granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = context.granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine) {
            onOpen(context, true, coarse)
        } else {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    LocationPickerContent(
        state = state,
        onClose = onClose,
        onRecenter = onRecenter,
        onUse = onUse,
        map = { modifier ->
            OsmMap(
                target = state.cameraTarget,
                moveId = state.moveId,
                onIdle = onCameraIdle,
                modifier = modifier,
            )
        },
    )
}

/** 팝업 그리기(미리보기에서도 쓰려고 지도 자리는 map 으로 받는다) */
@Composable
private fun LocationPickerContent(
    state: LocationPickerUiState,
    onClose: () -> Unit,
    onRecenter: () -> Unit,
    onUse: (PickedPlace) -> Unit,
    map: @Composable (Modifier) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        // 손잡이 모양(팝업이라는 표시)
        Box(
            Modifier
                .padding(top = 10.dp)
                .size(width = 40.dp, height = 4.dp)
                .background(LogUsColors.line, RoundedCornerShape(50))
                .align(Alignment.CenterHorizontally),
        )
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "위치 확인",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "닫기", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))

        // 지도 + 가운데 고정 핀 + "GPS 현재 위치" + "지도를 눌러 옮겨 보세요"
        Box(
            Modifier
                .weight(1f)
                .heightIn(min = 160.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            map(Modifier.fillMaxSize())
            // 핀(끝이 지도 가운데를 가리킨다) + 그림자
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 14.dp, height = 5.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f), CircleShape),
            )
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = "고른 위치(지도 가운데)",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-20).dp)
                    .size(44.dp),
            )
            if (state.gps != null) {
                Surface(
                    onClick = onRecenter,
                    shape = RoundedCornerShape(50),
                    color = LogUsColors.card,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(Success, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("GPS 현재 위치", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Text(
                "지도를 눌러 옮겨 보세요",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(LogUsColors.card, RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            if (state.locating && state.cameraTarget == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(14.dp))

        // 장소 이름·주소
        val place = state.place
        Text(
            when {
                state.resolving || (place == null && state.center != null) -> "주소를 찾는 중…"
                place != null -> place.name
                else -> "현재 위치를 찾는 중…"
            },
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (place != null && !state.resolving) place.address else " ",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            state.notice ?: "핀은 가운데에 고정돼요 · 지도를 움직이면 주소가 따라 바뀝니다",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Text("지도·주소 © OpenStreetMap", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))

        // 이 위치 사용
        Button(
            onClick = { place?.let(onUse) },
            enabled = place != null && !state.resolving,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("이 위치 사용", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * MapLibre 지도(오픈스트리트맵 데이터, OpenFreeMap 타일).
 * - 화면 생명주기(시작·멈춤)에 맞춰 지도를 켜고 끈다(MapView 규칙).
 * - target·moveId 가 바뀌면 그곳으로 부드럽게 옮긴다. 지도가 멈추면 가운데 좌표를 onIdle 로 알린다.
 * - 돌리기·기울이기는 끈다(핀 고르기에 필요 없음).
 */
@Composable
private fun OsmMap(
    target: GeoPoint?,
    moveId: Int,
    onIdle: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        // TextureView 방식: 둥근 모서리 상자 안에 지도가 잘려 보이고, 위에 겹친 버튼·핀과도 잘 섞인다
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, options).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val currentOnIdle by rememberUpdatedState(onIdle)

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer) // 지금 상태(시작·재개)까지의 이벤트가 바로 들어온다
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.setStyle(Style.Builder().fromUri(MAP_STYLE_URL))
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.addOnCameraIdleListener {
                val center = m.cameraPosition.target ?: return@addOnCameraIdleListener
                currentOnIdle(GeoPoint(center.latitude, center.longitude))
            }
            map = m
        }
    }
    LaunchedEffect(map, moveId) {
        val m = map ?: return@LaunchedEffect
        val t = target ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(t.lat, t.lng), START_ZOOM), 600)
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

@Preview(showBackground = true, heightDp = 520)
@Composable
private fun LocationPickerContentPreview() {
    LogUsTheme {
        Surface(color = LogUsColors.card) {
            LocationPickerContent(
                state = LocationPickerUiState(
                    locating = false,
                    gps = GeoPoint(41.14, -8.61),
                    cameraTarget = GeoPoint(41.14, -8.61),
                    center = GeoPoint(41.14, -8.61),
                    place = PickedPlace(GeoPoint(41.14, -8.61), "히베이라 강변 카페", "Rua da Fonte Taurina 12, Porto"),
                ),
                onClose = {},
                onRecenter = {},
                onUse = {},
                map = {},
            )
        }
    }
}
