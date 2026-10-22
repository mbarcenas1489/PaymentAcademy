package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentacademy.data.model.MemberPaymentSummary
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.domain.PaymentRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class PaymentViewModel(private val repository: PaymentRepository) : ViewModel() {
  private val _uiState = MutableStateFlow(PaymentUiState(isLoading = true))
  val uiState: StateFlow<PaymentUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      runCatching {
        if (repository.restoreSession()) loadDashboard() else null
      }.onSuccess { dashboard ->
        _uiState.value = if (dashboard == null) {
          PaymentUiState()
        } else {
          _uiState.value.copy(
            isAuthenticated = true,
            isLoading = false,
            members = dashboard.members,
            movements = dashboard.movements,
            monthlyTotal = dashboard.monthlyTotal,
          )
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
        loadDashboard()
      }.onSuccess { dashboard ->
        _uiState.update {
          it.copy(
            isAuthenticated = true,
            isLoading = false,
            members = dashboard.members,
            movements = dashboard.movements,
            monthlyTotal = dashboard.monthlyTotal,
          )
        }
      }.onFailure { error ->
        _uiState.update {
          it.copy(isLoading = false, message = error.userMessage("No fue posible iniciar sesión"))
        }
      }
    }
  }

  fun refresh() {
    if (!_uiState.value.isAuthenticated) return
    viewModelScope.launch {
      runCatching {
        _uiState.update { it.copy(isLoading = true) }
        loadDashboard()
      }.onSuccess { dashboard ->
        _uiState.update {
          it.copy(
            isLoading = false,
            members = dashboard.members,
            movements = dashboard.movements,
            monthlyTotal = dashboard.monthlyTotal,
          )
        }
      }.onFailure { error ->
        _uiState.update {
          it.copy(isLoading = false, message = error.userMessage("No fue posible cargar el resumen"))
        }
      }
    }
  }

  fun setMovementRange(fromDate: LocalDate, toDate: LocalDate) {
    if (toDate.isBefore(fromDate)) {
      _uiState.update { it.copy(message = "La fecha final no puede ser anterior a la inicial.") }
      return
    }
    _uiState.update { it.copy(fromDate = fromDate, toDate = toDate) }
    refresh()
  }

  fun deletePayment(paymentId: String) {
    viewModelScope.launch {
      runCatching {
        _uiState.update { it.copy(isLoading = true) }
        repository.deletePayment(paymentId)
        loadDashboard()
      }.onSuccess { dashboard ->
        _uiState.update {
          it.copy(
            isLoading = false,
            members = dashboard.members,
            movements = dashboard.movements,
            monthlyTotal = dashboard.monthlyTotal,
            message = "Pago eliminado correctamente.",
          )
        }
      }.onFailure { error ->
        _uiState.update {
          it.copy(isLoading = false, message = error.userMessage("No fue posible eliminar el pago"))
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

  private suspend fun loadDashboard(): DashboardData = coroutineScope {
    val range = _uiState.value
    val today = LocalDate.now()
    val monthStart = today.withDayOfMonth(1)
    val monthEnd = today.withDayOfMonth(today.lengthOfMonth())
    val members = async { repository.getMemberPaymentSummaries() }
    val monthlyPayments = async {
      repository.getPayments(
        fromDate = monthStart.toString(),
        toDate = monthEnd.toString(),
      )
    }
    val movements = async {
      repository.getPayments(
        fromDate = range.fromDate.toString(),
        toDate = range.toDate.toString(),
      )
    }
    DashboardData(
      members = members.await(),
      movements = movements.await(),
      monthlyTotal = monthlyPayments.await().sumOf { it.amount },
    )
  }

  private fun Throwable.userMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback

  private data class DashboardData(
    val members: List<MemberPaymentSummary>,
    val movements: List<PaymentSummary>,
    val monthlyTotal: Double,
  )
}
