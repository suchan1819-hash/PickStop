# 픽스탑 (PickStop)

차량 내비의 도착 예정 시간을 받아, 휴게소에 도착하는 순간 음식이 막 완성되도록 조리 시작 시점을 대신 잡아 주는 Pleos Connect 휴게소 미리주문 앱입니다. 운전 중에는 "다음 휴게소에서 늘 먹던 거" 한마디면 끝납니다.

## 시연 영상
- [픽스탑 시연 영상 (2분 41초, Pleos Connect 에뮬레이터 실제 동작 · 앱 안내 음성 포함)](https://drive.google.com/file/d/1G3g_WRJXKsWETbEJT53-aBZLXXZtsJo5/view?usp=sharing)

## 주요 기능
- **음성 한마디 주문 (주행 중)**: 화면 조작 없이 "다음 휴게소에서 늘 먹던 거" → "○○휴게소에서 소떡소떡과 아이스 아메리카노, 9,500원 주문할까요?" → "응".
- **도착 시간 맞춤 조리 (핵심)**: 매장별 조리 시간을 거꾸로 계산해 "도착 2분 전 완성"이 되는 시각에 매장으로 조리 시작을 요청합니다. 정체로 도착이 늦어지면 매장에 새 도착 시간을 알리고 조리 시작을 자동으로 미룹니다.
- **경유지 자동 추가**: 주문이 확정되면 휴게소를 NaviHelper 경유지로 넣고, 경유지 도착 이벤트(또는 FusedLocation 근접 알림)로 픽업 번호를 음성 안내합니다.
- **결제·취소**: 정차 중 한 번 등록한 결제 수단(PG 간편결제 빌링키 방식, 시연은 테스트 결제)으로 음성 확인만으로 결제. 조리 시작 전까지 "취소" 한마디로 전액 환불.
- **쉬어 갈 이유 만들기**: 연속 운전 2시간이 되면 "다음 휴게소에 커피 주문해 둘까요?"라고 먼저 제안합니다.
- **전기차 충전 맞춤 식사**: 배터리가 낮으면 충전기가 있는 휴게소를 고르고, 충전 시간 안에 먹을 수 있는 메뉴를 추천해 도착에 맞춰 조리합니다. 충전 완료 5분 전 알림.
- **정차 중 메뉴 · 우리 차 장바구니**: 메뉴 화면은 정차(P) 중에만 열립니다. 동승자는 차량 화면의 QR로 휴대폰에서 같은 장바구니에 담을 수 있습니다.
- **매장용 화면**: POS 연동 없이 태블릿 웹 화면(`/store`)으로 주문을 받습니다. 조리 대기(조리 시작까지 남은 시간) → 지금 조리 시작 → 완성·픽업 대기 순서로 보입니다.

## 실행 방법
1. Android Studio에서 이 폴더를 열고 Gradle Sync가 끝날 때까지 기다립니다.
2. Pleos Connect v2.1.0 에뮬레이터(AVD)를 실행합니다.
3. 상단 ▶ Run 'app'을 누릅니다. 처음 실행하면 위치(FusedLocation) 권한을 허용해 주세요.
4. 매장 화면 보기: `adb forward tcp:8090 tcp:8090` 후 PC 브라우저에서 http://localhost:8090/store 를 엽니다. 동승자 장바구니는 http://localhost:8090/join 입니다.

## 테스트 방법
- **시연 패널**: 앱 왼쪽 위 로고를 길게 누르면 시연 패널이 열립니다.
  - 시연 주행 시작 ×20: 영동고속도로 강릉 방향을 98km/h로 달리는 가상 주행을 20배속으로 보여 줍니다(내비 경로가 없을 때).
  - 정체 +6분 / 도착 처리 / 연속 운전 2시간 / 배터리 30% / 기어 P·D
  - 음성 대신: 에뮬레이터에 마이크가 없을 때 "다음 휴게소에서 늘 먹던 거", "응", "취소", "커피"를 버튼으로 넣습니다.
  - 매장 자동 응답: 매장 화면에서 직접 누르지 않아도 조리 시작·완성을 처리합니다.
- **기어 변경**: Pleos Connect Virtual Console (`adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088 → Motion → Gear)
- **adb 명령으로 시연**: `adb shell am broadcast -a com.zeroinjury.pickstop.DEMO -p com.zeroinjury.pickstop --es cmd start`
  (cmd: `start`, `say_usual`, `say_yes`, `congestion`, `arrive`, `picked`, `long`, `battery`, `card`, `reset` 등)
- **결제**: 메뉴 화면 오른쪽 위 "결제 수단 등록" → 테스트 카드 등록. 실제 카드 정보는 입력하지 않습니다.

## 테스트 환경
- Pleos Connect Emulator v2.1.0 (API 34)
- Android API 28 이상, Gradle 8.0 이상, Kotlin, Jetpack Compose

## 사용한 Pleos Connect SDK
| SDK | 쓰는 곳 |
|---|---|
| NaviHelper 3.0.5 | 경유지 정보(remainTime)·주행 정보(DrivingState.duration, ChargingPlan)·TripInfo(도착 시 예상 배터리)로 도착 예정 시간 계산, 휴게소 경유지 추가(addWaypoint), 경유지 도착·재탐색 이벤트 |
| FusedLocation 1.1.3 | 차량 위치, 휴게소 반경 300m 진입 감지(addProximityAlert) |
| TextToSpeech 3.3.1 | 정해진 안내 밖의 문장 음성 출력 (ON_DEVICE). 주요 안내 21문장은 녹음 음성(res/raw)으로 재생해 TTS 엔진이 없는 환경에서도 들립니다 |
| SpeechToText 3.3.1 | "늘 먹던 거", "응", "취소" 핸즈프리 주문 (ON_DEVICE) |
| Vehicle 2.3.2 / AAOS Car API | 기어·속도(주행 중에는 음성만, 정차 중에만 메뉴 화면), 전기차 배터리 잔량 |

## 데이터
휴게소·메뉴 데이터 구조는 공공데이터포털 한국도로공사 「휴게소 푸드메뉴현황 조회 서비스」(휴게소·매장·음식명·가격·대표메뉴)와 「노선별, 방향별 휴게소 편의시설 현황」(위치·전기차 충전기)을 따릅니다. 시연에서는 영동고속도로 강릉 방향 3곳(덕평·여주·문막)을 앱에 넣어 두었고, 좌표·가격·조리 시간은 시연용 값입니다. 매장 화면의 회색 주문(#104, #107, #109, #115, #118)은 시연용 가상 주문입니다.

## APK
- 위치: [`apk/이수찬_픽스탑_release.apk`](apk/) (release 빌드)
- 설치: Pleos Connect 에뮬레이터를 켠 상태에서 `adb install -r apk/이수찬_픽스탑_release.apk`

## 사용한 오픈소스와 리소스 출처
- Android Jetpack (Compose, Activity, Lifecycle, Core KTX) — Apache License 2.0
- Kotlin, kotlinx.coroutines — Apache License 2.0
- NanoHTTPD 2.3.1 (매장·동승자 웹 화면용 내장 서버) — BSD 3-Clause License
- ZXing core 3.5.3 (QR 코드) — Apache License 2.0
- Pleos Connect SDK (Vehicle, NaviHelper, FusedLocation, TextToSpeech, SpeechToText) — 42dot Pleos Connect SDK License Agreement
- 글꼴: Asta Sans — SIL Open Font License 1.1 (Pleos Connect 시스템 서체)
- 음식 사진: Pixabay 무료 이미지 (Pixabay Content License)
- 앱 안내 음성(res/raw/vo_*.mp3): Higgsfield Text to Speech(ElevenLabs 엔진, 프리셋 음성)로 개발자가 직접 생성
