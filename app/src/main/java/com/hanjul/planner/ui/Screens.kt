package com.hanjul.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hanjul.planner.data.Agenda
import com.hanjul.planner.data.Item
import com.hanjul.planner.data.Step
import com.hanjul.planner.domain.ItemType
import com.hanjul.planner.domain.KoreanDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

class Actions(
    val open: (Item) -> Unit,
    val toggle: (Item) -> Unit,
    val openProject: (Long) -> Unit,
    val toggleStep: (Step) -> Unit,
)

private fun stepsOf(all: List<Step>, id: Long) = all.filter { it.projectId == id }

// ───────────────────────── 현황 ─────────────────────────

@Composable
fun DashboardScreen(items: List<Item>, steps: List<Step>, today: LocalDate, act: Actions) {
    val overdue = Agenda.overdue(items, today)
    val soon = Agenda.dueSoon(items, today)
    val stepOverdue = Agenda.stepsOverdue(items, steps, today)
    val stepSoon = Agenda.stepsDueSoon(items, steps, today)
    val todaySteps = Agenda.stepsDueOn(items, steps, today)
    val todayEvents = Agenda.eventsOn(items, today)
    val todayDue = Agenda.dueOn(items, today)
    val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    var weekEvents = 0
    var d = today
    while (!d.isAfter(sunday)) { weekEvents += Agenda.eventsOn(items, d).size; d = d.plusDays(1) }
    val projects = items.filter { it.type == ItemType.PROJECT && !it.done }
    val doneThisWeek = items.count {
        it.done && it.doneAt != null && it.doneAt > System.currentTimeMillis() - 7L * 24 * 3600 * 1000
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(start = 14.dp, top = 14.dp, end = 14.dp)) {
                Text("SYSTEM STATUS // 전체 현황", color = Pal.Gray5, style = MaterialTheme.typography.labelMedium)
                Text(
                    "%04d.%02d.%02d %s".format(today.year, today.monthValue, today.dayOfMonth, KoreanDate.dayShort(today.dayOfWeek) + "요일"),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Pal.Amber,
                )
            }
        }
        item {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("오늘 할 일", todayDue.size + todayEvents.size + todaySteps.size, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                StatCard("마감 지남", overdue.size + stepOverdue.size, Overdue, Modifier.weight(1f))
                StatCard("3일 안 마감", soon.size + stepSoon.size, Soon, Modifier.weight(1f))
            }
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("이번 주 일정", weekEvents, EventColor, Modifier.weight(1f))
                StatCard("진행 중 프로젝트", projects.size, ProjectColor, Modifier.weight(1f))
                StatCard("7일간 완료", doneThisWeek, TaskColor, Modifier.weight(1f))
            }
        }
        if (overdue.isNotEmpty() || stepOverdue.isNotEmpty()) {
            item { SectionTitle("!! 마감 지남", Overdue) }
            items(overdue, key = { "o${it.id}" }) {
                ItemRow(it, today, { act.open(it) }, { act.toggle(it) }, steps = stepsOf(steps, it.id))
            }
            items(stepOverdue, key = { "os${it.step.id}" }) { e ->
                StepRow(e, today, { act.openProject(e.project.id) }, { act.toggleStep(e.step) })
            }
        }
        if (soon.isNotEmpty() || stepSoon.isNotEmpty()) {
            item { SectionTitle("곧 마감 (3일 안)", Soon) }
            items(soon, key = { "s${it.id}" }) {
                ItemRow(it, today, { act.open(it) }, { act.toggle(it) }, steps = stepsOf(steps, it.id))
            }
            items(stepSoon, key = { "ss${it.step.id}" }) { e ->
                StepRow(e, today, { act.openProject(e.project.id) }, { act.toggleStep(e.step) })
            }
        }
        item { SectionTitle("프로젝트 진행", ProjectColor) }
        if (projects.isEmpty()) item { EmptyHint("진행 중인 프로젝트가 없어요. \"이사 준비: 짐 싸기, 업체 예약\"처럼 입력해 보세요.") }
        items(projects, key = { "p${it.id}" }) { p ->
            val ps = stepsOf(steps, p.id)
            val (done, total) = Agenda.progress(ps)
            Column(
                Modifier.fillMaxWidth().clickable { act.open(p) }.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▤ " + p.title, style = MaterialTheme.typography.bodyLarge, color = Pal.Text, modifier = Modifier.weight(1f))
                    Text("$done/$total", style = MaterialTheme.typography.bodyMedium, color = ProjectColor)
                    p.date?.let { due ->
                        val u = Agenda.urgency(p, today)
                        if (u != com.hanjul.planner.data.Urgency.LATER) {
                            Text("  "); UrgencyBadge(due, today, u)
                        }
                    }
                }
                TextProgress(done, total)
                Agenda.nextStep(ps)?.let {
                    Text("  └ 다음: ${it.title}" + (it.date?.let { d -> " | ${KoreanDate.dday(d, today)}" } ?: ""), style = MaterialTheme.typography.bodySmall,
                        color = Pal.Gray5)
                }
            }
        }
        item { Gap(24) }
    }
}

@Composable
private fun StatCard(label: String, value: Int, color: Color, modifier: Modifier) {
    Column(modifier.border(1.dp, if (value > 0) color else Pal.Gray3).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(value.toString().padStart(2, '0'), fontSize = 30.sp, fontWeight = FontWeight.Bold,
            color = if (value > 0) color else Pal.Gray4)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Pal.Gray5, maxLines = 1)
    }
}

// ───────────────────────── 오늘 ─────────────────────────

@Composable
fun TodayScreen(items: List<Item>, steps: List<Step>, today: LocalDate, act: Actions) {
    val overdue = Agenda.overdue(items, today)
    val stepOverdue = Agenda.stepsOverdue(items, steps, today)
    val events = Agenda.eventsOn(items, today)
    val due = Agenda.dueOn(items, today)
    val stepDue = Agenda.stepsDueOn(items, steps, today)
    val anytime = Agenda.undatedTasks(items)
    val projects = items.filter { it.type == ItemType.PROJECT && !it.done }
    val doneToday = items.filter { it.done && it.type == ItemType.TASK && it.doneAt != null &&
        it.doneAt > today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }

    LazyColumn(Modifier.fillMaxSize()) {
        if (overdue.isNotEmpty() || stepOverdue.isNotEmpty()) {
            item { SectionTitle("!! 마감 지남", Overdue) }
            items(overdue, key = { "o${it.id}" }) { ItemRow(it, today, { act.open(it) }, { act.toggle(it) }, steps = stepsOf(steps, it.id)) }
            items(stepOverdue, key = { "os${it.step.id}" }) { e ->
                StepRow(e, today, { act.openProject(e.project.id) }, { act.toggleStep(e.step) })
            }
        }
        item { SectionTitle("오늘 일정", EventColor) }
        if (events.isEmpty()) item { EmptyHint("오늘 일정이 없어요.") }
        items(events, key = { "e${it.item.id}" }) { e ->
            ItemRow(e.item, today, { act.open(e.item) }, { act.toggle(e.item) }, showDate = false, occurrence = e.date)
        }
        item { SectionTitle("오늘까지 할 일", TaskColor) }
        if (due.isEmpty() && stepDue.isEmpty()) item { EmptyHint("오늘 마감인 일이 없어요.") }
        items(due, key = { "d${it.item.id}" }) { e ->
            ItemRow(e.item, today, { act.open(e.item) }, { act.toggle(e.item) }, showDate = false, steps = stepsOf(steps, e.item.id))
        }
        items(stepDue, key = { "ds${it.step.id}" }) { e ->
            StepRow(e, today, { act.openProject(e.project.id) }, { act.toggleStep(e.step) }, showDate = false)
        }
        if (projects.isNotEmpty()) {
            item { SectionTitle("프로젝트 다음 단계", ProjectColor) }
            items(projects, key = { "p${it.id}" }) { p ->
                ItemRow(p, today, { act.open(p) }, {}, steps = stepsOf(steps, p.id))
            }
        }
        if (anytime.isNotEmpty()) {
            item { SectionTitle("언제든 할 일") }
            items(anytime, key = { "a${it.id}" }) { ItemRow(it, today, { act.open(it) }, { act.toggle(it) }) }
        }
        if (doneToday.isNotEmpty()) {
            item { SectionTitle("오늘 끝낸 일 ${doneToday.size}개", Pal.Gray5) }
            items(doneToday, key = { "x${it.id}" }) { ItemRow(it, today, { act.open(it) }, { act.toggle(it) }) }
        }
        item { Gap(24) }
    }
}

// ───────────────────────── 달력 ─────────────────────────

@Composable
fun CalendarScreen(items: List<Item>, steps: List<Step>, today: LocalDate, act: Actions) {
    var selected by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var weekMode by rememberSaveable { mutableStateOf(false) }
    var monthOffset by rememberSaveable { mutableStateOf(0L) }
    val sel = LocalDate.ofEpochDay(selected)

    val month = YearMonth.from(today).plusMonths(monthOffset)
    val days: List<LocalDate> = if (weekMode) {
        val sun = sel.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        (0L until 7L).map { sun.plusDays(it) }
    } else {
        val first = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        (0L until 42L).map { first.plusDays(it) }
    }
    val markers = remember(items, steps, days.first(), days.last()) { Agenda.markers(items, steps, days.first(), days.last()) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (weekMode) selected = sel.minusWeeks(1).toEpochDay() else monthOffset -= 1
                }) { Text("◀", color = Pal.Amber) }
                Text(
                    if (weekMode) "%04d.%02d".format(sel.year, sel.monthValue) else "%04d.%02d".format(month.year, month.monthValue),
                    style = MaterialTheme.typography.titleLarge,
                    color = Pal.Amber,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                IconButton(onClick = {
                    if (weekMode) selected = sel.plusWeeks(1).toEpochDay() else monthOffset += 1
                }) { Text("▶", color = Pal.Amber) }
            }
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TermToggle("월간", !weekMode) { weekMode = false }
                    TermToggle("주간", weekMode) { weekMode = true }
                }
                TermToggle("오늘로", false, Pal.Cyan) { selected = today.toEpochDay(); monthOffset = 0 }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, s ->
                    Text(s, Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = when (i) { 0 -> Overdue; 6 -> EventColor; else -> Pal.Gray5 })
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 8.dp)) {
                days.chunked(7).forEach { week ->
                    Row {
                        week.forEach { day ->
                            val inMonth = weekMode || day.monthValue == month.monthValue
                            DayCell(
                                day = day,
                                isToday = day == today,
                                isSelected = day == sel,
                                faded = !inMonth,
                                marker = markers[day],
                                modifier = Modifier.weight(1f),
                                onClick = { selected = day.toEpochDay() },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp), color = Pal.Gray3)
        }
        val events = Agenda.eventsOn(items, sel)
        val due = Agenda.dueOn(items, sel)
        val stepDue = Agenda.stepsDueOn(items, steps, sel)
        item { SectionTitle(KoreanDate.date(sel, today)) }
        if (events.isEmpty() && due.isEmpty() && stepDue.isEmpty()) item { EmptyHint("이 날은 비어 있어요.") }
        items(events, key = { "e${it.item.id}" }) { e ->
            ItemRow(e.item, today, { act.open(e.item) }, { act.toggle(e.item) }, showDate = false, occurrence = e.date)
        }
        items(due, key = { "d${it.item.id}" }) { e ->
            ItemRow(e.item, today, { act.open(e.item) }, { act.toggle(e.item) }, showDate = false, steps = stepsOf(steps, e.item.id))
        }
        items(stepDue, key = { "ds${it.step.id}" }) { e ->
            StepRow(e, today, { act.openProject(e.project.id) }, { act.toggleStep(e.step) }, showDate = false)
        }
        item { Gap(24) }
    }
}

@Composable
private fun DayCell(
    day: LocalDate, isToday: Boolean, isSelected: Boolean, faded: Boolean,
    marker: Pair<Boolean, Boolean>?, modifier: Modifier, onClick: () -> Unit,
) {
    val fg = when {
        isSelected -> Pal.Black
        faded -> Pal.Gray3
        day.dayOfWeek == DayOfWeek.SUNDAY -> Overdue
        day.dayOfWeek == DayOfWeek.SATURDAY -> EventColor
        else -> Pal.Text
    }
    Column(
        modifier
            .aspectRatio(0.9f)
            .padding(1.dp)
            .then(if (isSelected) Modifier.background(Pal.Amber) else Modifier)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, Pal.Amber) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(day.dayOfMonth.toString().padStart(2, '0'), color = fg,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(if (marker?.first == true) "◆" else " ", color = if (isSelected) Pal.Black else EventColor,
                style = MaterialTheme.typography.labelSmall)
            Text(if (marker?.second == true) "■" else " ", color = if (isSelected) Pal.Black else TaskColor,
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ───────────────────────── 프로젝트 목록 ─────────────────────────

@Composable
fun ProjectsScreen(items: List<Item>, steps: List<Step>, today: LocalDate, act: Actions) {
    val projects = items.filter { it.type == ItemType.PROJECT }
    val active = projects.filter { !it.done }.sortedWith(compareBy(nullsLast()) { it.date })
    val finished = projects.filter { it.done }
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle("진행 중 ${active.size}개", ProjectColor) }
        if (active.isEmpty()) item {
            EmptyHint("입력창에 \"이사 준비: 짐 싸기, 업체 예약\"처럼 쓰면 프로젝트가 만들어져요.")
        }
        items(active, key = { it.id }) { p -> ItemRow(p, today, { act.open(p) }, {}, steps = stepsOf(steps, p.id)) }
        if (finished.isNotEmpty()) {
            item { SectionTitle("끝난 프로젝트 ${finished.size}개", Pal.Gray5) }
            items(finished, key = { it.id }) { p -> ItemRow(p, today, { act.open(p) }, {}, steps = stepsOf(steps, p.id)) }
        }
        item { Gap(24) }
    }
}
