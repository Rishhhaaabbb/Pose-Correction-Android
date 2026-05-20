package com.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.authStore by preferencesDataStore(name = "pose_auth")

class SessionTokenStore(private val context: Context) {
    private val dummyUserIdKey = stringPreferencesKey("dummy_user_id")
    private val dummyUserEmailKey = stringPreferencesKey("dummy_user_email")
    private val dummyUserNameKey = stringPreferencesKey("dummy_user_name")

    suspend fun saveDummySession(userId: String, email: String, displayName: String) {
        context.authStore.edit { prefs ->
            prefs[dummyUserIdKey] = userId
            prefs[dummyUserEmailKey] = email
            prefs[dummyUserNameKey] = displayName
        }
    }

    suspend fun readDummySession(): DummySession? {
        val prefs = context.authStore.data.first()
        val userId = prefs[dummyUserIdKey] ?: return null
        val email = prefs[dummyUserEmailKey]
        val displayName = prefs[dummyUserNameKey]
        return if (userId.isNotBlank()) DummySession(userId, email ?: "", displayName ?: "") else null
    }

    suspend fun clearDummySession() {
        context.authStore.edit { prefs ->
            prefs.remove(dummyUserIdKey)
            prefs.remove(dummyUserEmailKey)
            prefs.remove(dummyUserNameKey)
        }
    }

    suspend fun clear() {
        context.authStore.edit { it.clear() }
    }

    data class DummySession(val userId: String, val email: String, val displayName: String)
}
