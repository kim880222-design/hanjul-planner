package com.hanjul.planner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hanjul.planner.data.Item
import com.hanjul.planner.domain.DayMask
import com.hanjul.planner.domain.ItemType
import com.hanjul.planner.domain.KoreanDate
import com.hanjul.planner.domain.Repeat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val reminderOptions = listOf(-1, 0, 10, 30, 60, 1440)

/** 항목 고치기 창 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditItemDialog(
    item: Item,
    today: LocalDate,
    onSave: (Item) -> Unit,
    onDelete: (Item) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(item.title) }
    var note by remember { mutableStateOf(item.note) }
    var type by remember { mutableStateOf(item.type) }
    var date by remember { mutableStateOf(item.date) }
    var time by remember { mutableStateOf(item.time) }
    var endTime by remember { mutableStateOf(item.endTime) }
    var repeat by remember { mutableStateOf(item.repeat) }
    var repeatDays by remember { mutableStateOf(item.repeatDays) }
    var remind by remember { mutableStateOf(item.remindBefore) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Int?>(null) } // 0 = 시작, 1 = 끝
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${typeIcon(type)} 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("제목") }, modifier = Modifier.fillMaxWidth())

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ItemType.values().forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }

                Text(if (type == ItemType.EVENT) "날짜" else "마감일", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDate = true }) {
                        Text(date?.let { KoreanDate.date(it, today) } ?: "날짜 정하기")
                    }
                    if (date != null) TextButton(onClick = { date = null; repeat = Repeat.NONE }) { Text("지우기") }
                }

                Text("시간", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime = 0 }) {
                        Text(time?.let { KoreanDate.time(it) } ?: if (type == ItemType.EVENT) "하루 종일" else "시간 없음")
                    }
                    if (type == ItemType.EVENT && time != null) {
                        Text("~")
                        OutlinedButton(onClick = { pickTime = 1 }) { Text(endTime?.let { KoreanDate.time(it) } ?: "끝 시간") }
                    }
                    if (time != null) TextButton(onClick = { time = null; endTime = null }) { Text("지우기") }
                }

                Text("반복", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Repeat.values().forEach { r ->
                        FilterChip(selected = repeat == r, onClick = {
                            repeat = r
                            if (r != Repeat.NONE && date == null) date = today
                        }, label = { Text(r.label) })
                    }
                }
                if (repeat == Repeat.WEEKLY) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.values().forEach { d ->
                            val on = DayMask.contains(repeatDays, d) || (repeatDays == 0 && date?.dayOfWeek == d)
                            FilterChip(selected = on, onClick = {
                                val base = if (repeatDays == 0 && date != null) DayMask.of(date!!.dayOfWeek) else repeatDays
                                repeatDays = base xor DayMask.of(d)
                            }, label = { Text(KoreanDate.dayShort(d)) })
                        }
                    }
                }

                Text("알림", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    reminderOptions.forEach { m ->
                        val label = when (m) {
                            -1 -> "없음"
                            0 -> if (type == ItemType.EVENT) "정시" else "기본"
                            else -> KoreanDate.reminder(m).removeSuffix(" 알림")
                        }
                        FilterChip(selected = remind == m, onClick = { remind = m }, label = { Text(label) })
                    }
                }
                if (type != ItemType.EVENT && remind >= 0) {
                    Text(
                        "마감 하루 전과 당일 오전 9시에 알려드려요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(note, { note = it }, label = { Text("메모") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    item.copy(
                        title = title.ifBlank { item.title }, note = note, type = type, date = date,
                        time = time, endTime = if (time == null) null else endTime,
                        repeat = if (date == null) Repeat.NONE else repeat,
                        repeatDays = if (repeat == Repeat.WEEKLY) repeatDays else 0,
                        remindBefore = remind,
                    )
                )
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) { Text("삭제", color = Overdue) }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: today).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickDate = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }

    pickTime?.let { which ->
        val init = (if (which == 0) time else endTime) ?: (time?.plusHours(1) ?: LocalTime.of(9, 0))
        val state = rememberTimePickerState(initialHour = init.hour, initialMinute = init.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val t = LocalTime.of(state.hour, state.minute)
                    if (which == 0) {
                        time = t
                        if (date == null) date = today
                    } else endTime = t
                    pickTime = null
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("취소") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("삭제할까요?") },
            text = { Text("\"${item.title}\"을(를) 지웁니다. 되돌릴 수 없어요.") },
            confirmButton = { TextButton(onClick = { onDelete(item) }) { Text("삭제", color = Overdue) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}
