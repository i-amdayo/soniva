package com.soniva.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkMonitor(context: Context, scope: CoroutineScope) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isWifi = MutableStateFlow(checkWifi())
    val isWifi: StateFlow<Boolean> = _isWifi.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch(Dispatchers.Default) {
                    _isOnline.value = true
                    _isWifi.value = checkWifi()
                }
            }

            override fun onLost(network: Network) {
                scope.launch(Dispatchers.Default) {
                    _isOnline.value = checkOnline()
                    _isWifi.value = checkWifi()
                }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                scope.launch(Dispatchers.Default) {
                    val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    _isOnline.value = hasInternet
                    _isWifi.value = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                }
            }
        }

        try {
            cm?.registerNetworkCallback(request, callback)
        } catch (_: Exception) {
            _isOnline.value = true
        }
    }

    private fun checkOnline(): Boolean {
        val cm = cm ?: return true
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkWifi(): Boolean {
        val cm = cm ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
