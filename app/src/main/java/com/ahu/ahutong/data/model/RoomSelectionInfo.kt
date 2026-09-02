package com.ahu.ahutong.data.model

import com.ahu.ahutong.ui.state.CampusDataItem
import java.io.Serializable

enum class ElectricityController(
    val displayName: String,
    val feeItemId: String,
    val requiresCampus: Boolean
) {
    A("电控A", "408", false),
    B("电控B", "428", false),
    C("电控C", "488", true);

    val floorLevel: String get() = if (requiresCampus) "2" else "1"
    val roomLevel: String get() = if (requiresCampus) "3" else "2"
    val roomInfoLevel: String get() = if (requiresCampus) "4" else "3"
}

data class RoomSelectionInfo(
    val campus: CampusDataItem?,
    val building: CampusDataItem?,
    val floor: CampusDataItem?,
    val room: CampusDataItem?,
    val controller: ElectricityController? = null
) : Serializable
