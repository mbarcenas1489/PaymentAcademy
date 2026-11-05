package com.example.paymentacademy

import com.example.paymentacademy.domain.PendingSyncSummary
import com.example.paymentacademy.domain.OrganizationRole
import com.example.paymentacademy.domain.SyncAuditReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingSyncSummaryTest {
  @Test
  fun `total includes pending members and payments`() {
    val summary = PendingSyncSummary(memberCount = 2, paymentCount = 3)

    assertEquals(5, summary.totalCount)
    assertTrue(summary.hasPendingChanges)
  }

  @Test
  fun `empty summary has no pending changes`() {
    val summary = PendingSyncSummary()

    assertEquals(0, summary.totalCount)
    assertFalse(summary.hasPendingChanges)
  }

  @Test
  fun `only owners and admins can manage existing payments`() {
    assertTrue(OrganizationRole.fromRemoteValue("owner").canManagePayments)
    assertTrue(OrganizationRole.fromRemoteValue("admin").canManagePayments)
    assertFalse(OrganizationRole.fromRemoteValue("staff").canManagePayments)
    assertFalse(OrganizationRole.fromRemoteValue("unexpected").canManagePayments)
  }

  @Test
  fun `audit differences exclude pending changes from discrepancy total`() {
    val report = SyncAuditReport(
      localMemberCount = 10,
      remoteMemberCount = 9,
      localPaymentCount = 20,
      remotePaymentCount = 19,
      membersMissingLocally = 1,
      synchronizedMembersMissingRemotely = 2,
      paymentsMissingLocally = 3,
      synchronizedPaymentsMissingRemotely = 4,
      pendingChanges = 5,
    )

    assertEquals(10, report.differenceCount)
    assertTrue(report.hasDifferences)
  }
}
