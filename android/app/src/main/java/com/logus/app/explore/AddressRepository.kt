package com.logus.app.explore

import com.logus.app.journey.Nominatim
import com.logus.app.record.GeoPoint
import org.json.JSONObject
import java.util.Locale

/** 한 위치의 주소: 넓은 지역(영상 팝업 위 큰 글자) · 상세 주소(추억 지도 번호를 눌렀을 때) */
data class PlaceAddress(val area: String?, val detail: String?)

/**
 * 탐색 공개 여정 상세에서 쓰는 주소 찾기(오픈스트리트맵 Nominatim, 2026-11-08).
 * - 넓은 지역: "포르투 · 히베이라"처럼 도시 · 동네(영상 팝업 위 큰 글자)
 * - 상세 주소: 도로·번지까지(추억 지도의 번호 핀을 누르면)
 * 사람이 영상을 열거나 핀을 누를 때만 묻고(자동 검색 없음), 앱 전체에서 1초에 한 번을 넘지 않는다(Nominatim.kt).
 * 받은 주소는 저장하지 않고 앱을 켜 둔 동안만 기억한다(같은 위치는 다시 묻지 않음). 화면에 "© OpenStreetMap" 표시.
 */
class AddressRepository {
    private val cache = mutableMapOf<String, PlaceAddress>()

    suspend fun lookup(point: GeoPoint): PlaceAddress {
        val lat = String.format(Locale.US, "%.5f", point.lat)
        val lon = String.format(Locale.US, "%.5f", point.lng)
        val key = "$lat,$lon"
        cache[key]?.let { return it }
        val json = JSONObject(Nominatim.get("reverse?lat=$lat&lon=$lon&format=jsonv2&addressdetails=1&zoom=18&accept-language=ko"))
        val address = json.optJSONObject("address")
        fun part(vararg keys: String) =
            keys.firstNotNullOfOrNull { k -> address?.optString(k)?.takeIf { it.isNotBlank() } }

        val city = part("city", "town", "village", "municipality", "county", "state")
        val district = part("suburb", "neighbourhood", "quarter", "city_district", "borough")
        val area = listOfNotNull(city, district?.takeIf { it != city }).joinToString(" · ").ifBlank { null }
        val detail = json.optString("display_name").takeIf { it.isNotBlank() }
        return PlaceAddress(area = area, detail = detail).also { cache[key] = it }
    }
}
