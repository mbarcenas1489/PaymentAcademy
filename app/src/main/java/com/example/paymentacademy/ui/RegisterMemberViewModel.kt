package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentacademy.data.model.MemberTariffValue
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.NewMember
import com.example.paymentacademy.domain.PaymentRepository
import com.example.paymentacademy.domain.PaymentSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterMemberUiState(
  val isLoading: Boolean = false,
  val isSaved: Boolean = false,
  val paymentSetting: PaymentSetting = PaymentSetting(),
  val fullNameError: String? = null,
  val emailError: String? = null,
  val tariffError: String? = null,
  val message: String? = null,
)

class RegisterMemberViewModel(
  private val repository: MemberRepository,
  private val paymentRepository: PaymentRepository? = null,
) : ViewModel() {
  private val _uiState = MutableStateFlow(RegisterMemberUiState())
  val uiState: StateFlow<RegisterMemberUiState> = _uiState.asStateFlow()

  init {
    if (paymentRepository != null) {
      viewModelScope.launch {
        runCatching { paymentRepository.getPaymentSetting() }
          .onSuccess { setting ->
            _uiState.update { it.copy(paymentSetting = setting) }
          }
      }
    }
  }

  fun save(
    fullName: String,
    documentNumber: String,
    email: String,
    phone: String,
    notes: String,
    useCustomTariff: Boolean = false,
    customAmountCordoba: String = "",
    customAmountDollar: String = "",
  ) {
    val cleanName = fullName.trim()
    val cleanEmail = email.trim()
    val nameError = if (cleanName.isBlank()) "El nombre es obligatorio." else null
    val emailError = if (cleanEmail.isNotEmpty() && !EMAIL_REGEX.matches(cleanEmail)) {
      "Ingresa un correo válido."
    } else null

    val cordobaVal = customAmountCordoba.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
    val dollarVal = customAmountDollar.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
    val tariffError = if (useCustomTariff && cordobaVal == null && dollarVal == null) {
      "Ingresa un monto válido mayor a cero para la tarifa personalizada."
    } else null

    if (nameError != null || emailError != null || tariffError != null) {
      _uiState.update {
        it.copy(
          isLoading = false,
          fullNameError = nameError,
          emailError = emailError,
          tariffError = tariffError,
        )
      }
      return
    }

    val customTariff = if (useCustomTariff) {
      MemberTariffValue(
        AmountMonthCordoba = cordobaVal ?: _uiState.value.paymentSetting.amountMonthCordoba,
        AmountMonthDollar = dollarVal ?: _uiState.value.paymentSetting.amountMonthDollar,
      )
    } else null

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isLoading = true,
          fullNameError = null,
          emailError = null,
          tariffError = null,
          message = null,
        )
      }
      runCatching {
        repository.createMember(
          NewMember(
            fullName = cleanName,
            documentNumber = documentNumber.trim().ifBlank { null },
            email = cleanEmail.ifBlank { null },
            phone = phone.trim().ifBlank { null },
            notes = notes.trim().ifBlank { null },
            customTariff = customTariff,
          ),
        )
      }.onSuccess {
        _uiState.update { it.copy(isLoading = false, isSaved = true) }
      }.onFailure { error ->
        _uiState.update {
          it.copy(
            isLoading = false,
            message = error.message ?: "No fue posible guardar el miembro.",
          )
        }
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
