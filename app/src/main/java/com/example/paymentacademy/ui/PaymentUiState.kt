package com.example.paymentacademy.ui

import com.example.paymentacademy.data.model.MemberPaymentSummary
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.domain.OrganizationRole
import com.example.paymentacademy.domain.SyncAuditReport
import java.time.LocalDate

data class PaymentUiState(
  val isAuthenticated: Boolean = false,
  val isLoading: Boolean = false,
  val members: List<MemberPaymentSummary> = emptyList(),
  val movements: List<PaymentSummary> = emptyList(),
  val monthlyTotal: Double = 0.0,
  val organizationRole: OrganizationRole = OrganizationRole.STAFF,
  val syncStatus: SyncStatus = SyncStatus.SYNCED,
  val pendingSyncCount: Int = 0,
  val pendingLogoutCount: Int? = null,
  val syncAuditReport: SyncAuditReport? = null,
  val fromDate: LocalDate = currentMonthStart(),
  val toDate: LocalDate = currentMonthEnd(),
  val message: String? = null,
) {
  companion object {
    private fun currentMonthStart(): LocalDate = LocalDate.now().withDayOfMonth(1)
    private fun currentMonthEnd(): LocalDate = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth())
  }
}

enum class SyncStatus { SYNCED, PENDING, SYNCING, WAITING, ERROR }
