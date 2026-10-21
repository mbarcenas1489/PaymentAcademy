package com.example.paymentacademy.domain

import com.example.paymentacademy.data.model.Member

data class NewMember(
  val fullName: String,
  val documentNumber: String?,
  val email: String?,
  val phone: String?,
  val notes: String?,
)

interface MemberRepository {
  suspend fun getMembers(): List<Member>
  suspend fun createMember(member: NewMember)
}
