# TokiWeather Reliability Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 위젯 자동 갱신과 수동 새로고침의 실패 처리를 바로잡고, 잘못된 날씨 수치와 인증키 로그 노출을 막는다.

**Architecture:** 기존 WorkManager 고유 작업은 하나만 유지하고 두 위젯 종류의 존재 여부를 함께 판단한다. 날씨 조회 결과의 성공·실패 경계를 저장소에서 명확히 한 뒤 UI와 Worker가 그 결과를 사용한다. 공공 API 인증키는 로그에서 제외하고, APK 내 키의 공개 가능성은 운영 제약으로 다룬다.

**Tech Stack:** Android/Kotlin, Jetpack Compose·Glance, DataStore, WorkManager, Retrofit/OkHttp, JUnit.

**Spec:** `docs/superpowers/specs/2026-09-24-reliability-improvements-design.md`

## Global Constraints

- `AGENTS.md`의 하단 탭/위젯 디자인과 3일 예보 범위를 유지한다.
- 주요 동작 결정 변경 시 `AGENTS.md`를 갱신한다.
- 각 작업 후 `./gradlew testDebugUnitTest assembleDebug`를 실행한다.
- 인증키 값·키가 들어간 URL을 테스트 출력, 로그, Git에 넣지 않는다.
- 현재 작업 트리에는 앞선 에어코리아·위치 수정이 미커밋 상태다. 실행 전 `git status --short`를 확인하고 그 변경을 보존한다.

## Review Focus

- 기본형과 대형 위젯이 모두 있을 때 한 종류 제거: 남은 위젯의 자동 갱신 예약 유지(작업 1).
- 1시간 주기를 설정한 뒤 두 번째 위젯 추가: 주기가 30분으로 되돌아가지 않음(작업 1).
- KMA HTTP 200에 오류 결과 코드·빈 본문: 성공 토스트와 캐시 덮어쓰기 없음(작업 2·3).
- 실제 기온 0°C와 기온 항목 누락: 전자는 정상, 후자는 실패로 구분(작업 3).
- Debug 요청 및 예외: API 키 문자열이 로그에 나오지 않음(작업 4).

### Task 1: 두 위젯의 공통 예약 작업 관리

**Files:** `app/src/main/java/com/toki/weather/worker/WeatherWorkScheduler.kt`, `widget/TokiWeatherWidgetReceiver.kt`, `widget/TokiWeatherWidgetLargeReceiver.kt`, `data/cache/WeatherDataStore.kt`; 필요 시 `app/src/androidTest/.../WidgetSchedulingTest.kt`.

**Interface:** 두 리시버가 동일한 `scheduleFromSavedInterval(context)`와 `cancelIfNoWidgetsRemain(context)`를 사용한다. 후자는 `AppWidgetManager.getAppWidgetIds(ComponentName(...))`로 양쪽 수를 확인한다.

- [ ] 두 위젯을 설치한 뒤 한 종류만 제거하거나 60분 설정 뒤 두 번째 위젯을 추가하는 재현 절차를 기록한다. 현 구현에서 공통 작업이 취소/30분으로 재설정되는지 WorkManager 작업 정보로 확인한다.
- [ ] 해당 조건을 검증하는 Android 통합 테스트를 먼저 작성한다. `WorkManager.getWorkInfosForUniqueWork("toki_weather_periodic_update")`의 예약 상태·반복 간격을 확인하고, 수정 전 실패를 확인한다. 실기기 수동 검증을 병행한다.
- [ ] 리시버에서 저장된 `updateIntervalFlow.first()` 값을 읽어 예약한다. BroadcastReceiver의 `goAsync()`와 `Dispatchers.IO`를 사용해 DataStore 읽기가 리시버 메인 스레드를 막지 않게 한다. 두 종류 모두 없을 때만 취소한다. 핵심 조건은 `standardIds.isEmpty() && largeIds.isEmpty()`이다.
- [ ] 테스트와 빌드를 실행하고, 두 위젯 추가 → 하나 제거 → 나머지 갱신, 60분 설정 → 두 번째 위젯 추가 후 60분 유지 순으로 실기기에서 확인한다.
- [ ] `AGENTS.md`의 위젯 스케줄 결정을 갱신하고 `fix: preserve widget update schedule across widget types` 형식으로 별도 커밋한다.

### Task 2: 수동 갱신 결과와 취소 전파

**Files:** `app/src/main/java/com/toki/weather/MainActivity.kt`, `ui/screen/WidgetSettingsScreen.kt`, `data/repository/WeatherRepository.kt`, `worker/WeatherUpdateWorker.kt`; 테스트는 `app/src/test/java/com/toki/weather/data/repository/WeatherRefreshResultTest.kt`.

**Interface:** `WeatherRepository.fetchAndSave(): Result<CachedWeather>`를 유지한다. UI 호출부는 결과를 `getOrThrow()`로 확인한 뒤에만 갱신 성공 메시지를 표시한다.

- [ ] 날씨 조회가 `Result.failure(IOException("offline"))`을 반환했을 때 성공 토스트가 표시되지 않고 기존 캐시를 유지하는 UI/저장소 검증을 먼저 작성한다. 코루틴 취소가 `Result.failure` 또는 WorkManager 재시도로 바뀌지 않는 테스트도 작성해 수정 전 실패를 확인한다.
- [ ] 날씨 탭 호출부를 `val weather = withContext(Dispatchers.IO) { repo.fetchAndSave().getOrThrow() }`처럼 바꾸고, 성공할 때만 위젯 갱신·성공 알림을 실행한다. 설정 화면은 설정 저장 완료 후 조회 실패 시 '설정은 저장됨, 날씨 갱신 실패'를 알리고 예약 작업은 유지한다.
- [ ] 저장소에서 `catch (e: CancellationException) { throw e }`를 일반 `catch (e: Exception)` 앞에 두고 Worker도 취소를 다시 던진다. 네트워크 오류에 대한 `Result.retry()` 동작은 유지한다.
- [ ] 테스트·빌드와 실기기 오프라인 새로고침을 확인한다. `AGENTS.md`에 저장과 날씨 갱신의 결과 메시지 규칙을 기록하고 `fix: report refresh failures accurately`로 별도 커밋한다.

### Task 3: KMA 응답 및 필수 수치 검증

**Files:** `app/src/main/java/com/toki/weather/data/repository/WeatherRepository.kt`, 필요 시 새 `data/repository/KmaWeatherParser.kt`, `data/remote/KmaResponse.kt`; 테스트는 `app/src/test/java/com/toki/weather/data/repository/KmaWeatherParserTest.kt`.

**Interface:** API 응답의 `header.resultCode == "00"`과 필수 현재 기온 `T1H`의 숫자 파싱이 성공해야 `CachedWeather`를 생성한다. 누락된 예보 수치는 미수신 상태로 표현한다. 현재 모델에 미수신 표현이 없다면 먼저 nullable 예보 값 또는 명시적 상태를 추가하고 앱·두 위젯의 표시를 함께 바꾼다.

- [ ] 정상 0°C, HTTP 200+오류 코드, 본문 없음, T1H 없음/문자열 오류, 내일 최저·최고 누락을 각각 입력으로 주는 파서 테스트를 먼저 작성한다. 오류 입력에서 캐시가 덮어써지지 않는 저장소 테스트를 포함한다.
- [ ] API 결과 코드를 확인하고 파싱 실패를 명시적 실패로 반환한다. `toIntOrNull() ?: 0`처럼 누락과 실제 0을 합치는 기본값을 제거한다. 초단기실황 필수값은 조회 전체 실패, 예보 일부 누락은 해당 항목만 미수신으로 표시하는 정책을 따른다.
- [ ] 날짜 경계(23시대/0시대)에서 요청 기준시각과 응답 예보 항목을 확인한다. 기존 WeatherCondition 테스트와 전체 테스트·빌드를 실행한다.
- [ ] 실기기에서 API 정상 수치와 네트워크 오류 시 마지막 정상 캐시 보존을 확인한다. `AGENTS.md`를 갱신하고 `fix: reject incomplete weather responses`로 별도 커밋한다.

### Task 4: 인증키 로그 제거와 배포 점검

**Files:** `app/src/main/java/com/toki/weather/data/remote/RetrofitClient.kt`, `data/repository/WeatherRepository.kt`, 필요 시 `app/build.gradle.kts`; 테스트는 `app/src/test/java/com/toki/weather/data/remote/RequestLoggingTest.kt`.

**Interface:** 앱 로그에는 두 서비스의 인증키, 키가 들어간 요청 URL, 전체 HTTP 본문이 나오지 않는다. 실패 단계·HTTP 상태·예외 종류 같은 비밀이 아닌 정보만 남긴다.

- [ ] 가짜 키와 실패 응답을 사용해 Debug 요청 로그·예외 로그에 키 또는 인코딩된 키가 포함되면 실패하는 테스트를 먼저 작성한다. 실제 키를 테스트 파일/출력에 사용하지 않는다.
- [ ] KMA 클라이언트의 `HttpLoggingInterceptor.Level.BODY`를 제거하고, `Log.e(..., exception)`에서 URL이 들어간 예외 메시지를 출력하지 않도록 실패 종류만 기록한다. 에어코리아의 기존 비밀 없는 단계 로그는 유지한다.
- [ ] `./gradlew testDebugUnitTest assembleDebug` 실행 후 Debug 실기기 `logcat`에서 양쪽 키의 원본·URL 인코딩 값이 없음을 로컬 스크립트의 PASS/FAIL 결과로 확인한다. 키 값 자체를 콘솔에 출력하지 않는다.
- [ ] 공개 배포 전 공공데이터포털 개발/운영 계정 트래픽 한도와 키 교체 절차를 운영 문서에 기록한다. APK에 포함된 키는 추출 가능하므로 남용 위험이 크면 서버 프록시 도입을 별도 작업으로 계획한다. `chore: remove API key request logging`으로 별도 커밋한다.

## 완료 검증

- [ ] `./gradlew testDebugUnitTest assembleDebug` 성공.
- [ ] 두 위젯을 함께 설치·제거하는 경우와 저장된 주기 유지 확인.
- [ ] 정상 조회·오프라인 조회·빈 KMA 응답·API 결과 오류의 화면과 캐시 동작 확인.
- [ ] Debug 로그 키 검사 PASS 및 `git diff --check` 통과.
- [ ] 변경된 결정을 `AGENTS.md`에 반영하고 작업 트리/커밋 범위를 검토.
