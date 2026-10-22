package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.PaymentRepository

class PaymentsViewModelFactory(
  private val repository: PaymentRepository,
  private val memberId: String? = null,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    PaymentsViewModel(repository, memberId) as T
}

class PaymentFormViewModelFactory(
  private val paymentRepository: PaymentRepository,
  private val memberRepository: MemberRepository,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    PaymentFormViewModel(paymentRepository, memberRepository) as T
}
