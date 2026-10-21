package com.example.paymentacademy

import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.NewMember
import com.example.paymentacademy.ui.RegisterMemberViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RegisterMemberViewModelTest {
  private val viewModel = RegisterMemberViewModel(FakeMemberRepository())

  @Test
  fun `empty name shows validation error`() {
    viewModel.save("", "", "", "", "")

    assertNotNull(viewModel.uiState.value.fullNameError)
    assertEquals(false, viewModel.uiState.value.isLoading)
  }

  @Test
  fun `invalid optional email shows validation error`() {
    viewModel.save("Ana Torres", "", "correo-invalido", "", "")

    assertNotNull(viewModel.uiState.value.emailError)
    assertEquals(false, viewModel.uiState.value.isLoading)
  }

  private class FakeMemberRepository : MemberRepository {
    override suspend fun getMembers(): List<Member> = emptyList()
    override suspend fun createMember(member: NewMember) = Unit
  }
}
