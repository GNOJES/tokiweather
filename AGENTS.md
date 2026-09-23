# AGENTS.md

> **Notice for AI Agents (Antigravity, Codex, Claude, etc.)**
> 이 문서는 세션이 종료되거나 다른 AI 에이전트로 작업 주체가 변경되어도 프로젝트의 맥락, 사용자 의도, 제약사항, 과거 시행착오를 놓치지 않도록 작성된 **인수인계 문서**입니다.
> **향후 구조, 규칙, 주요 의사결정이 변경되면 본 문서(`AGENTS.md`)도 반드시 함께 갱신해야 합니다.**

---

## Project context

- **앱 이름**: 토끼날씨 (TokiWeather)
- **플랫폼 & 기술 스택**: Android (Kotlin), Jetpack Compose, Jetpack Glance (AppWidget), DataStore, Retrofit2/OkHttp, Material 3
- **현재 버전**: `0.8.0` (versionCode `7`), `main` 브랜치 운용 중
- **핵심 기능**:
  1. 기상청 실시간 날씨 및 대기질(미세먼지/초미세먼지) 데이터 연동
  2. 홈 화면 위젯 2종 제공: One UI 4×7(Glance 2×1) 및 노바런처 8×8(Glance 3×2)
  3. 앱 메인 3탭 구조: 날씨(종합 예보) / 초단기(네이버 초단기강수예측 웹뷰) / 설정(위젯 및 갱신 주기 설정)

---

## Important decisions

1. **하단 내비게이션 바(3탭) UI/UX 규칙**:
   - **텍스트 라벨 완전 제거**: 탭 하단 글자("날씨", "초단기", "설정")는 제거하고, 아이콘을 48dp로 최대화하여 미니멀하고 시원한 룩 유지.
   - **탭별 선택/미선택 상태 비주얼 룰**:
     - **미선택 상태**: 순수 테마 오브젝트 단독 노출 (100% 선명도/불투명 유지, 흐리게 처리 금지).
     - **선택 상태**: 테마 오브젝트 + 토끼날씨 캐릭터(Toki) 함께 노출 + 선명한 파스텔 라벤더 배경 캡슐(`primary.copy(alpha = 0.18f)`).
   - **개별 탭 아이콘 세부 형태**:
     - **날씨 (Tab 0)**:
       - 미선택: 기존 앱 고유의 따뜻하고 둥근 맑은 태양 디스크(`ic_tab_weather.xml`, `#FFA726` / `#FFD54F`) + 웃는 구름.
       - 선택: **구름 완전히 제거** + 맑은 태양 디스크 + 곁에 쏙 고개를 내민 토끼 (태양 얼굴에 손 올리지 않음).
     - **초단기 (Tab 1)**:
       - 미선택: 파스텔 하늘색 우산(얼굴 표정 있음) + 하단에 이격된 물방울.
       - 선택: **우산 얼굴 표정 유지** + 우산 좌측 아래 아늑하게 들어간 토끼(앞발로 우측 손잡이 잡음) + 우산에 붙지 않고 좌우로 자연스럽게 이격된 물방울.
     - **설정 (Tab 2)**:
       - 미선택: 파스텔 라일락 3D 기계식 톱니바퀴 (6개 명확한 사다리꼴 돌기 + 중앙 홀 + 베벨 링 + 골드 반짝이).
       - 선택: 톱니바퀴 + 좌측에서 인사하는 토끼 + 골드 반짝이.
2. **진입 및 뒤로가기 동작**:
   - 앱 실행 및 위젯 클릭 시 항상 **0번(날씨) 탭**으로 기본 진입.
   - 1번(초단기), 2번(설정) 탭에서 뒤로가기 누르면 **0번(날씨) 탭**으로 이동. 0번 탭에서 뒤로가기 누르면 앱 종료 (`MainActivity.kt`의 `BackHandler`).
3. **위젯 설정 화면 (Tab 2)**:
   - "저장 및 새로고침" 버튼 클릭 시 위젯 전체 갱신(`TokiWeatherWidget.updateAll()`) 후 `finish()`로 앱 종료.
   - 갱신 주기 옵션(15분/30분/1시간)에서 `(추천)` 텍스트 제거 및 선택된 칩의 시인성 높은 테두리/배경 적용.
4. **파스텔톤 컬러 원칙**:
   - 어둡거나 진한 원색(예: 진보라 `#5E35B1`, 진남색 `#1976D2`, 짙은 슬레이트 `#37474F` 등)은 절대 지양.
   - 부드러운 스카이블루, 라일락, 파스텔 옐로우, 소프트 핑크 등 화사하고 귀여운 파스텔톤 일관성 유지.

---

## Constraints and rules

1. **위젯(Glance) 제약**:
   - 위젯은 일반 Jetpack Compose가 아닌 `androidx.glance` 기반 (`TokiWeatherWidget`, `TokiWeatherWidgetLarge`).
   - 일반 Compose 컴포넌트(Canvas, ConstraintLayout, ImageVector 등) 사용 불가. Glance 전용 `Box`, `Row`, `Column`, `Text`, `Image`만 사용 가능.
   - 기상 아이콘은 로컬 정적 리소스(`res/drawable/ic_weather_*.png`)로 번들되어 있어야 함 (Glance 내 비동기 네트워크 이미지 로딩 불가).
2. **Android Vector Drawable 제약**:
   - Android Vector XML은 `<rect>`, `<circle>`, `<polygon>`, `<g>` 태그를 지원하지 않음.
   - 오직 `<vector>`, `<group>`, `<path>`만 유효함. 모든 도형은 `pathData` (SVG path 문법)로 작성해야 함.
3. **하단 내비게이션 바 수정 시 금기사항**:
   - 탭 텍스트 라벨 다시 추가 금지.
   - 미선택 아이콘에 `alpha(0.55f)` 등으로 투명도 감쇄 금지 (배경 대비상 흐려 보임).
   - 선택 상태 캡슐을 너무 연하게(`secondaryContainer alpha 0.75` 등) 설정 금지 (배경과 구분이 안 됨).
4. **Git 및 작업 원칙**:
   - 작업 디렉토리: `/Users/a220330002/Projects/personal/tokiweather` (상위 `Projects/tokiweather` 심링크 또는 직접 접근 확인 필요).
   - 커밋 메시지는 Conventional Commits(`feat:`, `fix:`, `chore:`, `style:`) 형식 준수.
   - 작업 완료 전 반드시 단위 테스트(`testDebugUnitTest`) 및 빌드(`assembleDebug`) 검증 필수.

---

## Known issues / limitations

1. **날씨 탭(Tab 0)의 상세 예보 미구현 (의도된 상태)**:
   - 현재는 상단 현재 날씨 카드, 대기질 카드, 3일 요약, 기상청 날씨누리 외부 링크만 노출.
   - 시간별 꺾은선 차트 및 상세 예보 카드는 사용자의 요청으로 추후 기획을 다시 다듬어서 진행하기로 하여 일시 보류 상태.
2. **예보 기간 한계 (3일 vs 7일)**:
   - 기상청 단기예보(VilageFcst)는 3일치만 제공. 7일치(주간 예보)를 구현하려면 기상청 중기예보 API(MidFcst) 연동 또는 별도 API 추가 필요 `[확인 필요]`.
3. **초단기 탭 네이버 지도 웹뷰**:
   - `https://weather.naver.com/map?visualMapType=maple` 사용.
   - 외부 웹 의존성이므로 네이버 측 모바일 웹페이지 레이아웃 변경 시 영향 가능성 있음.

---

## Testing and verification

- **단위 테스트**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
  (현재 WeatherCondition 8종 × 3가지 필수 속성 검증 = 총 24개 단언/검증 항목 통과 상태 유지 필수)
- **빌드 및 패키징**:
  ```bash
  ./gradlew assembleDebug
  ```
- **실기기 설치 및 실행 검증**:
  ```bash
  adb -s <DEVICE_ID> install -r app/build/outputs/apk/debug/app-debug.apk
  adb -s <DEVICE_ID> shell am start -n com.toki.weather/.MainActivity
  ```
- **화면 캡처 및 시각적 검증**:
  ```bash
  adb -s <DEVICE_ID> exec-out screencap -p > screen.png
  ```
  - 탭 UI 변경 시 반드시 3개 탭 각각의 선택/미선택 상태 스크린샷을 확인하여 캡슐 정렬, 아이콘 겹침, 색상 대비를 육안 검증할 것.

---

## Pending work

1. **날씨 탭(Tab 0) 고도화**:
   - 사용자 기획 전달 시 시간별 예보(차트/카드) 및 일별 예보 상세화 구현.
   - 중기예보(7일) API 도입 여부 결정 시 API 클라이언트 확장.
2. **차기 릴리즈 배포**:
   - 위젯 및 네비게이션 개편 완료 후 `0.8.x` 또는 `0.9.0` 태그 생성 및 GitHub Release APK 빌드/업로드.

---

## Agent handoff notes

### 과거 에이전트가 반복해서 실수했던 핵심 교훈
1. **기존 디자인 임의 재창작 금지 (Check Git History First)**:
   - 사용자가 "날씨 선택 시 구름을 빼고 태양과 토끼를 노출해줘"라고 했을 때, 에이전트가 기존 태양 디자인을 확인하지 않고 8갈래 각진 사다리꼴 태양과 손잡는 토끼를 새로 그려 넣어 큰 지적을 받았음.
   - **반드시 기존에 정의된 드로어블(`ic_weather_clear.png`나 이전 커밋의 `ic_tab_weather.xml`) 형태를 계승**하고, 손(앞발) 등 어색한 요소를 임의로 덧붙이지 말 것.
2. **초단기 우산 표정과 빗방울 간격**:
   - 우산 아이콘은 선택/미선택 모두 상단 캐노피에 귀여운 표정이 있어야 함.
   - 빗방울이 우산 캐노피 테두리에 딱 붙으면 답답해 보이므로, 캐노피 외곽과 충분한 간격을 두고 아래로 떨어지도록 배치해야 함.
3. **투명도(Alpha) 오남용 주의**:
   - 선택 상태를 강조하겠다고 미선택 아이콘에 `alpha(0.55f)`를 주면 고해상도 AMOLED 디스플레이에서 아이콘이 씻겨 나간 것처럼 보임.
   - 선택 상태는 **배경 캡슐(Pill)과 토끼 캐릭터의 등장 유무**로 구분하고, 아이콘 자체는 항상 100% 선명해야 함.
