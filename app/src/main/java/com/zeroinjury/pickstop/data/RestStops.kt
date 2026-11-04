package com.zeroinjury.pickstop.data

import androidx.annotation.DrawableRes
import com.zeroinjury.pickstop.R

/**
 * 휴게소·메뉴 데이터.
 *
 * 필드 구성은 공공데이터포털의 한국도로공사 「휴게소 푸드메뉴현황 조회 서비스」(휴게소명·노선·방향·매장·음식명·가격·대표메뉴)와
 * 「노선별, 방향별 휴게소 편의시설 현황」(위치·운영 여부·전기차 충전기)을 따른다.
 * 시연에서는 영동고속도로 강릉 방향 3곳을 앱에 넣어 두고, 서비스 단계에서는 같은 구조로 API 응답을 받는다.
 */
enum class Category(val label: String) { SNACK("분식"), MEAL("한식"), DRINK("커피·음료") }

data class MenuItem(
    val id: String,
    val name: String,
    val price: Int,
    /** 조리에 걸리는 시간(초). 매장이 직접 입력하고, 실제 완료 시간이 쌓이면 보정된다. */
    val cookSec: Int,
    val category: Category,
    @DrawableRes val photo: Int,
    val signature: Boolean = false,
    /** 먹는 데 걸리는 대략적인 시간(분). 충전 맞춤 추천에 쓴다. */
    val eatMin: Int = 10,
)

data class RestStop(
    val id: String,
    val name: String,
    val route: String,
    val direction: String,
    val lat: Double,
    val lng: Double,
    val evCharger: Boolean,
    val features: String,
    val counter: String,
    val menu: List<MenuItem>,
)

object RestStops {

    private fun baseMenu(prefix: String, signature: MenuItem) = listOf(
        MenuItem("$prefix-sotteok", "소떡소떡", 4500, 6 * 60, Category.SNACK, R.drawable.food_sotteok, eatMin = 5),
        MenuItem("$prefix-americano", "아이스 아메리카노", 5000, 2 * 60, Category.DRINK, R.drawable.food_americano, eatMin = 3),
        signature,
        MenuItem("$prefix-udon", "우동", 8000, 5 * 60, Category.MEAL, R.drawable.food_udon, eatMin = 12),
        MenuItem("$prefix-donkatsu", "돈가스", 10500, 9 * 60, Category.MEAL, R.drawable.food_donkatsu, eatMin = 15),
        MenuItem("$prefix-tteok", "떡볶이", 4500, 4 * 60, Category.SNACK, R.drawable.food_tteokbokki, eatMin = 8),
    )

    val DEOKPYEONG = RestStop(
        id = "deokpyeong", name = "덕평휴게소", route = "영동고속도로", direction = "강릉 방향",
        lat = 37.2394, lng = 127.3612, evCharger = true, features = "충전기 · 푸드코트", counter = "푸드코트 2번 창구",
        menu = baseMenu("dp", MenuItem("dp-gukbap", "한우국밥", 11000, 8 * 60, Category.MEAL, R.drawable.food_gukbap, signature = true, eatMin = 15)),
    )
    val YEOJU = RestStop(
        id = "yeoju", name = "여주휴게소", route = "영동고속도로", direction = "강릉 방향",
        lat = 37.3054, lng = 127.6183, evCharger = false, features = "푸드코트 · 카페", counter = "푸드코트 1번 창구",
        menu = baseMenu("yj", MenuItem("yj-gukbap", "한우국밥", 11000, 8 * 60, Category.MEAL, R.drawable.food_gukbap, signature = true, eatMin = 15)),
    )
    val MUNMAK = RestStop(
        id = "munmak", name = "문막휴게소", route = "영동고속도로", direction = "강릉 방향",
        lat = 37.3241, lng = 127.8194, evCharger = true, features = "충전기 · 푸드코트", counter = "푸드코트 3번 창구",
        menu = baseMenu("mm", MenuItem("mm-gukbap", "한우국밥", 11000, 8 * 60, Category.MEAL, R.drawable.food_gukbap, signature = true, eatMin = 15)),
    )

    /** 경로 순서대로 */
    val onRoute = listOf(DEOKPYEONG, YEOJU, MUNMAK)

    fun byId(id: String) = onRoute.firstOrNull { it.id == id }

    /** 휴게소마다 메뉴 이름이 달라도 "늘 먹는 것"을 가장 가까운 메뉴로 맞춘다. */
    fun matchFavorites(stop: RestStop, favoriteNames: List<String>): List<MenuItem> =
        favoriteNames.mapNotNull { name -> stop.menu.firstOrNull { it.name == name } }
}
