package com.logus.app.journey

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * 새 여정의 도시 추천 (지도 데이터: 오픈스트리트맵 OSM)
 * - 현재 위치: 폰 위치(대략) → OSM 에서 그 위치의 도시 이름을 찾는다.
 * - 지도에서 찾기: 입력한 글자로 OSM 에서 세계 도시를 찾는다(한국어 이름이 있으면 한국어로).
 *
 * 왜 OSM 인가: 우리 지도(OpenFreeMap)도 OSM 데이터라 같고, 구글·카카오 장소 결과는 저장할 수 없다(CLAUDE.md "데이터 원칙").
 * 안드로이드 기본 주소 변환(Geocoder)은 대부분 폰에서 구글 데이터를 쓰므로 쓰지 않는다.
 *
 * OSM 무료 검색 서버(Nominatim) 이용 규칙: 1초에 1번까지, 앱 이름(User-Agent)을 밝힐 것,
 * 글자를 칠 때마다 자동으로 검색하지 말 것(버튼을 눌렀을 때만), 화면에 출처(© OpenStreetMap) 표시.
 * 좌표는 도시를 찾는 데만 쓰고 저장하지 않으며, 보낼 때도 소수 둘째 자리(약 1km)로 줄인다.
 */
class CityRepository {

    /** OSM 지도에서 도시 찾기(최대 6곳) */
    suspend fun searchMap(query: String): List<City> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val json = request("search?q=$q&format=jsonv2&addressdetails=1&featureType=city&limit=8&accept-language=ko")
        val array = JSONArray(json)
        return (0 until array.length())
            .mapNotNull { toCity(array.getJSONObject(it)) }
            .distinctBy { it.name to it.country }
            .take(6)
    }

    /**
     * 현재 위치의 도시. 위치를 못 잡으면 null.
     * 호출 전에 위치 권한(대략적인 위치)을 받아 두어야 한다.
     */
    @SuppressLint("MissingPermission") // 권한은 NewJourneyFlow 에서 먼저 확인한다
    suspend fun currentCity(context: Context): City? {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val location = client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
            .await()
            ?: client.lastLocation.await()
            ?: return null
        // 좌표 (0,0)은 결측으로 본다(CLAUDE.md "데이터 원칙")
        if (location.latitude == 0.0 && location.longitude == 0.0) return null
        val lat = String.format(Locale.US, "%.2f", location.latitude)
        val lon = String.format(Locale.US, "%.2f", location.longitude)
        // zoom=10: 도시 단위로 찾는다
        val json = request("reverse?lat=$lat&lon=$lon&format=jsonv2&addressdetails=1&zoom=10&accept-language=ko")
        return toCity(JSONObject(json))
    }

    /** OSM 결과 한 건 → City. 이름이 없으면 null */
    private fun toCity(obj: JSONObject): City? {
        val address = obj.optJSONObject("address")
        val name = obj.optString("name").takeIf { it.isNotBlank() }
            ?: listOf("city", "town", "village", "municipality", "county")
                .firstNotNullOfOrNull { key -> address?.optString(key)?.takeIf { it.isNotBlank() } }
            ?: return null
        val country = address?.optString("country").orEmpty()
        return City(name = name.take(60), country = country.take(60), english = "", fromMap = true)
    }

    /** Nominatim 에 GET 요청(앱 전체에서 1초에 한 번 이하, Nominatim.kt) */
    private suspend fun request(pathAndQuery: String): String = Nominatim.get(pathAndQuery)
}
