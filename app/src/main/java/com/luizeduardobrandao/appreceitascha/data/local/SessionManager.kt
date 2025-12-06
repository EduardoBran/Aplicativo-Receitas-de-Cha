package com.luizeduardobrandao.appreceitascha.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "app_session",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_WELCOME_SHOWN = "welcome_shown_this_session"
    }

    fun shouldShowWelcome(): Boolean {
        return !prefs.getBoolean(KEY_WELCOME_SHOWN, false)
    }

    fun markWelcomeAsShown() {
        prefs.edit { putBoolean(KEY_WELCOME_SHOWN, true) }
    }

    fun resetWelcomeFlag() {
        prefs.edit { putBoolean(KEY_WELCOME_SHOWN, false) }
    }
}