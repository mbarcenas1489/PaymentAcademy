package com.example.paymentacademy.domain

import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.data.model.MemberTariffValue

data class NewMember(
  val fullName: String,
  val documentNumber: String?,
  val email: String?,
  val phone: String?,
  val notes: String?,
  val customTariff: MemberTariffValue? = null,
)

data class UpdateMemberRequest(
  val fullName: String,
  val documentNumber: String? = null,
  val phone: String? = null,
  val email: String? = null,
  val status: String = "active",
  val notes: String? = null,
)

interface MemberRepository {
  suspend fun getMembers(): List<Member>
  suspend fun createMember(member: NewMember)
  suspend fun updateMember(memberId: String, data: UpdateMemberRequest) = Unit
  suspend fun deleteMember(memberId: String) = Unit
  suspend fun getMemberTariffs(): Map<String, MemberTariffValue> = emptyMap()
  suspend fun saveMemberTariff(memberId: String, value: MemberTariffValue) = Unit
  suspend fun deleteMemberTariff(memberId: String) = Unit
}
