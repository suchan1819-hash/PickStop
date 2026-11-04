package com.zeroinjury.pickstop.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * 차량 상태: 기어(주행·정차), 속도, 전기차 배터리.
 * AAOS 표준 VehicleProperty 를 구독하고, 시연 때는 앱 안의 시연 패널로 값을 바꿀 수 있다.
 */
class VehicleBridge(context: Context) {

    enum class Gear { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

    data class Status(
        val gear: Gear = Gear.UNKNOWN,
        val speedKmh: Int = 0,
        /** 배터리 잔량(%). 전기차가 아니면 null */
        val batteryPct: Int? = null,
        val carConnected: Boolean = false,
    ) {
        /** 정차 중: P단이거나 속도가 0 */
        val isParked: Boolean get() = gear == Gear.PARK || (gear == Gear.UNKNOWN && speedKmh == 0)
        val isDriving: Boolean get() = !isParked
    }

    private val tag = "PickStop/Vehicle"
    private val appContext = context.applicationContext
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var car: Car? = null
    private var pm: CarPropertyManager? = null
    private var capacityWh: Float? = null

    private var simGear: Gear? = null
    private var simBattery: Int? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.CURRENT_GEAR ->
                    if (simGear == null) (value.value as? Int)?.let { g -> update { it.copy(gear = mapGear(g)) } }
                VehiclePropertyIds.PERF_VEHICLE_SPEED ->
                    (value.value as? Float)?.let { v -> update { it.copy(speedKmh = (v * 3.6f).roundToInt()) } }
                VehiclePropertyIds.EV_BATTERY_LEVEL ->
                    if (simBattery == null) (value.value as? Float)?.let { wh -> toPct(wh)?.let { p -> update { it.copy(batteryPct = p) } } }
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w(tag, "property error $propertyId")
        }
    }

    fun start() {
        runCatching {
            val c = Car.createCar(appContext)
            car = c
            val m = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            pm = m
            runCatching { capacityWh = m.getFloatProperty(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY, 0) }
            runCatching { update { it.copy(gear = mapGear(m.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0))) } }
            runCatching { toPct(m.getFloatProperty(VehiclePropertyIds.EV_BATTERY_LEVEL, 0))?.let { p -> update { it.copy(batteryPct = p) } } }
            listOf(
                VehiclePropertyIds.GEAR_SELECTION to CarPropertyManager.SENSOR_RATE_ONCHANGE,
                VehiclePropertyIds.CURRENT_GEAR to CarPropertyManager.SENSOR_RATE_ONCHANGE,
                VehiclePropertyIds.PERF_VEHICLE_SPEED to CarPropertyManager.SENSOR_RATE_NORMAL,
                VehiclePropertyIds.EV_BATTERY_LEVEL to CarPropertyManager.SENSOR_RATE_NORMAL,
            ).forEach { (id, rate) -> runCatching { m.registerCallback(callback, id, rate) } }
            update { it.copy(carConnected = true) }
        }.onFailure { Log.w(tag, "Car API unavailable: ${it.message}") }
    }

    private fun toPct(wh: Float): Int? {
        val cap = capacityWh ?: return null
        if (cap <= 0f) return null
        return (wh / cap * 100f).roundToInt().coerceIn(0, 100)
    }

    // ---------- 시연 패널 ----------
    fun simulateGear(g: Gear?) {
        simGear = g
        if (g != null) update { it.copy(gear = g, speedKmh = if (g == Gear.DRIVE) 98 else 0) }
    }

    fun simulateBattery(pct: Int?) {
        simBattery = pct
        if (pct != null) update { it.copy(batteryPct = pct) }
    }

    fun release() {
        runCatching { pm?.unregisterCallback(callback) }
        runCatching { car?.disconnect() }
    }

    private fun update(block: (Status) -> Status) { _status.value = block(_status.value) }

    private fun mapGear(raw: Int): Gear = when (raw) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
        else -> Gear.DRIVE
    }
}
