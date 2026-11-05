package com.example.paymentacademy.data.repository

import android.content.Context
import com.example.paymentacademy.data.model.OrganizationMembership
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Resolves and persists the organization that belongs to the authenticated session. */
internal class OrganizationScope(
  private val client: SupabaseClient,
  context: Context,
) {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val mutex = Mutex()
  private var cachedUserId: String? = null
  private var cachedMembership: OrganizationMembership? = null

  suspend fun activeOrganizationId(): String {
    client.auth.awaitInitialization()
    val userId = client.auth.currentUserOrNull()?.id
      ?: error("Debes iniciar sesión para acceder a una organización.")

    cachedMembership?.takeIf { cachedUserId == userId }?.let { return it.organizationId }
    val storedOrganizationId = preferences.getString(KEY_ORGANIZATION_ID, null)
    val storedUserId = preferences.getString(KEY_USER_ID, null)
    val storedRole = preferences.getString(KEY_ROLE, null)
    if (storedUserId == userId && !storedOrganizationId.isNullOrBlank()) {
      cachedUserId = userId
      if (!storedRole.isNullOrBlank()) {
        cachedMembership = OrganizationMembership(storedOrganizationId, storedRole)
      }
      return storedOrganizationId
    }

    return resolveMembership(userId).organizationId
  }

  suspend fun activeMembership(): OrganizationMembership {
    client.auth.awaitInitialization()
    val userId = client.auth.currentUserOrNull()?.id
      ?: error("Debes iniciar sesión para acceder a una organización.")

    cachedMembership?.takeIf { cachedUserId == userId }?.let { return it }
    val storedOrganizationId = preferences.getString(KEY_ORGANIZATION_ID, null)
    val storedUserId = preferences.getString(KEY_USER_ID, null)
    val storedRole = preferences.getString(KEY_ROLE, null)
    if (
      storedUserId == userId && !storedOrganizationId.isNullOrBlank() && !storedRole.isNullOrBlank()
    ) {
      cachedUserId = userId
      return OrganizationMembership(storedOrganizationId, storedRole).also {
        cachedMembership = it
      }
    }

    return resolveMembership(userId)
  }

  private suspend fun resolveMembership(userId: String): OrganizationMembership =
    mutex.withLock {
      cachedMembership?.takeIf { cachedUserId == userId }?.let { return@withLock it }
      val membership = client.from("organization_members")
        .select {
          filter { eq("user_id", userId) }
        }
        .decodeList<OrganizationMembership>()
        .firstOrNull()
        ?: error("Tu usuario no está asociado a una academia o gimnasio.")

      cachedUserId = userId
      cachedMembership = membership
      preferences.edit()
        .putString(KEY_USER_ID, userId)
        .putString(KEY_ORGANIZATION_ID, membership.organizationId)
        .putString(KEY_ROLE, membership.role)
        .apply()
      membership
    }

  fun clear() {
    cachedUserId = null
    cachedMembership = null
    preferences.edit().clear().apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "organization_scope"
    const val KEY_USER_ID = "user_id"
    const val KEY_ORGANIZATION_ID = "organization_id"
    const val KEY_ROLE = "role"
  }
}
