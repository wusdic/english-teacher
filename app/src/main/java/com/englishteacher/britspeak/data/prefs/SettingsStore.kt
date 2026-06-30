package com.englishteacher.britspeak.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.englishteacher.core.domain.model.FeedbackLanguage
import com.englishteacher.core.domain.model.ProficiencyLevel
import com.englishteacher.core.domain.port.LearnerPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persists user-tunable preferences (correction language, level) via Jetpack DataStore. */
@Singleton
class SettingsStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val feedbackKey = stringPreferencesKey("feedback_language")
        private val levelKey = stringPreferencesKey("proficiency")

        val preferences: Flow<LearnerPreferences> =
            context.dataStore.data.map { prefs ->
                LearnerPreferences(
                    feedbackLanguage =
                        prefs[feedbackKey]?.let { runCatching { FeedbackLanguage.valueOf(it) }.getOrNull() }
                            ?: FeedbackLanguage.CHINESE,
                    proficiency =
                        prefs[levelKey]?.let { runCatching { ProficiencyLevel.valueOf(it) }.getOrNull() }
                            ?: ProficiencyLevel.INTERMEDIATE,
                )
            }

        suspend fun setFeedbackLanguage(language: FeedbackLanguage) {
            context.dataStore.edit { it[feedbackKey] = language.name }
        }

        suspend fun setProficiency(level: ProficiencyLevel) {
            context.dataStore.edit { it[levelKey] = level.name }
        }
    }
