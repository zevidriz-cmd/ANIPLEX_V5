package com.aniplex.app.presentation.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aniplex.app.domain.model.Result
import com.aniplex.app.domain.model.ScheduleItem
import com.aniplex.app.domain.repository.AnimeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class DayTab(
    val dateString: String, // "YYYY-MM-DD"
    val displayDay: String, // "Today", "Tomorrow", "Mon", etc.
    val displayDate: String // "Jun 5"
)

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Success(val schedules: List<ScheduleItem>) : ScheduleUiState
    data class Error(val message: String) : ScheduleUiState
    data object Empty : ScheduleUiState
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: AnimeRepository
) : ViewModel() {

    val dayTabs: List<DayTab> = generateDayTabs()

    private val _uiState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(dayTabs.first().dateString)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val currentTimeMillis: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(10000L) // 10 seconds tick
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = System.currentTimeMillis()
    )

    init {
        loadSchedule(dayTabs.first().dateString)
    }

    fun selectDate(dateString: String) {
        if (_selectedDate.value == dateString) return
        _selectedDate.value = dateString
        loadSchedule(dateString)
    }

    fun loadSchedule(dateString: String) {
        viewModelScope.launch {
            repository.getSchedules(dateString).collect { result ->
                when (result) {
                    is Result.Loading -> {
                        _uiState.value = ScheduleUiState.Loading
                    }
                    is Result.Success -> {
                        val list = result.data
                        if (list.isEmpty()) {
                            _uiState.value = ScheduleUiState.Empty
                        } else {
                            _uiState.value = ScheduleUiState.Success(list)
                        }
                    }
                    is Result.Error -> {
                        _uiState.value = ScheduleUiState.Error(result.message)
                    }
                }
            }
        }
    }

    fun retry() {
        loadSchedule(_selectedDate.value)
    }

    private fun generateDayTabs(startDate: LocalDate = LocalDate.now()): List<DayTab> {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        val displayDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
        val dayNameFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)

        return List(7) { index ->
            val date = startDate.plusDays(index.toLong())
            val dateString = date.format(formatter)
            val displayDay = when (index) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> date.format(dayNameFormatter)
            }
            val displayDate = date.format(displayDateFormatter)
            DayTab(dateString, displayDay, displayDate)
        }
    }
}
