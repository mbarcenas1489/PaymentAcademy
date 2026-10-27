package com.example.paymentacademy.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.paymentacademy.data.local.DatabaseHelper
import com.example.paymentacademy.data.model.MemberPaymentSummary
import com.example.paymentacademy.data.model.OrganizationMembership
import com.example.paymentacademy.data.model.PaymentSummary
import com.example.paymentacademy.domain.PaymentDraft
import com.example.paymentacademy.domain.PaymentRepository
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

  override suspend fun getPayments(
    fromDate: String?,
    toDate: String?,
    memberId: String?,
    resultLimit: Long?,
  ): List<PaymentSummary> = withContext(Dispatchers.IO) {
    try {
      syncUpPayments()
    } catch (e: Exception) {}
    
    try {
      syncDownPayments()
    } catch (e: Exception) {}
    
    val list = mutableListOf<PaymentSummary>()
    val db = dbHelper.readableDatabase
    var selection = "p.is_deleted = 0"
    val args = mutableListOf<String>()

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
        LEFT JOIN members m ON p.member_id = m.id 
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
    try {
      syncUpPayments()
    } catch (e: Exception) {}

    try {
      syncDownPayments()
    } catch (e: Exception) {}

    val list = mutableListOf<MemberPaymentSummary>()
    val query = """
        SELECT m.id as member_id, m.organization_id, m.full_name, m.document_number, m.email, m.phone, m.status as member_status,
               p.id as last_payment_id, p.amount as last_amount, p.paid_on as last_paid_on, p.payment_type as last_payment_type,
               p.payment_method as last_payment_method, p.valid_from as last_valid_from, p.valid_until as last_valid_until
        FROM members m
        LEFT JOIN payments p ON p.id = (
            SELECT id FROM payments 
            WHERE member_id = m.id AND is_deleted = 0 
            ORDER BY valid_until DESC LIMIT 1
        )
        ORDER BY m.full_name ASC
    """
    
    dbHelper.readableDatabase.rawQuery(query, emptyArray()).use { cursor ->
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
    val orgId = currentOrganizationId()
    ensureValidityAvailableLocal(payment)
    
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

    try {
      syncUpPayments()
    } catch (e: Exception) {
      // Offline mode
    }
    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  override suspend fun updatePayment(paymentId: String, payment: PaymentDraft) = withContext(Dispatchers.IO) {
    ensureValidityAvailableLocal(payment, paymentId)
    
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
    val rows = dbHelper.writableDatabase.update("payments", values, "id = ?", arrayOf(paymentId))
    if (rows == 0) error("No tienes permiso para editar este pago o no existe localmente.")

    try {
      syncUpPayments()
    } catch (e: Exception) {
      // Offline mode
    }
    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  override suspend fun deletePayment(paymentId: String) = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
      put("is_deleted", 1)
      put("sync_pending", 1)
    }
    val rows = dbHelper.writableDatabase.update("payments", values, "id = ?", arrayOf(paymentId))
    if (rows == 0) error("No tienes permiso para eliminar este pago o no existe localmente.")

    try {
      syncUpPayments()
    } catch (e: Exception) {
      // Offline mode
    }
    
    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  suspend fun sync() {
    syncUpPayments()
    syncDownPayments()
  }

  private suspend fun syncDownPayments() {
    val remotePayments = client.from("payments").select().decodeList<PaymentEntity>()
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

  private suspend fun syncUpPayments() {
    val db = dbHelper.readableDatabase
    
    // 1. Sync Upserts (Inserts and Updates)
    val cursor = db.query("payments", null, "sync_pending = 1 AND is_deleted = 0", null, null, null, null)
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
          writeDb.update("payments", values, "id = ?", arrayOf(id))
        }
      } catch (e: Exception) {
        android.util.Log.e("PaymentRepository", "Error syncing upserts", e)
        throw e
      }
    }

    // 2. Sync Deletes
    val delCursor = db.query("payments", arrayOf("id"), "is_deleted = 1 AND sync_pending = 1", null, null, null, null)
    val deleteIds = mutableListOf<String>()
    delCursor.use {
      while (it.moveToNext()) {
        deleteIds.add(it.getString(it.getColumnIndexOrThrow("id")))
      }
    }

    if (deleteIds.isNotEmpty()) {
      for (id in deleteIds) {
        try {
          client.from("payments").delete { filter { eq("id", id) } }
          dbHelper.writableDatabase.delete("payments", "id = ?", arrayOf(id))
        } catch (e: Exception) {
          android.util.Log.e("PaymentRepository", "Error syncing deletes", e)
          throw e
        }
      }
    }
  }

  private suspend fun currentOrganizationId(): String {
    organizationId?.let { return it }
    
    // First try from local DB
    val db = dbHelper.readableDatabase
    db.query("members", arrayOf("organization_id"), null, null, null, null, null, "1").use { cursor ->
        if (cursor.moveToFirst()) {
            val orgId = cursor.getString(0)
            if (!orgId.isNullOrBlank()) {
                organizationId = orgId
                return orgId
            }
        }
    }

    // Then try Supabase
    val membership = client.from("organization_members")
      .select()
      .decodeList<OrganizationMembership>()
      .firstOrNull()
      ?: error("Tu usuario no está asociado a una academia o gimnasio.")
    return membership.organizationId.also { organizationId = it }
  }

  private suspend fun ensureValidityAvailableLocal(
    payment: PaymentDraft,
    excludedPaymentId: String? = null,
  ) {
    val currentOrgId = currentOrganizationId()
    val db = dbHelper.readableDatabase
    var selection = "organization_id = ? AND member_id = ? AND valid_from <= ? AND valid_until >= ? AND is_deleted = 0"
    val args = mutableListOf(currentOrgId, payment.memberId, payment.validUntil, payment.validFrom)
    
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
}
