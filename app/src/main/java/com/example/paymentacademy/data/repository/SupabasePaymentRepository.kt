package com.example.paymentacademy.data.repository

import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.domain.PaymentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from

class SupabasePaymentRepository(private val client: SupabaseClient) : PaymentRepository {
  override suspend fun restoreSession(): Boolean {
    client.auth.awaitInitialization()
    return client.auth.currentSessionOrNull() != null
  }

  override suspend fun signIn(email: String, password: String) {
    client.auth.signInWith(Email) {
      this.email = email
      this.password = password
    }
  }

  override suspend fun signOut() = client.auth.signOut()

  override suspend fun getRecentPayments(): List<PaymentSummary> =
    client.from("payment_overview")
      .select()
      .decodeList<PaymentSummary>()
      .sortedWith(compareByDescending<PaymentSummary> { it.paidOn }.thenByDescending { it.id })
      .take(100)
}
