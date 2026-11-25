package com.hanjul.planner.data

import android.content.Context
import com.hanjul.planner.domain.ItemType
import com.hanjul.planner.domain.KoreanParser
import com.hanjul.planner.domain.ParsedInput
import com.hanjul.planner.domain.Recurrence
import com.hanjul.planner.domain.Repeat
import com.hanjul.planner.notify.ReminderScheduler
import kotlinx.coroutines.flow.Flow

class Repository(private val context: Context) {
    private val dao = PlannerDatabase.get(context).dao()

    val items: Flow<List<Item>> = dao.observeItems()
    val steps: Flow<List<Step>> = dao.observeSteps()

    private suspend fun changed() = ReminderScheduler.rescheduleAll(context)

    suspend fun add(p: ParsedInput, type: ItemType): Long {
        val item = Item(
            type = type,
            title = p.title,
            date = p.date,
            time = p.time,
            endTime = p.endTime,
            repeat = p.repeat,
            repeatDays = p.repeatDays,
            remindBefore = when {
                type == ItemType.EVENT && p.time != null -> 30
                else -> 0
            },
        )
        val id = if (type == ItemType.PROJECT) dao.insertProject(item, p.steps.mapIndexed { i, t -> t to p.stepDates.getOrNull(i) }) else dao.insert(item)
        changed()
        return id
    }

    suspend fun item(id: Long): Item? = dao.item(id)

    suspend fun update(item: Item) { dao.update(item); changed() }

    suspend fun delete(item: Item) { dao.delete(item); changed() }

    /** 완료 체크. 반복 할 일은 완료 대신 다음 날짜로 넘어간다. */
    suspend fun toggleDone(item: Item) {
        val d = item.date
        if (!item.done && item.repeat != Repeat.NONE && d != null && item.type != ItemType.EVENT) {
            val next = Recurrence.nextOnOrAfter(d, item.repeat, item.repeatDays, d.plusDays(1))
            if (next != null) {
                dao.update(item.copy(date = next))
                changed(); return
            }
        }
        dao.update(item.copy(done = !item.done, doneAt = if (item.done) null else System.currentTimeMillis()))
        changed()
    }

    // ---- 프로젝트 단계 ----
    /** "짐 싸기 12/1"처럼 쓰면 마감일도 함께 저장 */
    suspend fun addStep(projectId: Long, text: String) {
        val pos = (dao.stepsOf(projectId).maxOfOrNull { it.position } ?: -1) + 1
        val parsed = KoreanParser.parse(text)
        val (title, date) = if (parsed.date != null && parsed.title.isNotBlank()) parsed.title to parsed.date else text to null
        dao.insertStep(Step(projectId = projectId, title = title, position = pos, date = date))
        refreshProjectDone(projectId)
    }

    suspend fun toggleStep(step: Step) {
        dao.updateStep(step.copy(done = !step.done))
        refreshProjectDone(step.projectId)
    }

    suspend fun updateStep(step: Step) { dao.updateStep(step); changed() }

    suspend fun deleteStep(step: Step) {
        dao.deleteStep(step)
        refreshProjectDone(step.projectId)
    }

    suspend fun moveStep(step: Step, delta: Int) {
        val list = dao.stepsOf(step.projectId).toMutableList()
        val i = list.indexOfFirst { it.id == step.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        val a = list[i]; list[i] = list[j]; list[j] = a
        dao.updateSteps(list.mapIndexed { idx, s -> s.copy(position = idx) })
    }

    /** 모든 단계가 끝나면 프로젝트도 완료로 */
    private suspend fun refreshProjectDone(projectId: Long) {
        val project = dao.item(projectId) ?: return
        val steps = dao.stepsOf(projectId)
        val allDone = steps.isNotEmpty() && steps.all { it.done }
        if (allDone != project.done) {
            dao.update(project.copy(done = allDone, doneAt = if (allDone) System.currentTimeMillis() else null))
        }
        changed()
    }
}
