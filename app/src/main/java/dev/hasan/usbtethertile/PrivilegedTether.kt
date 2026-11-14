package dev.hasan.usbtethertile

import android.os.IBinder
import rikka.shizuku.ShizukuBinderWrapper
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Privileged USB-tethering toggle on Android 15.
 *
 * The tethering control lives on ITetheringConnector, registered in
 * ServiceManager as "tethering" (verified on device:
 * `service list` -> "tethering: [android.net.ITetheringConnector]").
 * Its Android 15 signature is
 *   setUsbTethering(boolean enable, String callerPkg,
 *                   String attributionTag, IIntResultListener listener)
 * (dexdump of /apex/com.android.tethering/javalib/framework-tethering.jar).
 * The old IConnectivityManager.setUsbTethering(boolean, String) is gone on
 * Android 15 (verified: NoSuchMethodException on device).
 *
 * We call it through Shizuku's ShizukuBinderWrapper, so the transact runs as
 * shell (uid 2000), which holds TETHER_PRIVILEGED (verified on device via
 * dumpsys). The result arrives on the listener; we block briefly for it.
 * Returns the int result (0 == TETHER_ERROR_NO_ERROR == success).
 *
 * Must be called off the QS main thread.
 */
object PrivilegedTether {

    private const val CONNECTOR_SERVICE = "tethering"

    /**
     * The service validates callerPkg against the calling uid; through
     * Shizuku we call as shell (uid 2000), so the caller package must be
     * shell's package (verified on device: "Package name
     * dev.hasan.usbtethertile does not match UID 2000" otherwise).
     */
    private const val SHELL_PACKAGE = "com.android.shell"

    fun setUsbTethering(enable: Boolean, callerPkg: String = SHELL_PACKAGE): Int {
        // Idempotent-ish: exempts hidden members of android.net and android.os
        // (ServiceManager) so the reflective lookups are legal on API 28+.
        HiddenApiBypass.addHiddenApiExemptions("Landroid/net/", "Landroid/os/")
        // ServiceManager is a hidden class: reached reflectively.
        val getService = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
        val connectorBinder = getService.invoke(null, CONNECTOR_SERVICE) as IBinder

        val connectorStub = Class.forName("android.net.ITetheringConnector\$Stub")
        val proxy = connectorStub
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(connectorBinder))!!

        val listenerClass = Class.forName("android.net.IIntResultListener")
        val latch = CountDownLatch(1)
        val holder = IntArray(1)
        val listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass)
        ) { _, method, args ->
            if (method.name == "onResult") {
                holder[0] = args?.firstOrNull() as? Int ?: Int.MIN_VALUE
                latch.countDown()
            }
            null
        }

        Class.forName("android.net.ITetheringConnector")
            .getMethod(
                "setUsbTethering",
                Boolean::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                listenerClass
            )
            .invoke(proxy, enable, SHELL_PACKAGE, null, listener)

        if (!latch.await(10, TimeUnit.SECONDS)) {
            return Int.MIN_VALUE // no result within timeout -> treat as failure
        }
        return holder[0]
    }
}
