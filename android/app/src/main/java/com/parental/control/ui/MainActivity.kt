package com.parental.control.ui

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.parental.control.R
import com.parental.control.data.local.PreferencesManager
import com.parental.control.data.network.ApiClient
import com.parental.control.dpc.DeviceAdminReceiver
import com.parental.control.services.ParentalCoreService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var apiClient: ApiClient

    private val qrScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            updateUI()
            startCoreService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PreferencesManager(this)
        apiClient = ApiClient(prefs)

        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupListeners() {
        findViewById<Button>(R.id.btn_scan_qr).setOnClickListener {
            val intent = Intent(this, QrScannerActivity::class.java)
            qrScannerLauncher.launch(intent)
        }

        // Vinculación manual por código escrito
        findViewById<Button>(R.id.btn_pair_manual).setOnClickListener {
            val etToken = findViewById<android.widget.EditText>(R.id.et_manual_token)
            val token = etToken.text.toString().trim()

            if (token.isEmpty()) {
                Toast.makeText(this, "Por favor escribe el código (ej: CAMI-ABC1234)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val btn = findViewById<Button>(R.id.btn_pair_manual)
            btn.isEnabled = false
            btn.text = "Conectando..."

            CoroutineScope(Dispatchers.IO).launch {
                val model = "${Build.MANUFACTURER} ${Build.MODEL}"
                val androidVer = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                val isDpc = if (DeviceAdminReceiver.isDeviceOwner(this@MainActivity)) 1 else 0

                val success = apiClient.registerDevice(
                    serverUrl = "https://cami.diazsistemas.com/api",
                    token = token,
                    model = model,
                    androidVersion = androidVer,
                    isDpc = isDpc
                )

                withContext(Dispatchers.Main) {
                    btn.isEnabled = true
                    btn.text = "Vincular"
                    if (success) {
                        Toast.makeText(this@MainActivity, "¡Dispositivo vinculado con éxito! 🌸", Toast.LENGTH_SHORT).show()
                        updateUI()
                        startCoreService()
                    } else {
                        Toast.makeText(this@MainActivity, "Código inválido o error de conexión. Verifica en tu panel.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btn_perm_usage).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        findViewById<Button>(R.id.btn_perm_overlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btn_perm_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btn_sync_now).setOnClickListener {
            if (!prefs.isPaired) {
                Toast.makeText(this, "Primero vincula el dispositivo con el código QR", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startCoreService()
            Toast.makeText(this, "Sincronización iniciada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        val tvPairingStatus = findViewById<TextView>(R.id.tv_pairing_status)
        val tvDeviceInfo = findViewById<TextView>(R.id.tv_device_info)
        val tvStatusDpc = findViewById<TextView>(R.id.tv_status_dpc)

        if (prefs.isPaired) {
            tvPairingStatus.text = "Estado: Conectado ✅"
            tvPairingStatus.setTextColor(getColor(android.R.color.holo_green_light))
            tvDeviceInfo.text = "Supervisando a: ${prefs.childName}\nServidor: ${prefs.serverUrl}"
        } else {
            tvPairingStatus.text = "Estado: No Vinculado ⚠️"
            tvPairingStatus.setTextColor(getColor(android.R.color.holo_red_light))
            tvDeviceInfo.text = "Escanea el código QR de tu panel web para activar."
        }

        // Estado DPC
        val isDpc = DeviceAdminReceiver.isDeviceOwner(this)
        if (isDpc) {
            tvStatusDpc.text = "Activo (Máxima Protección)"
            tvStatusDpc.setTextColor(getColor(android.R.color.holo_green_light))
        } else {
            tvStatusDpc.text = "No Activo"
            tvStatusDpc.setTextColor(getColor(android.R.color.holo_red_light))
        }

        // Estado de Permisos
        val hasUsage = hasUsageStatsPermission()
        findViewById<Button>(R.id.btn_perm_usage).text = if (hasUsage) "Concedido ✅" else "Conceder"
        findViewById<Button>(R.id.btn_perm_usage).isEnabled = !hasUsage

        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        findViewById<Button>(R.id.btn_perm_overlay).text = if (hasOverlay) "Concedido ✅" else "Conceder"
        findViewById<Button>(R.id.btn_perm_overlay).isEnabled = !hasOverlay
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startCoreService() {
        val intent = Intent(this, ParentalCoreService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
