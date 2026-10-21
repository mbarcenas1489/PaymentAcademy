package com.example.paymentacademy.data.repository

import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.data.model.PaymentMutationResult
import com.example.paymentacademy.data.model.NewPaymentRecord
import com.example.paymentacademy.data.model.OrganizationMembership
import com.example.paymentacademy.data.model.UpdatePaymentRecord
import com.example.paymentacademy.domain.PaymentDraft
import com.example.paymentacademy.domain.PaymentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from

class SupabasePaymentRepository(private val client: SupabaseClient) : PaymentRepository {
  private var organizationId: String? = null
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

  override suspend fun createPayment(payment: PaymentDraft) {
    client.from("payments").insert(
      NewPaymentRecord(
        organizationId = currentOrganizationId(),
        memberId = payment.memberId,
        amount = payment.amount,
        paidOn = payment.paidOn,
        paymentType = payment.paymentType,
        paymentMethod = payment.paymentMethod,
        validFrom = payment.validFrom,
        validUntil = payment.validUntil,
        reference = payment.reference,
        notes = payment.notes,
      ),
    )
  }

  override suspend fun updatePayment(paymentId: String, payment: PaymentDraft) {
    val updated = client.from("payments").update(
      UpdatePaymentRecord(
        memberId = payment.memberId,
        amount = payment.amount,
        paidOn = payment.paidOn,
        paymentType = payment.paymentType,
        paymentMethod = payment.paymentMethod,
        validFrom = payment.validFrom,
        validUntil = payment.validUntil,
        reference = payment.reference,
        notes = payment.notes,
      ),
    ) {
      filter { eq("id", paymentId) }
      select()
    }
    if (updated.decodeList<PaymentMutationResult>().isEmpty()) {
      error("No tienes permiso para editar este pago.")
    }
  }

  override suspend fun deletePayment(paymentId: String) {
    val deleted = client.from("payments").delete {
      filter { eq("id", paymentId) }
      select()
    }
    if (deleted.decodeList<PaymentMutationResult>().isEmpty()) {
      error("No tienes permiso para eliminar este pago.")
    }
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
