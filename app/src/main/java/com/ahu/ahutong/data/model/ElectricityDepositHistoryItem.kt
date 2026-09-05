package com.ahu.ahutong.data.model

import java.io.Serializable

data class ElectricityDepositHistoryItem(
    val selection: RoomSelectionInfo,
    val label: String,
    val updatedAt: Long,
    /** True only when the room was persisted after a confirmed successful payment. */
    val confirmedByPayment: Boolean = false
) : Serializable
