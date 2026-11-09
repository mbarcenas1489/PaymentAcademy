package com.example.paymentacademy.domain

import com.example.paymentacademy.data.model.MemberPaymentSummary
import com.example.paymentacademy.data.model.PaymentSummary

data class PaymentDraft(
  val memberId: String,
  val amount: Double,
  val pendingAmount: Double = 0.0,
  val currency: String = "NIO",
  val paidOn: String,
  val paymentType: String,
  val paymentMethod: String,
  val validFrom: String,
  val validUntil: String,
  val reference: String?,
  val notes: String?,
)

data class InstallmentDraft(
  val paymentId: String,
  val installmentAmount: Double,
  val paidOn: String,
  val paymentMethod: String,
  val reference: String? = null,
  val notes: String? = null,
)

data class PaymentSetting(
  val amountMonthDollar: Double = 20.0,
  val amountMonthCordoba: Double = 750.0,
  val amountBiweek: Double = 10.0,
  val amountDay: Double = 3.0,
) {
  fun monthlyTariffFor(currency: String?): Double =
    if (CurrencyFormatter.normalizeCurrency(currency) == CurrencyFormatter.CURRENCY_USD) {
      amountMonthDollar
    } else {
      amountMonthCordoba
    }
}

data class PendingSyncSummary(
  val memberCount: Int = 0,
  val paymentCount: Int = 0,
) {
  val totalCount: Int
    get() = memberCount + paymentCount

  val hasPendingChanges: Boolean
    get() = totalCount > 0
}

enum class OrganizationRole(val remoteValue: String) {
  OWNER("owner"),
  ADMIN("admin"),
  STAFF("staff"),
  ;

  val canManagePayments: Boolean
    get() = this == OWNER || this == ADMIN

  companion object {
    fun fromRemoteValue(value: String?): OrganizationRole =
      entries.firstOrNull { it.remoteValue.equals(value, ignoreCase = true) } ?: STAFF
  }
}

data class SyncAuditReport(
  val localMemberCount: Int,
  val remoteMemberCount: Int,
  val localPaymentCount: Int,
  val remotePaymentCount: Int,
  val membersMissingLocally: Int,
  val synchronizedMembersMissingRemotely: Int,
  val paymentsMissingLocally: Int,
  val synchronizedPaymentsMissingRemotely: Int,
  val pendingChanges: Int,
) {
  val differenceCount: Int
    get() =
      membersMissingLocally +
        synchronizedMembersMissingRemotely +
        paymentsMissingLocally +
        synchronizedPaymentsMissingRemotely

  val hasDifferences: Boolean
    get() = differenceCount > 0
}

interface PaymentRepository {
  suspend fun restoreSession(): Boolean
  suspend fun signIn(email: String, password: String)
  suspend fun signOut()
  suspend fun getPendingSyncSummary(): PendingSyncSummary
  suspend fun synchronizePendingChanges(): PendingSyncSummary
  suspend fun getCurrentOrganizationRole(): OrganizationRole
  suspend fun auditSynchronization(): SyncAuditReport
  suspend fun getPayments(
    fromDate: String? = null,
    toDate: String? = null,
    memberId: String? = null,
    resultLimit: Long? = null,
  ): List<PaymentSummary>
  suspend fun getRecentPayments(): List<PaymentSummary> = getPayments(resultLimit = 100)
  suspend fun getMemberPaymentSummaries(): List<MemberPaymentSummary>
  suspend fun getPaymentSetting(): PaymentSetting
  suspend fun createPayment(payment: PaymentDraft): String
  suspend fun updatePayment(paymentId: String, payment: PaymentDraft)
  suspend fun registerInstallment(payment: PaymentSummary, installment: InstallmentDraft): PaymentSummary
  suspend fun deletePayment(paymentId: String)
}
