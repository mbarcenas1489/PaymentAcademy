package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.PaymentDraft
import com.example.paymentacademy.domain.PaymentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PaymentFormUiState(
  val isLoading: Boolean = true,
  val isSaving: Boolean = false,
  val members: List<Member> = emptyList(),
  val isSaved: Boolean = false,
  val memberError: String? = null,
  val amountError: String? = null,
  val dateError: String? = null,
  val validFromError: String? = null,
  val validUntilError: String? = null,
  val message: String? = null,
)

class PaymentFormViewModel(
  private val paymentRepository: PaymentRepository,
  private val memberRepository: MemberRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(PaymentFormUiState())
  val uiState: StateFlow<PaymentFormUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      runCatching { memberRepository.getMembers() }
        .onSuccess { _uiState.value = PaymentFormUiState(isLoading = false, members = it) }
        .onFailure { error ->
          _uiState.value = PaymentFormUiState(
            isLoading = false,
            message = error.message ?: "No fue posible cargar los miembros.",
          )
        }
    }
  }

  fun save(
    paymentId: String?,
    memberId: String?,
    amountText: String,
    paidOn: String,
    paymentType: String,
    paymentMethod: String,
    validFrom: String,
    validUntil: String,
    reference: String,
    notes: String,
  ) {
    val amount = amountText.trim().replace(',', '.').toDoubleOrNull()
    val memberError = if (memberId.isNullOrBlank()) "Selecciona un miembro." else null
    val amountError = if (amount == null || amount <= 0.0) "Ingresa un valor mayor que cero." else null
    val dateError = runCatching { LocalDate.parse(paidOn.trim()) }
      .exceptionOrNull()
      ?.let { "Usa una fecha válida." }
    val validFromDate = runCatching { LocalDate.parse(validFrom.trim()) }.getOrNull()
    val validUntilDate = runCatching { LocalDate.parse(validUntil.trim()) }.getOrNull()
    val validFromError = if (validFromDate == null) "Selecciona una fecha inicial válida." else null
    val validUntilError = when {
      validUntilDate == null -> "Selecciona una fecha final válida."
      validFromDate != null && validUntilDate.isBefore(validFromDate) ->
        "La fecha final debe ser igual o posterior a la inicial."
      else -> null
    }

    if (
      memberError != null || amountError != null || dateError != null ||
      validFromError != null || validUntilError != null
    ) {
      _uiState.update {
        it.copy(
          isLoading = false,
          memberError = memberError,
          amountError = amountError,
          dateError = dateError,
          validFromError = validFromError,
          validUntilError = validUntilError,
        )
      }
      return
    }

    val draft = PaymentDraft(
      memberId = requireNotNull(memberId),
      amount = requireNotNull(amount),
      paidOn = paidOn.trim(),
      paymentType = paymentType,
      paymentMethod = paymentMethod,
      validFrom = validFrom.trim(),
      validUntil = validUntil.trim(),
      reference = reference.trim().ifBlank { null },
      notes = notes.trim().ifBlank { null },
    )
    viewModelScope.launch {
      _uiState.update { it.copy(isSaving = true, message = null) }
      runCatching {
        if (paymentId == null) {
          paymentRepository.createPayment(draft)
        } else {
          paymentRepository.updatePayment(paymentId, draft)
        }
      }.onSuccess {
        _uiState.update { it.copy(isSaving = false, isSaved = true) }
      }.onFailure { error ->
        _uiState.update {
          it.copy(
            isSaving = false,
            message = error.message ?: "No fue posible guardar el pago.",
          )
        }
      }
    }
  }

  fun messageShown() {
    _uiState.update { it.copy(message = null) }
  }
}
