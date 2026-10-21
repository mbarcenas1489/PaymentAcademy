package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.NewMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterMemberUiState(
  val isLoading: Boolean = false,
  val isSaved: Boolean = false,
  val fullNameError: String? = null,
  val emailError: String? = null,
  val message: String? = null,
)

class RegisterMemberViewModel(
  private val repository: MemberRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(RegisterMemberUiState())
  val uiState: StateFlow<RegisterMemberUiState> = _uiState.asStateFlow()

  fun save(
    fullName: String,
    documentNumber: String,
    email: String,
    phone: String,
    notes: String,
  ) {
    val cleanName = fullName.trim()
    val cleanEmail = email.trim()
    val nameError = if (cleanName.isBlank()) "El nombre es obligatorio." else null
    val emailError = if (cleanEmail.isNotEmpty() && !EMAIL_REGEX.matches(cleanEmail)) {
      "Ingresa un correo válido."
    } else null

    if (nameError != null || emailError != null) {
      _uiState.value = RegisterMemberUiState(
        fullNameError = nameError,
        emailError = emailError,
      )
      return
    }

    viewModelScope.launch {
      _uiState.value = RegisterMemberUiState(isLoading = true)
      runCatching {
        repository.createMember(
          NewMember(
            fullName = cleanName,
            documentNumber = documentNumber.trim().ifBlank { null },
            email = cleanEmail.ifBlank { null },
            phone = phone.trim().ifBlank { null },
            notes = notes.trim().ifBlank { null },
          ),
        )
      }.onSuccess {
        _uiState.value = RegisterMemberUiState(isSaved = true)
      }.onFailure { error ->
        _uiState.value = RegisterMemberUiState(
          message = error.message ?: "No fue posible guardar el miembro.",
        )
      }
    }
  }

  fun messageShown() {
    _uiState.update { it.copy(message = null) }
  }

  companion object {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
  }
}
