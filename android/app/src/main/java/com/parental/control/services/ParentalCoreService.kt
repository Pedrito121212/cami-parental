package com.parental.control.services

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.parental.control.ParentalApp
import com.parental.control.R
import com.parental.control.data.local.PreferencesManager
import com.parental.control.data.models.AppItem
import com.parental.control.data.models.CommandItem
import com.parental.control.data.models.HeartbeatRequest
import com.parental.control.data.models.ScheduleItem
import com.parental.control.data.network.ApiClient
import com.parental.control.dpc.DeviceAdminReceiver
import com.parental.control.overlay.BlockOverlayActivity
import com.parental.control.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ParentalCoreService : Service() {

    private lateinit var prefs: PreferencesManager
    private lateinit var apiClient: ApiClient
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    
    private var isScreenOn = true
    private var lastActivePackage: String = ""
    private var activeAppSecondsCounter = 0

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> isScreenOn = true
                Intent.ACTION_SCREEN_OFF -> isScreenOn = false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
        apiClient = ApiClient(prefs)

        registerReceiver(screenStateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })

        startForeground(1001, createForegroundNotification())
        startHeartbeatLoop()
        startUsageTrackingLoop()
    }

    private fun createForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ParentalApp.CHANNEL_ID)
            .setContentTitle("Cami Control Parental")
            .setContentText("Supervisión y protección activa")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // 1. Loop de Heartbeat con el Backend PHP (cada 5 segs)
    private fun startHeartbeatLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    if (prefs.isPaired) {
                        val battery = getBatteryLevel()
                        val isCharging = getIsCharging()
                        val isDpc = if (DeviceAdminReceiver.isDeviceOwner(this@ParentalCoreService)) 1 else 0

                        val req = HeartbeatRequest(
                            batteryLevel = battery,
                            isCharging = isCharging,
                            isScreenOn = if (isScreenOn) 1 else 0,
                            currentApp = lastActivePackage,
                            isDeviceOwner = isDpc
                        )

                        val res = apiClient.sendHeartbeat(req)
                        res?.let { handleHeartbeatResponse(it) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(5000)
            }
        }
    }

    // 2. Loop de Monitoreo de Uso y Reglas Locales (cada 1 seg)
    private fun startUsageTrackingLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    if (isScreenOn) {
                        val currentPkg = getForegroundAppPackageName()
                        if (currentPkg.isNotEmpty() && currentPkg != packageName) {
                            lastActivePackage = currentPkg
                            
                            // Sumar tiempo si sigue en la misma app
                            activeAppSecondsCounter++
                            if (activeAppSecondsCounter >= 60) {
                                activeAppSecondsCounter = 0
                                prefs.addMinutesToApp(currentPkg, 1)
                            }

                            // Evaluar bloqueo por horario (bedtime / escuela)
                            if (isTimeInActiveSchedule()) {
                                showBlockOverlay("Horario de Descanso / Estudio activo")
                            } else if (prefs.isLocked) {
                                showBlockOverlay(prefs.lockMessage)
                            } else {
                                // Evaluar reglas de la app específica
                                evaluateAppRule(currentPkg)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000)
            }
        }
    }

    private fun evaluateAppRule(pkg: String) {
        val apps = prefs.getApps()
        val appRule = apps.find { it.packageName == pkg } ?: return

        // 1. Bloqueo total
        if (appRule.isBlocked == 1) {
            showBlockOverlay("La aplicación ${appRule.appName} ha sido bloqueada.")
            return
        }

        // 2. Límite de minutos diarios
        if (appRule.dailyLimitMinutes > 0) {
            val usedToday = prefs.getAppUsageMinutesToday(pkg)
            if (usedToday >= appRule.dailyLimitMinutes) {
                showBlockOverlay("Has alcanzado el límite diario (${appRule.dailyLimitMinutes} min) para ${appRule.appName}.")
            }
        }
    }

    private fun isTimeInActiveSchedule(): Boolean {
        if (prefs.bonusMinutes > 0) return false // Indulto temporal activo

        val schedules = prefs.getSchedules()
        if (schedules.isEmpty()) return false

        val cal = Calendar.getInstance()
        val currentDay = (cal.get(Calendar.DAY_OF_WEEK) - 1).toString() // 0 = Domingo, 1 = Lunes...
        val nowTime = String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

        for (s in schedules) {
            if (s.isActive == 1 && s.daysOfWeek.split(",").contains(currentDay)) {
                if (isTimeBetween(nowTime, s.startTime, s.endTime)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isTimeBetween(current: String, start: String, end: String): Boolean {
        return if (start <= end) {
            current in start..end
        } else {
            // Cruza la medianoche (ej: 22:00 a 07:00)
            current >= start || current <= end
        }
    }

    private fun showBlockOverlay(reason: String) {
        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("EXTRA_REASON", reason)
        }
        startActivity(intent)
    }

    private fun handleHeartbeatResponse(res: com.parental.control.data.models.HeartbeatResponse) {
        prefs.isLocked = res.isLocked
        res.lockMessage?.let { prefs.lockMessage = it }
        prefs.bonusMinutes = res.bonusMinutesRemaining

        // Ejecutar comandos remotos pendientes
        res.commands?.forEach { cmd ->
            executeRemoteCommand(cmd)
        }
    }

    private fun executeRemoteCommand(cmd: CommandItem) {
        serviceScope.launch {
            when (cmd.commandType) {
                "lock_screen" -> {
                    prefs.isLocked = true
                    showBlockOverlay("Bloqueo de emergencia activado por tus padres")
                }
                "unlock_screen" -> {
                    prefs.isLocked = false
                }
                "grant_bonus" -> {
                    prefs.isLocked = false
                }
                "sync_rules" -> {
                    // Re-sincronizar reglas
                    val apps = apiClient.syncInstalledApps(scanInstalledApps())
                }
                "sync_schedules" -> {
                    val schedules = apiClient.fetchSchedules()
                    prefs.saveSchedules(schedules)
                }
                "sync_webfilters" -> {
                    val filter = apiClient.fetchWebFilters()
                    filter?.let { prefs.saveWebFilter(it) }
                }
            }
            apiClient.confirmCommand(cmd.id)
        }
    }

    private fun scanInstalledApps(): List<AppItem> {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val list = mutableListOf<AppItem>()

        for (app in packages) {
            // Filtrar apps del sistema esenciales a menos que sean navegadores o tiendas
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val appName = pm.getApplicationLabel(app).toString()
            val category = detectAppCategory(app.packageName)

            if (!isSystem || app.packageName == "com.android.chrome" || app.packageName == "com.google.android.youtube") {
                list.add(AppItem(
                    packageName = app.packageName,
                    appName = appName,
                    isSystemApp = if (isSystem) 1 else 0,
                    category = category
                ))
            }
        }
        return list
    }

    private fun detectAppCategory(pkg: String): String {
        return when {
            pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("disney") || pkg.contains("twitch") -> "video"
            pkg.contains("tiktok") || pkg.contains("instagram") || pkg.contains("whatsapp") || pkg.contains("facebook") || pkg.contains("snapchat") -> "social"
            pkg.contains("roblox") || pkg.contains("freefire") || pkg.contains("brawlstars") || pkg.contains("minecraft") || pkg.contains("game") -> "games"
            pkg.contains("duolingo") || pkg.contains("classroom") || pkg.contains("canvas") -> "education"
            else -> "other"
        }
    }

    private fun getForegroundAppPackageName(): String {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val events = usm.queryEvents(time - 5000, time)
        val event = UsageEvents.Event()
        var current = ""

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                current = event.packageName
            }
        }
        return current
    }

    private fun getBatteryLevel(): Int {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level != -1 && scale != -1) ((level / scale.toFloat()) * 100).toInt() else 100
    }

    private fun getIsCharging(): Int {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) 1 else 0
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenStateReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
