package com.zeroinjury.pickstop.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.util.Log
import com.zeroinjury.pickstop.data.RestStop
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pleos FusedLocation SDK: 차량 위치와 휴게소 진입 감지(근접 알림).
 * 내비 경유지 도착 이벤트가 오지 않는 경우에도 휴게소 반경에 들어서면 픽업 안내를 시작한다.
 */
class LocationBridge(context: Context) {

    private val tag = "PickStop/Location"
    private val fused = FusedLocation(context.applicationContext)

    private val _location = MutableStateFlow<LocationData?>(null)
    val location: StateFlow<LocationData?> = _location.asStateFlow()

    private val _entered = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** 진입한 휴게소 id */
    val entered: SharedFlow<String> = _entered.asSharedFlow()

    var available = false
        private set
    private var alertId: String? = null
    private var alertStopId: String? = null

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) { _location.value = data }
        override fun onError(e: Exception) { Log.w(tag, "location error: ${e.message}") }
    }

    fun start() {
        runCatching {
            fused.initialize()
            available = fused.isSupported()
            if (available) fused.registerFusedLocationCallback(locationListener)
        }.onFailure { Log.w(tag, "FusedLocation unavailable: ${it.message}") }
    }

    /** 주문한 휴게소 반경 300m 진입을 감시한다. */
    fun watchArrival(stop: RestStop) {
        clearArrival()
        if (!available) return
        alertStopId = stop.id
        runCatching {
            fused.addProximityAlert(
                ProximityAlertRequest(stop.lat, stop.lng, 300f, setOf(ProximityTransition.ENTER)),
                object : ProximityAlertListener {
                    override fun onRegistered(id: String) { alertId = id }
                    override fun onTransition(event: ProximityAlertEvent) {
                        if (event.transition == ProximityTransition.ENTER) alertStopId?.let { _entered.tryEmit(it) }
                    }
                    override fun onError(e: Exception) { Log.w(tag, "proximity error: ${e.message}") }
                },
            )
        }.onFailure { Log.w(tag, "addProximityAlert failed: ${it.message}") }
    }

    fun clearArrival() {
        alertId?.let { id -> runCatching { fused.removeProximityAlert(id) } }
        alertId = null
        alertStopId = null
    }

    fun release() {
        clearArrival()
        runCatching { fused.unregisterFusedLocationCallback(locationListener) }
        runCatching { fused.release() }
    }
}
