package com.example.paymentacademy.domain

import com.example.paymentacademy.data.model.PaymentSummary

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
  suspend fun getRecentPayments(): List<PaymentSummary>
  suspend fun createPayment(payment: PaymentDraft)
  suspend fun updatePayment(paymentId: String, payment: PaymentDraft)
  suspend fun deletePayment(paymentId: String)
}
