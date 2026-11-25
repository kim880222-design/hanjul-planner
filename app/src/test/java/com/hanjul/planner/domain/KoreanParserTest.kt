package com.hanjul.planner.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class KoreanParserTest {
    // 2026-11-25 은 수요일
    private val today = LocalDate.of(2026, 11, 25)
    private fun p(s: String) = KoreanParser.parse(s, today)

    @Test fun tomorrowMeeting() {
        val r = p("내일 3시 회의")
        assertEquals(ItemType.EVENT, r.type)
        assertEquals("회의", r.title)
        assertEquals(LocalDate.of(2026, 11, 26), r.date)
        assertEquals(LocalTime.of(15, 0), r.time)
    }

    @Test fun particleAfterTime() {
        val r = p("내일 오전 10시 반에 치과")
        assertEquals("치과", r.title)
        assertEquals(LocalTime.of(10, 30), r.time)
        assertEquals(ItemType.EVENT, r.type)
    }

    @Test fun plainTask() {
        val r = p("우유 사기")
        assertEquals(ItemType.TASK, r.type)
        assertEquals("우유 사기", r.title)
        assertNull(r.date)
    }

    @Test fun deadline() {
        val r = p("금요일까지 보고서")
        assertEquals(ItemType.TASK, r.type)
        assertEquals(true, r.isDeadline)
        assertEquals("보고서", r.title)
        assertEquals(LocalDate.of(2026, 11, 27), r.date)
    }

    @Test fun deadlineWithMonthDay() {
        val r = p("보고서 제출 12월 3일까지")
        assertEquals(ItemType.TASK, r.type)
        assertEquals(LocalDate.of(2026, 12, 3), r.date)
        assertEquals("보고서 제출", r.title)
    }

    @Test fun project() {
        val r = p("이사 준비: 짐 싸기, 업체 예약, 주소 변경")
        assertEquals(ItemType.PROJECT, r.type)
        assertEquals("이사 준비", r.title)
        assertEquals(listOf("짐 싸기", "업체 예약", "주소 변경"), r.steps)
    }

    @Test fun projectWithDeadline() {
        val r = p("12월 20일까지 발표 준비: 자료조사, 슬라이드, 리허설")
        assertEquals(ItemType.PROJECT, r.type)
        assertEquals("발표 준비", r.title)
        assertEquals(LocalDate.of(2026, 12, 20), r.date)
        assertEquals(3, r.steps.size)
    }

    @Test fun projectStepDates() {
        val r = p("이사 준비: 짐 싸기 12/1, 업체 예약 금요일까지, 주소 변경")
        assertEquals(listOf("짐 싸기", "업체 예약", "주소 변경"), r.steps)
        assertEquals(listOf(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 11, 27), null), r.stepDates)
        assertEquals(LocalDate.of(2026, 12, 1), r.date)
    }

    @Test fun colonTimeIsNotProject() {
        val r = p("내일 14:30 미팅")
        assertEquals(ItemType.EVENT, r.type)
        assertEquals(LocalTime.of(14, 30), r.time)
        assertEquals("미팅", r.title)
    }

    @Test fun weeklyRepeat() {
        val r = p("매주 월요일 9시 팀회의")
        assertEquals(ItemType.EVENT, r.type)
        assertEquals(Repeat.WEEKLY, r.repeat)
        assertEquals(DayMask.of(DayOfWeek.MONDAY), r.repeatDays)
        assertEquals(LocalDate.of(2026, 11, 30), r.date)
        assertEquals(LocalTime.of(9, 0), r.time)
        assertEquals("팀회의", r.title)
    }

    @Test fun weeklyMultipleDays() {
        val r = p("매주 월요일, 수요일 저녁 7시 운동")
        assertEquals(Repeat.WEEKLY, r.repeat)
        assertEquals(DayMask.of(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)), r.repeatDays)
        assertEquals(today, r.date)
        assertEquals(LocalTime.of(19, 0), r.time)
        assertEquals("운동", r.title)
    }

    @Test fun dailyRepeat() {
        val r = p("매일 8시 운동")
        assertEquals(Repeat.DAILY, r.repeat)
        assertEquals(LocalTime.of(8, 0), r.time)
        assertEquals("운동", r.title)
    }

    @Test fun monthlyRent() {
        val r = p("매달 25일 월세")
        assertEquals(Repeat.MONTHLY, r.repeat)
        assertEquals(LocalDate.of(2026, 11, 25), r.date)
        assertEquals("월세", r.title)
        assertEquals(ItemType.TASK, r.type)
    }

    @Test fun monthlyPastDay() {
        val r = p("매월 10일 카드값")
        assertEquals(LocalDate.of(2026, 12, 10), r.date)
    }

    @Test fun yearlyBirthday() {
        val r = p("매년 3월 5일 엄마 생신")
        assertEquals(Repeat.YEARLY, r.repeat)
        assertEquals(LocalDate.of(2027, 3, 5), r.date)
        assertEquals(ItemType.EVENT, r.type)
        assertEquals("엄마 생신", r.title)
    }

    @Test fun nextWeekDay() {
        val r = p("다음주 화요일 저녁 약속")
        assertEquals(LocalDate.of(2026, 12, 1), r.date)
        assertEquals(ItemType.EVENT, r.type)
        assertEquals("저녁 약속", r.title)
    }

    @Test fun timeRange() {
        val r = p("모레 2시~4시 세미나")
        assertEquals(LocalDate.of(2026, 11, 27), r.date)
        assertEquals(LocalTime.of(14, 0), r.time)
        assertEquals(LocalTime.of(16, 0), r.endTime)
        assertEquals("세미나", r.title)
    }

    @Test fun daysLater() {
        val r = p("3일 뒤 택배 반품")
        assertEquals(LocalDate.of(2026, 11, 28), r.date)
        assertEquals("택배 반품", r.title)
    }

    @Test fun slashDate() {
        val r = p("12/24 크리스마스 파티")
        assertEquals(LocalDate.of(2026, 12, 24), r.date)
        assertEquals(ItemType.EVENT, r.type)
    }

    @Test fun durationNotTime() {
        val r = p("3시간 공부")
        assertNull(r.time)
        assertEquals("3시간 공부", r.title)
    }

    @Test fun halfChanNotHalfHour() {
        val r = p("내일 반찬 사기")
        assertNull(r.time)
        assertEquals("반찬 사기", r.title)
    }

    @Test fun wordsWithDayCharsNotWeekly() {
        val r = p("매주 수영 가기")
        assertEquals(Repeat.WEEKLY, r.repeat)
        assertEquals(0, r.repeatDays)
        assertEquals("수영 가기", r.title)
    }

    @Test fun recurrenceMonthlyEndOfMonth() {
        val start = LocalDate.of(2026, 1, 31)
        assertEquals(true, Recurrence.occursOn(start, Repeat.MONTHLY, 0, LocalDate.of(2026, 2, 28)))
        assertEquals(false, Recurrence.occursOn(start, Repeat.MONTHLY, 0, LocalDate.of(2026, 2, 27)))
    }

    @Test fun recurrenceWeekdays() {
        val list = Recurrence.occurrencesBetween(today, Repeat.WEEKDAYS, 0, today, today.plusDays(6))
        assertEquals(5, list.size)
    }
}
