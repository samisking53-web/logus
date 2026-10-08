package com.logus.app.record

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** L01 위치 확인 지도 상태 */
data class LocationPickerUiState(
    /** 폰 GPS 로 현재 위치를 찾는 중 */
    val locating: Boolean = true,
    /** 폰의 현재 위치(못 찾으면 null). "GPS 현재 위치" 버튼으로 돌아갈 곳 */
    val gps: GeoPoint? = null,
    /** 지도를 옮길 곳. moveId 가 바뀔 때마다 지도가 이곳으로 움직인다 */
    val cameraTarget: GeoPoint? = null,
    val moveId: Int = 0,
    /** 지금 지도 가운데(핀이 가리키는 곳) */
    val center: GeoPoint? = null,
    /** 핀 자리의 장소 이름·주소(오픈스트리트맵) */
    val place: PickedPlace? = null,
    /** 주소를 찾는 중 */
    val resolving: Boolean = false,
    /** 안내(위치 권한 없음·현재 위치 못 찾음 등) */
    val notice: String? = null,
)

/**
 * L01 위치 확인 지도: 폰 GPS 로 지도를 열고, 지도를 움직여 멈추면 가운데 핀 자리의 장소 이름·주소를 다시 찾는다.
 * - 주소는 지도를 멈추고 0.6초 뒤에 묻는다(움직이는 동안은 묻지 않음). 오픈스트리트맵 요청은 앱 전체에서 1초에 한 번 이하.
 * - GPS 를 못 쓰면 여정 도시 가운데(없으면 서울)에서 시작하고, 지도를 움직여 고르게 한다.
 */
class LocationPickerViewModel(
    private val repository: PlaceRepository = PlaceRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(LocationPickerUiState())
    val state: StateFlow<LocationPickerUiState> = _state.asStateFlow()

    private var reverseJob: Job? = null

    /**
     * 팝업을 열 때(위치 권한을 물은 뒤).
     * initial: S05 에서 이미 고른 장소가 있으면 그곳에서 시작한다.
     * hasFine·hasCoarse: 정확한·대략적인 위치 권한이 있는지. journeyCity: GPS 를 못 쓸 때 시작할 도시.
     */
    fun open(context: Context, initial: PickedPlace?, hasFine: Boolean, hasCoarse: Boolean, journeyCity: String) {
        reverseJob?.cancel()
        _state.value = if (initial != null) {
            LocationPickerUiState(locating = true, cameraTarget = initial.point, moveId = 1, center = initial.point, place = initial)
        } else {
            LocationPickerUiState(locating = true)
        }
        val appContext = context.applicationContext
        viewModelScope.launch {
            val gps = if (hasFine || hasCoarse) {
                runCatching { repository.currentLocation(appContext, precise = hasFine) }
                    .onFailure { Log.w(TAG, "현재 위치 찾기 실패", it) }
                    .getOrNull()
            } else {
                null
            }
            if (gps != null) {
                _state.update {
                    if (initial == null) {
                        it.copy(locating = false, gps = gps, cameraTarget = gps, moveId = it.moveId + 1)
                    } else {
                        it.copy(locating = false, gps = gps)
                    }
                }
                return@launch
            }
            // GPS 를 못 썼다: 이미 고른 곳이 있으면 그대로, 없으면 여정 도시(없으면 서울)에서 시작
            val notice = if (hasFine || hasCoarse) {
                "현재 위치를 찾지 못했어요. 지도를 움직여 위치를 골라 주세요."
            } else {
                "위치 권한이 없어 현재 위치를 쓸 수 없어요. 지도를 움직여 위치를 골라 주세요."
            }
            if (initial != null) {
                _state.update { it.copy(locating = false, notice = notice) }
                return@launch
            }
            val start = runCatching { repository.cityCenter(journeyCity) }.getOrNull() ?: SEOUL
            _state.update { it.copy(locating = false, notice = notice, cameraTarget = start, moveId = it.moveId + 1) }
        }
    }

    /** "GPS 현재 위치": 폰의 현재 위치로 지도를 옮긴다 */
    fun recenterToGps() {
        val gps = _state.value.gps ?: return
        _state.update { it.copy(cameraTarget = gps, moveId = it.moveId + 1) }
    }

    /** 지도가 멈췄을 때: 가운데(핀) 좌표로 장소 이름·주소를 다시 찾는다 */
    fun onCameraIdle(point: GeoPoint) {
        // 지도가 처음 켜질 때의 (0,0)이나 아직 시작 위치를 정하기 전의 멈춤은 무시한다
        if (_state.value.cameraTarget == null || (abs(point.lat) < 1e-6 && abs(point.lng) < 1e-6)) return
        val current = _state.value
        if (current.place != null && current.place.point.closeTo(point) && !current.resolving) {
            _state.update { it.copy(center = point) }
            return
        }
        _state.update { it.copy(center = point, resolving = true) }
        reverseJob?.cancel()
        reverseJob = viewModelScope.launch {
            delay(REVERSE_DELAY_MS)
            val place = try {
                repository.reverse(point)
            } catch (e: Exception) {
                Log.w(TAG, "주소 찾기 실패", e)
                PickedPlace(point = point, name = "선택한 위치", address = "주소를 찾지 못했어요. 인터넷 연결을 확인해 주세요.")
            }
            _state.update { it.copy(place = place, resolving = false) }
        }
    }

    private fun GeoPoint.closeTo(other: GeoPoint) = abs(lat - other.lat) < 1e-5 && abs(lng - other.lng) < 1e-5

    private companion object {
        const val TAG = "LocationPickerViewModel"
        const val REVERSE_DELAY_MS = 600L

        /** GPS 도 여정 도시도 못 찾을 때 시작 위치(서울시청) */
        val SEOUL = GeoPoint(37.5665, 126.9780)
    }
}
