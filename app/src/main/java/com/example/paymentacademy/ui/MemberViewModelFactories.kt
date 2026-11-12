package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.PaymentRepository

class MembersViewModelFactory(
  private val repository: MemberRepository,
  private val paymentRepository: PaymentRepository? = null,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    MembersViewModel(repository, paymentRepository) as T
}

class RegisterMemberViewModelFactory(
  private val repository: MemberRepository,
  private val paymentRepository: PaymentRepository? = null,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    RegisterMemberViewModel(repository, paymentRepository) as T
}
