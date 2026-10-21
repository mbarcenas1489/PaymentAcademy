package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.domain.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaymentsUiState(
  val isLoading: Boolean = false,
  val payments: List<PaymentSummary> = emptyList(),
  val message: String? = null,
)

class PaymentsViewModel(
  private val repository: PaymentRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(PaymentsUiState())
  val uiState: StateFlow<PaymentsUiState> = _uiState.asStateFlow()

  init { refresh() }

  fun refresh() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching { repository.getRecentPayments() }
        .onSuccess { _uiState.value = PaymentsUiState(payments = it) }
        .onFailure { error ->
          _uiState.update {
            it.copy(isLoading = false, message = error.message ?: "No fue posible cargar los pagos.")
          }
        }
    }
  }

  fun deletePayment(paymentId: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching {
        repository.deletePayment(paymentId)
        repository.getRecentPayments()
      }.onSuccess { payments ->
        _uiState.value = PaymentsUiState(
          payments = payments,
          message = "Pago eliminado correctamente.",
        )
      }.onFailure { error ->
        _uiState.update {
          it.copy(isLoading = false, message = error.message ?: "No fue posible eliminar el pago.")
        }
      }
    }
  }

  fun messageShown() {
    _uiState.update { it.copy(message = null) }
  }
}
