package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.model.Course
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleTimeRangeTest {
    @Test
    fun `single section uses its exact clock range`() {
        val course = Course().apply {
            setStartTime("1")
            setLength("1")
        }

        assertEquals(8 * 60..8 * 60 + 45, ScheduleViewModel.getCourseTimeRangeInMinutes(course))
    }

    @Test
    fun `multi section course ends at the last section`() {
        val course = Course().apply {
            setStartTime("4")
            setLength("3")
        }

        assertEquals(10 * 60 + 40..14 * 60 + 45, ScheduleViewModel.getCourseTimeRangeInMinutes(course))
    }
}
