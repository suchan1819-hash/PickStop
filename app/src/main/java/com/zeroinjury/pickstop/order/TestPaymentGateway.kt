package com.zeroinjury.pickstop.order

import android.content.Context
import java.util.UUID

/**
 * 결제: 결제대행사(PG) 간편결제(빌링키) 방식을 흉내 낸 테스트 결제.
 *
 * - 카드 등록은 정차 중에만, 한 번. 앱은 카드 번호를 저장하지 않고 PG가 준 빌링키만 보관한다.
 * - 주행 중에는 등록된 빌링키로 음성 확인만 받아 승인한다.
 * - 조리 시작 전 취소는 전액 환불.
 *
 * 공모전 시연용이라 실제 PG에 연결하지 않으며, 승인번호는 TEST- 로 시작한다.
 */
class TestPaymentGateway(context: Context) {

    data class Card(val billingKey: String, val label: String)

    private val prefs = context.applicationContext.getSharedPreferences("payment", Context.MODE_PRIVATE)

    val card: Card?
        get() {
            val key = prefs.getString("billingKey", null) ?: return null
            return Card(key, prefs.getString("label", "테스트 카드") ?: "테스트 카드")
        }

    /** 정차 중 카드 등록 → PG에서 빌링키 발급 (테스트) */
    fun registerTestCard(): Card {
        val c = Card("test_billing_" + UUID.randomUUID().toString().take(8), "테스트 카드 ••12")
        prefs.edit().putString("billingKey", c.billingKey).putString("label", c.label).apply()
        return c
    }

    fun removeCard() { prefs.edit().clear().apply() }

    /** 빌링키 결제 승인 (테스트) */
    fun approve(amount: Int): Result<String> {
        val c = card ?: return Result.failure(IllegalStateException("등록된 결제 수단이 없어요"))
        if (amount <= 0) return Result.failure(IllegalArgumentException("금액 오류"))
        return Result.success("TEST-" + c.billingKey.takeLast(4).uppercase() + "-" + (System.currentTimeMillis() % 100000))
    }

    /** 조리 시작 전 취소 → 전액 환불 (테스트) */
    fun cancel(approvalId: String): Boolean = approvalId.startsWith("TEST-")
}
