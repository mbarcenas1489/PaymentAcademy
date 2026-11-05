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
            organizationRole = dashboard.organizationRole,
            syncStatus = dashboard.syncStatus,
            pendingSyncCount = dashboard.pendingSyncCount,
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
            organizationRole = dashboard.organizationRole,
            syncStatus = dashboard.syncStatus,
            pendingSyncCount = dashboard.pendingSyncCount,
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
            organizationRole = dashboard.organizationRole,
            syncStatus = dashboard.syncStatus,
            pendingSyncCount = dashboard.pendingSyncCount,
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
            organizationRole = dashboard.organizationRole,
            syncStatus = dashboard.syncStatus,
            pendingSyncCount = dashboard.pendingSyncCount,
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

  fun requestSignOut() {
    viewModelScope.launch {
      runCatching {
        _uiState.update { it.copy(isLoading = true) }
        repository.getPendingSyncSummary()
      }.onSuccess { pending ->
        if (pending.hasPendingChanges) {
          _uiState.update {
            it.copy(
              isLoading = false,
              syncStatus = SyncStatus.PENDING,
              pendingSyncCount = pending.totalCount,
              pendingLogoutCount = pending.totalCount,
            )
          }
        } else {
          performSignOut()
        }
      }.onFailure { error ->
        _uiState.update {
          it.copy(
            isLoading = false,
            message = error.userMessage("No fue posible comprobar la sincronización"),
          )
        }
      }
    }
  }

  fun synchronizeNow() {
    viewModelScope.launch {
      runCatching {
        _uiState.update {
          it.copy(isLoading = true, syncStatus = SyncStatus.SYNCING, pendingLogoutCount = null)
        }
        repository.synchronizePendingChanges()
        loadDashboard()
      }.onSuccess { dashboard ->
        _uiState.update {
          it.copy(
            isLoading = false,
            members = dashboard.members,
            movements = dashboard.movements,
            monthlyTotal = dashboard.monthlyTotal,
            organizationRole = dashboard.organizationRole,
            syncStatus = dashboard.syncStatus,
            pendingSyncCount = dashboard.pendingSyncCount,
            message = "Sincronización completada.",
          )
        }
      }.onFailure { error ->
        _uiState.update {
          it.copy(
            isLoading = false,
            syncStatus = SyncStatus.ERROR,
            message = error.userMessage("No fue posible sincronizar los cambios"),
          )
        }
      }
    }
  }

  fun synchronizeAndSignOut() {
    viewModelScope.launch {
      runCatching {
        _uiState.update {
          it.copy(isLoading = true, syncStatus = SyncStatus.SYNCING, pendingLogoutCount = null)
        }
        val pending = repository.synchronizePendingChanges()
        check(!pending.hasPendingChanges) { "Aún hay cambios pendientes por sincronizar." }
        repository.signOut()
      }.onSuccess {
        _uiState.value = PaymentUiState()
      }.onFailure { error ->
        _uiState.update {
          it.copy(
            isLoading = false,
            syncStatus = SyncStatus.ERROR,
            message = error.userMessage("No se cerró la sesión para proteger tus datos"),
          )
        }
      }
    }
  }

  fun cancelSignOut() {
    _uiState.update { it.copy(pendingLogoutCount = null) }
  }

  fun backgroundSyncStarted() {
    if (!_uiState.value.isAuthenticated) return
    _uiState.update { it.copy(syncStatus = SyncStatus.SYNCING) }
  }

  fun backgroundSyncQueued() {
    if (!_uiState.value.isAuthenticated) return
    _uiState.update { it.copy(syncStatus = SyncStatus.WAITING) }
  }

  fun refreshSyncStatus() {
    if (!_uiState.value.isAuthenticated) return
    viewModelScope.launch {
      runCatching { repository.getPendingSyncSummary() }
        .onSuccess { pending ->
          _uiState.update {
            it.copy(
              syncStatus = if (pending.hasPendingChanges) {
                SyncStatus.PENDING
              } else {
                SyncStatus.SYNCED
              },
              pendingSyncCount = pending.totalCount,
            )
          }
        }
        .onFailure {
          _uiState.update { state -> state.copy(syncStatus = SyncStatus.ERROR) }
        }
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
    val pending = async { repository.getPendingSyncSummary() }
    val role = async { repository.getCurrentOrganizationRole() }
    val pendingSummary = pending.await()
    DashboardData(
      members = members.await(),
      movements = movements.await(),
      monthlyTotal = monthlyPayments.await().sumOf { it.amount },
      organizationRole = role.await(),
      pendingSyncCount = pendingSummary.totalCount,
      syncStatus = if (pendingSummary.hasPendingChanges) SyncStatus.PENDING else SyncStatus.SYNCED,
    )
  }

  private suspend fun performSignOut() {
    runCatching { repository.signOut() }
      .onSuccess { _uiState.value = PaymentUiState() }
      .onFailure { error ->
        _uiState.update {
          it.copy(
            isLoading = false,
            message = error.userMessage("No fue posible cerrar la sesión"),
          )
        }
      }
  }

  private fun Throwable.userMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback

  private data class DashboardData(
    val members: List<MemberPaymentSummary>,
    val movements: List<PaymentSummary>,
    val monthlyTotal: Double,
    val organizationRole: com.example.paymentacademy.domain.OrganizationRole,
    val pendingSyncCount: Int,
    val syncStatus: SyncStatus,
  )
}
