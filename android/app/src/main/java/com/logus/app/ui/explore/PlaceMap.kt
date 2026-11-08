package com.logus.app.ui.explore

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.logus.app.record.GeoPoint
import com.logus.app.ui.journeydetail.MAP_STYLE_URL
import com.logus.app.ui.journeydetail.rememberLifecycleMapView
import com.logus.app.ui.theme.MemberColors
import com.logus.app.ui.theme.PhotoFrame
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
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

private const val LINE_SOURCE = "place-lines"
private const val PIN_SOURCE = "place-pins"
private const val SINGLE_PIN_ZOOM = 15.0

/** 지도 위 번호 핀 하나(구성원마다 찍은 순서 1, 2, 3 …) */
data class PlacePin(val key: String, val uid: String, val colorIndex: Int, val number: Int, val point: GeoPoint)

/**
 * 탐색 공개 여정 상세의 추억 지도(마이로그 S16 RouteMap 과 같은 지도·핀 모양, 2026-11-08).
 * 다른 점: 번호 핀을 누르면 onPinClick(핀 key) 을 불러 그 위치의 상세 주소를 보여 줄 수 있다. 고른 핀은 크게 그린다.
 * 같은 구성원의 핀은 찍은 순서대로 선으로 잇는다. 처음에는 모든 핀이 보이게 맞춘다(돌리기·기울이기 끔).
 */
@Composable
fun PlaceMap(pins: List<PlacePin>, selectedKey: String?, onPinClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val mapView = rememberLifecycleMapView()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val clickHandler by rememberUpdatedState(onPinClick)

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { s ->
                s.addSource(GeoJsonSource(LINE_SOURCE))
                s.addSource(GeoJsonSource(PIN_SOURCE))
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
            // 핀을 누르면(핀 둘레 손가락 크기만큼 넓혀서 찾는다)
            m.addOnMapClickListener { latLng ->
                val p = m.projection.toScreenLocation(latLng)
                val r = 18 * density
                val hit = m.queryRenderedFeatures(
                    android.graphics.RectF(p.x - r, p.y - r, p.x + r, p.y + r),
                    PIN_SOURCE,
                ).firstOrNull()
                val key = hit?.getStringProperty("key")
                if (key != null) clickHandler(key)
                key != null
            }
            map = m
        }
    }

    LaunchedEffect(style, pins, selectedKey) {
        val s = style ?: return@LaunchedEffect
        val lines = pins.groupBy { it.uid }.values.filter { it.size >= 2 }.map { group ->
            Feature.fromGeometry(LineString.fromLngLats(group.map { Point.fromLngLat(it.point.lng, it.point.lat) })).apply {
                addStringProperty("color", colorHex(group.first().colorIndex))
            }
        }
        val pinFeatures = pins.map { pin ->
            val selected = pin.key == selectedKey
            val icon = "pin_${pin.colorIndex % MemberColors.size}_${pin.number}_${if (selected) "on" else "off"}"
            if (s.getImage(icon) == null) {
                s.addImage(icon, pinBitmap(MemberColors[pin.colorIndex % MemberColors.size].toArgb(), pin.number, density, selected))
            }
            Feature.fromGeometry(Point.fromLngLat(pin.point.lng, pin.point.lat)).apply {
                addStringProperty("icon", icon)
                addStringProperty("key", pin.key)
            }
        }
        (s.getSource(LINE_SOURCE) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(lines))
        (s.getSource(PIN_SOURCE) as? GeoJsonSource)?.setGeoJson(FeatureCollection.fromFeatures(pinFeatures))
    }

    // 처음에(핀 목록이 바뀔 때만) 모든 핀이 보이게 맞춘다. 핀을 골라도 카메라는 그대로 둔다
    LaunchedEffect(map, pins) {
        val m = map ?: return@LaunchedEffect
        val all = pins.map { LatLng(it.point.lat, it.point.lng) }.distinct()
        when {
            all.isEmpty() -> Unit
            all.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(all.first(), SINGLE_PIN_ZOOM))
            else -> {
                val bounds = LatLngBounds.Builder().includes(all).build()
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, (48 * density).toInt()))
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun colorHex(index: Int): String = String.format("#%06X", 0xFFFFFF and MemberColors[index % MemberColors.size].toArgb())

/** 번호 핀 그림: 구성원 색 동그라미 + 흰 테두리 + 흰 숫자. 고른 핀은 더 크게 */
private fun pinBitmap(color: Int, number: Int, density: Float, selected: Boolean): Bitmap {
    val dp = if (selected) 36 else 26
    val size = (dp * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = (if (selected) 3.5f else 2.5f) * density
    val white = PhotoFrame.toArgb()
    val center = size / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = white
    canvas.drawCircle(center, center, center, paint)
    paint.color = color
    canvas.drawCircle(center, center, center - stroke, paint)
    paint.color = white
    paint.textSize = (if (selected) 15 else 12) * density
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val baseline = center - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(number.toString(), center, baseline, paint)
    return bitmap
}
