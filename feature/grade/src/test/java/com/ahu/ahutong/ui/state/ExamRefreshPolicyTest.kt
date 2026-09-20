package com.ahu.ahutong.ui.state

import com.ahu.ahutong.data.model.Exam
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExamRefreshPolicyTest {
    @Test
    fun `recent exam snapshot skips automatic network refresh`() {
        val now = 10_000_000L

        assertFalse(
            ExamRefreshPolicy.shouldRefresh(
                cachedAtMillis = now - ExamRefreshPolicy.AUTO_REFRESH_INTERVAL_MS + 1L,
                nowMillis = now
            )
        )
        assertTrue(
            ExamRefreshPolicy.shouldRefresh(
                cachedAtMillis = now - ExamRefreshPolicy.AUTO_REFRESH_INTERVAL_MS,
                nowMillis = now
            )
        )
    }

    @Test
    fun `missing or future exam timestamp refreshes safely`() {
        assertTrue(ExamRefreshPolicy.shouldRefresh(cachedAtMillis = 0L, nowMillis = 10L))
        assertTrue(ExamRefreshPolicy.shouldRefresh(cachedAtMillis = 11L, nowMillis = 10L))
    }

    @Test
    fun `exam comparison uses visible values instead of object identity`() {
        val first = listOf(exam(course = "高等数学", seat = "18"))
        val same = listOf(exam(course = "高等数学", seat = "18"))
        val changed = listOf(exam(course = "高等数学", seat = "19"))

        assertTrue(first.hasSameExamContents(same))
        assertFalse(first.hasSameExamContents(changed))
    }

    private fun exam(course: String, seat: String) = Exam().apply {
        this.course = course
        location = "磬苑校区-博学楼-A101"
        time = "2026-09-01 09:00~11:00"
        seatNum = seat
        finished = false
    }
}
