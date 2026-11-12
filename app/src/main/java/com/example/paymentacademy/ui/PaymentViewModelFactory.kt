package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.PaymentRepository

class PaymentViewModelFactory(
  private val repository: PaymentRepository,
  private val memberRepository: MemberRepository? = null,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(PaymentViewModel::class.java))
    return PaymentViewModel(repository, memberRepository) as T
  }
}
