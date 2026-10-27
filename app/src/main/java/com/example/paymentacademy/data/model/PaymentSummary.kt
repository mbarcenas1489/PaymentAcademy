package com.example.paymentacademy.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentSummary(
  val id: String,
  @SerialName("organization_id") val organizationId: String,
  @SerialName("member_id") val memberId: String,
  @SerialName("member_name") val memberName: String,
  @SerialName("subscription_id") val subscriptionId: String? = null,
  @SerialName("plan_name") val planName: String? = null,
  val amount: Double,
  @SerialName("paid_on") val paidOn: String,
  @SerialName("payment_type") val paymentType: String = "monthly_fee",
  @SerialName("payment_method") val paymentMethod: String,
  @SerialName("valid_from") val validFrom: String? = null,
  @SerialName("valid_until") val validUntil: String? = null,
  val reference: String? = null,
  val notes: String? = null,
  @SerialName("created_at") val createdAt: String,
  val isSyncPending: Boolean = false,
)

@Serializable
data class NewPaymentRecord(
  @SerialName("organization_id") val organizationId: String,
  @SerialName("member_id") val memberId: String,
  val amount: Double,
  @SerialName("paid_on") val paidOn: String,
  @SerialName("payment_type") val paymentType: String,
  @SerialName("payment_method") val paymentMethod: String,
  @SerialName("valid_from") val validFrom: String,
  @SerialName("valid_until") val validUntil: String,
  val reference: String? = null,
  val notes: String? = null,
)

@Serializable
data class UpdatePaymentRecord(
  @SerialName("member_id") val memberId: String,
  val amount: Double,
  @SerialName("paid_on") val paidOn: String,
  @SerialName("payment_type") val paymentType: String,
  @SerialName("payment_method") val paymentMethod: String,
  @SerialName("valid_from") val validFrom: String,
  @SerialName("valid_until") val validUntil: String,
  val reference: String? = null,
  val notes: String? = null,
)

@Serializable
data class PaymentMutationResult(val id: String)
