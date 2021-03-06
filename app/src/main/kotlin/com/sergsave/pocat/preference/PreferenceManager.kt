package com.sergsave.pocat.preference

import android.content.Context
import androidx.preference.PreferenceManager
import com.sergsave.pocat.R

class PreferenceManager(context: Context) {
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)

    init {
        // With "readAgain = false" it's safe to create this class any times
        PreferenceManager.setDefaultValues(context, R.xml.settings_preferences, false)
    }

    private val vibrationKey = context.getString(R.string.vibration_preference_key)
    val isVibrationEnabled: Boolean
        get() = preferences.getBoolean(vibrationKey, false)

    private val tutorialKey = context.getString(R.string.purring_tutorial_achieve_preference_key)
    var isPurringTutorialAchieved: Boolean
        get() = preferences.getBoolean(tutorialKey, false)
        set(value) = with(preferences.edit()) {
            putBoolean(tutorialKey, value)
            apply()
        }

    private val tabKey = context.getString(R.string.last_tab_tag_preference_key)
    var lastTabTag: String?
        get() = preferences.getString(tabKey, null)
        set(value) = with(preferences.edit()) {
            putString(tabKey, value)
            apply()
        }
}