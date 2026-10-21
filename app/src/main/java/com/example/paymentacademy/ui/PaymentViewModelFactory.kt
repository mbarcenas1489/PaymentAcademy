package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.paymentacademy.domain.PaymentRepository

class PaymentViewModelFactory(
  private val repository: PaymentRepository,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    require(modelClass.isAssignableFrom(PaymentViewModel::class.java))
    return PaymentViewModel(repository) as T
  }
}
