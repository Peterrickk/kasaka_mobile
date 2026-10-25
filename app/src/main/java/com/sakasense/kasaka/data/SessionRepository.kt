package com.sakasense.kasaka.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserProfile(
    val name: String,
    val email: String,
    val contact: String,
    val tier: String,
    val isDemo: Boolean,
)

data class Session(
    val onboarded: Boolean,
    val user: UserProfile?,
    val theme: ThemeMode,
)

private val Context.dataStore by preferencesDataStore("session")

/**
 * Accounts and preferences, stored on the phone.
 *
 * sakasense.com has no public account API yet, so accounts are local to this phone (the password is
 * stored only as a salted SHA-256 hash). When the backend is ready, replace signIn/signUp with
 * network calls — the rest of the app only depends on [session].
 */
class SessionRepository(private val context: Context) {

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val signedInEmail = stringPreferencesKey("signed_in_email")
        val theme = stringPreferencesKey("theme")
        val accName = stringPreferencesKey("acc_name")
        val accEmail = stringPreferencesKey("acc_email")
        val accContact = stringPreferencesKey("acc_contact")
        val accSalt = stringPreferencesKey("acc_salt")
        val accHash = stringPreferencesKey("acc_hash")
    }

    val session: Flow<Session> = context.dataStore.data.map { p ->
        Session(
            onboarded = p[Keys.onboarded] ?: false,
            user = p[Keys.signedInEmail]?.let { profileFor(it, p) },
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun completeOnboarding() {
        context.dataStore.edit { it[Keys.onboarded] = true }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.theme] = mode.name }
    }

    suspend fun signIn(email: String, password: String): Result<UserProfile> {
        val e = email.trim().lowercase()
        val p = context.dataStore.data.first()
        val ok = when {
            e == DEMO_EMAIL -> password == DEMO_PASSWORD
            e == p[Keys.accEmail] -> hash(password, p[Keys.accSalt].orEmpty()) == p[Keys.accHash]
            else -> false
        }
        if (!ok) return Result.failure(IllegalArgumentException("Invalid email or password. Try the demo account."))
        context.dataStore.edit { it[Keys.signedInEmail] = e }
        return Result.success(profileFor(e, context.dataStore.data.first())!!)
    }

    suspend fun signUp(name: String, email: String, contact: String, password: String): Result<UserProfile> {
        val e = email.trim().lowercase()
        if (e == DEMO_EMAIL) return Result.failure(IllegalArgumentException("That email is reserved for the demo account."))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        context.dataStore.edit {
            it[Keys.accName] = name.trim()
            it[Keys.accEmail] = e
            it[Keys.accContact] = contact.trim()
            it[Keys.accSalt] = salt
            it[Keys.accHash] = hash(password, salt)
            it[Keys.signedInEmail] = e
        }
        return Result.success(profileFor(e, context.dataStore.data.first())!!)
    }

    suspend fun signOut() {
        context.dataStore.edit { it.remove(Keys.signedInEmail) }
    }

    private fun profileFor(email: String, p: Preferences): UserProfile? = when (email) {
        DEMO_EMAIL -> UserProfile("Maria Santos", DEMO_EMAIL, "+63 917 123 4567", "Farmer Pro", isDemo = true)
        p[Keys.accEmail] -> UserProfile(p[Keys.accName].orEmpty(), email, p[Keys.accContact].orEmpty(), "Free", isDemo = false)
        else -> null
    }

    private fun hash(password: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + password).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        // Same demo account as the web dashboard.
        const val DEMO_EMAIL = "maria@sakasense.demo"
        const val DEMO_PASSWORD = "sakasense123"
    }
}
