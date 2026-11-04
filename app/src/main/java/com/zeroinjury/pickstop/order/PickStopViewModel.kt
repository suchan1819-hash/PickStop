package com.zeroinjury.pickstop.order

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeroinjury.pickstop.data.MenuItem
import com.zeroinjury.pickstop.data.RestStop
import com.zeroinjury.pickstop.data.RestStops
import com.zeroinjury.pickstop.location.LocationBridge
import com.zeroinjury.pickstop.navi.NaviBridge
import com.zeroinjury.pickstop.vehicle.VehicleBridge
import com.zeroinjury.pickstop.voice.VoiceBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class Screen { HOME, CONFIRM, TIMELINE, PICKUP, MENU, PAYMENT }

/** 음성으로 확인을 받으려고 준비한 주문 */
data class Pending(
    val stop: RestStop,
    val lines: List<OrderLine>,
    val reason: Reason,
    val etaSec: Int,
    val chargeMin: Int? = null,
) {
    enum class Reason { FAVORITE, CART, COFFEE_BREAK, EV_CHARGE }
    val total: Int get() = lines.sumOf { it.item.price * it.qty }
    val cookSec: Int get() = lines.maxOf { it.item.cookSec }
    /** 지금 주문하면 조리 시작까지 남은 시간(무료 취소 가능 시간) */
    val freeCancelSec: Int get() = (etaSec - cookSec - 120).coerceAtLeast(0)
}

data class StopEta(val stop: RestStop, val etaSec: Int, val km: Int)

data class UiState(
    val vehicle: VehicleBridge.Status = VehicleBridge.Status(),
    val naviConnected: Boolean = false,
    val etaSource: String = "",
    val stops: List<StopEta> = emptyList(),
    val favorites: List<String> = emptyList(),
    val pending: Pending? = null,
    val order: Order? = null,
    val drivingSec: Long = 0,
    val clock: Long = 0,
    val card: TestPaymentGateway.Card? = null,
    val voice: VoiceBridge.State = VoiceBridge.State.IDLE,
    val heard: String = "",
    val spoken: String = "",
    val notice: String? = null,
    val chargeEndAt: Long? = null,
    val demo: Boolean = false,
    val manualScreen: Screen? = null,
    val savedMinutesMonth: Int = 34,
) {
    val next: StopEta? get() = stops.firstOrNull()
    val screen: Screen get() {
        val o = order
        return when {
            pending != null -> Screen.CONFIRM
            o != null && o.arrived && o.status != OrderStatus.PICKED_UP -> Screen.PICKUP
            vehicle.isParked && manualScreen != null -> manualScreen
            o != null && o.status != OrderStatus.PICKED_UP && o.status != OrderStatus.CANCELLED -> Screen.TIMELINE
            vehicle.isParked -> Screen.MENU
            else -> Screen.HOME
        }
    }
}

/**
 * 픽스탑의 두뇌.
 * 도착 예정 시간이 바뀔 때마다 "도착 2분 전 완성"이 되도록 조리 시작 시각을 다시 계산하고,
 * 매장(주문 서버)에 알린다. 주행 중 상호작용은 전부 음성으로만 한다.
 */
class PickStopViewModel(app: Application) : AndroidViewModel(app) {

    val vehicle = VehicleBridge(app)
    val navi = NaviBridge(app)
    val location = LocationBridge(app)
    val voice = VoiceBridge(app)
    val payment = TestPaymentGateway(app)
    private val server = OrderServer(app)
    private val prefs = app.getSharedPreferences("pickstop", Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    // ---- 시연 주행(내비 경로가 없을 때 쓰는 가상 주행) ----
    /** 출발점에서 각 휴게소까지 남은 거리(m) */
    private val demoDistance = mutableMapOf("deokpyeong" to 31_000.0, "yeoju" to 63_000.0, "munmak" to 92_000.0)
    private var demoCongestionSec = 0.0
    private var demoActive = false

    private var lastTick = AppClock.now()
    private var drivingAcc = 0.0
    private var coffeeOffered = false
    private var evOffered = false
    private var chargeAlerted = false

    init {
        vehicle.start(); navi.start(); location.start(); voice.start()
        runCatching { server.start() }
        voice.onHeard = { onVoice(it) }
        _ui.value = _ui.value.copy(
            favorites = prefs.getString("favorites", null)?.split("|")?.filter { it.isNotBlank() }
                ?: listOf("소떡소떡", "아이스 아메리카노"),
            card = payment.card,
        )
        OrderStore.seedSamples(RestStops.DEOKPYEONG)

        viewModelScope.launch { vehicle.status.collect { v -> _ui.value = _ui.value.copy(vehicle = v) } }
        viewModelScope.launch { navi.connected.collect { c -> _ui.value = _ui.value.copy(naviConnected = c) } }
        viewModelScope.launch { voice.state.collect { s -> _ui.value = _ui.value.copy(voice = s) } }
        viewModelScope.launch { voice.heard.collect { s -> _ui.value = _ui.value.copy(heard = s) } }
        viewModelScope.launch { voice.lastSpoken.collect { s -> _ui.value = _ui.value.copy(spoken = s) } }
        viewModelScope.launch { OrderStore.orders.collect { list -> syncOrder(list) } }
        viewModelScope.launch {
            navi.events.collect { e ->
                when (e) {
                    is NaviBridge.Event.WaypointArrived -> onArrived()
                    NaviBridge.Event.Rerouted -> Unit
                    is NaviBridge.Event.Error -> Unit
                }
            }
        }
        viewModelScope.launch { location.entered.collect { onArrived() } }
        viewModelScope.launch { while (isActive) { tick(); delay(250) } }
    }

    /** 내비 경유지 요청 뒤 내비 화면이 앞으로 나오면, 픽스탑 화면으로 다시 돌아온다(주행 중 화면 전환 최소화). */
    private fun returnToApp() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            delay(900)
            runCatching {
                ctx.startActivity(android.content.Intent(ctx, com.zeroinjury.pickstop.MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
        }
    }

    private fun syncOrder(list: List<Order>) {
        val cur = _ui.value.order ?: return
        val fresh = list.firstOrNull { it.no == cur.no } ?: return
        _ui.value = _ui.value.copy(order = fresh)
    }

    // =========================================================
    // 시간 흐름
    // =========================================================

    private fun tick() {
        val now = AppClock.now()
        val dt = (now - lastTick).coerceIn(0, 60_000) / 1000.0
        lastTick = now
        val s = _ui.value

        // 1) 주행 시간 누적, 시연 주행
        val driving = s.vehicle.isDriving
        if (driving) drivingAcc += dt
        val drivingSec = drivingAcc.toLong()
        if (demoActive && driving) {
            if (demoCongestionSec > 0) {
                // 정체: 그 시간만큼 차가 거의 서 있는다
                demoCongestionSec = (demoCongestionSec - dt).coerceAtLeast(0.0)
            } else {
                val mps = 98 / 3.6
                demoDistance.keys.toList().forEach { k -> demoDistance[k] = (demoDistance.getValue(k) - mps * dt) }
            }
            val target = s.order?.takeIf { !it.arrived }?.stop?.id
            if (target != null && demoDistance.getValue(target) <= 0.0) onArrived()
        }

        // 2) 경로상 휴게소 도착 예정
        val naviEta = navi.eta.value
        val stops = RestStops.onRoute.mapNotNull { st ->
            val d = demoDistance.getValue(st.id)
            if (d < -200 && st.id != s.order?.stop?.id) return@mapNotNull null
            val baseSec = (d.coerceAtLeast(0.0) / (98 / 3.6)).roundToInt()
            val etaSec = if (!demoActive && naviEta != null && naviEta.stopName == st.name) naviEta.remainSec
            else baseSec + demoCongestionSec.roundToInt()
            StopEta(st, etaSec, (d / 1000).roundToInt().coerceAtLeast(0))
        }
        val source = if (!demoActive && naviEta != null) naviEta.source else if (demoActive) "시연 주행" else "내비 경로 대기"

        _ui.value = s.copy(stops = stops, drivingSec = drivingSec, clock = now, etaSource = source)

        // 3) 주문 진행 (매장 화면의 다른 주문도 함께)
        s.order?.let { advanceOrder(it, stops, now) }
        OrderStore.advanceSamples(now)

        // 4) 먼저 말 거는 제안: 2시간 연속 운전 → 커피, 배터리 부족 → 충전 맞춤 식사
        if (driving && s.order == null && s.pending == null) {
            if (!coffeeOffered && drivingSec >= 2 * 3600) { coffeeOffered = true; offerCoffee() }
            val b = s.vehicle.batteryPct
            if (!evOffered && b != null && b <= 35) { evOffered = true; offerEvMeal(b) }
        }

        // 5) 충전 완료 5분 전 알림
        val endAt = _ui.value.chargeEndAt
        if (endAt != null && !chargeAlerted && endAt - now <= 5 * 60_000L) {
            chargeAlerted = true
            say("충전이 5분 뒤에 끝나요. 식사 마치고 바로 출발하시면 돼요.", "vo_charge_done")
        }
    }

    private fun advanceOrder(o: Order, stops: List<StopEta>, now: Long) {
        if (o.status == OrderStatus.CANCELLED || o.status == OrderStatus.PICKED_UP) return
        if (!o.arrived) {
            val eta = stops.firstOrNull { it.stop.id == o.stop.id }?.etaSec
            if (eta != null) {
                val newArrival = now + eta * 1000L
                val delayMin = ((newArrival - o.firstArrivalAt) / 60_000.0).roundToInt().coerceAtLeast(0)
                if (kotlin.math.abs(newArrival - o.arrivalAt) >= 30_000) {
                    val lateMin = ((newArrival - o.announcedArrivalAt) / 60_000.0).roundToInt()
                    val announce = lateMin >= 3 && o.status == OrderStatus.SCHEDULED
                    OrderStore.update(o.no) {
                        it.copy(arrivalAt = newArrival, delayMin = delayMin,
                            announcedArrivalAt = if (announce || newArrival < it.announcedArrivalAt) newArrival else it.announcedArrivalAt)
                    }
                    if (announce) {
                        val start = OrderStore.find(o.no)!!.cookStartAt
                        notice("앞쪽 정체로 도착이 ${lateMin}분 늦어져요", "매장에 새 도착 시간을 알리고, 조리 시작을 ${AppClock.hhmm(start)}로 미뤘어요")
                        say("앞쪽 정체로 도착이 ${lateMin}분 늦어져요. 매장에 알리고 조리 시작을 미뤘어요.", "vo_delay")
                    }
                }
            }
        }
        val cur = OrderStore.find(o.no) ?: return
        when (cur.status) {
            OrderStatus.SCHEDULED -> if (now >= cur.cookStartAt) {
                OrderStore.update(o.no) { it.copy(status = OrderStatus.COOK_NOW) }
            }
            OrderStatus.COOK_NOW -> if (OrderStore.storeAutoAccept && now >= cur.cookStartAt + 20_000) OrderStore.storeAction(o.no, "start")
            OrderStatus.COOKING -> if (OrderStore.storeAutoAccept && now >= (cur.cookStartedAt ?: now) + cur.cookSec * 1000L) OrderStore.storeAction(o.no, "ready")
            else -> Unit
        }
    }

    // =========================================================
    // 음성
    // =========================================================

    fun listen() = voice.listen()

    private fun onVoice(raw: String) {
        android.util.Log.i("PickStop/Voice", "heard: $raw (pending=${_ui.value.pending != null})")
        val t = raw.replace(" ", "")
        val s = _ui.value
        val yes = listOf("응", "네", "예", "좋아", "주문해", "그래", "오케이").any { t.startsWith(it) || t == it }
        val no = listOf("아니", "아뇨", "됐어", "괜찮아").any { t.startsWith(it) }
        when {
            s.pending != null && yes -> confirmPending()
            s.pending != null && no -> dismissPending()
            "취소" in t -> cancelOrder()
            "커피" in t -> offerCoffee()
            "충전" in t -> s.vehicle.batteryPct?.let { offerEvMeal(it) } ?: say("배터리 정보를 받을 수 없어요.")
            listOf("늘먹던", "먹던거", "다음휴게소", "주문해줘", "주문").any { it in t } -> offerUsual()
            else -> say("다음 휴게소에서 늘 먹던 거, 라고 말씀해 주세요.", "vo_help")
        }
    }

    /** clip: res/raw 에 녹음해 둔 음성(운전자 목소리). 없으면 TTS 로 말한다. */
    private fun say(text: String, clip: String? = null) = voice.speak(text, clip)

    private fun stopKey(s: RestStop) = when (s.id) { "deokpyeong" -> "dp"; "yeoju" -> "yj"; else -> "mm" }

    private fun notice(title: String, body: String) {
        _ui.value = _ui.value.copy(notice = "$title|$body")
    }

    // =========================================================
    // 주문 흐름
    // =========================================================

    /** "다음 휴게소에서 늘 먹던 거" */
    fun offerUsual() {
        val s = _ui.value
        if (s.order != null && s.order.status !in listOf(OrderStatus.PICKED_UP, OrderStatus.CANCELLED)) {
            say("이미 ${s.order.stop.name}에 주문해 두었어요.", "vo_already"); return
        }
        val next = reachable(6 * 60) ?: run { say("경로에서 다음 휴게소를 찾지 못했어요."); return }
        val cart = OrderStore.cart.value
        val (lines, reason) = if (cart.isNotEmpty())
            cart.mapNotNull { c -> next.stop.menu.firstOrNull { it.name == c.item.name }?.let { OrderLine(it, 1, c.who) } } to Pending.Reason.CART
        else RestStops.matchFavorites(next.stop, s.favorites).map { OrderLine(it) } to Pending.Reason.FAVORITE
        if (lines.isEmpty()) { say("저장된 단골 메뉴가 없어요. 정차 중에 메뉴를 담아 주세요."); return }
        propose(Pending(next.stop, lines, reason, next.etaSec))
    }

    private fun offerCoffee() {
        val next = reachable(2 * 60) ?: return
        val coffee = next.stop.menu.firstOrNull { it.name == "아이스 아메리카노" } ?: return
        _ui.value = _ui.value.copy(pending = Pending(next.stop, listOf(OrderLine(coffee)), Pending.Reason.COFFEE_BREAK, next.etaSec))
        say("2시간째 운전 중이에요. ${next.stop.name}에 커피 주문해 둘까요? 도착하면 바로 받아 가실 수 있어요.", "vo_coffee")
    }

    private fun offerEvMeal(battery: Int) {
        val s = _ui.value
        val st = s.stops.firstOrNull { it.stop.evCharger && it.etaSec >= 11 * 60 } ?: return
        val socAtArrival = navi.eta.value?.socAtArrival ?: (battery - 3).coerceAtLeast(5)
        // 내비 충전 계획이 있으면 그 시간을, 없으면 80%까지 1%당 0.5분으로 어림한다.
        val chargeMin = navi.eta.value?.chargingMin ?: ((80 - socAtArrival).coerceAtLeast(10) * 0.5).roundToInt()
        val fits = st.stop.menu.filter { it.cookSec / 60 <= 5 + 10 && it.eatMin + 2 <= chargeMin }
            .sortedByDescending { it.eatMin }.take(1) + st.stop.menu.filter { it.name == "아이스 아메리카노" }
        if (fits.isEmpty()) return
        _ui.value = s.copy(pending = Pending(st.stop, fits.distinct().map { OrderLine(it) }, Pending.Reason.EV_CHARGE, st.etaSec, chargeMin))
        say("배터리가 ${battery}%예요. 충전기가 있는 ${st.stop.name}에서 충전에 ${chargeMin}분쯤 걸려요. 그 안에 드실 수 있는 ${fits.first().name}으로 주문해 둘까요?", "vo_ev")
    }

    private fun propose(p: Pending) {
        _ui.value = _ui.value.copy(pending = p)
        val names = p.lines.groupBy { it.item.name }.keys.joinToString("과 ")
        val who = if (p.reason == Pending.Reason.CART && p.lines.size > 2) "${p.lines.map { it.who }.distinct().size}인 메뉴" else names
        val usual = who == "소떡소떡과 아이스 아메리카노" && p.total == 9500
        say("${p.stop.name}까지 ${(p.etaSec + 59) / 60}분, $who ${"%,d".format(p.total)}원 주문할까요?",
            if (usual) "vo_confirm_${stopKey(p.stop)}" else null)
    }

    fun confirmPending() {
        val p = _ui.value.pending ?: return
        if (payment.card == null) {
            _ui.value = _ui.value.copy(pending = null)
            say("등록된 결제 수단이 없어요. 정차 중에 결제 카드를 한 번만 등록해 주세요.", "vo_no_card"); return
        }
        val approval = payment.approve(p.total).getOrElse { say("결제에 실패했어요. 다시 시도해 주세요."); return }
        val now = AppClock.now()
        val etaSec = _ui.value.stops.firstOrNull { it.stop.id == p.stop.id }?.etaSec ?: p.etaSec
        val order = Order(
            no = OrderStore.newOrderNo(), stop = p.stop, lines = p.lines, total = p.total, approvalId = approval,
            createdAt = now, firstArrivalAt = now + etaSec * 1000L, arrivalAt = now + etaSec * 1000L, cookSec = p.cookSec,
        )
        OrderStore.put(order)
        OrderStore.clearCart()
        server.storeStopId = p.stop.id
        navi.addRestStopAsWaypoint(p.stop)
        returnToApp()
        location.watchArrival(p.stop)
        val chargeEnd = p.chargeMin?.let { order.firstArrivalAt + it * 60_000L }
        chargeAlerted = false
        _ui.value = _ui.value.copy(pending = null, order = order, manualScreen = null, notice = null, chargeEndAt = chargeEnd)
        say("주문했어요. 도착 2분 전에 완성되도록 조리 시작을 맞춰 둘게요. ${p.stop.name}을 경유지로 넣었어요.", "vo_ordered")
    }

    /** 먼저 건넨 제안을 거절하면, 같은 주행 중에는 다시 조르지 않는다. */
    fun dismissPending() {
        coffeeOffered = true; evOffered = true
        _ui.value = _ui.value.copy(pending = null)
        say("알겠어요. 주문하지 않을게요.", "vo_decline")
    }

    /** 조리 시간 + 3분보다 가까운 휴게소는 건너뛴다 (도착 전에 완성할 수 없으니까). */
    private fun reachable(cookSec: Int): StopEta? = _ui.value.stops.firstOrNull { it.etaSec >= cookSec + 180 }

    fun cancelOrder() {
        val o = _ui.value.order ?: run { say("취소할 주문이 없어요."); return }
        if (!o.freeCancel) {
            say("이미 조리를 시작해서 취소할 수 없어요. 도착하면 ${o.stop.counter}에서 받아 가세요.", "vo_cancel_late"); return
        }
        payment.cancel(o.approvalId)
        OrderStore.update(o.no) { it.copy(status = OrderStatus.CANCELLED) }
        navi.removeRestStopWaypoint()
        returnToApp()
        location.clearArrival()
        _ui.value = _ui.value.copy(order = null, notice = null, chargeEndAt = null)
        say("주문을 취소했어요. 결제 금액은 전액 환불돼요.", "vo_cancel")
    }

    private fun onArrived() {
        val o = _ui.value.order ?: return
        if (o.arrived || o.status == OrderStatus.CANCELLED) return
        OrderStore.update(o.no) { it.copy(arrived = true) }
        if (demoActive) vehicle.simulateGear(VehicleBridge.Gear.PARK)
        val ready = OrderStore.find(o.no)?.status == OrderStatus.READY
        say(if (ready) "${o.stop.name}에 도착했어요. 픽업 번호 ${o.no}번, ${o.stop.counter}입니다."
            else "${o.stop.name}에 도착했어요. 픽업 번호 ${o.no}번, 곧 완성돼요. ${o.stop.counter}입니다.",
            if (ready) "vo_arrived_${stopKey(o.stop)}" else "vo_arrived_soon_${stopKey(o.stop)}")
    }

    fun pickedUp() {
        val o = _ui.value.order ?: return
        OrderStore.storeAction(o.no, "picked")
        navi.removeRestStopWaypoint()
        returnToApp()
        location.clearArrival()
        val saved = _ui.value.savedMinutesMonth + 18
        _ui.value = _ui.value.copy(order = null, savedMinutesMonth = saved)
    }

    // =========================================================
    // 정차 중 화면
    // =========================================================

    fun open(screen: Screen?) { _ui.value = _ui.value.copy(manualScreen = screen) }

    fun addToCart(item: MenuItem) = OrderStore.addToCart(CartLine(item, "운전자"))
    fun removeFromCart(i: Int) = OrderStore.removeFromCart(i)

    fun saveFavoritesFromCart() {
        val names = OrderStore.cart.value.filter { it.who == "운전자" }.map { it.item.name }.distinct()
            .ifEmpty { OrderStore.cart.value.map { it.item.name }.distinct() }
        if (names.isEmpty()) return
        prefs.edit().putString("favorites", names.joinToString("|")).apply()
        _ui.value = _ui.value.copy(favorites = names)
        say("늘 먹던 조합으로 저장했어요. 주행 중에는 다음 휴게소에서 늘 먹던 거, 라고만 말씀하세요.", "vo_saved")
    }

    fun registerCard() {
        val c = payment.registerTestCard()
        _ui.value = _ui.value.copy(card = c)
    }

    fun removeCard() { payment.removeCard(); _ui.value = _ui.value.copy(card = null) }

    val joinUrl: String get() = OrderServer.lanUrl("/join")

    // =========================================================
    // 시연 패널
    // =========================================================

    fun demoStartDrive(speed: Int = 20) {
        demoActive = true
        demoDistance.putAll(mapOf("deokpyeong" to 39_200.0, "yeoju" to 83_300.0, "munmak" to 117_600.0))
        demoCongestionSec = 0.0
        coffeeOffered = false; evOffered = false
        AppClock.setSpeed(speed, AppClock.todayAt(12, 24))
        lastTick = AppClock.now()
        OrderStore.resetSamples(RestStops.DEOKPYEONG)
        vehicle.simulateGear(VehicleBridge.Gear.DRIVE)
        drivingAcc = 98 * 60.0
        _ui.value = _ui.value.copy(demo = true, drivingSec = 98 * 60L)
    }

    fun demoCongestion(min: Int = 6) { demoCongestionSec += min * 60.0 }

    fun demoLongDrive() { coffeeOffered = false; drivingAcc = 2 * 3600.0 - 20 }

    fun demoBattery(pct: Int) { evOffered = false; vehicle.simulateBattery(pct) }

    fun demoGear(g: VehicleBridge.Gear) = vehicle.simulateGear(g)

    fun demoArrive() { demoDistance[_ui.value.order?.stop?.id ?: return] = 0.0; onArrived() }

    fun demoSay(text: String) = voice.inject(text)

    fun demoReset() {
        demoActive = false
        demoDistance.putAll(mapOf("deokpyeong" to 39_200.0, "yeoju" to 83_300.0, "munmak" to 117_600.0))
        demoCongestionSec = 0.0
        coffeeOffered = false; evOffered = false; chargeAlerted = false
        AppClock.setSpeed(1)
        lastTick = AppClock.now()
        OrderStore.resetSamples(RestStops.DEOKPYEONG)
        vehicle.simulateGear(VehicleBridge.Gear.PARK); vehicle.simulateBattery(null)
        _ui.value.order?.let { o -> if (o.status != OrderStatus.PICKED_UP) OrderStore.update(o.no) { it.copy(status = OrderStatus.CANCELLED) } }
        navi.removeRestStopWaypoint(); location.clearArrival(); returnToApp()
        drivingAcc = 0.0
        _ui.value = _ui.value.copy(order = null, pending = null, notice = null, demo = false, drivingSec = 0, chargeEndAt = null)
    }

    fun setStoreAuto(on: Boolean) { OrderStore.storeAutoAccept = on }

    override fun onCleared() {
        runCatching { server.stop() }
        vehicle.release(); navi.release(); location.release(); voice.release()
        super.onCleared()
    }
}
