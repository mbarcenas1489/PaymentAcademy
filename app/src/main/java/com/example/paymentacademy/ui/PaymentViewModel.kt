package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentacademy.domain.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PaymentViewModel(private val repository: PaymentRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(PaymentUiState(isLoading = true))
  val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      runCatching {
        if (repository.restoreSession()) repository.getRecentPayments() else null
      }.onSuccess { payments ->
        _uiState.value = if (payments == null) {
          PaymentUiState()
        } else {
          PaymentUiState(isAuthenticated = true, payments = payments)
        }
      }.onFailure { error ->
        _uiState.value = PaymentUiState(
          message = error.userMessage("No fue posible restaurar la sesión"),
        )
      }
    }
  }

  fun signIn(email: String, password: String) {
    if (email.isBlank() || password.isBlank()) {
      _uiState.update { it.copy(message = "Ingresa tu correo y contraseña.") }
      return
    }
    viewModelScope.launch {
      runCatching {
        _uiState.update { it.copy(isLoading = true) }
        repository.signIn(email.trim(), password)
        repository.getRecentPayments()
      }.onSuccess { payments ->
        _uiState.value = PaymentUiState(isAuthenticated = true, payments = payments)
      }.onFailure { error ->
        _uiState.update {
          it.copy(isLoading = false, message = error.userMessage("No fue posible iniciar sesión"))
        }
      }
    }
  }

  fun refresh() {
    viewModelScope.launch {
      runCatching {
        _uiState.update { it.copy(isLoading = true) }
        repository.getRecentPayments()
      }.onSuccess { payments ->
        _uiState.update { it.copy(isLoading = false, payments = payments) }
      }.onFailure { error ->
        _uiState.update {
          it.copy(isLoading = false, message = error.userMessage("No fue posible cargar los pagos"))
        }
      }
    }
  }

  fun signOut() {
    viewModelScope.launch {
      runCatching { repository.signOut() }
      _uiState.value = PaymentUiState()
    }
  }

  fun messageShown() {
    _uiState.update { it.copy(message = null) }
  }

  private fun Throwable.userMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback
}
