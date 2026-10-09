package com.logus.app.record

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.logus.app.journey.Nominatim
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** 지도 위의 한 점(위도·경도) */
data class GeoPoint(val lat: Double, val lng: Double)

/**
 * 고른 장소: 핀 좌표 + 오픈스트리트맵이 알려준 장소 이름·주소(L01 → S05).
 * name 은 기록의 placeName 으로 저장한다(출처 © OpenStreetMap 을 화면에 표시).
 */
data class PickedPlace(
    val point: GeoPoint,
    /** 장소 이름(가게·건물 이름, 없으면 도로·번지) */
    val name: String,
    /** 짧은 주소(도로 번지, 도시) */
    val address: String,
)

/**
 * L01 위치 확인 지도의 데이터 담당 (폰 GPS + 오픈스트리트맵 Nominatim)
 * - 현재 위치: 폰 GPS(정확한 위치 권한)로 잡는다. 좌표 (0,0)은 결측으로 본다.
 * - 주소 찾기(reverse): 핀 좌표(소수 다섯째 자리, 약 1m)를 Nominatim 에 보내 장소 이름·주소를 받는다.
 *   지도를 멈춘 뒤에만 묻고(ViewModel), 앱 전체에서 1초에 한 번을 넘지 않는다(Nominatim.kt). 좌표는 기록에만 저장한다.
 * - 도시 중심: GPS 를 못 쓸 때 여정 도시 이름으로 지도 시작 위치를 찾는다.
 * 구글·카카오 장소 데이터는 쓰지 않는다(CLAUDE.md "데이터 원칙").
 */
class PlaceRepository {

    /** 폰의 현재 위치. 못 잡으면 null. 호출 전에 위치 권한을 받아 두어야 한다 */
    @SuppressLint("MissingPermission") // 권한은 위치 확인 팝업(LocationPickerSheet)에서 먼저 확인한다
    suspend fun currentLocation(context: Context, precise: Boolean): GeoPoint? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (precise) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val location = client.getCurrentLocation(priority, CancellationTokenSource().token).await()
            ?: client.lastLocation.await()
            ?: return null
        if (location.latitude == 0.0 && location.longitude == 0.0) return null
        return GeoPoint(location.latitude, location.longitude)
    }

    /** 핀 좌표의 장소 이름·주소(오픈스트리트맵). 아무것도 없으면 이름은 "선택한 위치" */
    suspend fun reverse(point: GeoPoint): PickedPlace {
        val lat = String.format(Locale.US, "%.5f", point.lat)
        val lon = String.format(Locale.US, "%.5f", point.lng)
        // zoom=18: 건물·가게 단위로 찾는다
        val json = JSONObject(Nominatim.get("reverse?lat=$lat&lon=$lon&format=jsonv2&addressdetails=1&zoom=18&accept-language=ko"))
        val address = json.optJSONObject("address")
        fun part(vararg keys: String) =
            keys.firstNotNullOfOrNull { key -> address?.optString(key)?.takeIf { it.isNotBlank() } }

        val street = listOfNotNull(part("road", "pedestrian", "footway", "path"), part("house_number"))
            .joinToString(" ")
            .ifBlank { null }
        val city = part("city", "town", "village", "municipality", "county", "state")
        val shortAddress = listOfNotNull(street, city).joinToString(", ")
            .ifBlank { json.optString("display_name").split(", ").take(3).joinToString(", ") }
        val name = json.optString("name").takeIf { it.isNotBlank() }
            ?: street
            ?: part("neighbourhood", "suburb", "quarter")
            ?: city
            ?: "선택한 위치"
        return PickedPlace(point = point, name = name.take(100), address = shortAddress.take(200))
    }

    /** 여정 도시의 중심 좌표(GPS 를 못 쓸 때 지도 시작 위치). 못 찾으면 null */
    suspend fun cityCenter(city: String): GeoPoint? {
        if (city.isBlank()) return null
        val q = URLEncoder.encode(city.trim(), "UTF-8")
        val array = JSONArray(Nominatim.get("search?q=$q&format=jsonv2&limit=1&accept-language=ko"))
        if (array.length() == 0) return null
        val obj = array.getJSONObject(0)
        val lat = obj.optString("lat").toDoubleOrNull() ?: return null
        val lng = obj.optString("lon").toDoubleOrNull() ?: return null
        return GeoPoint(lat, lng)
    }
}
