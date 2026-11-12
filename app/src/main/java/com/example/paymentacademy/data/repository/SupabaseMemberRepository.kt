package com.example.paymentacademy.data.repository

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.paymentacademy.data.local.DatabaseHelper
import com.example.paymentacademy.data.model.Member
import com.example.paymentacademy.data.model.MemberTariffValue
import com.example.paymentacademy.domain.MemberRepository
import com.example.paymentacademy.domain.NewMember
import com.example.paymentacademy.domain.UpdateMemberRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDate
import java.util.UUID

@Serializable
private data class UpsertMemberRecord(
  val id: String,
  @SerialName("organization_id") val organizationId: String,
  @SerialName("full_name") val fullName: String,
  @SerialName("document_number") val documentNumber: String? = null,
  val email: String? = null,
  val phone: String? = null,
  val status: String = "active",
  val notes: String? = null,
  @SerialName("updated_at") val updatedAt: String? = null,
  @SerialName("IsDelete") val isDelete: Boolean = false,
)

@Serializable
private data class MemberConfigurationEntity(
  @SerialName("Id") val id: String? = null,
  @SerialName("MemberId") val memberId: String? = null,
  @SerialName("member_id") val memberIdSnake: String? = null,
  @SerialName("memberid") val memberIdLower: String? = null,
  @SerialName("SettingName") val settingName: String? = null,
  @SerialName("setting_name") val settingNameSnake: String? = null,
  @SerialName("settingname") val settingNameLower: String? = null,
  val value: JsonElement? = null,
  @SerialName("Value") val valueUpper: JsonElement? = null,
)

@Serializable
private data class UpsertMemberConfigurationRecord(
  @SerialName("MemberId") val memberId: String,
  @SerialName("SettingName") val settingName: String = "PlaymentSetting",
  val value: MemberTariffValue,
)

class SupabaseMemberRepository(
  private val client: SupabaseClient,
  private val context: Context,
) : MemberRepository {
  private val dbHelper = DatabaseHelper(context)
  private val organizationScope = OrganizationScope(client, context)

  override suspend fun getMembers(): List<Member> = withContext(Dispatchers.IO) {
    val organizationId = organizationScope.activeOrganizationId()
    // Sincronizar de fondo usando WorkManager para NO bloquear la pantalla
    com.example.paymentacademy.worker.SyncWorker.schedule(context)

    return@withContext getLocalMembers(organizationId)
  }

  private fun getLocalMembers(organizationId: String): List<Member> {
    val members = mutableListOf<Member>()
    val cursor = dbHelper.readableDatabase.query(
      "members",
      null,
      "organization_id = ? AND is_deleted = 0",
      arrayOf(organizationId),
      null,
      null,
      "full_name COLLATE NOCASE ASC",
    )
    with(cursor) {
      while (moveToNext()) {
        val isDeletedCol = getColumnIndex("is_deleted")
        val isDeleted = if (isDeletedCol >= 0 && !isNull(isDeletedCol)) getInt(isDeletedCol) == 1 else false
        if (!isDeleted) {
          members.add(
            Member(
              id = getString(getColumnIndexOrThrow("id")),
              organizationId = getString(getColumnIndexOrThrow("organization_id")),
              fullName = getString(getColumnIndexOrThrow("full_name")),
              documentNumber = getString(getColumnIndexOrThrow("document_number")),
              email = getString(getColumnIndexOrThrow("email")),
              phone = getString(getColumnIndexOrThrow("phone")),
              status = getString(getColumnIndexOrThrow("status")) ?: "active",
              joinedOn = getString(getColumnIndexOrThrow("joined_on")),
              notes = getString(getColumnIndexOrThrow("notes")),
              createdAt = getString(getColumnIndexOrThrow("created_at")),
              updatedAt = getString(getColumnIndexOrThrow("updated_at")),
              isDelete = false,
              isSyncPending = getInt(getColumnIndexOrThrow("sync_pending")) == 1,
            ),
          )
        }
      }
      close()
    }
    return members
  }

  override suspend fun createMember(member: NewMember) = withContext(Dispatchers.IO) {
    val orgId = organizationScope.activeOrganizationId()
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
      put("is_deleted", 0)
    }
    dbHelper.writableDatabase.insert("members", null, values)

    if (member.customTariff != null) {
      upsertLocalMemberTariff(newId, member.customTariff, syncPending = 1)
    }

    // 2. Intentar subir inmediatamente
    runCatching {
      syncUp(orgId)
      syncUpMemberTariffs()
    }

    // 3. Programar tarea de sincronización en segundo plano garantizada
    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  override suspend fun updateMember(
    memberId: String,
    data: UpdateMemberRequest,
  ) = withContext(Dispatchers.IO) {
    val orgId = organizationScope.activeOrganizationId()
    val today = LocalDate.now().toString()

    val values = ContentValues().apply {
      put("full_name", data.fullName.trim())
      put("document_number", data.documentNumber?.trim()?.ifBlank { null })
      put("phone", data.phone?.trim()?.ifBlank { null })
      put("email", data.email?.trim()?.ifBlank { null })
      put("status", data.status)
      put("notes", data.notes?.trim()?.ifBlank { null })
      put("updated_at", today)
      put("sync_pending", 1)
    }
    val rows = dbHelper.writableDatabase.update(
      "members",
      values,
      "id = ? AND organization_id = ?",
      arrayOf(memberId, orgId),
    )
    if (rows == 0) error("No fue posible encontrar el miembro localmente.")

    runCatching { syncUp(orgId) }
    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  override suspend fun deleteMember(memberId: String) = withContext(Dispatchers.IO) {
    val orgId = organizationScope.activeOrganizationId()
    val today = LocalDate.now().toString()
    val db = dbHelper.writableDatabase

    db.beginTransaction()
    try {
      val memberValues = ContentValues().apply {
        put("is_deleted", 1)
        put("updated_at", today)
        put("sync_pending", 1)
      }
      db.update(
        "members",
        memberValues,
        "id = ? AND organization_id = ?",
        arrayOf(memberId, orgId),
      )

      // En cascada lógica: marcar también sus pagos asociados como eliminados
      val paymentValues = ContentValues().apply {
        put("is_deleted", 1)
        put("sync_pending", 1)
      }
      db.update(
        "payments",
        paymentValues,
        "member_id = ? AND organization_id = ?",
        arrayOf(memberId, orgId),
      )
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }

    runCatching {
      syncUp(orgId)
      SupabasePaymentRepository(client, context).sync()
    }
    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  override suspend fun getMemberTariffs(): Map<String, MemberTariffValue> =
    withContext(Dispatchers.IO) {
      runCatching {
        syncUpMemberTariffs()
        syncDownMemberTariffs()
      }
      readLocalMemberTariffs(dbHelper)
    }

  override suspend fun saveMemberTariff(
    memberId: String,
    value: MemberTariffValue,
  ) = withContext(Dispatchers.IO) {
    upsertLocalMemberTariff(memberId, value, syncPending = 1)
    runCatching { syncUpMemberTariffs() }
    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  override suspend fun deleteMemberTariff(memberId: String) = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
      put("is_deleted", 1)
      put("sync_pending", 1)
    }
    val updated = dbHelper.writableDatabase.update(
      "member_configuration",
      values,
      "member_id = ?",
      arrayOf(memberId),
    )
    if (updated == 0) {
      // Ya no existe localmente, intentar eliminar directo en remoto por seguridad
      runCatching { deleteRemoteMemberTariff(memberId) }
      return@withContext
    }
    runCatching { syncUpMemberTariffs() }
    com.example.paymentacademy.worker.SyncWorker.schedule(context)
  }

  private fun upsertLocalMemberTariff(
    memberId: String,
    value: MemberTariffValue,
    syncPending: Int,
  ) {
    val values = ContentValues().apply {
      put("member_id", memberId)
      put("setting_name", "PlaymentSetting")
      if (value.AmountMonthCordoba != null) {
        put("amount_month_cordoba", value.AmountMonthCordoba)
      } else {
        putNull("amount_month_cordoba")
      }
      if (value.AmountMonthDollar != null) {
        put("amount_month_dollar", value.AmountMonthDollar)
      } else {
        putNull("amount_month_dollar")
      }
      put("sync_pending", syncPending)
      put("is_deleted", 0)
    }
    dbHelper.writableDatabase.insertWithOnConflict(
      "member_configuration",
      null,
      values,
      SQLiteDatabase.CONFLICT_REPLACE,
    )
  }

  suspend fun sync() {
    val organizationId = organizationScope.activeOrganizationId()
    syncUp(organizationId)
    syncDown(organizationId)
    runCatching {
      syncUpMemberTariffs()
      syncDownMemberTariffs()
    }
  }

  private suspend fun syncDown(organizationId: String) {
    val remoteMembers = client.from("members")
      .select { filter { eq("organization_id", organizationId) } }
      .decodeList<Member>()
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
          put("is_deleted", if (member.isDelete) 1 else 0)
        }
        db.insertWithOnConflict("members", null, values, SQLiteDatabase.CONFLICT_REPLACE)
      }
      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  private suspend fun syncUp(organizationId: String) {
    val db = dbHelper.readableDatabase
    val cursor = db.query(
      "members",
      null,
      "organization_id = ? AND sync_pending = 1",
      arrayOf(organizationId),
      null,
      null,
      null,
    )
    val pending = mutableListOf<UpsertMemberRecord>()
    val pendingIds = mutableListOf<String>()

    with(cursor) {
      while (moveToNext()) {
        val isDeletedCol = getColumnIndex("is_deleted")
        val isDeleted = if (isDeletedCol >= 0 && !isNull(isDeletedCol)) getInt(isDeletedCol) == 1 else false
        pendingIds.add(getString(getColumnIndexOrThrow("id")))
        pending.add(
          UpsertMemberRecord(
            id = getString(getColumnIndexOrThrow("id")),
            organizationId = getString(getColumnIndexOrThrow("organization_id")),
            fullName = getString(getColumnIndexOrThrow("full_name")),
            documentNumber = getString(getColumnIndexOrThrow("document_number")),
            email = getString(getColumnIndexOrThrow("email")),
            phone = getString(getColumnIndexOrThrow("phone")),
            status = getString(getColumnIndexOrThrow("status")) ?: "active",
            notes = getString(getColumnIndexOrThrow("notes")),
            updatedAt = getString(getColumnIndexOrThrow("updated_at")),
            isDelete = isDeleted,
          ),
        )
      }
      close()
    }

    if (pending.isNotEmpty()) {
      try {
        client.from("members").upsert(pending)
        val writeDb = dbHelper.writableDatabase
        for (id in pendingIds) {
          val values = ContentValues().apply { put("sync_pending", 0) }
          writeDb.update(
            "members",
            values,
            "id = ? AND organization_id = ?",
            arrayOf(id, organizationId),
          )
        }
      } catch (e: Exception) {
        android.util.Log.e("MemberRepository", "Error syncing up members", e)
        throw e
      }
    }
  }

  private suspend fun syncUpMemberTariffs() {
    val db = dbHelper.readableDatabase
    // 1. Subir tarifas nuevas o editadas
    val upsertList = mutableListOf<UpsertMemberConfigurationRecord>()
    db.query(
      "member_configuration",
      null,
      "sync_pending = 1 AND is_deleted = 0",
      null,
      null,
      null,
      null,
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val memberId = cursor.getString(cursor.getColumnIndexOrThrow("member_id"))
        val settingName = cursor.getString(cursor.getColumnIndexOrThrow("setting_name")) ?: "PlaymentSetting"
        val cordobaCol = cursor.getColumnIndex("amount_month_cordoba")
        val dollarCol = cursor.getColumnIndex("amount_month_dollar")
        val cordoba = if (cordobaCol >= 0 && !cursor.isNull(cordobaCol)) cursor.getDouble(cordobaCol) else null
        val dollar = if (dollarCol >= 0 && !cursor.isNull(dollarCol)) cursor.getDouble(dollarCol) else null
        upsertList.add(
          UpsertMemberConfigurationRecord(
            memberId = memberId,
            settingName = settingName,
            value = MemberTariffValue(
              AmountMonthCordoba = cordoba,
              AmountMonthDollar = dollar,
            ),
          ),
        )
      }
    }

    for (record in upsertList) {
      upsertRemoteMemberTariff(record)
      val values = ContentValues().apply { put("sync_pending", 0) }
      dbHelper.writableDatabase.update(
        "member_configuration",
        values,
        "member_id = ?",
        arrayOf(record.memberId),
      )
    }

    // 2. Eliminar tarifas restablecidas a tarifa global
    val deleteMemberIds = mutableListOf<String>()
    db.query(
      "member_configuration",
      arrayOf("member_id"),
      "sync_pending = 1 AND is_deleted = 1",
      null,
      null,
      null,
      null,
    ).use { cursor ->
      while (cursor.moveToNext()) {
        deleteMemberIds.add(cursor.getString(0))
      }
    }

    for (memberId in deleteMemberIds) {
      deleteRemoteMemberTariff(memberId)
      dbHelper.writableDatabase.delete(
        "member_configuration",
        "member_id = ?",
        arrayOf(memberId),
      )
    }
  }

  private suspend fun upsertRemoteMemberTariff(record: UpsertMemberConfigurationRecord) {
    runCatching {
      client.from("MemberConfiguration").upsert(record) {
        onConflict = "MemberId,SettingName"
      }
    }.recoverCatching {
      deleteRemoteMemberTariff(record.memberId)
      client.from("MemberConfiguration").insert(record)
    }.getOrThrow()
  }

  private suspend fun deleteRemoteMemberTariff(memberId: String) {
    runCatching {
      client.from("MemberConfiguration").delete {
        filter {
          eq("MemberId", memberId)
          eq("SettingName", "PlaymentSetting")
        }
      }
    }.recoverCatching {
      client.from("memberconfiguration").delete {
        filter {
          eq("member_id", memberId)
          eq("setting_name", "PlaymentSetting")
        }
      }
    }.getOrThrow()
  }

  private suspend fun syncDownMemberTariffs() {
    val rows = runCatching {
      client.from("MemberConfiguration").select {
        filter { eq("SettingName", "PlaymentSetting") }
      }.decodeList<MemberConfigurationEntity>()
    }.getOrElse {
      runCatching {
        client.from("MemberConfiguration").select().decodeList<MemberConfigurationEntity>()
      }.getOrElse {
        runCatching {
          client.from("memberconfiguration").select().decodeList<MemberConfigurationEntity>()
        }.getOrDefault(emptyList())
      }
    }

    val db = dbHelper.writableDatabase
    val pendingLocalIds = mutableSetOf<String>()
    db.query(
      "member_configuration",
      arrayOf("member_id"),
      "sync_pending = 1",
      null,
      null,
      null,
      null,
    ).use { cursor ->
      while (cursor.moveToNext()) {
        pendingLocalIds.add(cursor.getString(0))
      }
    }

    val remoteMemberIds = mutableSetOf<String>()
    db.beginTransaction()
    try {
      for (row in rows) {
        val setting = row.settingName ?: row.settingNameSnake ?: row.settingNameLower ?: "PlaymentSetting"
        if (!setting.equals("PlaymentSetting", ignoreCase = true) &&
          !setting.equals("PaymentSetting", ignoreCase = true)
        ) {
          continue
        }
        val memberId = row.memberId ?: row.memberIdSnake ?: row.memberIdLower ?: continue
        if (memberId in pendingLocalIds) continue
        val rawValue = row.value ?: row.valueUpper ?: continue
        val jsonStr = if (rawValue is JsonPrimitive && rawValue.isString) {
          rawValue.content
        } else {
          rawValue.toString()
        }
        val tariff = parseMemberTariffJson(jsonStr) ?: continue
        remoteMemberIds.add(memberId)
        val values = ContentValues().apply {
          put("member_id", memberId)
          put("setting_name", "PlaymentSetting")
          if (tariff.AmountMonthCordoba != null) {
            put("amount_month_cordoba", tariff.AmountMonthCordoba)
          } else {
            putNull("amount_month_cordoba")
          }
          if (tariff.AmountMonthDollar != null) {
            put("amount_month_dollar", tariff.AmountMonthDollar)
          } else {
            putNull("amount_month_dollar")
          }
          put("sync_pending", 0)
          put("is_deleted", 0)
        }
        db.insertWithOnConflict("member_configuration", null, values, SQLiteDatabase.CONFLICT_REPLACE)
      }

      // Limpiar tarifas locales sincronizadas que ya fueron eliminadas en remoto
      val syncedCursor = db.query(
        "member_configuration",
        arrayOf("member_id"),
        "sync_pending = 0",
        null,
        null,
        null,
        null,
      )
      val toRemove = mutableListOf<String>()
      syncedCursor.use { cursor ->
        while (cursor.moveToNext()) {
          val id = cursor.getString(0)
          if (id !in remoteMemberIds) toRemove.add(id)
        }
      }
      for (id in toRemove) {
        db.delete("member_configuration", "member_id = ? AND sync_pending = 0", arrayOf(id))
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
  }

  companion object {
    fun readLocalMemberTariffs(dbHelper: DatabaseHelper): Map<String, MemberTariffValue> {
      val map = mutableMapOf<String, MemberTariffValue>()
      dbHelper.readableDatabase.query(
        "member_configuration",
        null,
        "is_deleted = 0",
        null,
        null,
        null,
        null,
      ).use { cursor ->
        while (cursor.moveToNext()) {
          val memberId = cursor.getString(cursor.getColumnIndexOrThrow("member_id"))
          val cordobaCol = cursor.getColumnIndex("amount_month_cordoba")
          val dollarCol = cursor.getColumnIndex("amount_month_dollar")
          val cordoba = if (cordobaCol >= 0 && !cursor.isNull(cordobaCol)) cursor.getDouble(cordobaCol) else null
          val dollar = if (dollarCol >= 0 && !cursor.isNull(dollarCol)) cursor.getDouble(dollarCol) else null
          if (cordoba != null || dollar != null) {
            map[memberId] = MemberTariffValue(
              AmountMonthCordoba = cordoba,
              AmountMonthDollar = dollar,
            )
          }
        }
      }
      return map
    }

    private fun parseMemberTariffJson(rawJson: String): MemberTariffValue? = runCatching {
      val normalized = rawJson.trim().let {
        if (it.startsWith("\"") && it.endsWith("\"")) {
          JSONTokener(it).nextValue().toString()
        } else {
          it
        }
      }
      val obj = JSONObject(normalized)
      val cordoba = when {
        obj.has("AmountMonthCordoba") && !obj.isNull("AmountMonthCordoba") ->
          obj.optDouble("AmountMonthCordoba")
        obj.has("amountMonthCordoba") && !obj.isNull("amountMonthCordoba") ->
          obj.optDouble("amountMonthCordoba")
        else -> Double.NaN
      }.takeIf { !it.isNaN() && it > 0.0 }
      val dollar = when {
        obj.has("AmountMonthDollar") && !obj.isNull("AmountMonthDollar") ->
          obj.optDouble("AmountMonthDollar")
        obj.has("amountMonthDollar") && !obj.isNull("amountMonthDollar") ->
          obj.optDouble("amountMonthDollar")
        else -> Double.NaN
      }.takeIf { !it.isNaN() && it > 0.0 }
      if (cordoba == null && dollar == null) null
      else MemberTariffValue(AmountMonthCordoba = cordoba, AmountMonthDollar = dollar)
    }.getOrNull()
  }
}
