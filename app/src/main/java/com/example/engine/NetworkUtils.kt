package com.example.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.DecimalFormat
import java.util.Collections
import java.util.Locale

object NetworkUtils {

    /**
     * Resolves the primary local IPv4 address across active Wi-Fi, Hotspot (AP), or P2P interfaces.
     * Prioritizes wlan0, ap0, softap, rndis interfaces.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            
            // First priority: Wi-Fi or Hotspot interfaces
            val prioritizedPrefixes = listOf("wlan", "ap", "swlan", "softap", "p2p", "eth", "rndis")
            for (prefix in prioritizedPrefixes) {
                for (nif in interfaces) {
                    if (nif.name.startsWith(prefix, ignoreCase = true) && nif.isUp && !nif.isLoopback) {
                        for (addr in Collections.list(nif.inetAddresses)) {
                            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                                val hostAddress = addr.hostAddress
                                if (hostAddress != null && !hostAddress.startsWith("127.")) {
                                    return hostAddress
                                }
                            }
                        }
                    }
                }
            }

            // Fallback: any active non-loopback IPv4 address
            for (nif in interfaces) {
                if (nif.isUp && !nif.isLoopback) {
                    for (addr in Collections.list(nif.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val hostAddress = addr.hostAddress
                            if (hostAddress != null && !hostAddress.startsWith("127.")) {
                                return hostAddress
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Checks if device is connected to a local Wi-Fi or Hotspot network.
     */
    fun isConnectedToLocalNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return getLocalIpAddress() != null
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return getLocalIpAddress() != null
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
               getLocalIpAddress() != null
    }

    /**
     * Formats raw bytes into human readable format (e.g. "1.4 MB", "520 KB", "2.1 GB").
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, index.toDouble())
        return String.format(Locale.US, if (index == 0) "%.0f %s" else "%.1f %s", value, units[index])
    }

    /**
     * Formats transfer speed in bytes per second into readable MB/s or KB/s.
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "0 KB/s"
        val mbPerSec = bytesPerSecond.toDouble() / (1024.0 * 1024.0)
        return if (mbPerSec >= 1.0) {
            String.format(Locale.US, "%.1f MB/s", mbPerSec)
        } else {
            val kbPerSec = bytesPerSecond.toDouble() / 1024.0
            String.format(Locale.US, "%.0f KB/s", kbPerSec)
        }
    }

    /**
     * Formats duration in seconds to "1h 10m", "2m 15s", or "45s".
     */
    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0s"
        val hours = seconds / 3600
        val remainder = seconds % 3600
        val mins = remainder / 60
        val secs = remainder % 60
        return when {
            hours > 0 -> if (mins > 0) "${hours}h ${mins}m" else "${hours}h"
            mins > 0 -> if (secs > 0) "${mins}m ${secs}s" else "${mins}m"
            else -> "${secs}s"
        }
    }
}
