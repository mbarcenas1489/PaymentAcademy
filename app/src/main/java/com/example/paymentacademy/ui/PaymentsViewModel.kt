package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.domain.CurrencyFormatter
import com.example.paymentacademy.domain.InstallmentDraft
import com.example.paymentacademy.domain.PaymentRepository
import com.example.paymentacademy.domain.OrganizationRole
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

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
  private val _uiState = MutableStateFlow(PaymentsUiState(isLoading = true))
  val uiState: StateFlow<PaymentsUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

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
          _uiState.value = PaymentsUiState(
            message = error.message ?: "No fue posible cargar los pagos.",
          )
        }
    }
  }

  fun registerInstallment(
    payment: PaymentSummary,
    installmentAmount: Double,
    paymentMethod: String = payment.paymentMethod,
    notes: String? = null,
    onCompleted: (PaymentSummary) -> Unit = {},
  ) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching {
        val updated = repository.registerInstallment(
          payment,
          InstallmentDraft(
            paymentId = payment.id,
            installmentAmount = installmentAmount,
            paidOn = LocalDate.now().toString(),
            paymentMethod = paymentMethod,
            notes = notes,
          ),
        )
        loadData() to updated
      }.onSuccess { (data, updated) ->
        _uiState.value = PaymentsUiState(
          payments = data.payments,
          organizationRole = data.organizationRole,
          message = if (updated.pendingAmount <= 0.0) {
            "¡Abono registrado! El saldo pendiente ha quedado en ${CurrencyFormatter.formatCurrency(0.0, updated.currency)}."
          } else {
            "Abono registrado correctamente."
          },
        )
        onCompleted(updated)
      }.onFailure { error ->
        _uiState.update {
          it.copy(isLoading = false, message = error.message ?: "No fue posible registrar el abono.")
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
          message = "Pago eliminado.",
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

  private suspend fun loadData(): PaymentsData = coroutineScope {
    val payments = async { repository.getPayments(memberId = memberId, resultLimit = 100) }
    val role = async { repository.getCurrentOrganizationRole() }
    PaymentsData(payments = payments.await(), organizationRole = role.await())
  }

  private data class PaymentsData(
    val payments: List<PaymentSummary>,
    val organizationRole: OrganizationRole,
  )
}
