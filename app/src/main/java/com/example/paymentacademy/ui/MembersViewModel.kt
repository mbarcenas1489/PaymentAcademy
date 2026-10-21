package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.domain.MemberRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MembersUiState(
  val isLoading: Boolean = false,
  val members: List<Member> = emptyList(),
  val message: String? = null,
)

class MembersViewModel(
  private val repository: MemberRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(MembersUiState())
  val uiState: StateFlow<MembersUiState> = _uiState.asStateFlow()

  init { refresh() }

  fun refresh() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching { repository.getMembers() }
        .onSuccess { members -> _uiState.value = MembersUiState(members = members) }
        .onFailure { error ->
          _uiState.update {
            it.copy(isLoading = false, message = error.message ?: "No fue posible cargar los miembros.")
          }
        }
    }
  }

  fun messageShown() {
    _uiState.update { it.copy(message = null) }
  }
}
