package com.parental.control.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.parental.control.services.ParentalCoreService
import com.parental.control.vpn.ParentalVpnService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            // Iniciar servicio central en primer plano
            val serviceIntent = Intent(context, ParentalCoreService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Iniciar VPN DNS si está activada
            val vpnIntent = Intent(context, ParentalVpnService::class.java)
            context.startService(vpnIntent)
        }
    }
}
