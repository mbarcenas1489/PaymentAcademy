package com.example.paymentacademy.domain

import com.example.paymentacademy.data.model.PaymentSummary

interface PaymentRepository {
  suspend fun restoreSession(): Boolean
  suspend fun signIn(email: String, password: String)
  suspend fun signOut()
  suspend fun getRecentPayments(): List<PaymentSummary>
}
