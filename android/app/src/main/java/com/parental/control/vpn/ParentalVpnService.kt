package com.parental.control.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.parental.control.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

class ParentalVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val vpnScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var prefs: PreferencesManager

    // DNS Familiar Seguro (CleanBrowsing Family Filter / Cloudflare 1.1.1.3 Family)
    // Bloquea pornografía, malware y fuerza SafeSearch automáticamente
    private val FAMILY_DNS_1 = "1.1.1.3"
    private val FAMILY_DNS_2 = "1.0.0.3"

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .setSession("Cami Safe Web Filter")
                .addAddress("10.0.0.2", 32)
                .addDnsServer(FAMILY_DNS_1)
                .addDnsServer(FAMILY_DNS_2)
                // Redirigir solo tráfico DNS (ahorro de batería y rendimiento máximo)
                .addRoute(FAMILY_DNS_1, 32)
                .addRoute(FAMILY_DNS_2, 32)
                .setBlocking(false)

            vpnInterface = builder.establish()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
