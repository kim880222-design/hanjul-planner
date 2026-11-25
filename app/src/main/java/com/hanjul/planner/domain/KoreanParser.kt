package com.hanjul.planner.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** 한 줄 입력을 분석한 결과 */
data class ParsedInput(
    val type: ItemType,
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val endTime: LocalTime? = null,
    val repeat: Repeat = Repeat.NONE,
    val repeatDays: Int = 0,
    val steps: List<String> = emptyList(),
    /** steps 와 같은 순서의 단계별 마감일 (없으면 null) */
    val stepDates: List<LocalDate?> = emptyList(),
    val isDeadline: Boolean = false,
)

/**
 * "내일 3시 회의", "금요일까지 보고서", "매주 월요일 9시 팀회의",
 * "이사 준비: 짐 싸기, 업체 예약" 같은 한국어 문장을 분석한다.
 */
object KoreanParser {

    private const val DAY_CHARS = "월화수목금토일"
    private const val SUFFIX = "(?:에는|에|까지|부터|쯤|경|날)?"

    private val eventWords = listOf(
        "회의", "미팅", "약속", "모임", "만남", "생일", "생신", "결혼식", "돌잔치", "장례",
        "진료", "병원", "치과", "검진", "여행", "출장", "수업", "강의", "면접", "행사",
        "예약", "공연", "콘서트", "영화", "식사", "점심", "저녁", "회식", "데이트", "세미나",
        "상담", "레슨", "시험", "발표", "파티", "기념일",
    )

    private val fillerWords = setOf(
        "에", "에는", "까지", "부터", "마감", "은", "는", "날", "쯤", "경", "정도", "까지는", "때",
        "데드라인", "-", "~", ",",
    )

    private const val TIME =
        "(오전|오후|아침|점심|저녁|밤|새벽)?\\s*(\\d{1,2})\\s*(?:시(?!간)\\s*(?:(\\d{1,2})\\s*분|(반)(?=\\s|$|에|쯤|경|까지|부터|~|-))?|:(\\d{2}))"

    private class Work(var text: String) {
        fun take(regex: Regex): MatchResult? {
            val m = regex.find(text) ?: return null
            text = text.removeRange(m.range).let {
                it.substring(0, m.range.first) + " " + it.substring(m.range.first)
            }
            return m
        }
    }

    fun parse(input: String, today: LocalDate = LocalDate.now()): ParsedInput {
        val original = input.trim()
        if (original.isEmpty()) return ParsedInput(ItemType.TASK, "")

        var head = original
        var steps = emptyList<String>()
        var forceProject = false

        // 1) 프로젝트: "제목: 단계1, 단계2" (숫자 사이 콜론 3:30 은 제외)
        val colon = Regex("(?<!\\d)[:：]|[:：](?!\\d)").find(original)
        if (colon != null) {
            val tail = original.substring(colon.range.last + 1)
            val parts = tail.split(Regex("[,，、;]|(?<!\\d)/|/(?!\\d)|\\s+그리고\\s+|→|>"))
                .map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isNotEmpty()) {
                steps = parts
                head = original.substring(0, colon.range.first)
            }
        }
        val w = Work(" $head ")
        if (w.take(Regex("\\s프로젝트\\s|^\\s*프로젝트|프로젝트\\s*$")) != null) forceProject = true

        var date: LocalDate? = null
        var time: LocalTime? = null
        var endTime: LocalTime? = null
        var repeat = Repeat.NONE
        var repeatDays = 0
        var deadline = false

        fun noteSuffix(m: MatchResult) {
            if (m.value.trimEnd().endsWith("까지")) deadline = true
        }

        // 2) 반복
        w.take(Regex("매일$SUFFIX|날마다"))?.let { repeat = Repeat.DAILY }
        if (repeat == Repeat.NONE) {
            w.take(Regex("(?:매\\s*)?(?:평일|주중)(?:마다)?"))?.let { repeat = Repeat.WEEKDAYS }
        }
        if (repeat == Repeat.NONE) {
            w.take(Regex("매\\s*주말|주말마다"))?.let {
                repeat = Repeat.WEEKLY
                repeatDays = DayMask.of(listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
            }
        }
        if (repeat == Repeat.NONE) {
            val dayList = "[$DAY_CHARS](?:요일|욜)(?:\\s*[,·와과및/]?\\s*[$DAY_CHARS](?:요일|욜))*"
            val m = w.take(Regex("매\\s*주\\s*($dayList)?\\s*(?:마다)?$SUFFIX"))
                ?: w.take(Regex("($dayList)마다"))
            if (m != null) {
                repeat = Repeat.WEEKLY
                val days = m.groupValues[1]
                repeatDays = DayMask.of(weekdayChars(days))
            }
        }
        if (repeat == Repeat.NONE) {
            val m = w.take(Regex("(?:매\\s*달|매\\s*월|달마다)\\s*(?:(\\d{1,2})\\s*일)?$SUFFIX"))
            if (m != null) {
                repeat = Repeat.MONTHLY
                val day = m.groupValues[1].toIntOrNull()
                if (day != null && day in 1..31) {
                    date = monthDay(today.year, today.monthValue, day).let {
                        if (it.isBefore(today)) monthDay(today.plusMonths(1).year, today.plusMonths(1).monthValue, day) else it
                    }
                }
            }
        }
        if (repeat == Repeat.NONE) {
            val m = w.take(Regex("(?:매\\s*년|해마다)\\s*(?:(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일)?$SUFFIX"))
            if (m != null) {
                repeat = Repeat.YEARLY
                val mo = m.groupValues[1].toIntOrNull()
                val d = m.groupValues[2].toIntOrNull()
                if (mo != null && d != null && mo in 1..12 && d in 1..31) {
                    date = monthDay(today.year, mo, d).let { if (it.isBefore(today)) monthDay(today.year + 1, mo, d) else it }
                }
            }
        }

        // 3) 시간 (범위 먼저)
        val range = w.take(Regex("$TIME\\s*(?:~|-|부터)\\s*$TIME\\s*(?:까지)?"))
        if (range != null) {
            val g = range.groupValues
            val start = toTime(g[1], g[2], g[3], g[4], g[5], null)
            if (start != null) {
                time = start
                endTime = toTime(g[6], g[7], g[8], g[9], g[10], start)
            }
        } else {
            val m = w.take(Regex("$TIME$SUFFIX"))
            if (m != null) {
                val g = m.groupValues
                time = toTime(g[1], g[2], g[3], g[4], g[5], null)
                if (time != null) noteSuffix(m)
            } else {
                w.take(Regex("정오$SUFFIX"))?.let { time = LocalTime.NOON; noteSuffix(it) }
                    ?: w.take(Regex("자정$SUFFIX"))?.let { time = LocalTime.MIDNIGHT; noteSuffix(it) }
            }
        }

        // 4) 날짜
        if (date == null) date = takeDate(w, today) { deadline = true }

        // 반복인데 날짜가 없으면 오늘 이후 첫 날짜
        if (repeat != Repeat.NONE) {
            val start = date ?: today
            date = Recurrence.nextOnOrAfter(start, repeat, repeatDays, start) ?: start
        }

        // 5) 마감 표현
        if (w.take(Regex("까지|마감|데드라인")) != null) deadline = true

        // 6) 제목 정리
        var title = w.text.split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in fillerWords }
            .joinToString(" ")
            .trim(' ', ',', '.', '-', '~', '!')
        if (title.isEmpty()) title = if (head.isNotBlank()) head.trim() else original

        val type = when {
            steps.isNotEmpty() || forceProject -> ItemType.PROJECT
            deadline -> ItemType.TASK
            time != null -> ItemType.EVENT
            eventWords.any { title.contains(it) } && date != null -> ItemType.EVENT
            else -> ItemType.TASK
        }

        // 단계별 마감일: "짐 싸기 12/1" -> 제목 "짐 싸기", 마감 12월 1일
        val stepParsed = steps.map { part ->
            val sp = parse(part, today)
            if (sp.date != null && sp.title.isNotBlank()) sp.title to sp.date else part to null
        }
        steps = stepParsed.map { it.first }
        val stepDates = stepParsed.map { it.second }
        if (type == ItemType.PROJECT && date == null) date = stepDates.filterNotNull().maxOrNull()

        // 날짜 없이 시간만 쓰면: 이미 지난 시간이면 내일, 아니면 오늘
        if (date == null && time != null) {
            date = if (time!!.isBefore(LocalTime.now()) && today == LocalDate.now()) today.plusDays(1) else today
        }

        return ParsedInput(
            type = type,
            title = title,
            date = date,
            time = time,
            endTime = endTime,
            repeat = repeat,
            repeatDays = repeatDays,
            steps = steps,
            stepDates = stepDates,
            isDeadline = deadline,
        )
    }

    private fun weekdayChars(s: String): List<DayOfWeek> {
        // "월요일, 수요일" -> [MON, WED]; "요일"의 '일'은 일요일이 아님
        val cleaned = s.replace("요일", "").replace("욜", "")
        return cleaned.filter { it in DAY_CHARS }.map { dayOf(it) }.distinct()
    }

    private fun dayOf(c: Char): DayOfWeek = when (c) {
        '월' -> DayOfWeek.MONDAY
        '화' -> DayOfWeek.TUESDAY
        '수' -> DayOfWeek.WEDNESDAY
        '목' -> DayOfWeek.THURSDAY
        '금' -> DayOfWeek.FRIDAY
        '토' -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun monthDay(year: Int, month: Int, day: Int): LocalDate {
        val first = LocalDate.of(year, month, 1)
        return first.withDayOfMonth(minOf(day, first.lengthOfMonth()))
    }

    private fun toTime(
        marker: String, hourS: String, minS: String, half: String, colonMin: String,
        startForEnd: LocalTime?,
    ): LocalTime? {
        var h = hourS.toIntOrNull() ?: return null
        val min = when {
            colonMin.isNotEmpty() -> colonMin.toInt()
            half.isNotEmpty() -> 30
            minS.isNotEmpty() -> minS.toInt()
            else -> 0
        }
        if (h > 24 || min > 59) return null
        if (h == 24) h = 0
        when (marker) {
            "오전", "아침", "새벽" -> if (h == 12) h = 0
            "오후", "저녁", "밤" -> if (h < 12) h += 12
            "점심" -> if (h in 1..4) h += 12
            else -> {
                if (startForEnd != null) {
                    // 끝 시간: 시작보다 앞이면 오후로
                    if (h < 12 && h < startForEnd.hour && h + 12 > startForEnd.hour) h += 12
                } else if (colonMin.isEmpty() || h < 7) {
                    // "3시" -> 오후 3시, "7시"~"11시" -> 오전, "12시" -> 낮 12시
                    if (h in 1..6) h += 12
                }
            }
        }
        if (h > 23) return null
        return LocalTime.of(h, min)
    }

    private fun takeDate(w: Work, today: LocalDate, onDeadline: () -> Unit): LocalDate? {
        fun hit(m: MatchResult) { if (m.value.trimEnd().endsWith("까지")) onDeadline() }
        val daysRe = "([$DAY_CHARS])(?:요일|욜)"

        w.take(Regex("내일\\s*모레$SUFFIX|모레$SUFFIX"))?.let { hit(it); return today.plusDays(2) }
        w.take(Regex("오늘$SUFFIX"))?.let { hit(it); return today }
        w.take(Regex("내일$SUFFIX"))?.let { hit(it); return today.plusDays(1) }
        w.take(Regex("글피$SUFFIX"))?.let { hit(it); return today.plusDays(3) }

        w.take(Regex("(\\d{1,3})\\s*일\\s*(?:뒤|후)$SUFFIX"))?.let {
            hit(it); return today.plusDays(it.groupValues[1].toLong())
        }
        w.take(Regex("(\\d{1,2})\\s*주\\s*(?:뒤|후)$SUFFIX"))?.let {
            hit(it); return today.plusWeeks(it.groupValues[1].toLong())
        }
        w.take(Regex("(\\d{1,2})\\s*(?:개월|달)\\s*(?:뒤|후)$SUFFIX"))?.let {
            hit(it); return today.plusMonths(it.groupValues[1].toLong())
        }

        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        w.take(Regex("(다음|다다음|이번)\\s*주\\s*$daysRe$SUFFIX"))?.let {
            hit(it)
            val weeks = when (it.groupValues[1]) { "다음" -> 1L; "다다음" -> 2L; else -> 0L }
            return monday.plusWeeks(weeks).plusDays((dayOf(it.groupValues[2][0]).value - 1).toLong())
        }
        w.take(Regex("(다음|이번)\\s*주\\s*말$SUFFIX|주말$SUFFIX"))?.let {
            hit(it)
            val sat = monday.plusDays(5)
            return if (it.groupValues[1] == "다음") sat.plusWeeks(1) else sat
        }
        w.take(Regex("다음\\s*주$SUFFIX"))?.let { hit(it); return monday.plusWeeks(1) }

        w.take(Regex("다음\\s*달\\s*(\\d{1,2})\\s*일$SUFFIX"))?.let {
            hit(it)
            val nm = today.plusMonths(1)
            return monthDay(nm.year, nm.monthValue, it.groupValues[1].toInt().coerceIn(1, 31))
        }
        w.take(Regex("다음\\s*달\\s*(?:말|끝)$SUFFIX"))?.let {
            hit(it); return today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
        }
        w.take(Regex("이번\\s*달\\s*(?:말|안|끝)$SUFFIX|월말$SUFFIX"))?.let {
            hit(it); return today.with(TemporalAdjusters.lastDayOfMonth())
        }
        w.take(Regex("다음\\s*달$SUFFIX"))?.let { hit(it); return today.plusMonths(1).withDayOfMonth(1) }

        w.take(Regex("(\\d{4})\\s*[.\\-/년]\\s*(\\d{1,2})\\s*[.\\-/월]\\s*(\\d{1,2})\\s*일?$SUFFIX"))?.let {
            hit(it)
            val (y, mo, d) = it.destructured
            if (mo.toInt() in 1..12) return monthDay(y.toInt(), mo.toInt(), d.toInt().coerceIn(1, 31))
        }
        w.take(Regex("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일$SUFFIX"))?.let {
            hit(it)
            val mo = it.groupValues[1].toInt()
            val d = it.groupValues[2].toInt().coerceIn(1, 31)
            if (mo in 1..12) {
                val c = monthDay(today.year, mo, d)
                return if (c.isBefore(today)) monthDay(today.year + 1, mo, d) else c
            }
        }
        w.take(Regex("(?<![\\d:])(\\d{1,2})/(\\d{1,2})(?![\\d:])$SUFFIX"))?.let {
            hit(it)
            val mo = it.groupValues[1].toInt()
            val d = it.groupValues[2].toInt().coerceIn(1, 31)
            if (mo in 1..12) {
                val c = monthDay(today.year, mo, d)
                return if (c.isBefore(today)) monthDay(today.year + 1, mo, d) else c
            }
        }
        w.take(Regex("(?<!\\d)(\\d{1,2})\\s*일(?![\\d간])$SUFFIX"))?.let {
            hit(it)
            val d = it.groupValues[1].toInt()
            if (d in 1..31) {
                val c = monthDay(today.year, today.monthValue, d)
                return if (c.isBefore(today)) {
                    val nm = today.plusMonths(1); monthDay(nm.year, nm.monthValue, d)
                } else c
            }
        }
        w.take(Regex("$daysRe$SUFFIX"))?.let {
            hit(it)
            return today.with(TemporalAdjusters.nextOrSame(dayOf(it.groupValues[1][0])))
        }
        return null
    }
}
