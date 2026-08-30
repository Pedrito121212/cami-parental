package com.parental.control.dpc

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.widget.Toast

class DeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Cami: Administrador de Dispositivo Activado", Toast.LENGTH_SHORT).show()
        enforceProtectionPolicies(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        enforceProtectionPolicies(context)
    }

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context.applicationContext, DeviceAdminReceiver::class.java)
        }

        fun isDeviceOwner(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isDeviceOwnerApp(context.packageName)
        }

        fun enforceProtectionPolicies(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getComponentName(context)

            if (dpm.isDeviceOwnerApp(context.packageName)) {
                try {
                    // 1. Bloquear desinstalación de Cami
                    dpm.setUninstallBlocked(adminComponent, context.packageName, true)

                    // 2. Desactivar restablecimiento de fábrica desde ajustes
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)

                    // 3. Desactivar modo seguro de Android si es posible
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)

                    // 4. Impedir añadir nuevos usuarios / perfiles secundarios
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_ADD_USER)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Suspender nativamente apps a nivel de SO (imposible abrirlas)
        fun setPackagesSuspended(context: Context, packages: Array<String>, suspended: Boolean) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = getComponentName(context)

            if (dpm.isDeviceOwnerApp(context.packageName)) {
                try {
                    dpm.setPackagesSuspended(adminComponent, packages, suspended)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Bloqueo instantáneo de pantalla del teléfono
        fun lockNow(context: Context) {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            try {
                dpm.lockNow()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
