package com.example.paymentacademy.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MemberPaymentSummary(
  @SerialName("member_id") val memberId: String,
  @SerialName("organization_id") val organizationId: String,
  @SerialName("full_name") val fullName: String,
  @SerialName("document_number") val documentNumber: String? = null,
  val email: String? = null,
  val phone: String? = null,
  @SerialName("member_status") val memberStatus: String,
  @SerialName("last_payment_id") val lastPaymentId: String? = null,
  @SerialName("last_amount") val lastAmount: Double? = null,
  @SerialName("last_paid_on") val lastPaidOn: String? = null,
  @SerialName("last_payment_type") val lastPaymentType: String? = null,
  @SerialName("last_payment_method") val lastPaymentMethod: String? = null,
  @SerialName("last_valid_from") val lastValidFrom: String? = null,
  @SerialName("last_valid_until") val lastValidUntil: String? = null,
)
