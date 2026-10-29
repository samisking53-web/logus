package com.logus.app.ui.journeydetail

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.logus.app.journeydetail.MemberRoute
import com.logus.app.ui.theme.MemberColors
import com.logus.app.ui.theme.PhotoFrame
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// L01 위치 확인과 같은 지도(오픈스트리트맵 데이터, OpenFreeMap 타일, 키 없음)
internal const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val LINE_SOURCE = "route-lines"
private const val PIN_SOURCE = "route-pins"

/** 핀이 하나뿐일 때 확대 정도(골목이 보이는 정도) */
private const val SINGLE_PIN_ZOOM = 15.0

/**
 * S16 추억 지도: MapLibre 지도 위에 구성원별 경로를 그린다.
 * - 같은 구성원의 기록 위치를 찍은 순서대로 선으로 잇고(구성원 구분색), 각 위치에 번호 핀(1, 2, 3 …)을 찍는다.
 * - 핀 그림은 앱이 직접 그린다(지도 글꼴이 없어도 번호가 보이게).
 * - 처음에는 모든 핀이 한 화면에 들어오게 맞춘다. 손가락으로 옮기고 확대할 수 있다(돌리기·기울이기는 끔).
 * - 화면 생명주기(시작·멈춤)에 맞춰 지도를 켜고 끈다(MapView 규칙, L01 과 같은 방식).
 */
@Composable
fun RouteMap(routes: List<MemberRoute>, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val mapView = rememberLifecycleMapView()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { s ->
                s.addSource(GeoJsonSource(LINE_SOURCE))
                s.addSource(GeoJsonSource(PIN_SOURCE))
                // 선을 먼저, 핀을 위에 그린다. 색·그림 이름은 각 선·핀에 적어 둔 값(color·icon)을 쓴다
                s.addLayer(
                    LineLayer(LINE_SOURCE, LINE_SOURCE).withProperties(
                        lineColor(get("color")),
                        lineWidth(3f),
                        lineCap(Property.LINE_CAP_ROUND),
                        lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                s.addLayer(
                    SymbolLayer(PIN_SOURCE, PIN_SOURCE).withProperties(
                        iconImage(get("icon")),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true),
                    ),
                )
                style = s
            }
            map = m
        }
    }
    // 경로가 바뀌면(다른 날짜) 선·핀을 다시 그리고 모든 핀이 보이게 화면을 맞춘다
    LaunchedEffect(style, routes) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val lines = mutableListOf<Feature>()
        val pins = mutableListOf<Feature>()
        routes.forEach { route ->
            val color = MemberColors[route.colorIndex % MemberColors.size]
            val points = route.points.map { Point.fromLngLat(it.lng, it.lat) }
            if (points.size >= 2) {
                lines += Feature.fromGeometry(LineString.fromLngLats(points)).apply {
                    addStringProperty("color", "#%06X".format(0xFFFFFF and color.toArgb()))
                }
            }
            points.forEachIndexed { i, point ->
                val icon = "pin-${route.colorIndex % MemberColors.size}-${i + 1}"
                if (s.getImage(icon) == null) s.addImage(icon, pinBitmap(color.toArgb(), i + 1, density))
                pins += Feature.fromGeometry(point).apply { addStringProperty("icon", icon) }
            }
        }
        s.getSourceAs<GeoJsonSource>(LINE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(lines))
        s.getSourceAs<GeoJsonSource>(PIN_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(pins))

        val all = routes.flatMap { r -> r.points.map { LatLng(it.lat, it.lng) } }.distinct()
        when {
            all.isEmpty() -> Unit
            all.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(all.first(), SINGLE_PIN_ZOOM))
            else -> {
                val bounds = LatLngBounds.Builder().includes(all).build()
                val padding = (48 * density).toInt() // 가장자리 핀이 잘리지 않게
                // 지도 크기가 정해진 뒤에 맞춘다
                mapView.post { m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding)) }
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/**
 * 화면 생명주기(시작·멈춤)에 맞춰 켜고 끄는 MapLibre 지도(MapView 규칙, L01 과 같은 방식). 화면을 떠나면 정리한다.
 * TextureView 방식이라 둥근 모서리 상자 안에 지도가 잘려 보인다. S16 추억 지도·S17 DAY RECAP 지도 장면이 함께 쓴다.
 */
@Composable
internal fun rememberLifecycleMapView(): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, options).apply { onCreate(null) }
    }
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
    return mapView
}

/** 번호 핀 그림: 구성원 색 동그라미 + 흰 테두리 + 흰 숫자(26dp) */
private fun pinBitmap(color: Int, number: Int, density: Float): Bitmap {
    val size = (26 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = 2.5f * density
    val white = PhotoFrame.toArgb()
    val center = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = white
    canvas.drawCircle(center, center, center, paint)
    paint.color = color
    canvas.drawCircle(center, center, center - stroke, paint)
    paint.color = white
    paint.textSize = 12 * density
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val baseline = center - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(number.toString(), center, baseline, paint)
    return bitmap
}
