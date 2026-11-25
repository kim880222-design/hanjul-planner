package com.hanjul.planner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hanjul.planner.data.Item
import com.hanjul.planner.data.Repository
import com.hanjul.planner.data.Step
import com.hanjul.planner.domain.ItemType
import com.hanjul.planner.domain.ParsedInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)

    val items: StateFlow<List<Item>> =
        repo.items.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val steps: StateFlow<List<Step>> =
        repo.steps.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _today = MutableStateFlow(LocalDate.now())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    /** 화면이 다시 보일 때 날짜가 바뀌었으면 갱신 */
    fun refreshToday() {
        val now = LocalDate.now()
        if (now != _today.value) _today.value = now
    }

    fun add(p: ParsedInput, type: ItemType, onAdded: (Long) -> Unit = {}) = viewModelScope.launch {
        onAdded(repo.add(p, type))
    }

    fun update(item: Item) = viewModelScope.launch { repo.update(item) }
    fun delete(item: Item) = viewModelScope.launch { repo.delete(item) }
    fun toggle(item: Item) = viewModelScope.launch { repo.toggleDone(item) }

    fun addStep(projectId: Long, title: String) = viewModelScope.launch { repo.addStep(projectId, title) }
    fun toggleStep(step: Step) = viewModelScope.launch { repo.toggleStep(step) }
    fun updateStep(step: Step) = viewModelScope.launch { repo.updateStep(step) }
    fun deleteStep(step: Step) = viewModelScope.launch { repo.deleteStep(step) }
    fun moveStep(step: Step, delta: Int) = viewModelScope.launch { repo.moveStep(step, delta) }
}
