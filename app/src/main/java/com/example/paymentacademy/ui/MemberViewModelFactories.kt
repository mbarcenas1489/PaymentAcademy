package com.example.paymentacademy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.paymentacademy.domain.MemberRepository

class MembersViewModelFactory(
  private val repository: MemberRepository,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    MembersViewModel(repository) as T
}

class RegisterMemberViewModelFactory(
  private val repository: MemberRepository,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T =
    RegisterMemberViewModel(repository) as T
}
