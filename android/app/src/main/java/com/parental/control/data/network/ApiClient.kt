package com.parental.control.data.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.parental.control.data.local.PreferencesManager
import com.parental.control.data.models.AppItem
import com.parental.control.data.models.HeartbeatRequest
import com.parental.control.data.models.HeartbeatResponse
import com.parental.control.data.models.ScheduleItem
import com.parental.control.data.models.WebFilterConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiClient(private val prefs: PreferencesManager) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // 1. Registro inicial con QR
    suspend fun registerDevice(serverUrl: String, token: String, model: String, androidVersion: String, isDpc: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("pairing_token", token)
                addProperty("model", model)
                addProperty("android_version", androidVersion)
                addProperty("is_device_owner", isDpc)
            }

            val url = "${serverUrl.trimEnd('/')}/devices.php?action=register_device"
            val request = Request.Builder()
                .url(url)
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val resObj = gson.fromJson(body, JsonObject::class.java)
                if (resObj.get("success")?.asBoolean == true) {
                    prefs.serverUrl = serverUrl
                    prefs.pairingToken = token
                    prefs.deviceId = resObj.get("device_id")?.asInt ?: 0
                    prefs.childName = resObj.get("child_name")?.asString ?: "Hijo"
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // 2. Enviar Heartbeat & Recibir Comandos
    suspend fun sendHeartbeat(req: HeartbeatRequest): HeartbeatResponse? = withContext(Dispatchers.IO) {
        if (!prefs.isPaired) return@withContext null
        try {
            val url = "${prefs.serverUrl.trimEnd('/')}/devices.php?action=heartbeat"
            val request = Request.Builder()
                .url(url)
                .header("X-Device-Token", prefs.pairingToken)
                .post(gson.toJson(req).toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                return@withContext gson.fromJson(body, HeartbeatResponse::class.java)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // 3. Sincronizar Apps Instaladas
    suspend fun syncInstalledApps(apps: List<AppItem>): Boolean = withContext(Dispatchers.IO) {
        if (!prefs.isPaired) return@withContext false
        try {
            val payload = JsonObject().apply {
                add("apps", gson.toJsonTree(apps))
            }
            val url = "${prefs.serverUrl.trimEnd('/')}/apps.php?action=sync_installed"
            val request = Request.Builder()
                .url(url)
                .header("X-Device-Token", prefs.pairingToken)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    // 4. Obtener Horarios
    suspend fun fetchSchedules(): List<ScheduleItem> = withContext(Dispatchers.IO) {
        if (!prefs.isPaired) return@withContext emptyList()
        try {
            val url = "${prefs.serverUrl.trimEnd('/')}/rules.php?action=get_schedules"
            val request = Request.Builder()
                .url(url)
                .header("X-Device-Token", prefs.pairingToken)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = gson.fromJson(body, JsonObject::class.java)
                val type = object : TypeToken<List<ScheduleItem>>() {}.type
                return@withContext gson.fromJson(json.get("schedules"), type)
            }
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 5. Obtener Filtro Web
    suspend fun fetchWebFilters(): WebFilterConfig? = withContext(Dispatchers.IO) {
        if (!prefs.isPaired) return@withContext null
        try {
            val url = "${prefs.serverUrl.trimEnd('/')}/webfilter.php?action=get_filters"
            val request = Request.Builder()
                .url(url)
                .header("X-Device-Token", prefs.pairingToken)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                return@withContext gson.fromJson(body, WebFilterConfig::class.java)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // 6. Confirmar Ejecución de Comando
    suspend fun confirmCommand(commandId: Int) = withContext(Dispatchers.IO) {
        if (!prefs.isPaired) return@withContext
        try {
            val json = JsonObject().apply { addProperty("command_id", commandId) }
            val url = "${prefs.serverUrl.trimEnd('/')}/commands.php?action=confirm_executed"
            val request = Request.Builder()
                .url(url)
                .header("X-Device-Token", prefs.pairingToken)
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            // Ignorar error de red puntual
        }
    }

    // 7. Reportar Alerta de Seguridad
    suspend fun reportSecurityEvent(type: String, description: String) = withContext(Dispatchers.IO) {
        if (!prefs.isPaired) return@withContext
        try {
            val json = JsonObject().apply {
                addProperty("event_type", type)
                addProperty("description", description)
            }
            val url = "${prefs.serverUrl.trimEnd('/')}/telemetry.php?action=log_security_event"
            val request = Request.Builder()
                .url(url)
                .header("X-Device-Token", prefs.pairingToken)
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            // Ignorar
        }
    }
}
