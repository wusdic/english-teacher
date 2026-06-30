package com.englishteacher.britspeak.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's Anthropic API key in [EncryptedSharedPreferences] (AES-256, key in the
 * Android Keystore). For a multi-tenant production deployment, point the engine at a backend
 * proxy instead and drop the BYO-key flow (see docs/PLAN.md §3).
 */
@Singleton
class ApiKeyStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs: SharedPreferences by lazy {
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            EncryptedSharedPreferences.create(
                context,
                "secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        var apiKey: String?
            get() = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }
            set(value) {
                prefs.edit().apply {
                    if (value.isNullOrBlank()) remove(KEY) else putString(KEY, value.trim())
                }.apply()
            }

        val hasKey: Boolean get() = apiKey != null

        companion object {
            private const val KEY = "anthropic_api_key"
        }
    }
