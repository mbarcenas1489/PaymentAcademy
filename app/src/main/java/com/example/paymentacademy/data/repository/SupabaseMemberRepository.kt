package com.example.paymentacademy.data.repository

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.paymentacademy.data.local.DatabaseHelper
import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.data.model.NewMemberRecord
import com.example.paymentacademy.data.model.OrganizationMembership
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.NewMember
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.UUID
import java.time.LocalDate

@Serializable
private data class UpsertMemberRecord(
  val id: String,
  @SerialName("organization_id") val organizationId: String,
  @SerialName("full_name") val fullName: String,
  @SerialName("document_number") val documentNumber: String? = null,
  val email: String? = null,
  val phone: String? = null,
  val notes: String? = null,
)

class SupabaseMemberRepository(
  private val client: SupabaseClient,
  private val context: Context
) : MemberRepository {
  private val dbHelper = DatabaseHelper(context)
  private var organizationId: String? = null

  override suspend fun getMembers(): List<Member> = withContext(Dispatchers.IO) {
    try {
      syncUp()
    } catch (e: Exception) {}
    
    try {
      syncDown()
    } catch (e: Exception) {}
    
    return@withContext getLocalMembers()
  }

  private fun getLocalMembers(): List<Member> {
    val members = mutableListOf<Member>()
    val cursor = dbHelper.readableDatabase.query("members", null, null, null, null, null, "full_name COLLATE NOCASE ASC")
    with(cursor) {
      while (moveToNext()) {
        members.add(
          Member(
            id = getString(getColumnIndexOrThrow("id")),
            organizationId = getString(getColumnIndexOrThrow("organization_id")),
            fullName = getString(getColumnIndexOrThrow("full_name")),
            documentNumber = getString(getColumnIndexOrThrow("document_number")),
            email = getString(getColumnIndexOrThrow("email")),
            phone = getString(getColumnIndexOrThrow("phone")),
            status = getString(getColumnIndexOrThrow("status")),
            joinedOn = getString(getColumnIndexOrThrow("joined_on")),
            notes = getString(getColumnIndexOrThrow("notes")),
            createdAt = getString(getColumnIndexOrThrow("created_at")),
            updatedAt = getString(getColumnIndexOrThrow("updated_at")),
            isSyncPending = getInt(getColumnIndexOrThrow("sync_pending")) == 1
          )
        )
      }
      close()
    }
    return members
  }

  override suspend fun createMember(member: NewMember) = withContext(Dispatchers.IO) {
    val orgId = currentOrganizationId()
    val newId = UUID.randomUUID().toString()
    val today = LocalDate.now().toString()
    
    // 1. Guardar localmente marcado para sincronizar
    val values = ContentValues().apply {
      put("id", newId)
      put("organization_id", orgId)
      put("full_name", member.fullName)
      put("document_number", member.documentNumber)
      put("email", member.email)
      put("phone", member.phone)
      put("status", "active")
      put("joined_on", today)
      put("notes", member.notes)
      put("created_at", today)
      put("updated_at", today)
      put("sync_pending", 1)
    }
    dbHelper.writableDatabase.insert("members", null, values)

    // 2. Intentar subir inmediatamente
    try {
      syncUp()
    } catch (e: Exception) {
      // Sin internet, no importa, ya está local y se subirá luego
    }
    
    // 3. Programar tarea de sincronización en segundo plano garantizada
    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  suspend fun sync() {
    syncUp()
    syncDown()
  }

  private suspend fun syncDown() {
    val remoteMembers = client.from("members").select().decodeList<Member>()
    val db = dbHelper.writableDatabase
    db.beginTransaction()
    try {
      for (member in remoteMembers) {
        val values = ContentValues().apply {
          put("id", member.id)
          put("organization_id", member.organizationId)
          put("full_name", member.fullName)
          put("document_number", member.documentNumber)
          put("email", member.email)
          put("phone", member.phone)
          put("status", member.status)
          put("joined_on", member.joinedOn)
          put("notes", member.notes)
          put("created_at", member.createdAt)
          put("updated_at", member.updatedAt)
          put("sync_pending", 0)
        }
        db.insertWithOnConflict("members", null, values, SQLiteDatabase.CONFLICT_REPLACE)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  private suspend fun syncUp() {
    val db = dbHelper.readableDatabase
    val cursor = db.query("members", null, "sync_pending = 1", null, null, null, null)
    val pending = mutableListOf<UpsertMemberRecord>()
    val pendingIds = mutableListOf<String>()
    
    with(cursor) {
      while (moveToNext()) {
        pendingIds.add(getString(getColumnIndexOrThrow("id")))
        pending.add(
          UpsertMemberRecord(
            id = getString(getColumnIndexOrThrow("id")),
            organizationId = getString(getColumnIndexOrThrow("organization_id")),
            fullName = getString(getColumnIndexOrThrow("full_name")),
            documentNumber = getString(getColumnIndexOrThrow("document_number")),
            email = getString(getColumnIndexOrThrow("email")),
            phone = getString(getColumnIndexOrThrow("phone")),
            notes = getString(getColumnIndexOrThrow("notes"))
          )
        )
      }
      close()
    }

    if (pending.isNotEmpty()) {
      // Supabase: actualiza o inserta la lista
      try {
          client.from("members").upsert(pending)
          val writeDb = dbHelper.writableDatabase
          for (id in pendingIds) {
              val values = ContentValues().apply { put("sync_pending", 0) }
              writeDb.update("members", values, "id = ?", arrayOf(id))
          }
      } catch (e: Exception) {
          android.util.Log.e("MemberRepository", "Error syncing up members", e)
          throw e
      }
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
