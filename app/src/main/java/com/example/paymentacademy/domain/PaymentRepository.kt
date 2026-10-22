package com.example.paymentacademy.domain

import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.data.model.MemberPaymentSummary

data class PaymentDraft(
  val memberId: String,
  val amount: Double,
  val paidOn: String,
  val paymentType: String,
  val paymentMethod: String,
  val validFrom: String,
  val validUntil: String,
  val reference: String?,
  val notes: String?,
)

interface PaymentRepository {
  suspend fun restoreSession(): Boolean
  suspend fun signIn(email: String, password: String)
  suspend fun signOut()
  suspend fun getPayments(
    fromDate: String? = null,
    toDate: String? = null,
    memberId: String? = null,
    resultLimit: Long? = null,
  ): List<PaymentSummary>
  suspend fun getRecentPayments(): List<PaymentSummary> = getPayments(resultLimit = 100)
  suspend fun getMemberPaymentSummaries(): List<MemberPaymentSummary>
  suspend fun createPayment(payment: PaymentDraft)
  suspend fun updatePayment(paymentId: String, payment: PaymentDraft)
  suspend fun deletePayment(paymentId: String)
}
