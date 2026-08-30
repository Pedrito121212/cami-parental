package com.parental.control.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.parental.control.data.models.AppItem
import com.parental.control.data.models.ScheduleItem
import com.parental.control.data.models.WebFilterConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("cami_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_PAIRING_TOKEN = "pairing_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CHILD_NAME = "child_name"
        private const val KEY_IS_LOCKED = "is_locked"
        private const val KEY_LOCK_MESSAGE = "lock_message"
        private const val KEY_BONUS_MINUTES = "bonus_minutes"
        private const val KEY_CACHED_APPS = "cached_apps"
        private const val KEY_CACHED_SCHEDULES = "cached_schedules"
        private const val KEY_CACHED_WEBFILTER = "cached_webfilter"
        private const val KEY_USAGE_DATE = "usage_date"
        private const val KEY_USAGE_PREFIX = "usage_pkg_"
    }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "https://cami.diazsistemas.com/api") ?: "https://cami.diazsistemas.com/api"
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value).apply()

    var pairingToken: String
        get() = prefs.getString(KEY_PAIRING_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PAIRING_TOKEN, value).apply()

    var deviceId: Int
        get() = prefs.getInt(KEY_DEVICE_ID, 0)
        set(value) = prefs.edit().putInt(KEY_DEVICE_ID, value).apply()

    var childName: String
        get() = prefs.getString(KEY_CHILD_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CHILD_NAME, value).apply()

    var isLocked: Boolean
        get() = prefs.getBoolean(KEY_IS_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOCKED, value).apply()

    var lockMessage: String
        get() = prefs.getString(KEY_LOCK_MESSAGE, "Dispositivo bloqueado por tus padres") ?: ""
        set(value) = prefs.edit().putString(KEY_LOCK_MESSAGE, value).apply()

    var bonusMinutes: Int
        get() = prefs.getInt(KEY_BONUS_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_BONUS_MINUTES, value).apply()

    val isPaired: Boolean
        get() = pairingToken.isNotEmpty() && serverUrl.isNotEmpty()

    fun saveApps(apps: List<AppItem>) {
        val json = gson.toJson(apps)
        prefs.edit().putString(KEY_CACHED_APPS, json).apply()
    }

    fun getApps(): List<AppItem> {
        val json = prefs.getString(KEY_CACHED_APPS, null) ?: return emptyList()
        val type = object : TypeToken<List<AppItem>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSchedules(schedules: List<ScheduleItem>) {
        val json = gson.toJson(schedules)
        prefs.edit().putString(KEY_CACHED_SCHEDULES, json).apply()
    }

    fun getSchedules(): List<ScheduleItem> {
        val json = prefs.getString(KEY_CACHED_SCHEDULES, null) ?: return emptyList()
        val type = object : TypeToken<List<ScheduleItem>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveWebFilter(filter: WebFilterConfig) {
        val json = gson.toJson(filter)
        prefs.edit().putString(KEY_CACHED_WEBFILTER, json).apply()
    }

    fun getWebFilter(): WebFilterConfig? {
        val json = prefs.getString(KEY_CACHED_WEBFILTER, null) ?: return null
        return try {
            gson.fromJson(json, WebFilterConfig::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // Registro diario de tiempo local (resetea a las 00:00)
    fun addMinutesToApp(packageName: String, minutes: Int = 1) {
        checkDailyReset()
        val current = getAppUsageMinutesToday(packageName)
        prefs.edit().putInt(KEY_USAGE_PREFIX + packageName, current + minutes).apply()
    }

    fun getAppUsageMinutesToday(packageName: String): Int {
        checkDailyReset()
        return prefs.getInt(KEY_USAGE_PREFIX + packageName, 0)
    }

    private fun checkDailyReset() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDate = prefs.getString(KEY_USAGE_DATE, "")
        if (savedDate != today) {
            // Borrar usos de ayer
            val editor = prefs.edit()
            val allKeys = prefs.all.keys
            for (key in allKeys) {
                if (key.startsWith(KEY_USAGE_PREFIX)) {
                    editor.remove(key)
                }
            }
            editor.putString(KEY_USAGE_DATE, today)
            editor.apply()
        }
    }
}
