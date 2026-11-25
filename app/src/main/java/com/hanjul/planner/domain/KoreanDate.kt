package com.hanjul.planner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** 날짜·시간을 한국어로 보기 좋게 바꾸는 도우미 */
object KoreanDate {

    fun dayShort(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "월"
        DayOfWeek.TUESDAY -> "화"
        DayOfWeek.WEDNESDAY -> "수"
        DayOfWeek.THURSDAY -> "목"
        DayOfWeek.FRIDAY -> "금"
        DayOfWeek.SATURDAY -> "토"
        DayOfWeek.SUNDAY -> "일"
    }

    fun date(d: LocalDate, today: LocalDate = LocalDate.now()): String {
        val base = if (d.year == today.year) "${d.monthValue}월 ${d.dayOfMonth}일"
        else "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일"
        val rel = when (ChronoUnit.DAYS.between(today, d)) {
            0L -> " 오늘"
            1L -> " 내일"
            2L -> " 모레"
            -1L -> " 어제"
            else -> ""
        }
        return "$base(${dayShort(d.dayOfWeek)})$rel"
    }

    fun time(t: LocalTime): String {
        val h = t.hour
        val ampm = if (h < 12) "오전" else "오후"
        val h12 = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return if (t.minute == 0) "$ampm ${h12}시" else "$ampm ${h12}:${"%02d".format(t.minute)}"
    }

    /** D-day 표시: 오늘=D-day, 내일=D-1, 지남=D+2 */
    fun dday(due: LocalDate, today: LocalDate = LocalDate.now()): String {
        val diff = ChronoUnit.DAYS.between(today, due)
        return when {
            diff == 0L -> "오늘 마감"
            diff > 0 -> "D-$diff"
            else -> "${-diff}일 지남"
        }
    }

    fun reminder(minutes: Int): String = when {
        minutes < 0 -> "알림 없음"
        minutes == 0 -> "정시에 알림"
        minutes < 60 -> "${minutes}분 전 알림"
        minutes % 1440 == 0 -> "${minutes / 1440}일 전 알림"
        minutes % 60 == 0 -> "${minutes / 60}시간 전 알림"
        else -> "${minutes}분 전 알림"
    }
}
