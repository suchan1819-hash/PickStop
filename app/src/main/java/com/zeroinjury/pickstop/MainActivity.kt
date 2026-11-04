package com.zeroinjury.pickstop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.zeroinjury.pickstop.data.RestStops
import com.zeroinjury.pickstop.order.PickStopViewModel
import com.zeroinjury.pickstop.order.Screen
import com.zeroinjury.pickstop.ui.PickStopApp
import com.zeroinjury.pickstop.ui.PickStopTheme
import com.zeroinjury.pickstop.vehicle.VehicleBridge

class MainActivity : ComponentActivity() {
    private val vm: PickStopViewModel by viewModels()

    /**
     * 시연·테스트용 명령 (adb shell am broadcast -a com.zeroinjury.pickstop.DEMO --es cmd start).
     * 앱 안의 시연 패널(로고 길게 누르기)과 같은 동작을 한다.
     */
    private val demoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (val cmd = intent.getStringExtra("cmd") ?: return) {
                "start" -> vm.demoStartDrive(intent.getIntExtra("speed", 30))
                "congestion" -> vm.demoCongestion(intent.getIntExtra("min", 6))
                "arrive" -> vm.demoArrive()
                "reset" -> vm.demoReset()
                "gear_p" -> vm.demoGear(VehicleBridge.Gear.PARK)
                "gear_d" -> vm.demoGear(VehicleBridge.Gear.DRIVE)
                "long" -> vm.demoLongDrive()
                "battery" -> vm.demoBattery(intent.getIntExtra("pct", 30))
                "say_usual" -> vm.demoSay("다음 휴게소에서 늘 먹던 거")
                "say_yes" -> vm.demoSay("응")
                "say_no" -> vm.demoSay("아니")
                "say_cancel" -> vm.demoSay("취소")
                "say_coffee" -> vm.demoSay("다음 휴게소에 커피")
                "card" -> vm.registerCard()
                "uncard" -> vm.removeCard()
                "payment" -> vm.open(Screen.PAYMENT)
                "menu" -> vm.open(Screen.MENU)
                "save_fav" -> vm.saveFavoritesFromCart()
                "picked" -> vm.pickedUp()
                "store_auto_on" -> vm.setStoreAuto(true)
                "store_auto_off" -> vm.setStoreAuto(false)
                else -> if (cmd.startsWith("add_")) {
                    RestStops.DEOKPYEONG.menu.firstOrNull { it.id == cmd.removePrefix("add_") }?.let { vm.addToCart(it) }
                } else if (cmd.startsWith("guest_")) {
                    RestStops.DEOKPYEONG.menu.firstOrNull { it.id == cmd.removePrefix("guest_") }
                        ?.let { com.zeroinjury.pickstop.order.OrderStore.addToCart(com.zeroinjury.pickstop.order.CartLine(it, intent.getStringExtra("who") ?: "동승자")) }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FusedLocation 권한은 위험 권한이라 실행 중에 요청한다.
        val perm = "pleos.car.permission.FUSED_LOCATION"
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            runCatching { requestPermissions(arrayOf(perm), 1) }
        }
        val filter = IntentFilter(DEMO_ACTION)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(demoReceiver, filter, Context.RECEIVER_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(demoReceiver, filter)
        setContent { PickStopTheme { PickStopApp(vm) } }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(demoReceiver) }
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) vm.location.start()
    }

    companion object { const val DEMO_ACTION = "com.zeroinjury.pickstop.DEMO" }
}
