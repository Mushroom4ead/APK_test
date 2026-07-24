package com.example.speedtest.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Определение типа активного сетевого подключения. */
object NetworkUtil {
    fun currentType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "—"
        val network = cm.activeNetwork ?: return "Нет сети"
        val caps = cm.getNetworkCapabilities(network) ?: return "Нет сети"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Мобильная сеть"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Другое"
        }
    }
}
