package com.ahu.ahutong.ui.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHUResponse
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.crawler.api.ycard.YcardApi
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.FormBody
import android.util.Log
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.dao.AHUCache.saveRoomSelection
import com.ahu.ahutong.data.crawler.utils.generateNonce
import com.ahu.ahutong.data.crawler.utils.getTimestamp
import com.ahu.ahutong.data.crawler.utils.sha256
import com.ahu.ahutong.data.model.ElectricityChargeInfo
import com.ahu.ahutong.data.model.ElectricityDepositHistoryItem
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.semantic.ContentStateBucket
import com.ahu.ahutong.personalization.semantic.ErrorTypeBucket
import com.ahu.ahutong.personalization.semantic.ResultCountBucket
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.collections.joinToString
import kotlin.collections.toMap
import kotlin.jvm.java
import kotlin.let
import kotlin.ranges.until
import kotlin.run
import kotlin.text.isNullOrBlank
import kotlin.text.isNullOrEmpty
import kotlin.text.map
import kotlin.text.mapIndexed
import kotlin.text.toDoubleOrNull
import kotlin.to

data class CampusApiResponse(
    val msg: String?,
    val code: Int,
    val map: CampusMap?
)

data class CampusMap(
    val data: List<CampusDataItem>?
)

data class CampusDataItem(
    val name: String,
    val value: String
)

data class RoomInfoApiResponse(
    val msg: String?,
    val code: Int,
    val map: RoomInfoMap?
)

data class RoomInfoMap(
    val showData: ShowData?,
    val data: RoomDetails?
)

data class ShowData(
    @SerializedName("信息")
    val info: String?
)

data class RoomDetails(
    val area: String?,
    val buildingName: String?,
    val areaName: String?,
    val floorName: String?,
    val floor: String?,
    val aid: String?,
    val account: String?,
    val building: String?,
    val room: String?,
    val roomName: String?
)

data class PaymentData(
    val area: String,
    val buildingName: String,
    val areaName: String,
    val extdata: String = "",
    val floorName: String,
    val floor: String,
    val aid: String,
    val account: String,
    val building: String,
    val room: String,
    val roomName: String,
    val myCustomInfo: String
)

data class OrderResponse(
    val code: Int,
    val success: Boolean,
    val data: OrderData?,
    val msg: String
)

data class OrderData(
    val orderid: String
)

data class FinalPayResponse(
    val code: Int,
    val success: Boolean,
    val data: String?,
    val msg: String
)

data class AccountPayInfoResponse(
    val code: Int,
    val success: Boolean,
    val data: AccountPayInfoData?,
    val msg: String
)

data class AccountPayInfoData(
    val passwordMap: Map<String, String>?
)

@HiltViewModel
class ElectricityDepositViewModel @Inject constructor(
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {
    var _payState = MutableStateFlow<PayState>(PayState.Idle)
    val payState : StateFlow<PayState> = _payState
    fun resetPaymentState() {
        _payState.value = PayState.Idle
    }

    private val _campusList = MutableStateFlow<List<CampusDataItem>>(emptyList())
    val campusList: StateFlow<List<CampusDataItem>> = _campusList

    private val _selectedCampus = MutableStateFlow<CampusDataItem?>(null)
    val selectedCampus: StateFlow<CampusDataItem?> = _selectedCampus

    private val _buildingsList = MutableStateFlow<List<CampusDataItem>>(emptyList())
    val buildingsList: StateFlow<List<CampusDataItem>> = _buildingsList

    private val _selectedBuilding = MutableStateFlow<CampusDataItem?>(null)
    val selectedBuilding: StateFlow<CampusDataItem?> = _selectedBuilding

    private val _floorsList = MutableStateFlow<List<CampusDataItem>>(emptyList())
    val floorsList: StateFlow<List<CampusDataItem>> = _floorsList

    private val _selectedFloor = MutableStateFlow<CampusDataItem?>(null)
    val selectedFloor: StateFlow<CampusDataItem?> = _selectedFloor

    private val _roomsList = MutableStateFlow<List<CampusDataItem>>(emptyList())
    val roomsList: StateFlow<List<CampusDataItem>> = _roomsList

    private val _selectedRoom = MutableStateFlow<CampusDataItem?>(null)
    val selectedRoom: StateFlow<CampusDataItem?> = _selectedRoom

    private val _fullRoomDetails = MutableStateFlow<RoomInfoMap?>(null)
    val fullRoomDetails: StateFlow<RoomInfoMap?> = _fullRoomDetails

    private val _roomInfo = MutableStateFlow<String?>(null)
    val roomInfo: StateFlow<String?> = _roomInfo

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _historyOptions = MutableStateFlow<List<ElectricityDepositHistoryItem>>(emptyList())
    val historyOptions: StateFlow<List<ElectricityDepositHistoryItem>> = _historyOptions
    private val _presetCandidates = MutableStateFlow<List<PresetCandidate>>(emptyList())
    val presetCandidates: StateFlow<List<PresetCandidate>> = _presetCandidates
    private var activePresetInteraction: PresetInteractionToken? = null
    private var candidatesAtOpportunity: List<PresetCandidate> = emptyList()
    private var selectionLoadJob: Job? = null

    init {
        val history = AHUCache.getElectricityDepositHistory()
            .filter(ElectricityDepositHistoryItem::confirmedByPayment)
            .sortedByDescending(ElectricityDepositHistoryItem::updatedAt)
            .take(MAX_ROOM_HISTORY)
        _historyOptions.value = history
        val lastSelection = AHUCache.getRoomSelection()
            ?.takeIf(::isCompleteSelection)
            ?: history.firstOrNull { isCompleteSelection(it.selection) }?.selection
        if (lastSelection != null) {
            Log.d("ElectricityDepositViewModel", "选择从缓存恢复")
            loadAndRestoreSelection(lastSelection)
        } else {
            fetchCampuses()
        }
        viewModelScope.launch {
            _presetCandidates.value = behaviorRuntime.rankLocalPresets(SemanticDomain.ELECTRICITY)
        }
    }

    private fun loadAndRestoreSelection(selection: RoomSelectionInfo, commitPresetOnRoomRequest: Boolean = false) {
        selectionLoadJob?.cancel()
        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                _selectedCampus.value = selection.campus
                _selectedBuilding.value = selection.building
                _selectedFloor.value = selection.floor
                _selectedRoom.value = selection.room
                _campusList.value = listOfNotNull(selection.campus)
                _buildingsList.value = listOfNotNull(selection.building)
                _floorsList.value = listOfNotNull(selection.floor)
                _roomsList.value = listOfNotNull(selection.room)

                // Restore the useful content first. Selector option lists are secondary and should
                // not keep the whole page blocked while a remembered room balance is available.
                val roomDetails = getRoomInfo()
                (roomDetails.data as? RoomInfoMap)?.let {
                    _fullRoomDetails.value = it
                    _roomInfo.value = it.showData?.info
                    persistCurrentSelection()
                    behaviorRuntime.onContentStateChanged(
                        SemanticDomain.ELECTRICITY,
                        ContentStateBucket.READY,
                        freshnessBucket = 0,
                        resultCount = ResultCountBucket.ONE_TO_FIVE
                    )
                } ?: throw Exception(roomDetails.msg ?: "加载房间信息失败")
                _isLoading.value = false

                val (campuses, buildings, floors, rooms) = coroutineScope {
                    val campusesRequest = async { getCampus() }
                    val buildingsRequest = async { getBuildings() }
                    val floorsRequest = async { getFloor() }
                    val roomsRequest = async { getRoom() }
                    listOf(
                        campusesRequest.await(),
                        buildingsRequest.await(),
                        floorsRequest.await(),
                        roomsRequest.await()
                    )
                }
                @Suppress("UNCHECKED_CAST")
                (campuses.data as? List<CampusDataItem>)?.let { _campusList.value = it }
                @Suppress("UNCHECKED_CAST")
                (buildings.data as? List<CampusDataItem>)?.let { _buildingsList.value = it }
                @Suppress("UNCHECKED_CAST")
                (floors.data as? List<CampusDataItem>)?.let { _floorsList.value = it }
                @Suppress("UNCHECKED_CAST")
                (rooms.data as? List<CampusDataItem>)?.let { _roomsList.value = it }
                if (commitPresetOnRoomRequest) recordRoomPreset()
                Log.d("ElectricityDepositViewModel", "从缓存恢复选择成功")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "恢复选择时发生未知错误"
                Log.e("ElectricityDepositViewModel", "恢复选择失败", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectHistory(item: ElectricityDepositHistoryItem) {
        loadAndRestoreSelection(item.selection)
    }

    fun deleteHistory(item: ElectricityDepositHistoryItem) {
        val deletedKey = selectionKey(item.selection)
        val updatedHistory = _historyOptions.value.filterNot {
            selectionKey(it.selection) == deletedKey
        }
        _historyOptions.value = updatedHistory
        AHUCache.saveElectricityDepositHistory(updatedHistory)
    }

    fun onCampusSelected(campus: CampusDataItem) {
        selectionLoadJob?.cancel()
        _selectedCampus.value = campus
        _buildingsList.value = emptyList()
        _selectedBuilding.value = null
        _floorsList.value = emptyList()
        _selectedFloor.value = null
        _roomsList.value = emptyList()
        _selectedRoom.value = null
        _roomInfo.value = null
        fetchBuildings()
    }

    fun onBuildingSelected(building: CampusDataItem) {
        selectionLoadJob?.cancel()
        _selectedBuilding.value = building
        _floorsList.value = emptyList()
        _selectedFloor.value = null
        _roomsList.value = emptyList()
        _selectedRoom.value = null
        _roomInfo.value = null
        fetchFloor()
    }

    fun onfloorSelected(floor: CampusDataItem) {
        selectionLoadJob?.cancel()
        _selectedFloor.value = floor
        _roomsList.value = emptyList()
        _selectedRoom.value = null
        _roomInfo.value = null
        fetchRoom()
    }

    fun onRoomSelected(room: CampusDataItem) {
        selectionLoadJob?.cancel()
        _selectedRoom.value = room
        _roomInfo.value = null
        fetchRoomInfo()
    }

    fun retry() {
        when {
            _selectedCampus.value == null -> fetchCampuses()
            _selectedBuilding.value == null -> fetchBuildings()
            _selectedFloor.value == null -> fetchFloor()
            _selectedRoom.value == null -> fetchRoom()
            else -> fetchRoomInfo()
        }
    }

    private fun fetchCampuses() {
        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = getCampus()
                if (response.code == 0 && response.data != null) {
                    val items = response.data!!
                    _campusList.value = items
                    if (_selectedCampus.value == null) {
                        items.firstOrNull()?.let { first ->
                            _selectedCampus.value = first
                            fetchBuildings()
                        }
                    }
                } else {
                    _errorMessage.value = response.msg ?: "加载校区失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                if (_errorMessage.value != null || _selectedCampus.value == null) {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun getCampus(): AHUResponse<List<CampusDataItem>> {
        val responseWrapper = AHUResponse<List<CampusDataItem>>()
        val formBody = FormBody.Builder()
            .add("feeitemid", "488")
            .add("type", "select")
            .add("level", "0")
            .build()
        try {
            val res = YcardApi.authorizedCall { getFeeItemThirdData(formBody) }
            Log.d("ElectricityDepositViewModel", "getCampus响应码: ${res.code()}")
            val responseBody = res.body()?.string()
            if (res.isSuccessful) {
                if (responseBody.isNullOrEmpty()) {
                    responseWrapper.code = -1
                    responseWrapper.msg = "服务器返回内容为空"
                    Log.e("ElectricityDepositViewModel", "getCampus Error: Server returned empty body")
                    return responseWrapper
                }
                val parsedResponse = Gson().fromJson(responseBody, CampusApiResponse::class.java)
                if (parsedResponse.map?.data != null) {
                    responseWrapper.code = 0
                    responseWrapper.msg = "success"
                    responseWrapper.data = parsedResponse.map.data
                    Log.d("ElectricityDepositViewModel", "getCampus Success: Loaded ${parsedResponse.map.data.size} items")
                } else {
                    responseWrapper.code = -1
                    responseWrapper.msg = "解析数据失败，未找到校区列表"
                    Log.e("ElectricityDepositViewModel", "getCampus Parse Error: map.data is null")
                }
            } else {
                responseWrapper.code = res.code()
                responseWrapper.msg = "请求接口失败: ${res.message()}"
                Log.e("ElectricityDepositViewModel", "getCampus Network Error: ${res.code()} ${res.message()}")
            }
        } catch (e: Exception) {
            responseWrapper.code = -1
            responseWrapper.msg = "发生未知错误: ${e.message}"
            Log.e("ElectricityDepositViewModel", "getCampus Exception", e)
        }
        return responseWrapper
    }

    private fun fetchBuildings() {
        if (_selectedCampus.value == null) {
            _errorMessage.value = "请先选择一个校区"
            return
        }

        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = getBuildings()
                if (response.code == 0 && response.data != null) {
                    val items = response.data!!
                    _buildingsList.value = items
                } else {
                    _errorMessage.value = response.msg ?: "加载楼栋失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                if (_errorMessage.value != null || _selectedBuilding.value == null) {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun getBuildings(): AHUResponse<List<CampusDataItem>> {
        val responseWrapper = AHUResponse<List<CampusDataItem>>()
        val selectedCampusValue = _selectedCampus.value?.value ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "selectedCampusValue内容为空"
            return responseWrapper
        }

        val formBody = FormBody.Builder()
            .add("feeitemid", "488")
            .add("type", "select")
            .add("level", "1")
            .add("campus", selectedCampusValue)
            .build()

        try {
            val res = YcardApi.authorizedCall { getFeeItemThirdData(formBody) }
            Log.d("ElectricityDepositViewModel", "getBuildings响应码: ${res.code()}")
            val responseBody = res.body()?.string()
            if (res.isSuccessful) {
                if (responseBody.isNullOrEmpty()) {
                    responseWrapper.code = -1
                    responseWrapper.msg = "服务器返回内容为空"
                    return responseWrapper
                }
                val parsedResponse = Gson().fromJson(responseBody, CampusApiResponse::class.java)
                if (parsedResponse.map?.data != null) {
                    responseWrapper.code = 0
                    responseWrapper.msg = "success"
                    responseWrapper.data = parsedResponse.map.data
                } else {
                    responseWrapper.code = -1
                    responseWrapper.msg = "解析数据失败，未找到楼栋列表"
                }
            } else {
                responseWrapper.code = res.code()
                responseWrapper.msg = "请求接口失败: ${res.message()}"
            }
        } catch (e: Exception) {
            responseWrapper.code = -1
            responseWrapper.msg = "发生未知错误: ${e.message}"
        }
        return responseWrapper
    }

    private fun fetchFloor() {
        if (_selectedBuilding.value == null) {
            _errorMessage.value = "请先选择一个楼栋"
            return
        }

        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = getFloor()
                if (response.code == 0 && response.data != null) {
                    val items = response.data!!
                    _floorsList.value = items
                } else {
                    _errorMessage.value = response.msg ?: "加载楼层失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                if (_errorMessage.value != null || _selectedFloor.value == null) {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun getFloor(): AHUResponse<List<CampusDataItem>> {
        val responseWrapper = AHUResponse<List<CampusDataItem>>()
        val selectedCampusValue = _selectedCampus.value?.value ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "selectedCampusValue内容为空"
            return responseWrapper
        }
        val selectedBuildingValue = _selectedBuilding.value?.value ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "selectedBuildingValue内容为空"
            return responseWrapper
        }

        val formBody = FormBody.Builder()
            .add("feeitemid", "488")
            .add("type", "select")
            .add("level", "2")
            .add("campus", selectedCampusValue)
            .add("building", selectedBuildingValue)
            .build()

        try {
            val res = YcardApi.authorizedCall { getFeeItemThirdData(formBody) }
            Log.d("ElectricityDepositViewModel", "getFloor响应码: ${res.code()}")
            val responseBody = res.body()?.string()
            if (res.isSuccessful) {
                if (responseBody.isNullOrEmpty()) {
                    responseWrapper.code = -1
                    responseWrapper.msg = "服务器返回内容为空"
                    return responseWrapper
                }
                val parsedResponse = Gson().fromJson(responseBody, CampusApiResponse::class.java)
                if (parsedResponse.map?.data != null) {
                    responseWrapper.code = 0
                    responseWrapper.msg = "success"
                    responseWrapper.data = parsedResponse.map.data
                } else {
                    responseWrapper.code = -1
                    responseWrapper.msg = "解析数据失败，未找到楼层列表"
                }
            } else {
                responseWrapper.code = res.code()
                responseWrapper.msg = "请求接口失败: ${res.message()}"
            }
        } catch (e: Exception) {
            responseWrapper.code = -1
            responseWrapper.msg = "发生未知错误: ${e.message}"
        }
        return responseWrapper
    }

    private fun fetchRoom() {
        if (_selectedFloor.value == null) {
            _errorMessage.value = "请先选择一个楼层"
            return
        }

        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = getRoom()
                if (response.code == 0 && response.data != null) {
                    val items = response.data!!
                    _roomsList.value = items
                } else {
                    _errorMessage.value = response.msg ?: "加载房间失败"
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
            } finally {
                if (_errorMessage.value != null || _selectedRoom.value == null) {
                    _isLoading.value = false
                }
            }
        }
    }

    private suspend fun getRoom(): AHUResponse<List<CampusDataItem>> {
        val responseWrapper = AHUResponse<List<CampusDataItem>>()
        val selectedFloorValue = _selectedFloor.value?.value ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "selectedFloorValue内容为空"
            return responseWrapper
        }
        val selectedCampusValue = _selectedCampus.value?.value ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "_selectedCampus内容为空"
            return responseWrapper
        }
        val selectedBuildingValue = _selectedBuilding.value?.value ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "selectedBuildingValue内容为空"
            return responseWrapper
        }

        val formBody = FormBody.Builder()
            .add("feeitemid", "488")
            .add("type", "select")
            .add("level", "3")
            .add("campus", selectedCampusValue)
            .add("building", selectedBuildingValue)
            .add("floor", selectedFloorValue)
            .build()

        try {
            val res = YcardApi.authorizedCall { getFeeItemThirdData(formBody) }
            Log.d("ElectricityDepositViewModel", "getRoom响应码: ${res.code()}")
            val responseBody = res.body()?.string()
            if (res.isSuccessful) {
                if (responseBody.isNullOrEmpty()) {
                    responseWrapper.code = -1
                    responseWrapper.msg = "服务器返回内容为空"
                    return responseWrapper
                }
                val parsedResponse = Gson().fromJson(responseBody, CampusApiResponse::class.java)
                if (parsedResponse.map?.data != null) {
                    responseWrapper.code = 0
                    responseWrapper.msg = "success"
                    responseWrapper.data = parsedResponse.map.data
                } else {
                    responseWrapper.code = -1
                    responseWrapper.msg = "解析数据失败，未找到房间列表"
                }
            } else {
                responseWrapper.code = res.code()
                responseWrapper.msg = "请求接口失败: ${res.message()}"
            }
        } catch (e: Exception) {
            responseWrapper.code = -1
            responseWrapper.msg = "发生未知错误: ${e.message}"
        }
        return responseWrapper
    }

    private fun fetchRoomInfo() {
        if (_selectedRoom.value == null) {
            _errorMessage.value = "请先选择一个房间"
            return
        }

        selectionLoadJob = viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = getRoomInfo()
                if (response.code == 0 && response.data != null) {
                    _fullRoomDetails.value = response.data
                    _roomInfo.value = response.data.showData?.info
                    persistCurrentSelection()
                    behaviorRuntime.onContentStateChanged(
                        SemanticDomain.ELECTRICITY,
                        ContentStateBucket.READY,
                        freshnessBucket = 0,
                        resultCount = ResultCountBucket.ONE_TO_FIVE
                    )
                    launch { recordRoomPreset() }
                } else {
                    _errorMessage.value = response.msg ?: "加载房间信息失败"
                    reportRoomError()
                }
            } catch (e: Exception) {
                _errorMessage.value = "网络错误: ${e.message}"
                reportRoomError()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun applyPresetCandidate(candidate: PresetCandidate) = viewModelScope.launch {
        val applied = behaviorRuntime.applyLocalPreset(candidate) ?: return@launch
        activePresetInteraction = applied.interactionToken
        candidatesAtOpportunity = _presetCandidates.value
        val selection = runCatching { Gson().fromJson(applied.localPayloadJson, RoomSelectionInfo::class.java) }.getOrNull()
            ?: return@launch
        if (selection.campus == null || selection.building == null || selection.floor == null || selection.room == null) {
            return@launch
        }
        _presetCandidates.value = emptyList()
        loadAndRestoreSelection(selection, commitPresetOnRoomRequest = true)
    }

    private suspend fun recordRoomPreset() {
        val selection = RoomSelectionInfo(
            campus = _selectedCampus.value,
            building = _selectedBuilding.value,
            floor = _selectedFloor.value,
            room = _selectedRoom.value
        )
        val campus = selection.campus ?: return
        val building = selection.building ?: return
        val floor = selection.floor ?: return
        val room = selection.room ?: return
        behaviorRuntime.recordNaturalPresetSubmission(
            PresetSubmission(
                SemanticDomain.ELECTRICITY,
                Gson().toJson(selection),
                "{\"roomCategory\":\"RECENT_LOCAL_ROOM\"}",
                "${campus.value}|${building.value}|${floor.value}|${room.value}"
            ),
            interactionToken = activePresetInteraction,
            candidatesAtOpportunity = candidatesAtOpportunity.ifEmpty { _presetCandidates.value }
        )
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
        _presetCandidates.value = behaviorRuntime.rankLocalPresets(SemanticDomain.ELECTRICITY)
    }

    fun onPresetCandidateVisible(candidate: PresetCandidate) = viewModelScope.launch {
        val token = behaviorRuntime.markPresetRecommendationExposed(candidate) ?: return@launch
        activePresetInteraction = token
        candidatesAtOpportunity = _presetCandidates.value
    }

    fun onPresetSurfaceDisposed() {
        behaviorRuntime.expirePresetInteractionAsync(activePresetInteraction)
        activePresetInteraction = null
        candidatesAtOpportunity = emptyList()
    }

    override fun onCleared() {
        onPresetSurfaceDisposed()
        super.onCleared()
    }

    private fun reportRoomError() {
        behaviorRuntime.onContentStateChanged(
            SemanticDomain.ELECTRICITY,
            ContentStateBucket.ERROR,
            freshnessBucket = 7,
            resultCount = ResultCountBucket.ZERO,
            errorType = ErrorTypeBucket.NETWORK
        )
    }

    private suspend fun getRoomInfo(): AHUResponse<RoomInfoMap> {
        val responseWrapper = AHUResponse<RoomInfoMap>()

        val selectedRoomValue = _selectedRoom.value?.value ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "selectedRoomValue内容为空"
            return responseWrapper
        }
        val selectedFloorValue = _selectedFloor.value?.value ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "selectedFloorValue内容为空"
            return responseWrapper
        }
        val selectedBuildingValue = _selectedBuilding.value?.value ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "selectedBuildingValue内容为空"
            return responseWrapper
        }
        val selectedCampusValue = _selectedCampus.value?.value ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "selectedCampusValue内容为空"
            return responseWrapper
        }

        val formBody = FormBody.Builder()
            .add("feeitemid", "488")
            .add("type", "IEC")
            .add("level", "4")
            .add("campus", selectedCampusValue)
            .add("building", selectedBuildingValue)
            .add("floor", selectedFloorValue)
            .add("room", selectedRoomValue)
            .build()

        try {
            val res = YcardApi.authorizedCall { getFeeItemThirdData(formBody) }
            Log.d("ElectricityDepositViewModel", "getRoomInfo响应码: ${res.code()}")
            val responseBody = res.body()?.string()
            if (res.isSuccessful) {
                if (responseBody.isNullOrEmpty()) {
                    responseWrapper.code = -1
                    responseWrapper.msg = "服务器返回内容为空"
                    return responseWrapper
                }
                val parsedResponse = Gson().fromJson(responseBody, RoomInfoApiResponse::class.java)
                if (parsedResponse.map != null) {
                    responseWrapper.code = 0
                    responseWrapper.msg = "success"
                    responseWrapper.data = parsedResponse.map
                } else {
                    responseWrapper.code = -1
                    responseWrapper.msg = "解析数据失败，未找到房间信息"
                }
            } else {
                responseWrapper.code = res.code()
                responseWrapper.msg = "请求接口失败: ${res.message()}"
            }
        } catch (e: Exception) {
            responseWrapper.code = -1
            responseWrapper.msg = "发生未知错误: ${e.message}"
        }
        return responseWrapper
    }

    private suspend fun getPaymentOrder(amount: String): AHUResponse<OrderData> {
        val responseWrapper = AHUResponse<OrderData>()

        val fullDetails = _fullRoomDetails.value?.data ?: run {
            responseWrapper.code = -1
            responseWrapper.msg = "房间详细信息为空，无法支付"
            return responseWrapper
        }
        val paymentData = PaymentData(
            area = fullDetails.area ?: "",
            buildingName = fullDetails.buildingName ?: "",
            areaName = fullDetails.areaName ?: "",
            floorName = fullDetails.floorName ?: "",
            floor = fullDetails.floor ?: "",
            aid = fullDetails.aid ?: "",
            account = fullDetails.account ?: "",
            building = fullDetails.building ?: "",
            room = fullDetails.room ?: "",
            roomName = fullDetails.roomName ?: "",
            myCustomInfo = "房间：${fullDetails.areaName} ${fullDetails.buildingName} ${fullDetails.floorName} ${fullDetails.roomName}"
        )
        val thirdPartyJson = Gson().toJson(paymentData)
        val formBody = buildSignedFormBody(
            linkedMapOf(
                "feeitemid" to "488",
                "tranamt" to amount,
                "flag" to "choose",
                "source" to "app",
                "paystep" to "0",
                "abstracts" to "",
                "redirect_url" to "https://ycard.ahu.edu.cn/plat",
                "third_party" to thirdPartyJson
            )
        )
        try {
            val res = YcardApi.authorizedCall { pay(formBody) }
            Log.d("ElectricityDepositViewModel", "getPaymentOrder响应码: ${res.code()}")
            val responseBody = res.body()?.string()
            if (res.isSuccessful) {
                if (responseBody.isNullOrEmpty()) {
                    responseWrapper.code = -1
                    responseWrapper.msg = "服务器返回内容为空"
                    return responseWrapper
                }
                val parsedResponse = Gson().fromJson(responseBody, OrderResponse::class.java)
                if (parsedResponse.code == 200 && parsedResponse.data != null) {
                    responseWrapper.code = 0
                    responseWrapper.msg = "success"
                    responseWrapper.data = parsedResponse.data
                } else {
                    responseWrapper.code = -1
                    responseWrapper.msg = parsedResponse.msg
                }
            } else {
                responseWrapper.code = res.code()
                responseWrapper.msg = "请求接口失败: ${res.message()}"
            }
        } catch (e: Exception) {
            responseWrapper.code = -1
            responseWrapper.msg = "发生未知错误: ${e.message}"
        }
        return responseWrapper
    }

    private suspend fun getAccountPayInfo(orderId: String): AHUResponse<AccountPayInfoData> {
        val responseWrapper = AHUResponse<AccountPayInfoData>()
        val formBody = buildSignedFormBody(
            linkedMapOf(
                "paytypeid" to "64",
                "paytype" to "ACCOUNTTSM",
                "paystep" to "2",
                "orderid" to orderId
            )
        )

        try {
            val res = YcardApi.authorizedCall { pay(formBody) }
            Log.d("ElectricityDepositViewModel", "getAccountPayInfo响应码: ${res.code()}")
            val responseBody = res.body()?.string()
            if (res.isSuccessful) {
                if (responseBody.isNullOrEmpty()) {
                    responseWrapper.code = -1
                    responseWrapper.msg = "服务器返回内容为空"
                    return responseWrapper
                }
                val parsedResponse = Gson().fromJson(responseBody, AccountPayInfoResponse::class.java)
                if (parsedResponse.code == 200 && parsedResponse.data?.passwordMap?.isNotEmpty() == true) {
                    responseWrapper.code = 0
                    responseWrapper.msg = "success"
                    responseWrapper.data = parsedResponse.data
                } else {
                    responseWrapper.code = -1
                    responseWrapper.msg = parsedResponse.msg
                }
            } else {
                responseWrapper.code = res.code()
                responseWrapper.msg = "请求接口失败: ${res.message()}"
            }
        } catch (e: Exception) {
            responseWrapper.code = -1
            responseWrapper.msg = "发生未知错误: ${e.message}"
        }
        return responseWrapper
    }

    fun pay(amount: String, password: String) {
        if (amount.toDoubleOrNull() ?: 0.0 <= 0) {
            _errorMessage.value = "请输入有效金额"
            return
        }
        if (password.length != 6) {
            _errorMessage.value = "请输入6位密码"
            return
        }

        _payState.value = PayState.InProgress
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                Log.d("ElectricityDepositViewModel", "开始支付流程（金额与凭证已隐藏）")

                val orderResult = getPaymentOrder(amount)
                if (orderResult.code != 0 || orderResult.data == null) {
                    val msg = orderResult.msg ?: "创建订单失败"
                    _errorMessage.value = msg
                    _payState.value = PayState.Failed(msg)
                    Log.e("ElectricityDepositViewModel", "创建订单失败: $msg")
                    return@launch
                }
                val orderId = orderResult.data.orderid
                Log.d("ElectricityDepositViewModel", "订单创建成功（订单标识已隐藏）")

                val accountPayInfoResult = getAccountPayInfo(orderId)
                val passwordMap = accountPayInfoResult.data?.passwordMap
                if (accountPayInfoResult.code != 0 || passwordMap.isNullOrEmpty()) {
                    val msg = accountPayInfoResult.msg ?: "获取支付信息失败"
                    _errorMessage.value = msg
                    _payState.value = PayState.Failed(msg)
                    Log.e("ElectricityDepositViewModel", "获取支付信息失败: $msg")
                    return@launch
                }

                val (uuid, mapString) = passwordMap.entries.first()
                val plainDigits = "0123456789"

                val keymap = mapString.mapIndexed { index, c ->
                    c.toString() to plainDigits[index].toString()
                }.toMap()

                val cipherText = password.map { ch ->
                    keymap[ch.toString()] ?: ch.toString()
                }.joinToString("")

                val finalFormBody = buildSignedFormBody(
                    linkedMapOf(
                        "orderid" to orderId,
                        "paystep" to "2",
                        "paytype" to "ACCOUNTTSM",
                        "paytypeid" to "64",
                        "userAgent" to "h5",
                        "ccctype" to "000",
                        "password" to cipherText,
                        "uuid" to uuid,
                        "isWX" to "0"
                    )
                )

                Log.d("ElectricityDepositViewModel", "开始执行最终支付请求...")
                val finalRes = YcardApi.authorizedCall { pay(finalFormBody) }
                Log.d("ElectricityDepositViewModel", "最终支付请求完成，响应码: ${finalRes.code()}")
                val responseBody = finalRes.body()?.string()

                if (finalRes.isSuccessful) {
                    val parsedResponse = Gson().fromJson(responseBody, FinalPayResponse::class.java)
                    if (parsedResponse.code == 200 && parsedResponse.success) {
                        _errorMessage.value = null
                        _payState.value = PayState.Succeeded(orderId)
                        Log.d("ElectricityDepositViewModel", "支付成功!")
                        val chargeAmount = amount.toDoubleOrNull()
                        if (chargeAmount != null && chargeAmount > 0) {
                            val existingInfo = AHUCache.getElectricityChargeInfo()
                            if (existingInfo == null) {
                                val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
                                val firstDate = dateFormat.format(Date())
                                val newInfo = ElectricityChargeInfo(
                                    totalAmount = chargeAmount,
                                    firstChargeDate = firstDate
                                )
                                AHUCache.saveElectricityChargeInfo(newInfo)
                            } else {
                                val updatedInfo = existingInfo.copy(
                                    totalAmount = existingInfo.totalAmount + chargeAmount
                                )
                                AHUCache.saveElectricityChargeInfo(updatedInfo)
                            }
                        }
                        val roomSelectionInfo = RoomSelectionInfo(
                            campus = _selectedCampus.value,
                            building = _selectedBuilding.value,
                            floor = _selectedFloor.value,
                            room = _selectedRoom.value
                        )
                        persistCurrentSelection(
                            selection = roomSelectionInfo,
                            confirmedByPayment = true
                        )
                        delay(1_000L)
                        val refreshedInfo = getRoomInfo()
                        if (refreshedInfo.code == 0 && refreshedInfo.data != null) {
                            _fullRoomDetails.value = refreshedInfo.data
                            _roomInfo.value = refreshedInfo.data.showData?.info
                        }
                    } else {
                        val errorMessage = parsedResponse.msg ?: "支付失败，未知错误"
                        _errorMessage.value = errorMessage
                        _payState.value = PayState.Failed(errorMessage)
                        Log.e("ElectricityDepositViewModel", "支付失败 code=${parsedResponse.code}")
                    }
                } else {
                    val errorMessage = "支付失败，请稍后重试（${finalRes.code()}）"
                    _errorMessage.value = errorMessage
                    _payState.value = PayState.Failed(errorMessage)
                    Log.e("ElectricityDepositViewModel", "最终支付请求失败 code=${finalRes.code()}")
                }

            } catch (e: Exception) {
                val errorMessage = "支付请求异常: ${e.message}"
                _errorMessage.value = errorMessage
                _payState.value = PayState.Failed(errorMessage)
                Log.e("ElectricityDepositViewModel", "支付请求异常")
            } finally {
                _isLoading.value = false
                Log.d("ElectricityDepositViewModel", "支付流程结束。")
            }
        }
    }

    private fun buildSignedFormBody(params: LinkedHashMap<String, String>): FormBody {
        val appId = "56321"
        val timestamp = getTimestamp()
        val signType = "SHA256"
        val nonce = generateNonce()
        val signParams = linkedMapOf(
            "APP_ID" to appId,
            "NONCE" to nonce,
            "SIGN_TYPE" to signType,
            "TIMESTAMP" to timestamp
        ).apply {
            params.filterValues { it.isNotEmpty() }
                .toSortedMap()
                .forEach { (key, value) -> put(key, value) }
        }
        val signSource = signParams.entries.joinToString("&") { (key, value) -> "$key=$value" } +
                "&SECRET_KEY=0osTIhce7uPvDKHz6aa67bhCukaKoYl4"
        val sign = sha256(signSource).uppercase()

        val builder = FormBody.Builder()
        params.forEach { (key, value) -> builder.add(key, value) }
        builder.add("APP_ID", appId)
        builder.add("TIMESTAMP", timestamp)
        builder.add("SIGN_TYPE", signType)
        builder.add("NONCE", nonce)
        builder.add("SIGN", sign)
        return builder.build()
    }

    private fun isCompleteSelection(selection: RoomSelectionInfo): Boolean {
        return selection.campus != null &&
            selection.building != null &&
            selection.floor != null &&
            selection.room != null
    }

    private fun persistCurrentSelection(
        selection: RoomSelectionInfo = RoomSelectionInfo(
            campus = _selectedCampus.value,
            building = _selectedBuilding.value,
            floor = _selectedFloor.value,
            room = _selectedRoom.value
        ),
        confirmedByPayment: Boolean = false
    ) {
        if (!isCompleteSelection(selection)) return

        saveRoomSelection(selection)
        if (!confirmedByPayment) return
        val label = normalizeLabel(
            _fullRoomDetails.value?.data?.roomName ?: selection.room?.name.orEmpty()
        )
        if (label.isBlank()) return

        val item = ElectricityDepositHistoryItem(
            selection = selection,
            label = label,
            updatedAt = System.currentTimeMillis(),
            confirmedByPayment = true
        )
        val key = selectionKey(selection)
        val updatedHistory = (listOf(item) + _historyOptions.value.filter {
            selectionKey(it.selection) != key
        }).take(MAX_ROOM_HISTORY)
        _historyOptions.value = updatedHistory
        AHUCache.saveElectricityDepositHistory(updatedHistory)
    }

    private fun selectionKey(selection: RoomSelectionInfo): String {
        return listOf(
            selection.campus?.value,
            selection.building?.value,
            selection.floor?.value,
            selection.room?.value
        ).joinToString("|") { it ?: "" }
    }

    private fun normalizeLabel(raw: String): String {
        var value = raw.trim()
        if (value.startsWith("房间：")) {
            value = value.removePrefix("房间：").trim()
        }
        val parts = value.split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.isEmpty()) "" else parts.last()
    }

    private companion object {
        const val MAX_ROOM_HISTORY = 12
    }
}
