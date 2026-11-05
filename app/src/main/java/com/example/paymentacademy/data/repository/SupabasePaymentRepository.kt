package com.example.paymentacademy.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.work.WorkManager
import com.example.paymentacademy.data.local.DatabaseHelper
import com.example.paymentacademy.data.model.MemberPaymentSummary
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.domain.PaymentDraft
import com.example.paymentacademy.domain.PaymentRepository
import com.example.paymentacademy.domain.PendingSyncSummary
import com.example.paymentacademy.domain.OrganizationRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
private data class PaymentEntity(
  val id: String,
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
  @SerialName("created_at") val createdAt: String
)

@Serializable
private data class UpsertPaymentRecord(
  val id: String,
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

class SupabasePaymentRepository(
  private val client: SupabaseClient,
  private val context: Context
) : PaymentRepository {
  private val dbHelper = DatabaseHelper(context)
  private val organizationScope = OrganizationScope(client, context)

  override suspend fun restoreSession(): Boolean {
    client.auth.awaitInitialization()
    return client.auth.currentSessionOrNull() != null
  }

  override suspend fun signIn(email: String, password: String) {
    val previousPending = withContext(Dispatchers.IO) { getAllPendingSyncSummary() }
    check(!previousPending.hasPendingChanges) {
      "Hay ${previousPending.totalCount} cambios locales pendientes. No se cambiará de cuenta para evitar perderlos."
    }
    client.auth.signInWith(Email) {
      this.email = email
      this.password = password
    }
    cancelSyncWork()
    organizationScope.clear()
    runCatching {
      SupabaseMemberRepository(client, context).sync()
      sync()
    }.onFailure { error ->
      android.util.Log.w("PaymentRepository", "La sincronización inicial se reintentará.", error)
      com.example.paymentacademy.worker.SyncWorker.schedule(context)
    }
  }

  override suspend fun signOut() {
    val pending = withContext(Dispatchers.IO) { getAllPendingSyncSummary() }
    check(!pending.hasPendingChanges) {
      "Hay ${pending.totalCount} cambios pendientes. Sincronízalos antes de cerrar sesión."
    }
    cancelSyncWork()
    try {
      client.auth.signOut()
    } finally {
      organizationScope.clear()
      withContext(Dispatchers.IO) { dbHelper.clearAllData() }
    }
  }

  override suspend fun getPendingSyncSummary(): PendingSyncSummary = withContext(Dispatchers.IO) {
    pendingSyncSummary(organizationScope.activeOrganizationId())
  }

  override suspend fun synchronizePendingChanges(): PendingSyncSummary =
    withContext(Dispatchers.IO) {
      SupabaseMemberRepository(client, context).sync()
      sync()
      pendingSyncSummary(organizationScope.activeOrganizationId())
    }

  override suspend fun getCurrentOrganizationRole(): OrganizationRole =
    runCatching {
      OrganizationRole.fromRemoteValue(organizationScope.activeMembership().role)
    }.getOrDefault(OrganizationRole.STAFF)

  override suspend fun getPayments(
    fromDate: String?,
    toDate: String?,
    memberId: String?,
    resultLimit: Long?,
  ): List<PaymentSummary> = withContext(Dispatchers.IO) {
    val organizationId = organizationScope.activeOrganizationId()
    com.example.paymentacademy.worker.SyncWorker.schedule(context)
    
    val list = mutableListOf<PaymentSummary>()
    val db = dbHelper.readableDatabase
    var selection = "p.organization_id = ? AND p.is_deleted = 0"
    val args = mutableListOf(organizationId)

    if (fromDate != null) {
      selection += " AND p.paid_on >= ?"
      args.add(fromDate)
    }
    if (toDate != null) {
      selection += " AND p.paid_on <= ?"
      args.add(toDate)
    }
    if (memberId != null) {
      selection += " AND p.member_id = ?"
      args.add(memberId)
    }

    var query = """
        SELECT p.*, m.full_name as member_name 
        FROM payments p 
        LEFT JOIN members m
          ON p.member_id = m.id AND p.organization_id = m.organization_id
        WHERE $selection 
        ORDER BY p.paid_on DESC, p.created_at DESC
    """
    if (resultLimit != null) query += " LIMIT $resultLimit"

    db.rawQuery(query, args.toTypedArray()).use { cursor ->
      while (cursor.moveToNext()) {
        list.add(
          PaymentSummary(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            organizationId = cursor.getString(cursor.getColumnIndexOrThrow("organization_id")),
            memberId = cursor.getString(cursor.getColumnIndexOrThrow("member_id")),
            memberName = cursor.getString(cursor.getColumnIndexOrThrow("member_name")) ?: "Desconocido",
            amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount")),
            paidOn = cursor.getString(cursor.getColumnIndexOrThrow("paid_on")),
            paymentType = cursor.getString(cursor.getColumnIndexOrThrow("payment_type")),
            paymentMethod = cursor.getString(cursor.getColumnIndexOrThrow("payment_method")),
            validFrom = cursor.getString(cursor.getColumnIndexOrThrow("valid_from")),
            validUntil = cursor.getString(cursor.getColumnIndexOrThrow("valid_until")),
            reference = cursor.getString(cursor.getColumnIndexOrThrow("reference")),
            notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
            createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
            isSyncPending = cursor.getInt(cursor.getColumnIndexOrThrow("sync_pending")) == 1
          )
        )
      }
    }
    list
  }

  override suspend fun getMemberPaymentSummaries(): List<MemberPaymentSummary> = withContext(Dispatchers.IO) {
    val organizationId = organizationScope.activeOrganizationId()
    com.example.paymentacademy.worker.SyncWorker.schedule(context)

    val list = mutableListOf<MemberPaymentSummary>()
    val query = """
        SELECT m.id as member_id, m.organization_id, m.full_name, m.document_number, m.email, m.phone, m.status as member_status,
               p.id as last_payment_id, p.amount as last_amount, p.paid_on as last_paid_on, p.payment_type as last_payment_type,
               p.payment_method as last_payment_method, p.valid_from as last_valid_from, p.valid_until as last_valid_until
        FROM members m
        LEFT JOIN payments p ON p.id = (
            SELECT id FROM payments 
            WHERE organization_id = m.organization_id AND member_id = m.id AND is_deleted = 0
            ORDER BY valid_until DESC LIMIT 1
        )
        WHERE m.organization_id = ?
        ORDER BY m.full_name ASC
    """
    
    dbHelper.readableDatabase.rawQuery(query, arrayOf(organizationId)).use { cursor ->
      while (cursor.moveToNext()) {
        list.add(
          MemberPaymentSummary(
            memberId = cursor.getString(cursor.getColumnIndexOrThrow("member_id")),
            organizationId = cursor.getString(cursor.getColumnIndexOrThrow("organization_id")),
            fullName = cursor.getString(cursor.getColumnIndexOrThrow("full_name")),
            documentNumber = cursor.getString(cursor.getColumnIndexOrThrow("document_number")),
            email = cursor.getString(cursor.getColumnIndexOrThrow("email")),
            phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")),
            memberStatus = cursor.getString(cursor.getColumnIndexOrThrow("member_status")),
            lastPaymentId = cursor.getString(cursor.getColumnIndexOrThrow("last_payment_id")),
            lastAmount = if (!cursor.isNull(cursor.getColumnIndexOrThrow("last_amount"))) cursor.getDouble(cursor.getColumnIndexOrThrow("last_amount")) else null,
            lastPaidOn = cursor.getString(cursor.getColumnIndexOrThrow("last_paid_on")),
            lastPaymentType = cursor.getString(cursor.getColumnIndexOrThrow("last_payment_type")),
            lastPaymentMethod = cursor.getString(cursor.getColumnIndexOrThrow("last_payment_method")),
            lastValidFrom = cursor.getString(cursor.getColumnIndexOrThrow("last_valid_from")),
            lastValidUntil = cursor.getString(cursor.getColumnIndexOrThrow("last_valid_until"))
          )
        )
      }
    }
    list
  }

  override suspend fun createPayment(payment: PaymentDraft) = withContext(Dispatchers.IO) {
    val orgId = organizationScope.activeOrganizationId()
    ensureValidityAvailableLocal(payment, organizationId = orgId)
    
    val newId = UUID.randomUUID().toString()
    val today = LocalDate.now().toString()

    val values = ContentValues().apply {
      put("id", newId)
      put("organization_id", orgId)
      put("member_id", payment.memberId)
      put("amount", payment.amount)
      put("paid_on", payment.paidOn)
      put("payment_type", payment.paymentType)
      put("payment_method", payment.paymentMethod)
      put("valid_from", payment.validFrom)
      put("valid_until", payment.validUntil)
      put("reference", payment.reference)
      put("notes", payment.notes)
      put("created_at", today)
      put("sync_pending", 1)
      put("is_deleted", 0)
    }
    dbHelper.writableDatabase.insert("payments", null, values)

    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  override suspend fun updatePayment(paymentId: String, payment: PaymentDraft) = withContext(Dispatchers.IO) {
    check(getCurrentOrganizationRole().canManagePayments) {
      "Tu rol no permite editar pagos."
    }
    val organizationId = organizationScope.activeOrganizationId()
    ensureValidityAvailableLocal(payment, paymentId, organizationId)
    
    val values = ContentValues().apply {
      put("member_id", payment.memberId)
      put("amount", payment.amount)
      put("paid_on", payment.paidOn)
      put("payment_type", payment.paymentType)
      put("payment_method", payment.paymentMethod)
      put("valid_from", payment.validFrom)
      put("valid_until", payment.validUntil)
      put("reference", payment.reference)
      put("notes", payment.notes)
      put("sync_pending", 1)
    }
    val rows = dbHelper.writableDatabase.update(
      "payments",
      values,
      "id = ? AND organization_id = ?",
      arrayOf(paymentId, organizationId),
    )
    if (rows == 0) error("No tienes permiso para editar este pago o no existe localmente.")

    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  override suspend fun deletePayment(paymentId: String) = withContext(Dispatchers.IO) {
    check(getCurrentOrganizationRole().canManagePayments) {
      "Tu rol no permite eliminar pagos."
    }
    val organizationId = organizationScope.activeOrganizationId()
    val values = ContentValues().apply {
      put("is_deleted", 1)
      put("sync_pending", 1)
    }
    val rows = dbHelper.writableDatabase.update(
      "payments",
      values,
      "id = ? AND organization_id = ?",
      arrayOf(paymentId, organizationId),
    )
    if (rows == 0) error("No tienes permiso para eliminar este pago o no existe localmente.")

    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  suspend fun sync() {
    val organizationId = organizationScope.activeOrganizationId()
    syncUpPayments(organizationId)
    syncDownPayments(organizationId)
  }

  private suspend fun syncDownPayments(organizationId: String) {
    val remotePayments = client.from("payments")
      .select { filter { eq("organization_id", organizationId) } }
      .decodeList<PaymentEntity>()
    val db = dbHelper.writableDatabase
    db.beginTransaction()
    try {
      for (payment in remotePayments) {
        val values = ContentValues().apply {
          put("id", payment.id)
          put("organization_id", payment.organizationId)
          put("member_id", payment.memberId)
          put("amount", payment.amount)
          put("paid_on", payment.paidOn)
          put("payment_type", payment.paymentType)
          put("payment_method", payment.paymentMethod)
          put("valid_from", payment.validFrom)
          put("valid_until", payment.validUntil)
          put("reference", payment.reference)
          put("notes", payment.notes)
          put("created_at", payment.createdAt)
          put("sync_pending", 0)
          put("is_deleted", 0)
        }
        db.insertWithOnConflict("payments", null, values, SQLiteDatabase.CONFLICT_REPLACE)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  private suspend fun syncUpPayments(organizationId: String) {
    val db = dbHelper.readableDatabase
    
    // 1. Sync Upserts (Inserts and Updates)
    val cursor = db.query(
      "payments",
      null,
      "organization_id = ? AND sync_pending = 1 AND is_deleted = 0",
      arrayOf(organizationId),
      null,
      null,
      null,
    )
    val upserts = mutableListOf<UpsertPaymentRecord>()
    val upsertIds = mutableListOf<String>()
    
    cursor.use {
      while (it.moveToNext()) {
        upsertIds.add(it.getString(it.getColumnIndexOrThrow("id")))
        upserts.add(
          UpsertPaymentRecord(
            id = it.getString(it.getColumnIndexOrThrow("id")),
            organizationId = it.getString(it.getColumnIndexOrThrow("organization_id")),
            memberId = it.getString(it.getColumnIndexOrThrow("member_id")),
            amount = it.getDouble(it.getColumnIndexOrThrow("amount")),
            paidOn = it.getString(it.getColumnIndexOrThrow("paid_on")),
            paymentType = it.getString(it.getColumnIndexOrThrow("payment_type")),
            paymentMethod = it.getString(it.getColumnIndexOrThrow("payment_method")),
            validFrom = it.getString(it.getColumnIndexOrThrow("valid_from")),
            validUntil = it.getString(it.getColumnIndexOrThrow("valid_until")),
            reference = it.getString(it.getColumnIndexOrThrow("reference")),
            notes = it.getString(it.getColumnIndexOrThrow("notes"))
          )
        )
      }
    }

    if (upserts.isNotEmpty()) {
      try {
        client.from("payments").upsert(upserts)
        val writeDb = dbHelper.writableDatabase
        for (id in upsertIds) {
          val values = ContentValues().apply { put("sync_pending", 0) }
          writeDb.update(
            "payments",
            values,
            "id = ? AND organization_id = ?",
            arrayOf(id, organizationId),
          )
        }
      } catch (e: Exception) {
        android.util.Log.e("PaymentRepository", "Error syncing upserts", e)
        throw e
      }
    }

    // 2. Sync Deletes
    val delCursor = db.query(
      "payments",
      arrayOf("id"),
      "organization_id = ? AND is_deleted = 1 AND sync_pending = 1",
      arrayOf(organizationId),
      null,
      null,
      null,
    )
    val deleteIds = mutableListOf<String>()
    delCursor.use {
      while (it.moveToNext()) {
        deleteIds.add(it.getString(it.getColumnIndexOrThrow("id")))
      }
    }

    if (deleteIds.isNotEmpty()) {
      for (id in deleteIds) {
        try {
          client.from("payments").delete {
            filter {
              eq("id", id)
              eq("organization_id", organizationId)
            }
          }
          dbHelper.writableDatabase.delete(
            "payments",
            "id = ? AND organization_id = ?",
            arrayOf(id, organizationId),
          )
        } catch (e: Exception) {
          android.util.Log.e("PaymentRepository", "Error syncing deletes", e)
          throw e
        }
      }
    }
  }

  private suspend fun ensureValidityAvailableLocal(
    payment: PaymentDraft,
    excludedPaymentId: String? = null,
    organizationId: String,
  ) {
    val db = dbHelper.readableDatabase
    var selection = "organization_id = ? AND member_id = ? AND valid_from <= ? AND valid_until >= ? AND is_deleted = 0"
    val args = mutableListOf(organizationId, payment.memberId, payment.validUntil, payment.validFrom)
    
    if (excludedPaymentId != null) {
       selection += " AND id != ?"
       args.add(excludedPaymentId)
    }
    
    db.query("payments", arrayOf("id"), selection, args.toTypedArray(), null, null, null).use { cursor ->
        if (cursor.count > 0) {
            error("Ya existe un pago para este miembro dentro del período de vigencia seleccionado.")
        }
    }
  }

  private companion object {
    const val SYNC_WORK_NAME = "PaymentAcademySync"
  }

  private suspend fun cancelSyncWork() {
    withContext(Dispatchers.IO) {
      WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME).result.get()
    }
  }

  private fun pendingSyncSummary(organizationId: String): PendingSyncSummary = PendingSyncSummary(
    memberCount = countRows(
      table = "members",
      selection = "organization_id = ? AND sync_pending = 1",
      selectionArgs = arrayOf(organizationId),
    ),
    paymentCount = countRows(
      table = "payments",
      selection = "organization_id = ? AND sync_pending = 1",
      selectionArgs = arrayOf(organizationId),
    ),
  )

  private fun getAllPendingSyncSummary(): PendingSyncSummary = PendingSyncSummary(
    memberCount = countRows("members", "sync_pending = 1"),
    paymentCount = countRows("payments", "sync_pending = 1"),
  )

  private fun countRows(
    table: String,
    selection: String,
    selectionArgs: Array<String>? = null,
  ): Int {
    dbHelper.readableDatabase.query(
      table,
      arrayOf("COUNT(*)"),
      selection,
      selectionArgs,
      null,
      null,
      null,
    ).use { cursor ->
      return if (cursor.moveToFirst()) cursor.getInt(0) else 0
    }
  }
}
