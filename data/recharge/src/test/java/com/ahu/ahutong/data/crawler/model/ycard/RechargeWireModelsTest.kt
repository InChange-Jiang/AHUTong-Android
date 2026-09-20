package com.ahu.ahutong.data.crawler.model.ycard

import com.ahu.ahutong.data.recharge.ElectricityRoomDetails
import com.ahu.ahutong.data.recharge.NetworkFeeItem
import com.ahu.ahutong.data.recharge.NetworkThirdPartyData
import com.google.gson.annotations.SerializedName
import kotlin.test.Test
import kotlin.test.assertTrue

class RechargeWireModelsTest {

    @Test
    fun `every recharge wire field has an explicit serialized name`() {
        val missing = WIRE_TYPES.flatMap { type ->
            type.declaredFields
                .filterNot { it.isSynthetic }
                .filterNot { it.isAnnotationPresent(SerializedName::class.java) }
                .map { field -> "${type.simpleName}.${field.name}" }
        }

        assertTrue(
            missing.isEmpty(),
            "Recharge wire fields must survive release shrinking: ${missing.joinToString()}"
        )
    }

    private companion object {
        val WIRE_TYPES = listOf(
            NetworkFeeItemPageResponse::class.java,
            NetworkFeeItem::class.java,
            NetworkFeeInfoResponse::class.java,
            NetworkFeeInfoMap::class.java,
            NetworkThirdPartyData::class.java,
            NetworkOrderResponse::class.java,
            NetworkOrderData::class.java,
            NetworkAccountPayInfoResponse::class.java,
            NetworkAccountPayInfoData::class.java,
            NetworkFinalPayResponse::class.java,
            NetworkThirdDataResponse::class.java,
            ElectricityCampusApiResponse::class.java,
            ElectricityCampusMap::class.java,
            ElectricityRoomInfoApiResponse::class.java,
            ElectricityRoomInfoMap::class.java,
            ElectricityRoomShowData::class.java,
            ElectricityRoomDetails::class.java,
            ElectricityPaymentData::class.java,
            ElectricityOrderResponse::class.java,
            ElectricityOrderData::class.java,
            ElectricityAccountPayInfoResponse::class.java,
            ElectricityAccountPayInfoData::class.java,
            ElectricityFinalPayResponse::class.java
        )
    }
}
