package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.data.model.MemberTariffValue
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.PaymentRepository
import com.example.paymentacademy.domain.PaymentSetting
import com.example.paymentacademy.domain.UpdateMemberRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MembersUiState(
  val isLoading: Boolean = false,
  val members: List<Member> = emptyList(),
  val memberTariffs: Map<String, MemberTariffValue> = emptyMap(),
  val paymentSetting: PaymentSetting = PaymentSetting(),
  val message: String? = null,
)

class MembersViewModel(
  private val repository: MemberRepository,
  private val paymentRepository: PaymentRepository? = null,
) : ViewModel() {
  private val _uiState = MutableStateFlow(MembersUiState())
  val uiState: StateFlow<MembersUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching { loadAll() }
        .onSuccess { (members, tariffs, setting) ->
          _uiState.update {
            it.copy(
              isLoading = false,
              members = members,
              memberTariffs = tariffs,
              paymentSetting = setting,
            )
          }
        }
        .onFailure { error ->
          _uiState.update {
            it.copy(
              isLoading = false,
              message = error.message ?: "No fue posible cargar los miembros.",
            )
          }
        }
    }
  }

  fun updateMember(memberId: String, data: UpdateMemberRequest) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching {
        repository.updateMember(memberId, data)
        loadAll()
      }.onSuccess { (members, tariffs, setting) ->
        _uiState.update {
          it.copy(
            isLoading = false,
            members = members,
            memberTariffs = tariffs,
            paymentSetting = setting,
            message = "Datos del miembro actualizados.",
          )
        }
      }.onFailure { error ->
        _uiState.update {
          it.copy(
            isLoading = false,
            message = error.message ?: "No fue posible actualizar el miembro.",
          )
        }
      }
    }
  }

  fun deleteMember(memberId: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching {
        repository.deleteMember(memberId)
        loadAll()
      }.onSuccess { (members, tariffs, setting) ->
        _uiState.update {
          it.copy(
            isLoading = false,
            members = members,
            memberTariffs = tariffs,
            paymentSetting = setting,
            message = "Miembro dado de baja correctamente.",
          )
        }
      }.onFailure { error ->
        _uiState.update {
          it.copy(
            isLoading = false,
            message = error.message ?: "No fue posible eliminar el miembro.",
          )
        }
      }
    }
  }

  fun saveMemberTariff(memberId: String, value: MemberTariffValue) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching {
        repository.saveMemberTariff(memberId, value)
        loadAll()
      }.onSuccess { (members, tariffs, setting) ->
        _uiState.update {
          it.copy(
            isLoading = false,
            members = members,
            memberTariffs = tariffs,
            paymentSetting = setting,
            message = "Tarifa personalizada guardada.",
          )
        }
      }.onFailure { error ->
        _uiState.update {
          it.copy(
            isLoading = false,
            message = error.message ?: "No fue posible guardar la tarifa.",
          )
        }
      }
    }
  }

  fun deleteMemberTariff(memberId: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true) }
      runCatching {
        repository.deleteMemberTariff(memberId)
        loadAll()
      }.onSuccess { (members, tariffs, setting) ->
        _uiState.update {
          it.copy(
            isLoading = false,
            members = members,
            memberTariffs = tariffs,
            paymentSetting = setting,
            message = "Tarifa restablecida a los valores globales.",
          )
        }
      }.onFailure { error ->
        _uiState.update {
          it.copy(
            isLoading = false,
            message = error.message ?: "No fue posible restablecer la tarifa.",
          )
        }
      }
    }
  }

  fun messageShown() {
    _uiState.update { it.copy(message = null) }
  }

  private suspend fun loadAll(): Triple<List<Member>, Map<String, MemberTariffValue>, PaymentSetting> =
    coroutineScope {
      val membersDeferred = async { repository.getMembers() }
      val tariffsDeferred = async {
        runCatching { repository.getMemberTariffs() }.getOrDefault(emptyMap())
      }
      val settingDeferred = async {
        runCatching { paymentRepository?.getPaymentSetting() ?: PaymentSetting() }
          .getOrDefault(PaymentSetting())
      }
      Triple(membersDeferred.await(), tariffsDeferred.await(), settingDeferred.await())
    }
}
