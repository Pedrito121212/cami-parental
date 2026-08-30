package com.parental.control.data.models

import com.google.gson.annotations.SerializedName

data class HeartbeatRequest(
    @SerializedName("battery_level") val batteryLevel: Int,
    @SerializedName("is_charging") val isCharging: Int,
    @SerializedName("is_screen_on") val isScreenOn: Int,
    @SerializedName("current_app") val currentApp: String,
    @SerializedName("is_device_owner") val isDeviceOwner: Int
)

data class HeartbeatResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("is_locked") val isLocked: Boolean,
    @SerializedName("lock_message") val lockMessage: String?,
    @SerializedName("safesearch_enabled") val safeSearchEnabled: Boolean,
    @SerializedName("vpn_filter_enabled") val vpnFilterEnabled: Boolean,
    @SerializedName("bonus_minutes_remaining") val bonusMinutesRemaining: Int,
    @SerializedName("commands") val commands: List<CommandItem>?
)

data class CommandItem(
    @SerializedName("id") val id: Int,
    @SerializedName("command_type") val commandType: String,
    @SerializedName("payload") val payload: String?
)

data class AppItem(
    @SerializedName("package_name") val packageName: String,
    @SerializedName("app_name") val appName: String,
    @SerializedName("is_system_app") val isSystemApp: Int,
    @SerializedName("category") val category: String,
    @SerializedName("is_blocked") var isBlocked: Int = 0,
    @SerializedName("daily_limit_minutes") var dailyLimitMinutes: Int = 0,
    @SerializedName("used_today_minutes") var usedTodayMinutes: Int = 0
)

data class ScheduleItem(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("days_of_week") val daysOfWeek: String, // "0,1,2,3,4,5,6"
    @SerializedName("start_time") val startTime: String,   // "22:00"
    @SerializedName("end_time") val endTime: String,       // "07:00"
    @SerializedName("rule_type") val ruleType: String,
    @SerializedName("is_active") val isActive: Int
)

data class WebFilterConfig(
    @SerializedName("safesearch_enabled") val safeSearchEnabled: Boolean,
    @SerializedName("vpn_filter_enabled") val vpnFilterEnabled: Boolean,
    @SerializedName("blocked_categories") val blockedCategories: List<CategoryFilterItem>?,
    @SerializedName("domains") val domains: List<DomainFilterItem>?
)

data class CategoryFilterItem(
    @SerializedName("category_key") val categoryKey: String,
    @SerializedName("is_blocked") val isBlocked: Int
)

data class DomainFilterItem(
    @SerializedName("domain") val domain: String,
    @SerializedName("filter_type") val filterType: String // "blacklist", "whitelist"
)

data class QrPayload(
    @SerializedName("server_url") val serverUrl: String,
    @SerializedName("pairing_token") val pairingToken: String,
    @SerializedName("device_id") val deviceId: Int,
    @SerializedName("child_name") val childName: String
)
