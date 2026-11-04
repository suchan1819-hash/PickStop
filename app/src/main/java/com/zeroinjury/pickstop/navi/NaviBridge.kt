package com.zeroinjury.pickstop.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
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
 * Pleos NaviHelper SDK 연결.
 *
 * - 도착 예정 시간: 경유지 정보(remainTime)·주행 정보(DrivingState.duration)·TripInfo(eta)
 * - 주문한 휴게소를 경유지로 추가, 경유지 도착 이벤트로 픽업 안내
 * - 경로 재탐색 이벤트로 도착 시간 변화를 즉시 다시 계산
 * - 전기차: 경유지 충전 계획(ChargingPlan)과 도착 시 예상 배터리 잔량
 */
class NaviBridge(context: Context) {

    data class Eta(
        val stopName: String,
        val remainSec: Int,
        val remainMeters: Int,
        val chargingMin: Int? = null,
        val socAtArrival: Int? = null,
        val source: String,
    )

    sealed interface Event {
        data class WaypointArrived(val name: String) : Event
        data object Rerouted : Event
        data class Error(val code: String) : Event
    }

    private val tag = "PickStop/Navi"
    private val helper = NaviHelper(context.applicationContext)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _eta = MutableStateFlow<Eta?>(null)
    /** 경로상 첫 번째 경유지(주문한 휴게소)까지의 도착 정보. 내비가 알려 주지 않으면 null */
    val eta: StateFlow<Eta?> = _eta.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private var targetName: String? = null

    private val listener = object : NaviHelperEventListener {
        override fun onWayPointInfo(waypointInfo: List<WaypointInfo>) {
            val wp = waypointInfo.firstOrNull { it.name == targetName } ?: waypointInfo.firstOrNull() ?: return
            _eta.value = Eta(wp.name, wp.remainTime, wp.remainDistance, _eta.value?.chargingMin, _eta.value?.socAtArrival, "NaviHelper 경유지")
        }

        override fun onDrivingInfo(drivingInfo: DrivingInfo) {
            val st = drivingInfo.waypoints.firstOrNull { it.name == targetName } ?: drivingInfo.waypoints.firstOrNull() ?: return
            val plan = st.chargingPlan
            _eta.value = Eta(
                stopName = st.name,
                remainSec = st.duration,
                remainMeters = st.distance,
                chargingMin = plan?.duration?.takeIf { it > 0 }?.let { (it + 59) / 60 },
                socAtArrival = _eta.value?.socAtArrival,
                source = "NaviHelper 주행 정보",
            )
        }

        override fun onTripInfo(tripInfo: TripInfo) {
            val cur = _eta.value
            _eta.value = (cur ?: Eta(targetName ?: "", tripInfo.eta, tripInfo.distance, source = "NaviHelper TripInfo"))
                .copy(socAtArrival = tripInfo.estimatedSocAtDest.takeIf { it in 0..100 })
        }

        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {
            _events.tryEmit(Event.WaypointArrived(waypointArrivedInfo.name))
        }

        override fun onReRouteEnded() {
            _events.tryEmit(Event.Rerouted)
            refresh()
        }

        override fun onRoutingCompleted() = refresh()

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(tag, "navi error $errorCode")
            _events.tryEmit(Event.Error(errorCode.name))
        }
    }

    fun start() {
        runCatching {
            helper.initialize(
                onConnected = {
                    _connected.value = true
                    helper.addListener(listener)
                    refresh()
                },
                onFailed = { t ->
                    Log.w(tag, "NaviHelper unavailable: ${t.message}")
                    _connected.value = false
                },
            )
        }.onFailure { Log.w(tag, "NaviHelper init failed: ${it.message}") }
    }

    /** 주문을 확정하면 휴게소를 첫 번째 경유지로 넣어, 깜빡 지나치지 않게 한다. */
    fun addRestStopAsWaypoint(stop: RestStop): Boolean {
        targetName = stop.name
        if (!_connected.value) return false
        return runCatching {
            helper.addWaypoint(
                RequestWaypointInfo(
                    stop.lat, stop.lng, stop.name, WaypointIndex.FIRST,
                    stop.id, "${stop.route} ${stop.direction}", "0", "pickstop",
                )
            )
            helper.requestTrip(TripRequest(stop.lat, stop.lng, stop.id, stop.name, "0"))
            refresh()
        }.onFailure { Log.w(tag, "addWaypoint failed: ${it.message}") }.isSuccess
    }

    fun removeRestStopWaypoint() {
        targetName = null
        _eta.value = null
        if (_connected.value) runCatching { helper.removeWaypoint(WaypointIndex.FIRST) }
    }

    /** 경유지·주행 정보를 다시 요청한다. 결과는 리스너로 들어온다. */
    fun refresh() {
        if (!_connected.value) return
        runCatching { helper.getWaypointInfo() }
        runCatching { helper.getRouteStateInfo() }
    }

    fun release() {
        runCatching { helper.removeListener(listener) }
        runCatching { helper.release() }
    }
}
