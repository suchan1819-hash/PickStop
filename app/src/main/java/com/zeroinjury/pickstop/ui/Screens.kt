package com.zeroinjury.pickstop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroinjury.pickstop.data.Category
import com.zeroinjury.pickstop.data.RestStops
import com.zeroinjury.pickstop.order.AppClock
import com.zeroinjury.pickstop.order.Order
import com.zeroinjury.pickstop.order.OrderStatus
import com.zeroinjury.pickstop.order.OrderStore
import com.zeroinjury.pickstop.order.Pending
import com.zeroinjury.pickstop.order.PickStopViewModel
import com.zeroinjury.pickstop.order.Screen
import com.zeroinjury.pickstop.order.UiState
import com.zeroinjury.pickstop.vehicle.VehicleBridge
import com.zeroinjury.pickstop.voice.VoiceBridge

@Composable
fun PickStopApp(vm: PickStopViewModel) {
    val s by vm.ui.collectAsState()
    var demoOpen by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(PS.Bg1, PS.Bg0))
        )
    ) {
        // 따뜻한 조명
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(listOf(PS.Orange.copy(alpha = 0.13f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 1.1f), radius = size.width * 0.75f))
        })
        Header(s, vm, onLongPressLogo = { demoOpen = !demoOpen })
        Crossfade(targetState = s.screen, label = "screen") { sc ->
            Box(Modifier.fillMaxSize()) {
                when (sc) {
                    Screen.HOME -> HomeScreen(s, vm)
                    Screen.CONFIRM -> ConfirmScreen(s, vm)
                    Screen.TIMELINE -> TimelineScreen(s, vm)
                    Screen.PICKUP -> PickupScreen(s, vm)
                    Screen.MENU -> MenuScreen(s, vm)
                    Screen.PAYMENT -> PaymentScreen(s, vm)
                }
            }
        }
        VoiceCaption(s)
        AnimatedVisibility(demoOpen, enter = fadeIn(), exit = fadeOut()) { DemoPanel(s, vm) { demoOpen = false } }
    }
}

// =====================================================================
// 공통 머리글
// =====================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Header(s: UiState, vm: PickStopViewModel, onLongPressLogo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 48.dp, end = 48.dp, top = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        AppLogo(modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPressLogo))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(if (s.screen == Screen.MENU) "픽스탑 · ${s.next?.stop?.name ?: RestStops.DEOKPYEONG.name}" else "픽스탑", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("도착 시간에 맞춰 준비되는 휴게소 주문", fontSize = 13.sp, color = PS.Sub)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.demo) Pill("시연 배속 ×${AppClock.speed}", PS.Red)
            val o = s.order
            when {
                o != null && !o.arrived -> { Pill("주문 #${o.no} · 결제 완료", PS.Amber); Pill("${o.stop.name} 경유지 추가됨") }
                o != null -> Pill("${o.stop.name} 도착 · P", PS.Mint)
                s.vehicle.isDriving -> { Pill("주행 중 · 음성 모드", PS.Mint); s.next?.let { Pill("${it.stop.route} · ${it.stop.direction}") } }
                else -> {
                    Pill("정차 중(P) · 메뉴 선택 가능", PS.Amber)
                    Box(Modifier.clickable { vm.open(if (s.screen == Screen.PAYMENT) Screen.MENU else Screen.PAYMENT) }) {
                        Pill(if (s.screen == Screen.PAYMENT) "메뉴로" else (s.card?.label ?: "결제 수단 등록"))
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceCaption(s: UiState) {
    val text = when (s.voice) {
        VoiceBridge.State.LISTENING -> "듣고 있어요… ${s.heard}"
        else -> null
    } ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(text, fontSize = 20.sp, color = PS.Text, modifier = Modifier.padding(bottom = 12.dp)
            .glass(RoundedCornerShape(99.dp)).padding(horizontal = 24.dp, vertical = 10.dp))
    }
}

// =====================================================================
// 1. 주행 중 홈
// =====================================================================

@Composable
private fun HomeScreen(s: UiState, vm: PickStopViewModel) {
    val next = s.next
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 48.dp, top = 150.dp).width(600.dp)) {
            Eyebrow("경로상 다음 휴게소")
            Spacer(Modifier.height(14.dp))
            Text(next?.stop?.name ?: "경로를 기다리는 중", fontSize = 46.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp)
            Spacer(Modifier.height(26.dp))
            if (next != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${mins(next.etaSec)}", fontSize = 120.sp, fontWeight = FontWeight.Bold, letterSpacing = (-4).sp, style = Tnum, lineHeight = 120.sp)
                    Text("분 뒤 도착", fontSize = 36.sp, color = PS.Sub, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, bottom = 18.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text("${next.km}km · ${AppClock.hhmm(s.clock + next.etaSec * 1000L)} 도착 예정 · ${next.stop.features}", fontSize = 18.sp, color = PS.Sub)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    s.favorites.forEach { Chip(it) }
                    val total = RestStops.matchFavorites(next.stop, s.favorites).sumOf { it.price }
                    if (total > 0) Chip("늘 먹던 조합 · ${won(total)}", PS.Sub)
                }
            }
        }
        // 이후 휴게소 + 연속 운전
        Column(Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 150.dp).width(500.dp).height(520.dp).glass().padding(36.dp)) {
            Text("이후 휴게소", fontSize = 16.sp, color = PS.Sub)
            Spacer(Modifier.height(10.dp))
            s.stops.take(3).forEachIndexed { i, st ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(st.stop.name.removeSuffix("휴게소"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(st.stop.features, fontSize = 16.sp, color = PS.Sub)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(durText(st.etaSec.toLong()), fontSize = 30.sp, fontWeight = FontWeight.Bold, style = Tnum,
                        color = if (i == 0) PS.Amber else PS.Sub)
                }
            }
            Spacer(Modifier.weight(1f))
            Divider()
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("연속 운전", fontSize = 16.sp, color = PS.Sub)
                Spacer(Modifier.weight(1f))
                Text(durText(s.drivingSec, ceil = false), fontSize = 20.sp, fontWeight = FontWeight.Bold, style = Tnum)
                Text(" / 2시간", fontSize = 20.sp, color = PS.Faint)
            }
            Spacer(Modifier.height(12.dp))
            Progress((s.drivingSec / 7200f).coerceIn(0f, 1f))
            Spacer(Modifier.height(10.dp))
            Text("2시간이 되면 휴식 겸 커피를 먼저 제안해요", fontSize = 15.sp, color = PS.Faint)
        }
        VoiceBar(s, vm, Modifier.align(Alignment.BottomCenter).padding(start = 48.dp, end = 48.dp, bottom = 60.dp))
    }
}

@Composable
private fun Chip(text: String, color: Color = PS.Text) {
    Text(text, fontSize = 18.sp, color = color, modifier = Modifier.clip(RoundedCornerShape(16.dp))
        .background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 18.dp, vertical = 12.dp))
}

@Composable
private fun Progress(f: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(PS.Warm))
    }
}

@Composable
private fun VoiceBar(s: UiState, vm: PickStopViewModel, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(30.dp))
            .background(Brush.horizontalGradient(listOf(PS.Amber.copy(alpha = 0.12f), PS.Amber.copy(alpha = 0.03f))))
            .border(1.dp, PS.Amber.copy(alpha = 0.25f), RoundedCornerShape(30.dp))
            .clickable { vm.listen() }.padding(horizontal = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(PS.Amber.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(74.dp).clip(CircleShape).background(PS.Warm), contentAlignment = Alignment.Center) { IconMic(32.dp, PS.Ink) }
        }
        Spacer(Modifier.width(20.dp))
        Column {
            Text(if (s.voice == VoiceBridge.State.LISTENING) "듣고 있어요" else "이렇게 말해 보세요", fontSize = 17.sp, color = PS.Sub)
            Row {
                Text("\"", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("다음 휴게소", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = PS.Amber)
                Text("에서 늘 먹던 거\"", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// =====================================================================
// 2. 음성 확인
// =====================================================================

@Composable
private fun ConfirmScreen(s: UiState, vm: PickStopViewModel) {
    val p = s.pending ?: return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 160.dp).width(880.dp).glass().padding(horizontal = 56.dp, vertical = 48.dp)) {
            VoiceBars(s.voice != VoiceBridge.State.IDLE)
            Spacer(Modifier.height(26.dp))
            Text(
                when (p.reason) {
                    Pending.Reason.FAVORITE, Pending.Reason.CART -> "\"${s.heard.ifBlank { "다음 휴게소에서 늘 먹던 거" }}\""
                    Pending.Reason.COFFEE_BREAK -> "연속 운전 2시간 · 먼저 제안"
                    Pending.Reason.EV_CHARGE -> "배터리 ${s.vehicle.batteryPct ?: 0}% · 충전 맞춤 식사"
                }, fontSize = 20.sp, color = PS.Sub,
            )
            Spacer(Modifier.height(12.dp))
            val names = p.lines.groupBy { it.item.name }.entries.joinToString(", ") { (n, l) -> if (l.size > 1) "$n ${l.size}" else n }
            Text("${p.stop.name}에서 $names, ${won(p.total)} 주문할까요?", fontSize = 40.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp)
            Spacer(Modifier.height(24.dp))
            KeyValue("도착 예정", "${mins(p.etaSec)}분 뒤 · ${AppClock.hhmm(s.clock + p.etaSec * 1000L)}")
            Divider()
            KeyValue("결제", s.card?.label ?: "결제 수단 없음 (정차 중 등록)", if (s.card == null) PS.Red else PS.Text)
            Divider()
            if (p.chargeMin != null) { KeyValue("충전 예상", "${p.chargeMin}분 · 식사 ${p.lines.maxOf { it.item.eatMin }}분"); Divider() }
            KeyValue("무료 취소", "조리 시작 전까지 (${mins(p.freeCancelSec)}분 남음)")
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                PrimaryButton("응, 주문해 줘", Modifier.weight(1f)) { vm.confirmPending() }
                SecondaryButton("아니", Modifier.weight(1f)) { vm.dismissPending() }
            }
        }
    }
}

// =====================================================================
// 3. 도착 맞춤 조리
// =====================================================================

@Composable
private fun TimelineScreen(s: UiState, vm: PickStopViewModel) {
    val o = s.order ?: return
    val now = s.clock
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 48.dp, top = 150.dp)) {
            Eyebrow("도착 맞춤 조리", dot = null)
            Spacer(Modifier.height(12.dp))
            Text(
                when (o.status) {
                    OrderStatus.SCHEDULED -> "도착 2분 전에 완성되도록\n조리 시작을 맞춰 둘게요"
                    OrderStatus.COOK_NOW, OrderStatus.COOKING -> "지금 조리 중이에요\n도착 2분 전에 완성돼요"
                    OrderStatus.READY -> "음식이 완성됐어요\n도착하면 바로 받아 가세요"
                    else -> o.status.label
                }, fontSize = 46.sp, fontWeight = FontWeight.Bold, lineHeight = 56.sp, letterSpacing = (-1.2).sp,
            )
        }
        TimelineCard(o, now, Modifier.padding(start = 48.dp, end = 48.dp, top = 330.dp))
        // 하단: 정체 알림 또는 취소 안내
        val n = s.notice?.split("|")
        Row(
            Modifier.align(Alignment.BottomCenter).padding(start = 48.dp, end = 48.dp, bottom = 56.dp).fillMaxWidth().height(120.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(if (n != null) PS.Red.copy(alpha = 0.08f) else PS.Glass2)
                .border(1.dp, if (n != null) PS.Red.copy(alpha = 0.25f) else PS.Stroke, RoundedCornerShape(26.dp))
                .padding(horizontal = 34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconClock(40.dp, if (n != null) PS.Red else PS.Amber)
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                if (n != null) {
                    Text(n[0], fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(n.getOrElse(1) { "" }, fontSize = 18.sp, color = PS.Sub)
                } else if (o.freeCancel) {
                    Text("조리 시작 전까지 무료 취소", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("\"취소\"라고 말하면 결제 금액이 전액 환불돼요 · ${AppClock.hhmm(o.cookStartAt)}까지", fontSize = 18.sp, color = PS.Sub)
                } else {
                    Text("매장에서 조리를 시작했어요", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("${o.stop.counter}에서 픽업 번호 ${o.no}번으로 받아요", fontSize = 18.sp, color = PS.Sub)
                }
            }
            if (o.freeCancel) SecondaryButton("취소", Modifier.width(140.dp), height = 64.dp) { vm.cancelOrder() }
        }
    }
}

@Composable
private fun TimelineCard(o: Order, now: Long, modifier: Modifier) {
    val t0 = o.createdAt
    val t1 = o.arrivalAt.coerceAtLeast(t0 + 60_000)
    fun pos(t: Long) = ((t - t0).toFloat() / (t1 - t0)).coerceIn(0f, 1f)
    val start = o.cookStartAt
    val done = start + o.cookSec * 1000L
    val nowF = pos(now)
    Box(modifier.fillMaxWidth().height(250.dp).glass()) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 90.dp)) {
            val w = maxWidth
            // 선
            Box(Modifier.offset(y = 118.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.08f))) {
                Box(Modifier.fillMaxWidth(nowF).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(PS.Warm))
            }
            // 라벨이 겹치지 않도록, 도착부터 왼쪽으로 최소 간격을 둔다
            val raw = floatArrayOf(0f, pos(start), pos(done), 1f)
            val gap = 0.16f
            val lab = raw.copyOf()
            for (i in lab.size - 2 downTo 1) if (lab[i + 1] - lab[i] < gap) lab[i] = lab[i + 1] - gap
            for (i in 1 until lab.size - 1) lab[i] = maxOf(lab[i], lab[i - 1] + gap)
            @Composable fun node(idx: Int, label: String, time: String, on: Boolean, accent: Boolean = false) {
                val f = raw[idx]
                val lf = lab[idx]
                Box(Modifier.offset(x = w * f - 26.dp, y = 96.dp).size(52.dp).clip(CircleShape)
                    .background(if (on) PS.Warm else Brush.linearGradient(listOf(Color(0xFF20242D), Color(0xFF20242D))))
                    .border(if (on) 0.dp else 2.dp, Color.White.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                    if (on) IconCheck(22.dp, PS.Ink)
                }
                Column(Modifier.offset(x = w * lf - 100.dp, y = 170.dp).width(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (accent) PS.Amber else PS.Text)
                    Text(time, fontSize = 16.sp, color = PS.Sub, style = Tnum, textAlign = TextAlign.Center)
                }
            }
            node(0, "주문 완료", AppClock.hhmm(t0), true)
            node(1, "조리 시작", if (now < start) "${mins(((start - now) / 1000).toInt())}분 뒤 · ${AppClock.hhmm(start)}" else AppClock.hhmm(start), now >= start, accent = now < start)
            node(2, "완성", AppClock.hhmm(done), o.status == OrderStatus.READY)
            node(3, "도착", AppClock.hhmm(o.arrivalAt), o.arrived)
            // 지금 위치
            Box(Modifier.offset(x = w * nowF - 26.dp, y = 96.dp).size(52.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                IconCar(24.dp, Color(0xFF111111))
            }
            Column(Modifier.offset(x = w * nowF - 100.dp, y = 20.dp).width(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("지금", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("도착까지 ${mins(((o.arrivalAt - now) / 1000).toInt())}분", fontSize = 16.sp, color = PS.Sub)
            }
        }
    }
}

// =====================================================================
// 4. 픽업
// =====================================================================

@Composable
private fun PickupScreen(s: UiState, vm: PickStopViewModel) {
    val o = s.order ?: return
    val ready = o.status == OrderStatus.READY
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 48.dp, top = 150.dp).width(640.dp).height(620.dp).glass().padding(52.dp)) {
            Eyebrow(if (ready) "음식이 준비됐어요" else "곧 완성돼요", if (ready) PS.Mint else PS.Amber)
            Spacer(Modifier.height(22.dp))
            Text("픽업 번호", fontSize = 22.sp, color = PS.Sub)
            Text("${o.no}", fontSize = 250.sp, fontWeight = FontWeight.Bold, letterSpacing = (-12).sp, lineHeight = 240.sp, style = Tnum,
                color = Color(0xFFFFF1DF))
            Spacer(Modifier.weight(1f))
            Row {
                Text(o.stop.counter.substringBefore(" "), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(" " + o.stop.counter.substringAfter(" "), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = PS.Amber)
            }
            Text(if (ready) "방금 완성됐어요 · 줄 서지 말고 바로 받아 가세요" else "조리 중 · 완성되면 바로 받아 가세요", fontSize = 18.sp, color = PS.Sub)
        }
        Column(Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 150.dp).width(470.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoRow({ IconList(28.dp, PS.Amber) }, "${o.lines.size}개 메뉴 · ${won(o.total)}", o.summary)
            InfoRow({ IconArrowUp(28.dp, PS.Amber) }, "입구에서 오른쪽 45m", "푸드코트 B동")
            val endAt = s.chargeEndAt
            if (endAt != null) InfoRow({ IconBolt(28.dp, PS.Amber) }, "충전 완료까지 ${mins(((endAt - s.clock) / 1000).toInt())}분", "완료 5분 전에 알려 드려요")
            else InfoRow({ IconClock(28.dp, PS.Amber) }, "아낀 대기 시간 약 18분", "이번 달 누적 ${s.savedMinutesMonth}분")
            SecondaryButton("받았어요", Modifier.fillMaxWidth()) { vm.pickedUp() }
        }
    }
}

@Composable
private fun InfoRow(icon: @Composable () -> Unit, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().glass().padding(horizontal = 30.dp, vertical = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(PS.Amber.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(sub, fontSize = 16.sp, color = PS.Sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// =====================================================================
// 5. 정차 중 메뉴 · 우리 차 장바구니
// =====================================================================

@Composable
private fun MenuScreen(s: UiState, vm: PickStopViewModel) {
    val stop = s.next?.stop ?: RestStops.DEOKPYEONG
    var tab by remember { mutableStateOf<Category?>(null) }
    val cart by OrderStore.cart.collectAsState()
    Box(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 48.dp, top = 150.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TabChip("추천", tab == null) { tab = null }
            Category.entries.forEach { c -> TabChip(c.label, tab == c) { tab = c } }
        }
        val items = stop.menu.filter { tab == null || it.category == tab }
        Column(Modifier.padding(start = 48.dp, top = 224.dp).width(760.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    row.forEach { m ->
                        val inCart = cart.any { it.item.name == m.name }
                        Box(Modifier.width(241.dp).height(300.dp).glass(stroke = if (inCart) PS.Amber.copy(alpha = 0.6f) else PS.Stroke,
                            fill = if (inCart) PS.Amber.copy(alpha = 0.07f) else PS.Glass).clickable { vm.addToCart(m) }) {
                            Image(painterResource(m.photo), m.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp))
                            if (m.signature) Text("휴게소 대표", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PS.Amber,
                                modifier = Modifier.padding(16.dp).clip(RoundedCornerShape(9.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 6.dp))
                            Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, bottom = 22.dp, end = 84.dp)) {
                                Text(m.name, fontSize = if (m.name.length > 7) 19.sp else 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text("${won(m.price)} · ${mins(m.cookSec)}분", fontSize = 17.sp, color = PS.Sub)
                            }
                            Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 22.dp).size(46.dp).clip(RoundedCornerShape(14.dp))
                                .background(if (inCart) PS.Warm else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))),
                                contentAlignment = Alignment.Center) {
                                if (inCart) IconCheck(22.dp, PS.Ink) else Text("+", fontSize = 26.sp)
                            }
                        }
                    }
                }
            }
        }
        // 장바구니
        Column(Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 150.dp).width(360.dp).height(700.dp).glass().padding(32.dp)) {
            Text("우리 차 장바구니", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("가족이 각자 휴대폰으로 담을 수 있어요", fontSize = 16.sp, color = PS.Sub)
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                QrCode(vm.joinUrl, 120.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("QR로 참여", fontSize = 16.sp, color = PS.Sub)
                    val others = cart.map { it.who }.filter { it != "운전자" }.distinct()
                    Text(if (others.isEmpty()) "주행 중에도 동승자만 담을 수 있어요" else "${others.joinToString(" · ")} 참여 중", fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                cart.forEachIndexed { i, c ->
                    Row(Modifier.fillMaxWidth().clickable { vm.removeFromCart(i) }) {
                        Text(if (c.who == "운전자") c.item.name else "${c.item.name} · ${c.who}", fontSize = 19.sp,
                            color = if (c.who == "운전자") PS.Text else PS.Sub, modifier = Modifier.weight(1f))
                        Text("%,d".format(c.item.price), fontSize = 19.sp, style = Tnum)
                    }
                }
                if (cart.isEmpty()) Text("메뉴를 눌러 담으세요.\n저장해 두면 주행 중에는 음성 한마디로 주문돼요.", fontSize = 16.sp, color = PS.Faint, lineHeight = 24.sp)
            }
            Row { Text("합계", fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
                Text(won(cart.sumOf { it.item.price }), fontSize = 22.sp, fontWeight = FontWeight.Bold, style = Tnum) }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("늘 먹던 조합으로 저장", Modifier.fillMaxWidth(), height = 68.dp) { vm.saveFavoritesFromCart() }
        }
    }
}

@Composable
private fun TabChip(text: String, on: Boolean, onClick: () -> Unit) {
    Text(text, fontSize = 18.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) Color(0xFF111111) else PS.Sub,
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(if (on) PS.Text else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 13.dp))
}

// =====================================================================
// 6. 결제 수단 (정차 중 한 번만)
// =====================================================================

@Composable
private fun PaymentScreen(s: UiState, vm: PickStopViewModel) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.padding(top = 160.dp).width(880.dp).glass().padding(horizontal = 56.dp, vertical = 48.dp)) {
            Eyebrow("결제 수단 · 정차 중 한 번만 등록")
            Spacer(Modifier.height(14.dp))
            Text(if (s.card == null) "카드를 한 번만 등록하면\n주행 중에는 음성 확인만으로 결제돼요" else "${s.card.label} 등록됨",
                fontSize = 38.sp, fontWeight = FontWeight.Bold, lineHeight = 50.sp)
            Spacer(Modifier.height(24.dp))
            KeyValue("결제 방식", "결제대행사 간편결제(빌링키)")
            Divider()
            KeyValue("앱이 보관하는 정보", "빌링키만 · 카드 번호 저장 안 함")
            Divider()
            KeyValue("취소·환불", "조리 시작 전 전액 환불")
            Spacer(Modifier.height(16.dp))
            Text("공모전 시연용 테스트 결제예요. 실제 카드 정보는 입력하지 않아요.", fontSize = 16.sp, color = PS.Faint)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                if (s.card == null) PrimaryButton("테스트 카드 등록", Modifier.weight(1f)) { vm.registerCard() }
                else SecondaryButton("등록 해제", Modifier.weight(1f)) { vm.removeCard() }
                SecondaryButton("메뉴로", Modifier.weight(1f)) { vm.open(Screen.MENU) }
            }
        }
    }
}

// =====================================================================
// 시연 패널 (로고 길게 누르기)
// =====================================================================

@Composable
private fun DemoPanel(s: UiState, vm: PickStopViewModel, close: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = close), contentAlignment = Alignment.Center) {
        Column(Modifier.width(980.dp).glass(fill = Color(0xF21A1D25)).clickable {}.padding(36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("시연 패널", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(14.dp))
                Text("내비 ${if (s.naviConnected) "연결" else "미연결"} · 차량 ${if (s.vehicle.carConnected) "연결" else "미연결"} · 도착 시간 출처: ${s.etaSource}",
                    fontSize = 15.sp, color = PS.Sub)
            }
            Spacer(Modifier.height(20.dp))
            DemoRow("주행", listOf(
                "시연 주행 시작 ×20" to { vm.demoStartDrive(20) },
                "정체 +6분" to { vm.demoCongestion(6) },
                "도착 처리" to { vm.demoArrive() },
                "초기화" to { vm.demoReset() },
            ))
            DemoRow("차량", listOf(
                "기어 P" to { vm.demoGear(VehicleBridge.Gear.PARK) },
                "기어 D" to { vm.demoGear(VehicleBridge.Gear.DRIVE) },
                "연속 운전 2시간" to { vm.demoLongDrive() },
                "배터리 30%" to { vm.demoBattery(30) },
            ))
            DemoRow("음성 대신", listOf(
                "\"다음 휴게소에서 늘 먹던 거\"" to { vm.demoSay("다음 휴게소에서 늘 먹던 거") },
                "\"응\"" to { vm.demoSay("응") },
                "\"취소\"" to { vm.demoSay("취소") },
                "\"커피\"" to { vm.demoSay("다음 휴게소에 커피") },
            ))
            DemoRow("매장", listOf(
                (if (OrderStore.storeAutoAccept) "매장 자동 응답 끄기" else "매장 자동 응답 켜기") to { vm.setStoreAuto(!OrderStore.storeAutoAccept) },
                "닫기" to close,
            ))
        }
    }
}

@Composable
private fun DemoRow(title: String, buttons: List<Pair<String, () -> Unit>>) {
    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 17.sp, color = PS.Sub, modifier = Modifier.width(110.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            buttons.forEach { (label, action) ->
                Text(label, fontSize = 17.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, PS.Stroke, RoundedCornerShape(14.dp)).clickable { action() }.padding(horizontal = 18.dp, vertical = 14.dp))
            }
        }
    }
}
