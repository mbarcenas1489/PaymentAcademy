package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.domain.PaymentRepository
import com.example.paymentacademy.domain.OrganizationRole
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaymentsUiState(
  val isLoading: Boolean = false,
  val payments: List<PaymentSummary> = emptyList(),
  val organizationRole: OrganizationRole = OrganizationRole.STAFF,
  val message: String? = null,
)

class PaymentsViewModel(
  private val repository: PaymentRepository,
  private val memberId: String? = null,
) : ViewModel() {
  private val _uiState = MutableStateFlow(PaymentsUiState())
  val uiState: StateFlow<PaymentsUiState> = _uiState.asStateFlow()

  init { refresh() }

  fun refresh() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching { loadData() }
        .onSuccess { data ->
          _uiState.value = PaymentsUiState(
            payments = data.payments,
            organizationRole = data.organizationRole,
          )
        }
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
        loadData()
      }.onSuccess { data ->
        _uiState.value = PaymentsUiState(
          payments = data.payments,
          organizationRole = data.organizationRole,
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

  private suspend fun loadPayments() = if (memberId == null) {
    repository.getRecentPayments()
  } else {
    repository.getPayments(memberId = memberId)
  }

  private suspend fun loadData(): PaymentsData = coroutineScope {
    val payments = async { loadPayments() }
    val role = async { repository.getCurrentOrganizationRole() }
    PaymentsData(payments.await(), role.await())
  }

  private data class PaymentsData(
    val payments: List<PaymentSummary>,
    val organizationRole: OrganizationRole,
  )
}
