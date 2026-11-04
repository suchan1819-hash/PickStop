package com.zeroinjury.pickstop.order

import android.content.Context
import android.util.Log
import com.zeroinjury.pickstop.data.RestStops
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 매장 화면(/store)과 동승자 장바구니(/join)를 띄우는 작은 웹 서버.
 * 시연에서는 차 안에서 돌고, 실제 서비스에서는 같은 API 를 클라우드 주문 서버가 제공한다.
 */
class OrderServer(context: Context, port: Int = PORT) : NanoHTTPD(port) {

    private val assets = context.applicationContext.assets
    /** 매장 화면이 보여 줄 휴게소 */
    @Volatile var storeStopId: String = RestStops.DEOKPYEONG.id

    override fun serve(session: IHTTPSession): Response = runCatching {
        val uri = session.uri
        when {
            uri == "/" -> redirect("/store")
            uri == "/store" -> asset("store.html")
            uri == "/join" -> asset("join.html")
            uri.startsWith("/img/") -> image(uri.removePrefix("/img/"))
            uri == "/api/store" -> json(storeJson())
            uri == "/api/menu" -> json(menuJson())
            uri == "/api/cart" && session.method == Method.GET -> json(cartJson())
            uri == "/api/cart" && session.method == Method.POST -> {
                val p = params(session)
                val stop = RestStops.byId(storeStopId) ?: RestStops.DEOKPYEONG
                val item = stop.menu.firstOrNull { it.id == p["item"] }
                if (item == null) bad() else {
                    OrderStore.addToCart(CartLine(item, p["who"]?.take(8)?.ifBlank { null } ?: "동승자"))
                    json(cartJson())
                }
            }
            uri.matches(Regex("/api/orders/\\d+/(start|ready|picked)")) && session.method == Method.POST -> {
                val parts = uri.split("/")
                val ok = OrderStore.storeAction(parts[3].toInt(), parts[4])
                json(JSONObject().put("ok", ok))
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    }.getOrElse {
        Log.w("PickStop/Server", "serve failed", it)
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "error")
    }

    private fun params(session: IHTTPSession): Map<String, String> {
        val files = HashMap<String, String>()
        runCatching { session.parseBody(files) }
        return session.parms ?: emptyMap()
    }

    private fun storeJson(): JSONObject {
        val now = AppClock.now()
        val stop = RestStops.byId(storeStopId) ?: RestStops.DEOKPYEONG
        val arr = JSONArray()
        OrderStore.orders.value
            .filter { it.stop.id == stop.id && it.status != OrderStatus.CANCELLED && it.status != OrderStatus.PICKED_UP }
            .sortedBy { it.cookStartAt }
            .forEach { o ->
                arr.put(JSONObject()
                    .put("no", o.no)
                    .put("summary", o.summary)
                    .put("status", o.status.name)
                    .put("arrivalAt", AppClock.hhmm(o.arrivalAt))
                    .put("firstArrivalAt", AppClock.hhmm(o.firstArrivalAt))
                    .put("delayMin", o.delayMin)
                    .put("arrived", o.arrived)
                    .put("toStartSec", ((o.cookStartAt - now) / 1000).coerceAtLeast(0))
                    .put("toArriveSec", ((o.arrivalAt - now) / 1000).coerceAtLeast(0))
                    .put("sample", o.sample))
            }
        return JSONObject()
            .put("stop", stop.name)
            .put("counter", stop.counter)
            .put("clock", AppClock.hhmm(now))
            .put("speed", AppClock.speed)
            .put("todayCount", 47 + OrderStore.orders.value.count { !it.sample })
            .put("orders", arr)
    }

    private fun menuJson(): JSONObject {
        val stop = RestStops.byId(storeStopId) ?: RestStops.DEOKPYEONG
        val arr = JSONArray()
        stop.menu.forEach { m ->
            arr.put(JSONObject().put("id", m.id).put("name", m.name).put("price", m.price)
                .put("cookMin", (m.cookSec + 59) / 60).put("img", "/img/${m.id}"))
        }
        return JSONObject().put("stop", stop.name).put("menu", arr)
    }

    private fun cartJson(): JSONObject {
        val arr = JSONArray()
        OrderStore.cart.value.forEach { arr.put(JSONObject().put("name", it.item.name).put("price", it.item.price).put("who", it.who)) }
        return JSONObject().put("lines", arr).put("total", OrderStore.cart.value.sumOf { it.item.price })
    }

    private var appContext = context.applicationContext
    private fun image(id: String): Response {
        val stop = RestStops.byId(storeStopId) ?: RestStops.DEOKPYEONG
        val item = stop.menu.firstOrNull { it.id == id } ?: return bad()
        val stream = appContext.resources.openRawResource(item.photo)
        return newChunkedResponse(Response.Status.OK, "image/webp", stream)
    }

    private fun asset(name: String): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", assets.open(name).bufferedReader().use { it.readText() })

    private fun json(o: JSONObject): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", o.toString()).apply {
            addHeader("Cache-Control", "no-store")
        }

    private fun redirect(to: String): Response =
        newFixedLengthResponse(Response.Status.REDIRECT, "text/plain", "").apply { addHeader("Location", to) }

    private fun bad(): Response = newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "bad request")

    companion object {
        const val PORT = 8090

        /** 동승자 휴대폰이 접속할 주소 (같은 차량 와이파이) */
        fun lanUrl(path: String): String {
            val ip = runCatching {
                NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }?.hostAddress
            }.getOrNull() ?: "127.0.0.1"
            return "http://$ip:$PORT$path"
        }
    }
}
