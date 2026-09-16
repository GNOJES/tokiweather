# Cute 3D Weather Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current text/Unicode emojis with a set of 8 cute 3D marshmallow/clay weather icons in TokiWeather widget and in-app preview.

**Architecture:** Generates and processes 8 high-resolution 3D weather icons (Style C marshmallow style) into transparent WebP/PNG drawables. Associates each `WeatherCondition` with its `@DrawableRes val iconRes: Int`, renders icons via Glance `Image` in `TokiWeatherWidget.kt`, and renders via Compose `Image` in `MainActivity.kt`.

**Tech Stack:** Android Glance AppWidget, Jetpack Compose, Kotlin, Android Drawable Resources, Python (Pillow for asset extraction).

## Global Constraints
- Target device reference: Galaxy S25 (xxxhdpi, density ~3.0).
- Preserve existing 2×1 widget layout, padding, and 5:3:3 proportions exactly.
- All drawable names must follow `ic_weather_<condition_lowercase>.png`.
- Every `WeatherCondition` enum entry must have a valid `@DrawableRes val iconRes: Int` and retain its `val emoji: String` fallback.
- No third-party network image loading; all icons are bundled locally as static Android drawables.

---

### Task 1: Generate and Optimize 8 Cute 3D Weather Icon Assets

**Files:**
- Create: `app/src/main/res/drawable/ic_weather_clear.png`
- Create: `app/src/main/res/drawable/ic_weather_cloudy.png`
- Create: `app/src/main/res/drawable/ic_weather_overcast.png`
- Create: `app/src/main/res/drawable/ic_weather_rain.png`
- Create: `app/src/main/res/drawable/ic_weather_sleet.png`
- Create: `app/src/main/res/drawable/ic_weather_snow.png`
- Create: `app/src/main/res/drawable/ic_weather_shower.png`
- Create: `app/src/main/res/drawable/ic_weather_unknown.png`
- Script: `scripts/process_weather_icons.py`

**Interfaces:**
- Consumes: Style C generation prompts matching 3D marshmallow/clay toy aesthetic.
- Produces: 8 transparent square PNG assets in `app/src/main/res/drawable/` (192×192px).

- [ ] **Step 1: Generate remaining 3D weather condition images**
Generate images for `CLEAR`, `OVERCAST`, `RAIN`, `SLEET`, `SNOW`, `SHOWER`, and `UNKNOWN` matching the exact marshmallow clay style of the approved `CLOUDY` image (`sunny_3d_minimal_1789573146944.jpg`).

- [ ] **Step 2: Create Python asset processing script**
Write `scripts/process_weather_icons.py` using Pillow to remove white backgrounds, crop tightly with uniform padding, resize to 192×192 with antialiasing, and save as `ic_weather_<condition>.png` in `app/src/main/res/drawable/`.

- [ ] **Step 3: Run asset processing script and verify drawables**
Run `python3 scripts/process_weather_icons.py`. Verify that all 8 files exist, are valid PNGs with alpha channels, and have dimensions 192×192.

- [ ] **Step 4: Commit assets**
```bash
git add app/src/main/res/drawable/ic_weather_*.png scripts/process_weather_icons.py
git commit -m "feat(assets): add cute 3D marshmallow weather icons"
```

---

### Task 2: Update `WeatherCondition` Model with `@DrawableRes`

**Files:**
- Modify: `app/src/main/java/com/toki/weather/data/model/WeatherCondition.kt`
- Create: `app/src/test/java/com/toki/weather/data/model/WeatherConditionTest.kt`
- Modify: `app/build.gradle.kts` (add JUnit dependency if needed)

**Interfaces:**
- Consumes: `R.drawable.ic_weather_*` from Task 1.
- Produces: `WeatherCondition.iconRes: Int` property accessible across the app and widget.

- [ ] **Step 1: Write failing unit test**
Create `app/src/test/java/com/toki/weather/data/model/WeatherConditionTest.kt` verifying that every condition in `WeatherCondition.values()` has a valid non-zero `iconRes`, `label`, and `emoji`.

- [ ] **Step 2: Run test to verify it fails**
Run: `./gradlew testDebugUnitTest --tests "com.toki.weather.data.model.WeatherConditionTest"`
Expected: Compilation failure or FAIL (property `iconRes` not defined on `WeatherCondition`).

- [ ] **Step 3: Update `WeatherCondition.kt`**
Add `@DrawableRes val iconRes: Int` parameter to `WeatherCondition` enum constructor and map each entry:
```kotlin
enum class WeatherCondition(
    val label: String,
    val emoji: String,
    @DrawableRes val iconRes: Int
) {
    CLEAR("맑음", "☀️", R.drawable.ic_weather_clear),
    CLOUDY("구름많음", "⛅", R.drawable.ic_weather_cloudy),
    OVERCAST("흐림", "☁️", R.drawable.ic_weather_overcast),
    RAIN("비", "🌧️", R.drawable.ic_weather_rain),
    SLEET("비/눈", "🌨️", R.drawable.ic_weather_sleet),
    SNOW("눈", "❄️", R.drawable.ic_weather_snow),
    SHOWER("소나기", "🌦️", R.drawable.ic_weather_shower),
    UNKNOWN("알수없음", "❓", R.drawable.ic_weather_unknown);
```

- [ ] **Step 4: Run test to verify it passes**
Run: `./gradlew testDebugUnitTest --tests "com.toki.weather.data.model.WeatherConditionTest"`
Expected: PASS.

- [ ] **Step 5: Commit model updates**
```bash
git add app/build.gradle.kts app/src/main/java/com/toki/weather/data/model/WeatherCondition.kt app/src/test/java/com/toki/weather/data/model/WeatherConditionTest.kt
git commit -m "feat(model): add iconRes to WeatherCondition enum"
```

---

### Task 3: Update `TokiWeatherWidget.kt` to Render Glance `Image`

**Files:**
- Modify: `app/src/main/java/com/toki/weather/widget/TokiWeatherWidget.kt`

**Interfaces:**
- Consumes: `weather.currentCondition.iconRes`, `weather.tomorrowCondition.iconRes`, `weather.dayAfterCondition.iconRes`.
- Produces: Visual 3D weather icons in the Android home screen widget via Glance `Image(provider = ImageProvider(...))`.

- [ ] **Step 1: Replace today weather emoji with Glance `Image`**
In `TokiWeatherWidget.kt`:
Define `todayIconSize = if (isCompact) 26.dp else 30.dp`.
Replace:
```kotlin
Text(
    text = weather.currentCondition.emoji,
    style = TextStyle(fontSize = todayEmojiSize.fixedSp(fontScale))
)
```
with:
```kotlin
Image(
    provider = ImageProvider(weather.currentCondition.iconRes),
    contentDescription = weather.currentCondition.label,
    modifier = GlanceModifier.size(todayIconSize)
)
```

- [ ] **Step 2: Replace tomorrow & day-after forecast emojis with Glance `Image`**
Define `forecastIconSize = if (isCompact) 16.dp else 18.dp`.
Replace tomorrow and dayAfter `Text` composables with:
```kotlin
Image(
    provider = ImageProvider(weather.tomorrowCondition.iconRes),
    contentDescription = weather.tomorrowCondition.label,
    modifier = GlanceModifier.size(forecastIconSize)
)
```
and
```kotlin
Image(
    provider = ImageProvider(weather.dayAfterCondition.iconRes),
    contentDescription = weather.dayAfterCondition.label,
    modifier = GlanceModifier.size(forecastIconSize)
)
```

- [ ] **Step 3: Compile and test widget build**
Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit widget changes**
```bash
git add app/src/main/java/com/toki/weather/widget/TokiWeatherWidget.kt
git commit -m "feat(widget): render cute 3D weather icons using Glance Image"
```

---

### Task 4: Update `MainActivity.kt` (`WidgetPreviewBox`) to Render Compose `Image`

**Files:**
- Modify: `app/src/main/java/com/toki/weather/MainActivity.kt`

**Interfaces:**
- Consumes: `weather.currentCondition.iconRes`, `weather.tomorrowCondition.iconRes`, `weather.dayAfterCondition.iconRes`.
- Produces: 1:1 matching visual preview of the 3D icons in `WidgetPreviewBox`.

- [ ] **Step 1: Replace today weather emoji with Compose `Image`**
In `WidgetPreviewBox` in `MainActivity.kt`:
Define `todayIconSize = if (isCompact) 26.dp else 30.dp`.
Replace:
```kotlin
Text(
    text = weather.currentCondition.emoji,
    fontSize = todayEmojiSize
)
```
with:
```kotlin
Image(
    painter = painterResource(weather.currentCondition.iconRes),
    contentDescription = weather.currentCondition.label,
    modifier = Modifier.size(todayIconSize)
)
```

- [ ] **Step 2: Replace tomorrow & day-after forecast emojis with Compose `Image`**
Define `forecastIconSize = if (isCompact) 16.dp else 18.dp`.
Replace tomorrow and dayAfter forecast `Text(...)` with:
```kotlin
Image(
    painter = painterResource(weather.tomorrowCondition.iconRes),
    contentDescription = weather.tomorrowCondition.label,
    modifier = Modifier.size(forecastIconSize)
)
```
and
```kotlin
Image(
    painter = painterResource(weather.dayAfterCondition.iconRes),
    contentDescription = weather.dayAfterCondition.label,
    modifier = Modifier.size(forecastIconSize)
)
```

- [ ] **Step 3: Verify preview layout and build**
Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit preview changes**
```bash
git add app/src/main/java/com/toki/weather/MainActivity.kt
git commit -m "feat(preview): render cute 3D weather icons in WidgetPreviewBox"
```

---

### Task 5: End-to-End Verification & Remote Push

**Files:**
- Repository working directory

- [ ] **Step 1: Run full unit test suite and clean debug build**
Run: `./gradlew clean testDebugUnitTest assembleDebug`
Expected: All tests pass, build succeeds.

- [ ] **Step 2: Verify git status and push to origin/main**
Run:
```bash
git status
git push origin main
```
Expected: Clean working tree, pushed to `origin/main`.
