package com.ahu.ahutong.data.schedule

import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal object TeachingWeekPolicy {
    const val SCHEDULE_WEEK_COUNT = 20

    data class Position(
        val week: Int,
        val isInSemester: Boolean,
        val startDate: LocalDate
    )

    fun resolveRemotePosition(
        now: LocalDate,
        remoteWeekIndex: Int,
        remoteIsInSemester: Boolean,
        cachedStartDate: LocalDate?,
        officialStartDate: LocalDate?
    ): Position {
        val mondayOfCurrentWeek = now.minusDays((now.dayOfWeek.value - 1).toLong())
        val isInSemester = remoteIsInSemester && remoteWeekIndex > 0
        val startDate = when {
            isInSemester -> mondayOfCurrentWeek.minusWeeks((remoteWeekIndex - 1).toLong())
            officialStartDate != null -> officialStartDate
            cachedStartDate != null -> cachedStartDate
            else -> mondayOfCurrentWeek
        }
        val week = if (isInSemester) {
            remoteWeekIndex
        } else {
            weekForDate(startDate, now)
        }

        return Position(
            week = week.coerceIn(1, SCHEDULE_WEEK_COUNT),
            isInSemester = isInSemester,
            startDate = startDate
        )
    }

    fun weekForDate(startDate: LocalDate, date: LocalDate): Int {
        val days = ChronoUnit.DAYS.between(startDate, date)
        return ((days / 7L).toInt() + 1).coerceIn(1, SCHEDULE_WEEK_COUNT)
    }

    fun resolveLocalSemesterState(
        startDate: LocalDate,
        date: LocalDate,
        cachedState: Boolean?,
        stateObservedOn: LocalDate?
    ): Boolean {
        if (cachedState != null && stateObservedOn == date) return cachedState
        return !date.isBefore(startDate) && date.isBefore(startDate.plusWeeks(SCHEDULE_WEEK_COUNT.toLong()))
    }
}
