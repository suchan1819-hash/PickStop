package com.zeroinjury.pickstop.order

import android.os.SystemClock
import com.zeroinjury.pickstop.data.MenuItem
import com.zeroinjury.pickstop.data.RestStop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * 앱 시계. 실제 주행에서는 1배속이고, 시연 때는 배속을 올려 24분 주행을 1~2분 안에 보여 준다.
 * 도착·조리·충전 시간 계산은 모두 이 시계를 기준으로 하므로 배속을 바꿔도 결과가 같다.
 */
object AppClock {
    @Volatile var speed: Int = 1
        private set
    private var baseReal = SystemClock.elapsedRealtime()
    private var baseApp = System.currentTimeMillis()

    @Synchronized fun now(): Long = baseApp + (SystemClock.elapsedRealtime() - baseReal) * speed

    @Synchronized fun setSpeed(newSpeed: Int, startAt: Long? = null) {
        val n = now()
        baseReal = SystemClock.elapsedRealtime()
        baseApp = startAt ?: n
        speed = newSpeed.coerceAtLeast(1)
    }

    fun hhmm(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    /** 시연 시작 시각: 오늘 12:20 */
    fun todayAt(h: Int, m: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

enum class OrderStatus(val label: String) {
    SCHEDULED("조리 대기"),
    COOK_NOW("지금 조리 시작"),
    COOKING("조리 중"),
    READY("완성"),
    PICKED_UP("수령 완료"),
    CANCELLED("취소됨"),
}

data class OrderLine(val item: MenuItem, val qty: Int = 1, val who: String = "운전자")

data class Order(
    val no: Int,
    val stop: RestStop,
    val lines: List<OrderLine>,
    val total: Int,
    val approvalId: String,
    val createdAt: Long,
    /** 처음 주문할 때의 도착 예정 시각 */
    val firstArrivalAt: Long,
    /** 지금 계산한 도착 예정 시각 */
    val arrivalAt: Long,
    val cookSec: Int,
    val status: OrderStatus = OrderStatus.SCHEDULED,
    val cookStartedAt: Long? = null,
    val readyAt: Long? = null,
    val arrived: Boolean = false,
    /** 처음 예정보다 늦어진 분 */
    val delayMin: Int = 0,
    /** 마지막으로 매장·운전자에게 알린 도착 예정 시각 */
    val announcedArrivalAt: Long = arrivalAt,
    val sample: Boolean = false,
) {
    /** 도착 2분 전에 완성되도록 조리를 시작할 시각 */
    val cookStartAt: Long get() = arrivalAt - cookSec * 1000L - READY_BEFORE_MS
    val freeCancel: Boolean get() = status == OrderStatus.SCHEDULED
    val summary: String get() = lines.groupBy { it.item.name }.entries.joinToString(" · ") { (n, l) -> "$n ${l.sumOf { it.qty }}" }

    companion object { const val READY_BEFORE_MS = 2 * 60 * 1000L }
}

data class CartLine(val item: MenuItem, val who: String)

/**
 * 주문 서버 역할. 앱 화면과 매장 웹 화면(내장 서버)이 같은 상태를 본다.
 * 실제 서비스에서는 이 부분이 외부 주문 서버 API 로 바뀐다.
 */
object OrderStore {
    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    private val _cart = MutableStateFlow<List<CartLine>>(emptyList())
    val cart: StateFlow<List<CartLine>> = _cart.asStateFlow()

    /** 시연: 매장 직원이 웹 화면에서 누르지 않아도 조리를 시작·완성 처리 */
    @Volatile var storeAutoAccept: Boolean = true

    private var nextNo = 112

    @Synchronized fun newOrderNo(): Int {
        while (_orders.value.any { it.no == nextNo }) nextNo++
        return nextNo++
    }

    @Synchronized fun put(order: Order) {
        _orders.value = _orders.value.filterNot { it.no == order.no } + order
    }

    @Synchronized fun update(no: Int, block: (Order) -> Order) {
        _orders.value = _orders.value.map { if (it.no == no) block(it) else it }
    }

    fun find(no: Int): Order? = _orders.value.firstOrNull { it.no == no }

    /** 매장 화면에서 누른 버튼 */
    fun storeAction(no: Int, action: String): Boolean {
        val o = find(no) ?: return false
        val now = AppClock.now()
        when (action) {
            "start" -> if (o.status == OrderStatus.SCHEDULED || o.status == OrderStatus.COOK_NOW)
                update(no) { it.copy(status = OrderStatus.COOKING, cookStartedAt = now) } else return false
            "ready" -> if (o.status == OrderStatus.COOKING || o.status == OrderStatus.COOK_NOW)
                update(no) { it.copy(status = OrderStatus.READY, readyAt = now) } else return false
            "picked" -> update(no) { it.copy(status = OrderStatus.PICKED_UP) }
            else -> return false
        }
        return true
    }

    @Synchronized fun addToCart(line: CartLine) { _cart.value = _cart.value + line }
    @Synchronized fun removeFromCart(index: Int) { _cart.value = _cart.value.filterIndexed { i, _ -> i != index } }
    @Synchronized fun clearCart() { _cart.value = emptyList() }

    /** 시연용 가상 주문도 시간에 맞춰 흐르게 한다. */
    fun advanceSamples(now: Long) {
        _orders.value.filter { it.sample }.forEach { o ->
            when {
                o.status == OrderStatus.SCHEDULED && now >= o.cookStartAt -> update(o.no) { it.copy(status = OrderStatus.COOK_NOW) }
                o.status == OrderStatus.COOK_NOW && storeAutoAccept && now >= o.cookStartAt + 20_000 ->
                    update(o.no) { it.copy(status = OrderStatus.COOKING, cookStartedAt = now) }
                o.status == OrderStatus.COOKING && now >= (o.cookStartedAt ?: now) + o.cookSec * 1000L ->
                    update(o.no) { it.copy(status = OrderStatus.READY, readyAt = now) }
                o.status == OrderStatus.READY && !o.arrived && now >= o.arrivalAt -> update(o.no) { it.copy(arrived = true) }
                o.status == OrderStatus.READY && o.arrived && now >= o.arrivalAt + 4 * 60_000L -> update(o.no) { it.copy(status = OrderStatus.PICKED_UP) }
            }
        }
    }

    /** 시계(배속)를 바꾼 뒤 가상 주문을 새 시각 기준으로 다시 만든다. */
    @Synchronized fun resetSamples(stop: RestStop) {
        _orders.value = _orders.value.filterNot { it.sample }
        seedSamples(stop)
    }

    /** 매장 화면이 비어 보이지 않도록 넣는 시연용 가상 주문 */
    fun seedSamples(stop: RestStop) {
        if (_orders.value.any { it.sample }) return
        val now = AppClock.now()
        fun item(name: String) = stop.menu.first { it.name == name }
        fun sample(no: Int, names: List<String>, arriveInMin: Int, status: OrderStatus, arrived: Boolean = false) = Order(
            no = no, stop = stop, lines = names.map { OrderLine(item(it)) }, total = names.sumOf { item(it).price },
            approvalId = "SAMPLE", createdAt = now, firstArrivalAt = now + arriveInMin * 60_000L,
            arrivalAt = now + arriveInMin * 60_000L, cookSec = names.maxOf { item(it).cookSec },
            status = status, arrived = arrived, sample = true,
        )
        listOf(
            sample(104, listOf("우동", "떡볶이"), 0, OrderStatus.READY, arrived = true),
            sample(107, listOf("아이스 아메리카노"), 2, OrderStatus.READY),
            sample(109, listOf("소떡소떡", "소떡소떡", "아이스 아메리카노"), 8, OrderStatus.COOKING).copy(cookStartedAt = now),
            sample(115, listOf("돈가스", "우동"), 30, OrderStatus.SCHEDULED),
            sample(118, listOf("한우국밥", "한우국밥"), 42, OrderStatus.SCHEDULED),
        ).forEach { put(it) }
    }
}
