package dev.hasan.usbtethertile

import android.content.Context
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

/**
 * Tethering-state observation via the hidden TetheringManager. These APIs
 * are not in the public SDK on API 35 (ConnectivityManager lost them), so
 * we reflect exactly like the privileged toggle — observation itself needs
 * no privileges (the binder checks caller only for privileged calls).
 */
object TetherObservation {

    /** handle for unregister */
    class Registration internal constructor(
        val manager: Any,
        val proxy: Any,
        val unregister: (Any, Any) -> Unit
    )

    private fun manager(context: Context): Any? =
        context.getSystemService("tethering")

    /** Tethered iface names right now, via hidden TetheringManager.getTetheredIfaces(). */
    @Suppress("UNCHECKED_CAST")
    fun tetheredIfaces(context: Context): List<String> {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/net/", "Landroid/os/")
        val tm = manager(context) ?: return emptyList()
        val result = tm.javaClass
            .getMethod("getTetheredIfaces")
            .invoke(tm) as Array<*>? ?: return emptyList()
        return result.filterIsInstance<String>()
    }

    /** USB-tethered iface name right now, or null. */
    fun usbIface(context: Context): String? =
        usbTetherIface(tetheredIfaces(context))

    /**
     * Registers a callback (dynamic proxy of TetheringManager$TetheringEventCallback)
     * delivering [onChange] on tethered-interface changes.
     */
    fun register(context: Context, executor: Executor, onChange: (List<String>) -> Unit): Registration? {
        try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/net/", "Landroid/os/")
            val tm = manager(context) ?: return null
            val callbackClass = Class.forName("android.net.TetheringManager\$TetheringEventCallback")
            val proxy = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "onTetheredInterfacesChanged" -> {
                        val list = (args?.firstOrNull() as? List<*>)?.filterIsInstance<String>()
                            ?: emptyList()
                        onChange(list)
                    }
                    "toString" -> "TetherObservation"
                    else -> null
                }
            }
            tm.javaClass
                .getMethod("registerTetheringEventCallback", Executor::class.java, callbackClass)
                .invoke(tm, executor, proxy)
            return Registration(tm, proxy) { m, p ->
                m.javaClass
                    .getMethod("unregisterTetheringEventCallback", callbackClass)
                    .invoke(m, p)
            }
        } catch (e: Exception) {
            return null
        }
    }

    fun unregister(context: Context, registration: Registration?) {
        try {
            registration?.unregister(registration.manager, registration.proxy)
        } catch (_: Exception) {
            // Nothing sensible to do; state refreshes on next onStartListening.
        }
    }
}
