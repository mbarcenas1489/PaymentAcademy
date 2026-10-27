package com.example.paymentacademy.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Member(
  val id: String,
  @SerialName("organization_id") val organizationId: String,
  @SerialName("full_name") val fullName: String,
  @SerialName("document_number") val documentNumber: String? = null,
  val email: String? = null,
  val phone: String? = null,
  val status: String,
  @SerialName("joined_on") val joinedOn: String,
  val notes: String? = null,
  @SerialName("created_at") val createdAt: String,
  @SerialName("updated_at") val updatedAt: String,
  val isSyncPending: Boolean = false,
)

@Serializable
data class OrganizationMembership(
  @SerialName("organization_id") val organizationId: String,
)

@Serializable
data class NewMemberRecord(
  @SerialName("organization_id") val organizationId: String,
  @SerialName("full_name") val fullName: String,
  @SerialName("document_number") val documentNumber: String? = null,
  val email: String? = null,
  val phone: String? = null,
  val notes: String? = null,
)
