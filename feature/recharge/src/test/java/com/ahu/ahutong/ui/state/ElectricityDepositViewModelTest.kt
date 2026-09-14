package com.ahu.ahutong.ui.state

import com.ahu.ahutong.core.common.AhuResult
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.model.CampusDataItem
import com.ahu.ahutong.data.model.ElectricityController
import com.ahu.ahutong.data.model.RoomSelectionInfo
import com.ahu.ahutong.data.recharge.ElectricityRoom
import com.ahu.ahutong.data.recharge.ElectricityRoomDetails
import com.ahu.ahutong.data.recharge.RechargeReceipt
import com.ahu.ahutong.personalization.preset.AppliedPreset
import com.ahu.ahutong.personalization.preset.PresetCandidate
import com.ahu.ahutong.personalization.preset.PresetInteractionToken
import com.ahu.ahutong.personalization.preset.PresetSubmission
import com.ahu.ahutong.personalization.preset.PresetSuggestions
import com.ahu.ahutong.personalization.semantic.SemanticDomain
import com.ahu.ahutong.testing.FakeBehaviorRecorder
import com.ahu.ahutong.testing.FakeElectricityDepositSource
import com.ahu.ahutong.testing.FakePaymentKeyboardSetting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
class ElectricityDepositViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `restored room and payment cross typed seams`() = runTest(dispatcher) {
        val selection = selection()
        val source = FakeElectricityDepositSource(
            controller = ElectricityController.C,
            selection = selection,
            optionsResult = AhuResult.Success(listOfNotNull(selection.room)),
            roomResult = AhuResult.Success(room()),
            payResult = AhuResult.Success(RechargeReceipt("order-1"))
        )
        val subject = ElectricityDepositViewModel(
            source,
            FakeBehaviorRecorder(),
            EmptyPresetSuggestions,
            FakePaymentKeyboardSetting()
        )
        advanceUntilIdle()

        subject.pay("10", "012345")
        advanceUntilIdle()

        assertEquals(PayState.Succeeded("order-1"), subject.payState.value)
        assertEquals("10", source.payments.single().amount)
        assertEquals("012345", source.payments.single().password)
    }

    private fun selection() = RoomSelectionInfo(
        campus = CampusDataItem("磬湖校区", "campus"),
        building = CampusDataItem("宿舍", "building"),
        floor = CampusDataItem("1层", "floor"),
        room = CampusDataItem("101", "room"),
        controller = ElectricityController.C
    )

    private fun room() = ElectricityRoom(
        displayInfo = "余额 20 元",
        details = ElectricityRoomDetails(
            area = "campus",
            buildingName = "宿舍",
            areaName = "磬湖校区",
            floorName = "1层",
            floor = "floor",
            aid = "aid",
            account = "account",
            building = "building",
            room = "room",
            roomName = "101"
        )
    )

    private object EmptyPresetSuggestions : PresetSuggestions {
        override suspend fun rank(domain: SemanticDomain): List<PresetCandidate> = emptyList()
        override suspend fun markExposed(candidate: PresetCandidate): PresetInteractionToken? = null
        override suspend fun apply(candidate: PresetCandidate): AppliedPreset? = null
        override fun expire(token: PresetInteractionToken?) = Unit
        override suspend fun recordNaturalSubmission(
            submission: PresetSubmission,
            interactionToken: PresetInteractionToken?,
            candidatesAtOpportunity: List<PresetCandidate>
        ) = Unit
    }
}
