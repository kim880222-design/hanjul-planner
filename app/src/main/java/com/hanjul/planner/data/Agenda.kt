package com.hanjul.planner.data

import com.hanjul.planner.domain.ItemType
import com.hanjul.planner.domain.Recurrence
import com.hanjul.planner.domain.Repeat
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 화면에 보여줄 한 줄: 반복 일정은 날짜마다 따로 생긴다 */
data class Entry(val item: Item, val date: LocalDate?)

enum class Urgency { OVERDUE, TODAY, SOON, LATER, NONE }

/** 마감일이 있는 프로젝트 단계 한 줄 */
data class StepEntry(val step: Step, val project: Item)

object Agenda {

    const val SOON_DAYS = 3

    fun urgency(item: Item, today: LocalDate): Urgency {
        if (item.type == ItemType.EVENT) return Urgency.NONE
        return urgency(item.date, item.done, today)
    }

    fun urgency(step: Step, today: LocalDate): Urgency = urgency(step.date, step.done, today)

    fun urgency(date: LocalDate?, done: Boolean, today: LocalDate): Urgency {
        val d = date ?: return Urgency.NONE
        if (done) return Urgency.NONE
        val diff = ChronoUnit.DAYS.between(today, d)
        return when {
            diff < 0 -> Urgency.OVERDUE
            diff == 0L -> Urgency.TODAY
            diff <= SOON_DAYS -> Urgency.SOON
            else -> Urgency.LATER
        }
    }

    /** 특정 날짜의 일정 (반복 포함) */
    fun eventsOn(items: List<Item>, date: LocalDate): List<Entry> =
        items.filter { it.type == ItemType.EVENT && it.date != null &&
            Recurrence.occursOn(it.date, it.repeat, it.repeatDays, date) }
            .sortedWith(compareBy(nullsFirst()) { it.time })
            .map { Entry(it, date) }

    /** 특정 날짜가 마감인 할 일·프로젝트 (반복 할 일은 다음 날짜 기준) */
    fun dueOn(items: List<Item>, date: LocalDate): List<Entry> =
        items.filter { it.type != ItemType.EVENT && it.date != null && !it.done &&
            (it.date == date || (it.repeat != Repeat.NONE && it.date.isBefore(date) &&
                Recurrence.occursOn(it.date, it.repeat, it.repeatDays, date))) }
            .sortedWith(compareBy(nullsLast()) { it.time })
            .map { Entry(it, date) }

    fun overdue(items: List<Item>, today: LocalDate): List<Item> =
        items.filter { it.type != ItemType.EVENT && !it.done && it.date != null && it.date.isBefore(today) }
            .sortedBy { it.date }

    fun dueSoon(items: List<Item>, today: LocalDate): List<Item> =
        items.filter { it.type != ItemType.EVENT && !it.done && it.date != null &&
            !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(SOON_DAYS.toLong())) }
            .sortedBy { it.date }

    fun undatedTasks(items: List<Item>): List<Item> =
        items.filter { it.type == ItemType.TASK && !it.done && it.date == null }

    /** 날짜 범위 안에 무언가 있는 날짜들 (달력 점 표시용) */
    fun markers(items: List<Item>, steps: List<Step>, from: LocalDate, to: LocalDate): Map<LocalDate, Pair<Boolean, Boolean>> {
        val map = HashMap<LocalDate, Pair<Boolean, Boolean>>()
        for (e in datedSteps(items, steps)) {
            val day = e.step.date!!
            if (day.isBefore(from) || day.isAfter(to)) continue
            val cur = map[day] ?: (false to false); map[day] = cur.first to true
        }
        for (it in items) {
            val d = it.date ?: continue
            if (it.type == ItemType.EVENT) {
                Recurrence.occurrencesBetween(d, it.repeat, it.repeatDays, from, to).forEach { day ->
                    val cur = map[day] ?: (false to false); map[day] = true to cur.second
                }
            } else if (!it.done) {
                val days = if (it.repeat == Repeat.NONE) listOf(d).filter { !it.isBefore(from) && !it.isAfter(to) }
                else Recurrence.occurrencesBetween(d, it.repeat, it.repeatDays, from, to)
                days.forEach { day -> val cur = map[day] ?: (false to false); map[day] = cur.first to true }
            }
        }
        return map
    }

    // ---- 프로젝트 단계 마감 ----
    private fun datedSteps(items: List<Item>, steps: List<Step>): List<StepEntry> {
        val projects = items.filter { it.type == ItemType.PROJECT }.associateBy { it.id }
        return steps.filter { !it.done && it.date != null }
            .mapNotNull { s -> projects[s.projectId]?.let { StepEntry(s, it) } }
            .sortedBy { it.step.date }
    }

    fun stepsOverdue(items: List<Item>, steps: List<Step>, today: LocalDate): List<StepEntry> =
        datedSteps(items, steps).filter { it.step.date!!.isBefore(today) }

    fun stepsDueSoon(items: List<Item>, steps: List<Step>, today: LocalDate): List<StepEntry> =
        datedSteps(items, steps).filter {
            val d = it.step.date!!
            !d.isBefore(today) && !d.isAfter(today.plusDays(SOON_DAYS.toLong()))
        }

    fun stepsDueOn(items: List<Item>, steps: List<Step>, date: LocalDate): List<StepEntry> =
        datedSteps(items, steps).filter { it.step.date == date }

    fun allDatedSteps(items: List<Item>, steps: List<Step>): List<StepEntry> = datedSteps(items, steps)

    fun nextStep(steps: List<Step>): Step? = steps.sortedBy { it.position }.firstOrNull { !it.done }

    fun progress(steps: List<Step>): Pair<Int, Int> = steps.count { it.done } to steps.size
}
