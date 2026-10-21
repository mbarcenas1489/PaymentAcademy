package com.example.paymentacademy.data.repository

import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.data.model.NewMemberRecord
import com.example.paymentacademy.data.model.OrganizationMembership
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.NewMember
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class SupabaseMemberRepository(
  private val client: SupabaseClient,
) : MemberRepository {
  private var organizationId: String? = null

  override suspend fun getMembers(): List<Member> = client.from("members")
    .select()
    .decodeList<Member>()
    .sortedBy { it.fullName.lowercase() }

  override suspend fun createMember(member: NewMember) {
    client.from("members").insert(
      NewMemberRecord(
        organizationId = currentOrganizationId(),
        fullName = member.fullName,
        documentNumber = member.documentNumber,
        email = member.email,
        phone = member.phone,
        notes = member.notes,
      ),
    )
  }

  private suspend fun currentOrganizationId(): String {
    organizationId?.let { return it }
    val membership = client.from("organization_members")
      .select()
      .decodeList<OrganizationMembership>()
      .firstOrNull()
      ?: error("Tu usuario no está asociado a una academia o gimnasio.")
    return membership.organizationId.also { organizationId = it }
  }
}
