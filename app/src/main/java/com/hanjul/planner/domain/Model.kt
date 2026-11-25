package com.hanjul.planner.domain

import java.time.DayOfWeek
import java.time.LocalDate

/** 항목 종류: 할 일, 일정, 프로젝트 */
enum class ItemType(val label: String) {
    TASK("할 일"),
    EVENT("일정"),
    PROJECT("프로젝트"),
}

/** 반복 방식 */
enum class Repeat(val label: String) {
    NONE("반복 안 함"),
    DAILY("매일"),
    WEEKDAYS("평일마다"),
    WEEKLY("매주"),
    MONTHLY("매달"),
    YEARLY("매년"),
}

/** 요일 비트마스크: 월=1, 화=2, 수=4 ... 일=64 */
object DayMask {
    fun of(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun of(days: Collection<DayOfWeek>): Int = days.fold(0) { acc, d -> acc or of(d) }
    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and of(day) != 0
    fun days(mask: Int): List<DayOfWeek> = DayOfWeek.values().filter { contains(mask, it) }
}

/**
 * 반복 규칙 계산. 시작 날짜(start)와 규칙으로 특정 날짜에 해당하는지,
 * 또는 어떤 날짜 이후의 다음 날짜가 언제인지 계산한다.
 */
object Recurrence {

    fun occursOn(start: LocalDate, repeat: Repeat, dayMask: Int, date: LocalDate): Boolean {
        if (date.isBefore(start)) return false
        return when (repeat) {
            Repeat.NONE -> date == start
            Repeat.DAILY -> true
            Repeat.WEEKDAYS -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
            Repeat.WEEKLY -> {
                val mask = if (dayMask == 0) DayMask.of(start.dayOfWeek) else dayMask
                DayMask.contains(mask, date.dayOfWeek)
            }
            Repeat.MONTHLY -> {
                val wanted = start.dayOfMonth
                val actual = minOf(wanted, date.lengthOfMonth())
                date.dayOfMonth == actual
            }
            Repeat.YEARLY -> {
                if (date.monthValue != start.monthValue) return false
                val actual = minOf(start.dayOfMonth, date.lengthOfMonth())
                date.dayOfMonth == actual
            }
        }
    }

    /** from 날짜(포함) 이후 처음 해당하는 날짜. 없으면 null. */
    fun nextOnOrAfter(start: LocalDate, repeat: Repeat, dayMask: Int, from: LocalDate): LocalDate? {
        if (repeat == Repeat.NONE) return if (from.isAfter(start)) null else start
        var d = if (from.isBefore(start)) start else from
        // 최대 2년 + 하루 안에 반드시 한 번은 나온다
        repeat(800) {
            if (occursOn(start, repeat, dayMask, d)) return d
            d = d.plusDays(1)
        }
        return null
    }

    /** 주어진 범위 안의 모든 해당 날짜 */
    fun occurrencesBetween(
        start: LocalDate, repeat: Repeat, dayMask: Int,
        from: LocalDate, toInclusive: LocalDate,
    ): List<LocalDate> {
        val out = ArrayList<LocalDate>()
        var d = if (from.isBefore(start)) start else from
        while (!d.isAfter(toInclusive)) {
            if (occursOn(start, repeat, dayMask, d)) out += d
            if (repeat == Repeat.NONE) break
            d = d.plusDays(1)
        }
        return out
    }

    fun describe(repeat: Repeat, dayMask: Int, start: LocalDate?): String = when (repeat) {
        Repeat.NONE -> ""
        Repeat.DAILY -> "매일"
        Repeat.WEEKDAYS -> "평일마다"
        Repeat.WEEKLY -> {
            val mask = if (dayMask == 0 && start != null) DayMask.of(start.dayOfWeek) else dayMask
            "매주 " + DayMask.days(mask).joinToString(",") { KoreanDate.dayShort(it) }
        }
        Repeat.MONTHLY -> if (start != null) "매달 ${start.dayOfMonth}일" else "매달"
        Repeat.YEARLY -> if (start != null) "매년 ${start.monthValue}월 ${start.dayOfMonth}일" else "매년"
    }
}
