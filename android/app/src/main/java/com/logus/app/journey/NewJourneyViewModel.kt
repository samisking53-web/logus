package com.logus.app.journey

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 현재 위치로 도시 찾기 진행 상태 */
enum class LocationStatus {
    IDLE, // 아직 시도 안 함
    LOCATING, // 위치·도시를 찾는 중
    FOUND, // 찾음(currentCity)
    DENIED, // 위치 권한을 허용하지 않음
    FAILED, // 위치를 못 잡았거나 인터넷 문제
}

/** 새 여정 만들기 안에서 지금 보이는 화면 */
enum class NewJourneyStep {
    FORM, // S02 새 여정 만들기
    CALENDAR, // S03 여행 기간 선택
}

/** S02·S03 입력 상태 */
data class NewJourneyUiState(
    val step: NewJourneyStep = NewJourneyStep.FORM,
    val name: String = "",
    /** 도시 입력칸 글자 */
    val cityQuery: String = "",
    /** 추천 목록에서 고른 도시(고르지 않고 직접 입력만 했으면 null) */
    val selectedCity: City? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /** 촬영 알림 간격(1·2·3시간) */
    val notifyHours: Int = 2,
    /** "촬영 알림 받지 않기"를 골랐는지 */
    val notifyOff: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    /** 현재 위치의 도시(GPS + 지도 데이터) */
    val currentCity: City? = null,
    val locationStatus: LocationStatus = LocationStatus.IDLE,
    /** 이번에 화면을 열고 위치 권한을 이미 물어봤는지(달력에 다녀와도 다시 묻지 않게) */
    val locationAsked: Boolean = false,
    /** 지도에서 찾은 글자와 결과 */
    val mapQuery: String? = null,
    val mapResults: List<City> = emptyList(),
    val mapSearching: Boolean = false,
    val mapError: String? = null,
    /** 이미 만든 여정 ID(친구 초대하기로 먼저 만들었으면 저장 때 다시 만들지 않는다) */
    val createdJourneyId: String? = null,
    /** 서버가 만든 6자리 초대 코드 */
    val inviteCode: String? = null,
    /** 초대 코드 팝업을 보여 주는 중인지 */
    val showInviteDialog: Boolean = false,
    /** 친구 초대하기를 눌렀는데 아직 다 채우지 않았을 때 버튼 위에 보여 주는 안내 */
    val inviteHint: String? = null,
) {
    /** 지도 검색 결과가 지금 입력한 글자에 대한 것인지 */
    val showMapResults: Boolean
        get() = mapQuery != null && mapQuery == cityQuery.trim()

    /** 도시 추천 목록(이미 고른 도시와 입력이 같으면 다시 보여 주지 않는다) */
    val suggestions: List<City>
        get() = if (selectedCity != null && selectedCity.name == cityQuery.trim()) emptyList() else Cities.search(cityQuery)

    /** 이름 1~40자, 도시 1~60자, 기간 선택까지 모두 채웠는지 */
    val isComplete: Boolean
        get() = name.trim().length in 1..NAME_MAX &&
            cityQuery.trim().length in 1..CITY_MAX &&
            startDate != null && endDate != null

    /** 저장할 수 있는지 */
    val canSave: Boolean
        get() = isComplete && !saving

    companion object {
        const val NAME_MAX = 40 // 서버 함수 createJourney 의 제한과 같다
        const val CITY_MAX = 60
    }
}

/**
 * S02 새 여정 만들기·S03 여행 기간 달력 상태 관리.
 * 저장하면 서버 함수 createJourney 가 여정을 만든다. 만든 뒤 홈으로 돌아가면, 시작일이 오늘인 여정은
 * 홈이 폰 카메라를 연다(미래 여정은 시작일에 앱을 열 때 연다. ui/MainScreen.kt 참고).
 */
class NewJourneyViewModel(
    private val repository: JourneyRepository = JourneyRepository(),
    private val cityRepository: CityRepository = CityRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(NewJourneyUiState())
    val state: StateFlow<NewJourneyUiState> = _state.asStateFlow()

    /** 새로 만들기를 시작할 때 이전 입력을 지운다 */
    fun reset() {
        _state.value = NewJourneyUiState()
    }

    fun updateName(value: String) = update {
        if (value.length <= NewJourneyUiState.NAME_MAX) it.copy(name = value, error = null, inviteHint = null) else it
    }

    /** 도시 입력: 글자를 바꾸면 고른 도시는 풀린다(직접 입력한 글자 그대로 저장할 수도 있다) */
    fun updateCityQuery(value: String) = update {
        if (value.length <= NewJourneyUiState.CITY_MAX) {
            it.copy(cityQuery = value, selectedCity = it.selectedCity?.takeIf { c -> c.name == value.trim() }, error = null)
        } else {
            it
        }
    }

    fun selectCity(city: City) = update { it.copy(cityQuery = city.name, selectedCity = city, error = null, inviteHint = null) }

    fun clearCity() = update { it.copy(cityQuery = "", selectedCity = null) }

    // ---------- 현재 위치로 도시 찾기 ----------

    fun markLocationAsked() = update { it.copy(locationAsked = true) }

    fun onLocationDenied() = update { it.copy(locationAsked = true, locationStatus = LocationStatus.DENIED) }

    /** 위치 권한을 받은 뒤 부른다: 현재 위치 → 지도 데이터에서 도시 이름 */
    fun locate(context: Context) {
        val current = _state.value
        if (current.locationStatus == LocationStatus.LOCATING) return
        _state.value = current.copy(locationAsked = true, locationStatus = LocationStatus.LOCATING)
        val appContext = context.applicationContext
        viewModelScope.launch {
            val city = try {
                cityRepository.currentCity(appContext)
            } catch (e: Exception) {
                Log.w(TAG, "현재 위치의 도시 찾기 실패", e)
                null
            }
            _state.value = _state.value.copy(
                currentCity = city,
                locationStatus = if (city != null) LocationStatus.FOUND else LocationStatus.FAILED,
            )
        }
    }

    // ---------- 지도에서 찾기 ----------

    /** "지도에서 찾기" 버튼: 입력한 글자로 OSM 지도 데이터에서 도시를 찾는다(버튼을 누를 때만 검색) */
    fun searchMap() {
        val current = _state.value
        val query = current.cityQuery.trim()
        if (query.isEmpty() || current.mapSearching) return
        _state.value = current.copy(mapSearching = true, mapError = null)
        viewModelScope.launch {
            _state.value = try {
                val results = cityRepository.searchMap(query)
                _state.value.copy(mapQuery = query, mapResults = results, mapSearching = false)
            } catch (e: Exception) {
                Log.w(TAG, "지도에서 도시 찾기 실패", e)
                _state.value.copy(mapSearching = false, mapError = "지도에서 찾지 못했어요. 인터넷 연결을 확인해 주세요.")
            }
        }
    }

    fun openCalendar() = update { it.copy(step = NewJourneyStep.CALENDAR) }

    fun closeCalendar() = update { it.copy(step = NewJourneyStep.FORM) }

    /** S03 "기간 선택 완료" */
    fun confirmPeriod(start: LocalDate, end: LocalDate) = update {
        it.copy(startDate = start, endDate = end, step = NewJourneyStep.FORM, error = null, inviteHint = null)
    }

    fun selectNotifyHours(hours: Int) = update { it.copy(notifyHours = hours, notifyOff = false) }

    fun toggleNotifyOff() = update { it.copy(notifyOff = !it.notifyOff) }

    /**
     * 위쪽 "저장 →". 성공하면 onSaved(시작일)를 부른다.
     * 친구 초대하기로 이미 여정을 만들었으면 다시 만들지 않고 바로 onSaved 를 부른다.
     */
    fun save(onSaved: (startDate: LocalDate) -> Unit) {
        val s = _state.value
        val start = s.startDate ?: return
        if (s.createdJourneyId != null) {
            onSaved(start)
            return
        }
        createJourney { onSaved(start) }
    }

    /**
     * "친구 초대하기": 여정을 저장하고(서버가 6자리 초대 코드를 만든다) 코드 팝업을 띄운다.
     * 이름·도시·기간을 다 채우지 않았으면 안내만 보여 준다.
     */
    fun inviteFriends() {
        val s = _state.value
        if (s.saving) return
        if (s.inviteCode != null) {
            _state.value = s.copy(showInviteDialog = true)
            return
        }
        if (!s.isComplete) {
            _state.value = s.copy(inviteHint = "여정 이름·도시·여행 기간을 먼저 정해 주세요.")
            return
        }
        createJourney { _state.value = _state.value.copy(showInviteDialog = true) }
    }

    /** 초대 코드 팝업 닫기(X·뒤로 가기): 여정은 이미 저장됐으니 저장과 똑같이 홈으로 간다 */
    fun closeInviteDialog(onSaved: (startDate: LocalDate) -> Unit) {
        val s = _state.value
        _state.value = s.copy(showInviteDialog = false)
        s.startDate?.let(onSaved)
    }

    /** 서버 함수 createJourney 로 여정을 만든다. 성공하면 여정 ID·초대 코드를 기억하고 onDone 을 부른다 */
    private fun createJourney(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        val start = s.startDate ?: return
        val end = s.endDate ?: return
        _state.value = s.copy(saving = true, error = null, inviteHint = null)
        viewModelScope.launch {
            try {
                val city = s.selectedCity
                val created = repository.createJourney(
                    name = s.name.trim(),
                    city = city?.name ?: s.cityQuery.trim(),
                    country = city?.country ?: "",
                    startDate = start.toString(), // "YYYY-MM-DD"
                    endDate = end.toString(),
                    notifyIntervalHours = if (s.notifyOff) null else s.notifyHours,
                )
                _state.value = _state.value.copy(
                    saving = false,
                    createdJourneyId = created.journeyId,
                    inviteCode = created.inviteCode,
                )
                onDone()
            } catch (e: Exception) {
                Log.w(TAG, "여정 만들기 실패 (createJourney 함수 배포 확인)", e)
                _state.value = _state.value.copy(saving = false, error = e.toKoreanMessage())
            }
        }
    }

    private inline fun update(change: (NewJourneyUiState) -> NewJourneyUiState) {
        val current = _state.value
        if (current.saving) return
        _state.value = change(current)
    }

    private companion object {
        const val TAG = "NewJourneyViewModel"
    }
}

/** 서버 함수 오류를 사용자에게 보여 줄 한국어 문구로 */
private fun Exception.toKoreanMessage(): String = when {
    this is FirebaseNetworkException -> "인터넷 연결을 확인해 주세요."
    this is FirebaseFunctionsException -> when (code) {
        // 서버(functions/src/common.ts)가 보낸 한국어 문구를 그대로 보여 준다
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> message ?: "입력한 내용을 확인해 주세요."
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> "다시 로그인해 주세요."
        FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
            "인터넷 연결을 확인해 주세요."
        FirebaseFunctionsException.Code.NOT_FOUND -> "여정 만들기 기능이 아직 준비되지 않았어요. (서버 함수 배포 필요)"
        else -> "여정을 만들지 못했어요. 잠시 후 다시 시도해 주세요."
    }
    else -> "여정을 만들지 못했어요. 잠시 후 다시 시도해 주세요."
}
