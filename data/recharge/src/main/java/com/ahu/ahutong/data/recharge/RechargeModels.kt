package com.ahu.ahutong.data.recharge

import com.ahu.ahutong.data.model.Data as BathroomAccount
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.google.gson.annotations.SerializedName

/** A successful recharge reference shown by the feature. */
data class RechargeReceipt(val reference: String)

data class NetworkFeeItem(
    @SerializedName("name") val name: String?,
    @SerializedName("layout") val layout: String?,
    @SerializedName("maxmoney") val maxMoney: String?,
    @SerializedName("daymaxmoney") val dayMaxMoney: String?,
    @SerializedName("billing_unit") val billingUnit: String?
)

/** The account payload required again when a network recharge is submitted. */
data class NetworkThirdPartyData(
    @SerializedName("state_time") val stateTime: String?,
    @SerializedName("state_memo") val stateMemo: String?,
    @SerializedName("balance") val balance: String?,
    @SerializedName("use_time") val useTime: String?,
    @SerializedName("tsmAbstract") val tsmAbstract: String?,
    @SerializedName("use_money") val useMoney: String?,
    @SerializedName("use_flow") val useFlow: String?,
    @SerializedName("account") val account: String?,
    @SerializedName("user_state") val userState: String?,
    @SerializedName("start_date") val startDate: String?
)

data class NetworkRechargeSnapshot(
    val feeItem: NetworkFeeItem,
    val showData: Map<String, String>,
    val paymentData: NetworkThirdPartyData
)

data class BathroomPayment(
    val bathroom: String,
    val amount: String,
    val password: String,
    val account: BathroomAccount
)

enum class ElectricityOptionLevel {
    Initial,
    Buildings,
    Floors,
    Rooms
}

data class ElectricityOptionQuery(
    val controller: ElectricityController,
    val selection: RoomSelectionInfo,
    val level: ElectricityOptionLevel
)

data class ElectricityRoom(
    val displayInfo: String?,
    val details: ElectricityRoomDetails
)

data class ElectricityRoomDetails(
    @SerializedName("area") val area: String?,
    @SerializedName("buildingName") val buildingName: String?,
    @SerializedName("areaName") val areaName: String?,
    @SerializedName("floorName") val floorName: String?,
    @SerializedName("floor") val floor: String?,
    @SerializedName("aid") val aid: String?,
    @SerializedName("account") val account: String?,
    @SerializedName("building") val building: String?,
    @SerializedName("room") val room: String?,
    @SerializedName("roomName") val roomName: String?
)

data class ElectricityPayment(
    val controller: ElectricityController,
    val amount: String,
    val password: String,
    val room: ElectricityRoomDetails
)
