package com.logus.app.journey

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * 오픈스트리트맵(OSM) 무료 검색 서버 Nominatim 에 묻는 곳(앱 전체가 함께 쓴다).
 * 이용 규칙: 1초에 1번까지, 앱 이름(User-Agent)을 밝힐 것, 글자를 칠 때마다 자동 검색하지 말 것,
 * 화면에 출처(© OpenStreetMap) 표시. 도시 추천(S02)과 위치 확인 지도(L01)가 이 요청 간격을 함께 지킨다.
 */
internal object Nominatim {
    private const val BASE_URL = "https://nominatim.openstreetmap.org"
    private const val USER_AGENT = "LOGUS-Android/1.0 (university capstone; https://github.com/samisking53-web/logus)"
    private const val MIN_INTERVAL_MS = 1_100L

    private val rateLimit = Mutex()
    private var lastRequestAt = 0L

    /** GET 요청. 1초에 한 번을 넘지 않게 앞 요청과 간격을 둔다 */
    suspend fun get(pathAndQuery: String): String = rateLimit.withLock {
        val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            withContext(Dispatchers.IO) {
                val connection = URL("$BASE_URL/$pathAndQuery").openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 8_000
                    connection.setRequestProperty("User-Agent", USER_AGENT)
                    connection.setRequestProperty("Accept-Language", "ko")
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        throw IOException("지도 검색 서버 응답 ${connection.responseCode}")
                    }
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }
        } finally {
            lastRequestAt = System.currentTimeMillis()
        }
    }
}
