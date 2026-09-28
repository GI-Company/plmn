package com.example.plmn.security

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.NetworkInterface

object NetworkDiagnosticsHelper {

    data class DiagnosticNetworkInfo(
        val activeNetworkType: String,
        val localIpAddresses: List<String>
    )

    fun getNetworkInfo(context: Context?): DiagnosticNetworkInfo {
        if (context == null) {
            return DiagnosticNetworkInfo(
                activeNetworkType = "unknown (no context)",
                localIpAddresses = getLocalIpAddresses()
            )
        }

        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val caps = cm?.getNetworkCapabilities(activeNetwork)

            val netType = when {
                caps == null -> "unavailable"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI (Active)"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR (Active)"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET (Active)"
                else -> "OTHER (Active)"
            }

            val ipList = getLocalIpAddresses()

            DiagnosticNetworkInfo(
                activeNetworkType = netType,
                localIpAddresses = if (ipList.isNotEmpty()) ipList else listOf("unknown")
            )
        } catch (_: Exception) {
            DiagnosticNetworkInfo(
                activeNetworkType = "unknown",
                localIpAddresses = listOf("unknown")
            )
        }
    }

    private fun getLocalIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    val host = addr.hostAddress.orEmpty()
                    if (host.isNotBlank() && !host.contains(":")) {
                        ips.add(host)
                    }
                }
            }
        } catch (_: Exception) {}
        return ips
    }
}
