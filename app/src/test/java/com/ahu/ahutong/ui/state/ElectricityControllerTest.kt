package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.model.ElectricityController
import org.junit.Assert.assertEquals
import org.junit.Test

class ElectricityControllerTest {
    @Test
    fun controllersMatchCapturedFeeItemsAndHierarchyLevels() {
        assertEquals(
            listOf(
                listOf("电控A", "408", "false", "1", "2", "3"),
                listOf("电控B", "428", "false", "1", "2", "3"),
                listOf("电控C", "488", "true", "2", "3", "4")
            ),
            ElectricityController.entries.map { controller ->
                listOf(
                    controller.displayName,
                    controller.feeItemId,
                    controller.requiresCampus.toString(),
                    controller.floorLevel,
                    controller.roomLevel,
                    controller.roomInfoLevel
                )
            }
        )
    }
}
