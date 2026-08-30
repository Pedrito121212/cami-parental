package com.parental.control.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.parental.control.data.local.PreferencesManager
import com.parental.control.data.network.ApiClient
import com.parental.control.overlay.BlockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ParentalAccessibilityService : AccessibilityService() {

    private lateinit var prefs: PreferencesManager
    private lateinit var apiClient: ApiClient
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = PreferencesManager(this)
        apiClient = ApiClient(prefs)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName == null) return
        val currentPackage = event.packageName.toString()

        // 1. Evitar acceso no autorizado a los Ajustes de Desinstalación si no es Device Owner
        if (currentPackage == "com.android.settings" || currentPackage == "com.google.android.packageinstaller") {
            val textList = event.text.map { it.toString().lowercase() }
            val containsTamperKeywords = textList.any { 
                it.contains("desinstalar") || it.contains("cami") || it.contains("accesibilidad") || it.contains("forzar detención")
            }

            if (containsTamperKeywords) {
                // Volver a Home
                performGlobalAction(GLOBAL_ACTION_HOME)
                scope.launch {
                    apiClient.reportSecurityEvent("tamper_attempt", "Intento bloqueado de manipular ajustes o desinstalar la aplicación.")
                }
                return
            }
        }

        // 2. Verificar si la app en primer plano está bloqueada
        val apps = prefs.getApps()
        val appRule = apps.find { it.packageName == currentPackage }
        if (appRule != null && appRule.isBlocked == 1) {
            val intent = Intent(this, BlockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("EXTRA_REASON", "La aplicación ${appRule.appName} está bloqueada.")
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {}
}
