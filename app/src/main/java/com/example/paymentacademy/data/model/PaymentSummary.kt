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
  @SerialName("payment_method") val paymentMethod: String,
  val reference: String? = null,
  val notes: String? = null,
  @SerialName("created_at") val createdAt: String,
)
