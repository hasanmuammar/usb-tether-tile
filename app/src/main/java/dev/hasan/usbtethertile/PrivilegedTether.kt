package dev.hasan.usbtethertile

import android.os.IBinder
import rikka.shizuku.ShizukuBinderWrapper
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Privileged USB-tethering toggle: calls the hidden
 * IConnectivityManager.setUsbTethering(boolean, String) through Shizuku's
 * privileged binder, running as shell (which holds TETHER_PRIVILEGED —
 * verified on device, see docs/dev-environment.md).
 *
 * Returns the binder result int; 0 == TETHER_ERROR_NO_ERROR == success.
 * Must be called off the QS main thread.
 */
object PrivilegedTether {

    fun setUsbTethering(enable: Boolean, callerPkg: String): Int {
        // Idempotent: exempts hidden members of android.net and android.os
        // (ServiceManager) so the reflective lookups are legal on API 28+.
        HiddenApiBypass.addHiddenApiExemptions("Landroid/net/", "Landroid/os/")
        // ServiceManager is a hidden class: reached reflectively.
        val getService = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "connectivity") as IBinder
        val proxy = Class.forName("android.net.IConnectivityManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(binder))!!
        return Class.forName("android.net.IConnectivityManager")
            .getMethod(
                "setUsbTethering",
                Boolean::class.javaPrimitiveType,
                String::class.java
            )
            .invoke(proxy, enable, callerPkg) as Int
    }
}
