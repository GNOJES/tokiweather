# Cute 3D Weather Icons Design Spec

## Overview
Replace the existing text/Unicode emojis (`☀️`, `⛅`, `🌧️`, etc.) in the TokiWeather Android widget and in-app preview with a unified set of cute 3D minimal marshmallow/clay weather icons (Style C: rounded toy clay texture, gentle pastel tones, warm expressions, no rabbit character, focused entirely on clean weather objects).

## Goals
1. Provide 8 cute 3D marshmallow weather icons matching Style C aesthetic.
2. Store optimized transparent PNG/WebP drawables in `res/drawable/`.
3. Update `WeatherCondition` enum to link to `@DrawableRes val iconRes: Int` while keeping `emoji: String` as a fallback.
4. Replace Glance `Text(emoji)` in `TokiWeatherWidget.kt` with Glance `Image(provider = ImageProvider(iconRes))`.
5. Replace Compose `Text(emoji)` in `MainActivity.kt` (`WidgetPreviewBox`) with Compose `Image(painter = painterResource(iconRes))`.
6. Maintain exact 2×1 layout proportions, grid scalability (4~6 columns, 4~7 rows), and prevent any clipping or distortion.

## Non-Goals
- Changing the widget layout to 3×2 or altering the Nova launcher grid behavior in this iteration (postponed to a separate dedicated task per user request).

## Icon Specifications

| Weather Condition | Description & Visual Concept | Drawable Name |
| :--- | :--- | :--- |
| `CLEAR` | Smiling warm golden 3D sun with rounded clay rays | `ic_weather_clear` |
| `CLOUDY` | Puffy 3D marshmallow cloud with a smiling golden sun peeking out | `ic_weather_cloudy` |
| `OVERCAST` | Chubby 3D pastel gray-blue cloud with a calm peaceful sleeping face | `ic_weather_overcast` |
| `RAIN` | Cute puffy cloud with small glistening 3D raindrop beads falling | `ic_weather_rain` |
| `SLEET` | Cute cloud with both falling raindrops and tiny marshmallow snowflake crystals | `ic_weather_sleet` |
| `SNOW` | Cute white puffy cloud with soft 3D marshmallow snowflake crystals | `ic_weather_snow` |
| `SHOWER` | Dynamic rain cloud with brisk diagonal 3D raindrops | `ic_weather_shower` |
| `UNKNOWN` | Cute pastel cloud with a subtle question mark | `ic_weather_unknown` |

### Asset Technical Specs
- Format: PNG / WebP (transparent background, trimmed tight bounding box with equal square aspect ratio).
- Target dimensions: 192×192px or 256×256px (crisp on xxxhdpi devices like Galaxy S25).

## Component Changes

### 1. Data Layer (`WeatherCondition.kt`)
- Add `@DrawableRes val iconRes: Int` parameter to `WeatherCondition` enum constructor.
- Map each condition to its respective `R.drawable.ic_weather_*`.
- Retain `val emoji: String` for logging and string representation.

### 2. Widget Layer (`TokiWeatherWidget.kt`)
- In `WeatherContent`:
  - Today weather icon: Replace `Text(...)` with `Image(provider = ImageProvider(weather.currentCondition.iconRes), contentDescription = weather.currentCondition.label, modifier = GlanceModifier.size(todayIconSize))`
    - `todayIconSize = if (isCompact) 28.dp else 32.dp`
  - Tomorrow forecast icon: Replace `Text(...)` with `Image(provider = ImageProvider(weather.tomorrowCondition.iconRes), contentDescription = weather.tomorrowCondition.label, modifier = GlanceModifier.size(forecastIconSize))`
    - `forecastIconSize = if (isCompact) 18.dp else 20.dp`
  - Day after forecast icon: Replace `Text(...)` with `Image(provider = ImageProvider(weather.dayAfterCondition.iconRes), contentDescription = weather.dayAfterCondition.label, modifier = GlanceModifier.size(forecastIconSize))`
- Ensure `contentScale = ContentScale.Fit` or default container sizing maintains proper aspect ratio.

### 3. App UI Layer (`MainActivity.kt`)
- In `WidgetPreviewBox`:
  - Today icon: Replace Compose `Text(weather.currentCondition.emoji, fontSize = todayEmojiSize)` with `Image(painter = painterResource(weather.currentCondition.iconRes), contentDescription = weather.currentCondition.label, modifier = Modifier.size(todayIconSizeDp))`
  - Tomorrow / Day After icons: Replace Compose `Text(...)` with `Image(painter = painterResource(condition.iconRes), contentDescription = condition.label, modifier = Modifier.size(forecastIconSizeDp))`
  - Adjust dp sizes to match the visual footprint of the previous text emojis without disrupting text alignment.

## Verification Plan
1. **Asset Integrity**: Verify all 8 drawable assets are present, transparent, and non-empty.
2. **Build Verification**: Run `./gradlew assembleDebug` to confirm zero compilation errors.
3. **Preview & Layout Verification**: Verify that in `WidgetPreviewBox`, all conditions render clearly with no clipping across different grid selections (4, 5, 6 cols).
4. **Git Verification**: Commit spec and implementation to git and push to `origin/main`.
