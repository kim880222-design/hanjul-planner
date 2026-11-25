package com.hanjul.planner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hanjul.planner.data.Agenda
import com.hanjul.planner.data.Item
import com.hanjul.planner.data.Step
import com.hanjul.planner.data.StepEntry
import com.hanjul.planner.data.Urgency
import com.hanjul.planner.domain.ItemType
import com.hanjul.planner.domain.KoreanDate
import com.hanjul.planner.domain.KoreanParser
import com.hanjul.planner.domain.ParsedInput
import com.hanjul.planner.domain.Recurrence
import com.hanjul.planner.domain.Repeat
import kotlinx.coroutines.delay
import java.time.LocalDate

fun typeColor(t: ItemType): Color = when (t) {
    ItemType.TASK -> TaskColor
    ItemType.EVENT -> EventColor
    ItemType.PROJECT -> ProjectColor
}

/** 종류 표시 기호 (글꼴에 들어 있는 기호만 사용) */
fun typeIcon(t: ItemType): String = when (t) {
    ItemType.TASK -> "■"
    ItemType.EVENT -> "◆"
    ItemType.PROJECT -> "▤"
}

/** 깜빡이는 커서 */
@Composable
fun BlinkCursor(color: Color = Pal.Amber) {
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(530); on = !on } }
    Text("█", color = if (on) color else Color.Transparent)
}

/** 반전 글자 (DOS 선택 표시) */
@Composable
fun Inverse(text: String, color: Color, modifier: Modifier = Modifier, bold: Boolean = true) {
    Text(
        text,
        color = Pal.Black,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.background(color).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** [ 이름 ] 모양 토글 버튼 */
@Composable
fun TermToggle(label: String, selected: Boolean, color: Color = Pal.Amber, onClick: () -> Unit) {
    Text(
        if (selected) "[$label]" else " $label ",
        color = if (selected) Pal.Black else color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .then(if (selected) Modifier.background(color) else Modifier.border(1.dp, Pal.Gray3))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** [X] / [ ] 체크 상자 */
@Composable
fun TermCheck(checked: Boolean, color: Color = TaskColor, onToggle: () -> Unit) {
    Text(
        if (checked) "[X]" else "[ ]",
        color = if (checked) Pal.Gray5 else color,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.clickable(onClick = onToggle).padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

/** 글자로 그린 진행 막대: [██████░░░░] */
@Composable
fun TextProgress(done: Int, total: Int, color: Color = ProjectColor, cells: Int = 16) {
    val filled = if (total == 0) 0 else (done * cells + total / 2) / total
    val pct = if (total == 0) 0 else done * 100 / total
    Row {
        Text("[", color = Pal.Gray5, style = MaterialTheme.typography.bodySmall)
        Text("█".repeat(filled), color = color, style = MaterialTheme.typography.bodySmall)
        Text("░".repeat(cells - filled), color = Pal.Gray3, style = MaterialTheme.typography.bodySmall)
        Text("] ${pct.toString().padStart(3)}%", color = Pal.Gray5, style = MaterialTheme.typography.bodySmall)
    }
}

/** 화면 아래 빠른 입력창: C:\> 프롬프트 */
@Composable
fun QuickAddBar(today: LocalDate, onAdd: (ParsedInput, ItemType) -> Unit) {
    var text by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf<ItemType?>(null) }
    val parsed = remember(text, today) { KoreanParser.parse(text, today) }
    val type = chosen ?: parsed.type

    fun submit() {
        if (text.isBlank()) return
        onAdd(parsed, type)
        text = ""
        chosen = null
    }

    Column(Modifier.fillMaxWidth().background(Pal.Gray0)) {
        HorizontalDivider(color = Pal.Amber, thickness = 1.dp)
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (text.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("종류:", color = Pal.Gray5, style = MaterialTheme.typography.labelMedium)
                    ItemType.values().forEach { t ->
                        TermToggle(t.label, type == t, typeColor(t)) { chosen = t }
                    }
                }
                Text(
                    "» " + previewText(parsed, type, today),
                    style = MaterialTheme.typography.bodySmall,
                    color = Pal.Cyan,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("C:\\>", color = Pal.Amber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (text.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BlinkCursor()
                            Text(" 내일 3시 회의", color = Pal.Gray4, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it; if (it.isBlank()) chosen = null },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Pal.AmberLight),
                        cursorBrush = SolidColor(Pal.Amber),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "ENTER",
                    color = if (text.isNotBlank()) Pal.Black else Pal.Gray4,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .then(if (text.isNotBlank()) Modifier.background(Pal.Amber) else Modifier.border(1.dp, Pal.Gray3))
                        .clickable(enabled = text.isNotBlank()) { submit() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun previewText(p: ParsedInput, type: ItemType, today: LocalDate): String {
    val parts = ArrayList<String>()
    parts += "\"${p.title}\""
    p.date?.let { parts += (if (type == ItemType.EVENT) "" else "마감 ") + KoreanDate.date(it, today) }
    p.time?.let { t -> parts += KoreanDate.time(t) + (p.endTime?.let { " ~ " + KoreanDate.time(it) } ?: "") }
    if (p.repeat != Repeat.NONE) parts += "↻ " + Recurrence.describe(p.repeat, p.repeatDays, p.date)
    if (type == ItemType.PROJECT && p.steps.isNotEmpty()) {
        val dated = p.stepDates.count { it != null }
        parts += "단계 ${p.steps.size}개" + if (dated > 0) " (마감 있는 단계 ${dated}개)" else ""
    }
    if (type == ItemType.EVENT && p.date == null) parts += "날짜 없음 → 오늘"
    return parts.joinToString(" | ")
}

/** 목록 한 줄 */
@Composable
fun ItemRow(
    item: Item,
    today: LocalDate,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    showDate: Boolean = true,
    occurrence: LocalDate? = null,
    steps: List<Step> = emptyList(),
) {
    val urgency = Agenda.urgency(item, today)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item.type) {
            ItemType.TASK -> TermCheck(item.done) { onToggle() }
            ItemType.EVENT -> Text(
                item.time?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "종일",
                color = EventColor, fontWeight = FontWeight.Bold,
                modifier = Modifier.widthIn(min = 52.dp).padding(horizontal = 6.dp, vertical = 10.dp),
            )
            ItemType.PROJECT -> Text("▤", color = ProjectColor, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) Pal.Gray4 else Pal.Text,
            )
            val sub = buildList {
                val d = occurrence ?: item.date
                if (showDate && d != null) add(KoreanDate.date(d, today))
                if (item.type == ItemType.EVENT) {
                    item.endTime?.let { add("~ " + KoreanDate.time(it)) }
                } else item.time?.let { add(KoreanDate.time(it)) }
                if (item.repeat != Repeat.NONE) add("↻ " + Recurrence.describe(item.repeat, item.repeatDays, item.date))
                if (item.type == ItemType.PROJECT) {
                    val (done, total) = Agenda.progress(steps)
                    add("$done/$total 단계")
                    Agenda.nextStep(steps)?.let { n ->
                        add("다음: ${n.title}" + (n.date?.let { " (${KoreanDate.dday(it, today)})" } ?: ""))
                    }
                }
            }
            if (sub.isNotEmpty()) {
                Text(
                    sub.joinToString(" | "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Pal.Gray5,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.type == ItemType.PROJECT && steps.isNotEmpty()) {
                val (done, total) = Agenda.progress(steps)
                TextProgress(done, total)
            }
        }
        if (item.date != null && urgency != Urgency.NONE && urgency != Urgency.LATER) {
            UrgencyBadge(item.date, today, urgency)
        } else if (item.repeat != Repeat.NONE && item.type != ItemType.EVENT) {
            Text("↻", color = Pal.Gray5, modifier = Modifier.padding(8.dp))
        }
    }
}

@Composable
fun UrgencyBadge(due: LocalDate, today: LocalDate, urgency: Urgency) {
    Inverse(KoreanDate.dday(due, today), if (urgency == Urgency.OVERDUE) Overdue else Soon)
}

/** ▌제목 ──────── */
@Composable
fun SectionTitle(text: String, color: Color = Pal.Amber) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("▌", color = color)
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(Modifier.weight(1f), color = color, thickness = 1.dp)
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        "  -- $text",
        style = MaterialTheme.typography.bodyMedium,
        color = Pal.Gray4,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
fun Gap(h: Int = 8) = Spacer(Modifier.height(h.dp).width(1.dp))

/** 마감일이 있는 프로젝트 단계 한 줄 (현황·오늘·달력에서) */
@Composable
fun StepRow(
    entry: StepEntry,
    today: LocalDate,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    showDate: Boolean = true,
) {
    val s = entry.step
    val urgency = Agenda.urgency(s, today)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TermCheck(s.done, ProjectColor) { onToggle() }
        Column(Modifier.weight(1f)) {
            Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Pal.Text)
            Text(
                listOfNotNull(
                    "▤ ${entry.project.title}의 단계",
                    if (showDate) s.date?.let { KoreanDate.date(it, today) } else null,
                ).joinToString(" | "),
                style = MaterialTheme.typography.bodySmall,
                color = ProjectColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (s.date != null && urgency != Urgency.NONE && urgency != Urgency.LATER) UrgencyBadge(s.date, today, urgency)
    }
}
