package com.hanjul.planner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.hanjul.planner.data.Agenda
import com.hanjul.planner.data.Item
import com.hanjul.planner.data.Step
import com.hanjul.planner.data.Urgency
import com.hanjul.planner.domain.KoreanDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    project: Item,
    steps: List<Step>,
    today: LocalDate,
    vm: MainViewModel,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    var newStep by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Step?>(null) }
    val sorted = steps.sortedBy { it.position }
    val (done, total) = Agenda.progress(sorted)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TermToggle("◀ ESC", false, Pal.Amber, onBack)
            Text(
                "  ▤ ${project.title}",
                style = MaterialTheme.typography.titleMedium,
                color = ProjectColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            TermToggle("고치기", false, Pal.Cyan, onEdit)
        }
        HorizontalDivider(color = ProjectColor)
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$done / $total 단계 완료",
                    style = MaterialTheme.typography.titleMedium,
                    color = Pal.Text,
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                )
                project.date?.let { due ->
                    val u = Agenda.urgency(project, today)
                    if (u == Urgency.LATER || u == Urgency.NONE) {
                        Text("마감 ${KoreanDate.date(due, today)}", style = MaterialTheme.typography.bodyMedium)
                    } else UrgencyBadge(due, today, u)
                }
            }
            Box(Modifier.padding(vertical = 8.dp)) { TextProgress(done, total, cells = 20) }
            if (project.done) Inverse("** 모든 단계 완료 **", TaskColor)
        }

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(sorted, key = { _, s -> s.id }) { i, s ->
                Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TermCheck(s.done, ProjectColor) { vm.toggleStep(s) }
                    Column(Modifier.weight(1f).clickable { renaming = s }.padding(vertical = 6.dp)) {
                        Text(
                            "${i + 1}. ${s.title}",
                            textDecoration = if (s.done) TextDecoration.LineThrough else null,
                            color = if (s.done) Pal.Gray4 else Pal.Text,
                            fontWeight = if (s == Agenda.nextStep(sorted)) FontWeight.Bold else FontWeight.Normal,
                        )
                        val su = Agenda.urgency(s, today)
                        Text(
                            s.date?.let { "마감 " + KoreanDate.date(it, today) + if (!s.done) " · " + KoreanDate.dday(it, today) else "" }
                                ?: "마감일 없음 (눌러서 정하기)",
                            style = MaterialTheme.typography.bodySmall,
                            color = when (su) {
                                Urgency.OVERDUE -> Overdue
                                Urgency.TODAY, Urgency.SOON -> Soon
                                else -> Pal.Gray5
                            },
                        )
                    }
                    IconButton(onClick = { vm.moveStep(s, -1) }, enabled = i > 0) { Text("▲", color = if (i > 0) Pal.Amber else Pal.Gray3) }
                    IconButton(onClick = { vm.moveStep(s, 1) }, enabled = i < sorted.size - 1) {
                        Text("▼", color = if (i < sorted.size - 1) Pal.Amber else Pal.Gray3)
                    }
                }
            }
            item {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newStep,
                        onValueChange = { newStep = it },
                        placeholder = { Text("단계 추가 (예: 짐 싸기 12/1)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (newStep.isNotBlank()) { vm.addStep(project.id, newStep.trim()); newStep = "" }
                        }),
                    )
                    IconButton(onClick = {
                        if (newStep.isNotBlank()) { vm.addStep(project.id, newStep.trim()); newStep = "" }
                    }) { Text("+", color = Pal.Amber, style = MaterialTheme.typography.titleLarge) }
                }
            }
            if (project.note.isNotBlank()) item {
                Text("메모: ${project.note}", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    renaming?.let { s ->
        StepEditDialog(
            step = s,
            today = today,
            onSave = { vm.updateStep(it); renaming = null },
            onDelete = { vm.deleteStep(s); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepEditDialog(
    step: Step,
    today: LocalDate,
    onSave: (Step) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(step.id) { mutableStateOf(step.title) }
    var date by remember(step.id) { mutableStateOf(step.date) }
    var pickDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("단계 고치기") },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("단계 이름") })
                Text("마감일", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pickDate = true }) {
                        Text(date?.let { KoreanDate.date(it, today) } ?: "날짜 정하기")
                    }
                    if (date != null) TextButton(onClick = { date = null }) { Text("지우기") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(step.copy(title = text.trim().ifBlank { step.title }, date = date)) }) { Text("저장") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("삭제", color = Overdue) }
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
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("취소") } },
        ) { DatePicker(state = state) }
    }
}
