package com.toki.weather.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import com.toki.weather.R
import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.model.WeatherCondition
import com.toki.weather.data.model.WidgetThemeConfig
import com.toki.weather.data.repository.WeatherRepository
import com.toki.weather.util.DateTimeUtils
import com.toki.weather.util.LocationHelper
import com.toki.weather.widget.TokiWeatherWidget
import com.toki.weather.widget.TokiWeatherWidgetLarge
import com.toki.weather.worker.WeatherWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WidgetPreset(
    val title: String,
    val subtitle: String,
    val tag: String
) {
    SAMSUNG_2X1("📱 삼성 One UI (2×1)", "기본 가로형 (5:3:3)", "2x1"),
    NOVA_3X2("🚀 노바런처 8×8 (3×2)", "아내 폰 맞춤 세로형 (2단)", "3x2")
}

@Composable
fun SettingsScreen(
    initialWidgetType: String? = null,
    onSaved: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dataStore = remember { WeatherDataStore(context) }

    val cachedWeather by dataStore.weatherFlow.collectAsState(initial = CachedWeather.EMPTY)
    val savedTheme by dataStore.themeFlow.collectAsState(initial = WidgetThemeConfig.DEFAULT)
    val savedInterval by dataStore.updateIntervalFlow.collectAsState(initial = 30)
    val savedCustomLocation by dataStore.customLocationNameFlow.collectAsState(initial = "")

    // 편집 중인 임시 상태
    var currentBgColor by remember { mutableStateOf(WidgetThemeConfig.DEFAULT.backgroundColorHex) }
    var currentAlpha by remember { mutableFloatStateOf(WidgetThemeConfig.DEFAULT.backgroundAlpha) }
    var currentTextColor by remember { mutableStateOf(WidgetThemeConfig.DEFAULT.textColorHex) }
    var currentInterval by remember { mutableIntStateOf(30) }
    var customLocationName by remember { mutableStateOf("") }
    var selectedPreset by remember {
        mutableStateOf(
            if (initialWidgetType == "3x2" || initialWidgetType == "large") {
                WidgetPreset.NOVA_3X2
            } else {
                WidgetPreset.SAMSUNG_2X1
            }
        )
    }
    var showBgColorPicker by remember { mutableStateOf(false) }
    var showTextColorPicker by remember { mutableStateOf(false) }

    LaunchedEffect(initialWidgetType) {
        if (initialWidgetType == "3x2" || initialWidgetType == "large") {
            selectedPreset = WidgetPreset.NOVA_3X2
        } else if (initialWidgetType == "2x1") {
            selectedPreset = WidgetPreset.SAMSUNG_2X1
        }
    }

    // DataStore에서 로드되면 상태 동기화
    LaunchedEffect(savedTheme) {
        currentBgColor = savedTheme.backgroundColorHex
        currentAlpha = savedTheme.backgroundAlpha
        currentTextColor = savedTheme.textColorHex
    }
    LaunchedEffect(savedInterval) {
        currentInterval = savedInterval
    }
    LaunchedEffect(savedCustomLocation) {
        customLocationName = savedCustomLocation
    }

    // 1. 기본 위치 권한 (Fine / Coarse) 상태
    var hasForegroundLocation by remember {
        mutableStateOf(LocationHelper.hasLocationPermission(context))
    }

    // 2. 백그라운드 위치 권한 (항상 허용) 상태
    var hasBackgroundLocation by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasForegroundLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasForegroundLocation) {
            Toast.makeText(context, "위치 권한이 승인되었습니다.", Toast.LENGTH_SHORT).show()
            WeatherWorkScheduler.runOnce(context)
        }
    }

    val backgroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasBackgroundLocation = isGranted
        if (isGranted) {
            Toast.makeText(context, "백그라운드 위치(항상 허용)가 승인되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "앱 설정에서 '항상 허용'을 선택해주셔야 백그라운드 자동 갱신이 가능합니다.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // [상단 고정 헤더: 스크롤해도 항상 고정 유지]
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "토끼날씨 위젯 설정",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                val updatedTheme = WidgetThemeConfig(
                                    backgroundColorHex = currentBgColor,
                                    backgroundAlpha = currentAlpha,
                                    textColorHex = currentTextColor
                                )
                                dataStore.saveTheme(updatedTheme)
                                dataStore.saveUpdateInterval(currentInterval)
                                dataStore.saveCustomLocationName(customLocationName.trim())

                                Toast.makeText(context, "설정을 저장하고 날씨를 새로고침합니다.", Toast.LENGTH_SHORT).show()

                                // 즉시 날씨 및 위치 새로고침 실행
                                withContext(Dispatchers.IO) {
                                    val repo = WeatherRepository(context)
                                    repo.fetchAndSave()
                                }

                                WeatherWorkScheduler.schedule(context, currentInterval.toLong())
                                TokiWeatherWidget().updateAll(context)
                                TokiWeatherWidgetLarge().updateAll(context)

                                if (onSaved != null) {
                                    onSaved()
                                } else {
                                    (context as? android.app.Activity)?.finish()
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("저장", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 1-A. 기본 위치 권한 안내 배너
            if (!hasForegroundLocation) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "위치 권한이 필요합니다",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "현재 계신 동네의 날씨를 보려면 위치 권한을 허용해주세요.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = {
                                foregroundPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        ) {
                            Text("허용")
                        }
                    }
                }
            } else if (!hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 1-B. 백그라운드 '항상 허용' 권한 안내 배너
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "위치 '항상 허용' 설정",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "이동할 때 화면을 켜지 않아도 동네가 자동 갱신되도록 '항상 허용'을 권장합니다.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    // Android 11+는 설정의 권한 페이지로 바로 안내하는 것이 가장 안정적
                                    try {
                                        backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    } catch (_: Exception) {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    }
                                } else {
                                    backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                }
                            }
                        ) {
                            Text("설정")
                        }
                    }
                }
            }

            // 2. 실시간 위젯 미리보기
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "위젯 미리보기",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Text(
                            text = selectedPreset.subtitle,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 위젯 규격 탭 선택 (삼성 2×1 vs 노바 3×2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetPreset.values().forEach { preset ->
                            val isSelected = selectedPreset == preset
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedPreset = preset },
                                label = {
                                    Text(
                                        text = preset.title,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    WidgetPreviewBox(
                        weather = cachedWeather,
                        customLocationName = customLocationName,
                        bgColorHex = currentBgColor,
                        bgAlpha = currentAlpha,
                        textColorHex = currentTextColor,
                        preset = selectedPreset
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. 위치 표기 설정 (동네 이름 직접 지정 / GPS 재측정)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "현재 위치 표기",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    Toast.makeText(context, "GPS 위치 재측정 중...", Toast.LENGTH_SHORT).show()
                                    withContext(Dispatchers.IO) {
                                        val repo = WeatherRepository(context)
                                        repo.fetchAndSave()
                                    }
                                    TokiWeatherWidget().updateAll(context)
                                    TokiWeatherWidgetLarge().updateAll(context)
                                    Toast.makeText(context, "위치 및 날씨가 갱신되었습니다.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_location_pin),
                                contentDescription = "재측정",
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("GPS 재측정", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = customLocationName,
                        onValueChange = { customLocationName = it },
                        label = { Text("동네 이름 (비워두면 GPS 자동 감지)", fontSize = 12.sp) },
                        placeholder = { Text(cachedWeather.locationName.ifBlank { "영등포동7가" }, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (customLocationName.isNotBlank()) {
                                IconButton(onClick = { customLocationName = "" }) {
                                    Icon(
                                        painter = painterResource(android.R.drawable.ic_menu_close_clear_cancel),
                                        contentDescription = "지우기",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (customLocationName.isNotBlank()) {
                            "현재 '${customLocationName}'(으)로 고정 표기됩니다."
                        } else {
                            "현재 GPS 감지 위치: '${cachedWeather.locationName}'"
                        },
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. 갱신 주기 옵션
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(text = "날씨 및 GPS 갱신 주기", fontWeight = FontWeight.Bold)
                    Text(
                        text = "기상청 데이터 주기 및 배터리 절약을 고려하여 30분을 권장합니다.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            15 to "15분",
                            30 to "30분 (추천)",
                            60 to "1시간"
                        ).forEach { (minutes, label) ->
                            FilterChip(
                                selected = currentInterval == minutes,
                                onClick = { currentInterval = minutes },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. 투명도 슬라이더
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "배경 투명도", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${(currentAlpha * 100).toInt()}%",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = currentAlpha,
                        onValueChange = { currentAlpha = it },
                        valueRange = 0.0f..1.0f,
                        steps = 19
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("완전 투명 (0%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        Text("완전 불투명 (100%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. 배경색 프리셋 선택
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "배경 색상", fontWeight = FontWeight.Bold)
                        val isCustomBg = WidgetThemeConfig.PRESET_COLORS.none { it.first.equals(currentBgColor, ignoreCase = true) }
                        if (isCustomBg) {
                            Text(
                                text = "직접 지정: $currentBgColor",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val rainbowSweep = remember {
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFFE53935),
                                Color(0xFFFB8C00),
                                Color(0xFFFDD835),
                                Color(0xFF43A047),
                                Color(0xFF00ACC1),
                                Color(0xFF1E88E5),
                                Color(0xFF8E24AA),
                                Color(0xFFE53935)
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        WidgetThemeConfig.PRESET_COLORS.forEach { (hex, label) ->
                            val isSelected = currentBgColor.equals(hex, ignoreCase = true)
                            val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Black }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { currentBgColor = hex }
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = label, fontSize = 10.sp)
                            }
                        }

                        // 5번째 슬롯: 무지개 그라데이션 원 (직접 선택)
                        val isCustomBg = WidgetThemeConfig.PRESET_COLORS.none { it.first.equals(currentBgColor, ignoreCase = true) }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { showBgColorPicker = true }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(rainbowSweep)
                                    .border(
                                        width = if (isCustomBg) 3.dp else 1.dp,
                                        color = if (isCustomBg) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCustomBg) {
                                    Text(
                                        text = "✓",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "직접 선택",
                                fontSize = 10.sp,
                                fontWeight = if (isCustomBg) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCustomBg) MaterialTheme.colorScheme.primary else Color.Unspecified
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 6. 글자 색상 선택
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "글자 색상", fontWeight = FontWeight.Bold)

                    val isWhite = currentTextColor.equals("#FFFFFF", ignoreCase = true)
                    val isDark = currentTextColor.equals("#1A1A1A", ignoreCase = true) || currentTextColor.equals("#1C1C1E", ignoreCase = true)
                    val isCustomText = !isWhite && !isDark

                    val rainbowSweep = remember {
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFFE53935),
                                Color(0xFFFB8C00),
                                Color(0xFFFDD835),
                                Color(0xFF43A047),
                                Color(0xFF00ACC1),
                                Color(0xFF1E88E5),
                                Color(0xFF8E24AA),
                                Color(0xFFE53935)
                            )
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = isWhite,
                            onClick = { currentTextColor = "#FFFFFF" },
                            label = { Text("화이트", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = isDark,
                            onClick = { currentTextColor = "#1C1C1E" },
                            label = { Text("다크", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = isCustomText,
                            onClick = { showTextColorPicker = true },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val customColor = try { Color(android.graphics.Color.parseColor(currentTextColor)) } catch (_: Exception) { Color.Gray }
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (isCustomText) Modifier.background(customColor) else Modifier.background(rainbowSweep)
                                            )
                                            .border(1.dp, Color.LightGray, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("직접 선택", fontSize = 12.sp)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Spacer(modifier = Modifier.height(24.dp))

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 배경 색상 직접 선택 다이얼로그
        if (showBgColorPicker) {
            ColorPickerDialog(
                title = "배경 색상 직접 선택",
                initialColorHex = currentBgColor,
                onDismiss = { showBgColorPicker = false },
                onColorSelected = { newHex ->
                    currentBgColor = newHex
                }
            )
        }

        // 글자 색상 직접 선택 다이얼로그
        if (showTextColorPicker) {
            ColorPickerDialog(
                title = "글자 색상 직접 선택",
                initialColorHex = currentTextColor,
                onDismiss = { showTextColorPicker = false },
                onColorSelected = { newHex ->
                    currentTextColor = newHex
                }
            )
        }
    }
}

/**
 * 홈 화면 실시간 2×1 위젯 미리보기
 * - Galaxy S25 해상도 및 One UI 런처 가로(4,5,6열) / 세로(4,5,6,7행) 그리드 기준 실측 크기 반영
 * - Solution 1: 상하 늘어짐(vertical weight) 없이 고정된 안전 높이를 중앙 정렬(Arrangement.Center)로 배치
 * - 오늘 5 : 내일 3 : 모레 3 황금 비율 및 강수확률 바 수평 동기화
 */
@Composable
fun WidgetPreviewBox(
    weather: CachedWeather,
    customLocationName: String = "",
    bgColorHex: String,
    bgAlpha: Float,
    textColorHex: String,
    preset: WidgetPreset = WidgetPreset.SAMSUNG_2X1
) {
    val currentDensity = LocalDensity.current

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = 1.0f
        )
    ) {
        val parsedBg = try { Color(android.graphics.Color.parseColor(bgColorHex)) } catch (_: Exception) { Color(0xFF261643) }
        val parsedText = try { Color(android.graphics.Color.parseColor(textColorHex)) } catch (_: Exception) { Color.White }
        val subText = parsedText.copy(alpha = 0.75f)

        val displayLocName = when {
            customLocationName.isNotBlank() -> customLocationName
            weather.lastUpdated > 0 && weather.locationName.isNotBlank() -> weather.locationName
            else -> "영등포동7가"
        }

        val currentCondition = if (weather.lastUpdated > 0) weather.currentCondition else WeatherCondition.CLEAR
        val tomorrowCondition = if (weather.lastUpdated > 0) weather.tomorrowCondition else WeatherCondition.OVERCAST
        val dayAfterCondition = if (weather.lastUpdated > 0) weather.dayAfterCondition else WeatherCondition.CLOUDY

        // 스마트폰 배경화면 시뮬레이션 컨테이너
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF3A6073))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (preset == WidgetPreset.SAMSUNG_2X1) {
                // ─── 1. 삼성 One UI 2×1 가로형 위젯 (184×96dp) ───
                val previewWidth = 184.dp
                val previewHeight = 96.dp
                val horizPadding = 10.dp
                val vertPadding = 5.dp
                val vertSpacer = 3.dp
                val interColSpacer = 6.dp
                val forecastSpacer = 4.dp

                val totalContentWidth = previewWidth - (horizPadding * 2) - interColSpacer
                val todayWidth = totalContentWidth * (5f / 11f)
                val rightWidth = totalContentWidth * (6f / 11f)
                val forecastItemWidth = (rightWidth - forecastSpacer) / 2f

                val todayIconSize = 30.dp
                val todayTempSize = 17.sp
                val todayPmSize = 9.5.sp
                val locNameSize = 9.5.sp
                val forecastIconSize = 18.dp
                val subTempSize = 8.sp
                val popBlockSize = 3.5.dp

                Box(
                    modifier = Modifier
                        .width(previewWidth)
                        .heightIn(min = previewHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(parsedBg.copy(alpha = bgAlpha))
                        .padding(horizontal = horizPadding, vertical = vertPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // [상단: 날씨 정보 행] 오늘 5 : 내일 3 : 모레 3 비율
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 좌측: 오늘 (폭 = todayWidth, 5/11)
                            Column(
                                modifier = Modifier.width(todayWidth),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(currentCondition.iconRes),
                                    contentDescription = currentCondition.label,
                                    modifier = Modifier.size(todayIconSize)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (weather.lastUpdated > 0) "${weather.currentTemp}°" else "25°",
                                    fontSize = todayTempSize,
                                    fontWeight = FontWeight.Bold,
                                    color = parsedText,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val pm10Val = if (weather.lastUpdated > 0 && weather.pm10 >= 0) weather.pm10 else 42
                                    val pm25Val = if (weather.lastUpdated > 0 && weather.pm25 >= 0) weather.pm25 else 41
                                    val pm10Color = if (weather.lastUpdated > 0 && weather.pm10 >= 0) weather.getPm10Color() else Color(0xFF43A047)
                                    val pm25Color = if (weather.lastUpdated > 0 && weather.pm25 >= 0) weather.getPm25Color() else Color(0xFFFB8C00)

                                    Text(text = "$pm10Val", fontSize = todayPmSize, fontWeight = FontWeight.Bold, color = pm10Color, maxLines = 1)
                                    Text(text = " · ", fontSize = (todayPmSize.value - 1).sp, fontWeight = FontWeight.Bold, color = subText, maxLines = 1)
                                    Text(text = "$pm25Val", fontSize = todayPmSize, fontWeight = FontWeight.Bold, color = pm25Color, maxLines = 1)
                                }
                            }

                            Spacer(modifier = Modifier.width(interColSpacer))

                            // 우측: 상단 지역명 + 하단 내일/모레 예보 (폭 = rightWidth, 6/11)
                            Column(
                                modifier = Modifier.width(rightWidth),
                                horizontalAlignment = Alignment.End
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(end = 5.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_location_pin),
                                        contentDescription = "위치",
                                        tint = subText,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(1.5.dp))
                                    Text(
                                        text = displayLocName,
                                        fontSize = locNameSize,
                                        fontWeight = FontWeight.Bold,
                                        color = subText,
                                        maxLines = 1,
                                        textAlign = TextAlign.End,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 내일
                                    Column(
                                        modifier = Modifier.width(forecastItemWidth),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "내일", fontSize = (subTempSize.value - 0.5f).sp, color = subText, maxLines = 1)
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Image(painter = painterResource(tomorrowCondition.iconRes), contentDescription = tomorrowCondition.label, modifier = Modifier.size(forecastIconSize))
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(text = if (weather.lastUpdated > 0) "${weather.tomorrowMin}~${weather.tomorrowMax}°" else "18~27°", fontSize = subTempSize, color = parsedText, maxLines = 1, softWrap = false)
                                    }

                                    Spacer(modifier = Modifier.width(forecastSpacer))

                                    // 모레
                                    Column(
                                        modifier = Modifier.width(forecastItemWidth),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "모레", fontSize = (subTempSize.value - 0.5f).sp, color = subText, maxLines = 1)
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Image(painter = painterResource(dayAfterCondition.iconRes), contentDescription = dayAfterCondition.label, modifier = Modifier.size(forecastIconSize))
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(text = if (weather.lastUpdated > 0) "${weather.dayAfterMin}~${weather.dayAfterMax}°" else "19~29°", fontSize = subTempSize, color = parsedText, maxLines = 1, softWrap = false)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(vertSpacer))

                        // [하단: 강수확률 바 행]
                        Row(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.width(todayWidth), contentAlignment = Alignment.Center) {
                                ComposePopBar(pop = if (weather.lastUpdated > 0) weather.todayPop else 10, textColor = parsedText, blockSize = popBlockSize)
                            }
                            Spacer(modifier = Modifier.width(interColSpacer))
                            Row(modifier = Modifier.width(rightWidth), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.width(forecastItemWidth), contentAlignment = Alignment.Center) {
                                    ComposePopBar(pop = if (weather.lastUpdated > 0) weather.tomorrowPop else 30, textColor = parsedText, blockSize = popBlockSize)
                                }
                                Spacer(modifier = Modifier.width(forecastSpacer))
                                Box(modifier = Modifier.width(forecastItemWidth), contentAlignment = Alignment.Center) {
                                    ComposePopBar(pop = if (weather.lastUpdated > 0) weather.dayAfterPop else 30, textColor = parsedText, blockSize = popBlockSize)
                                }
                            }
                        }
                    }
                }
            } else {
                // ─── 2. 노바런처 8×8 (3×2) 세로 2단 위젯 (138×187dp) ───
                val previewWidth = 138.dp
                val previewHeight = 187.dp

                Box(
                    modifier = Modifier
                        .width(previewWidth)
                        .height(previewHeight)
                        .clip(RoundedCornerShape(16.dp))
                        .background(parsedBg.copy(alpha = bgAlpha))
                        .padding(start = 7.dp, end = 7.dp, top = 3.dp, bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 1. 상단: 좌측 현재 날짜 + 우측 현재 위치 (모서리에서 중앙 쪽으로 살짝 이동)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = DateTimeUtils.todayDateWithDayOfWeekString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = subText,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                painter = painterResource(R.drawable.ic_location_pin),
                                contentDescription = "위치",
                                tint = subText,
                                modifier = Modifier.size(9.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = displayLocName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = subText,
                                maxLines = 1,
                                textAlign = TextAlign.End
                            )
                        }

                        // 2. 오늘 날씨: [아이콘] + [기온 & 강수확률] + [미세/초미세 동그라미 2열] (가운데 정렬)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = painterResource(currentCondition.iconRes),
                                    contentDescription = currentCondition.label,
                                    modifier = Modifier.size(57.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.padding(top = 2.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (weather.lastUpdated > 0) "${weather.currentTemp}°" else "27°",
                                        fontSize = 27.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = parsedText,
                                        maxLines = 1
                                    )
                                    val todayPopVal = if (weather.lastUpdated > 0) weather.todayPop else 10
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_rain_drop),
                                            contentDescription = "강수확률",
                                            tint = Color(0xFF4AA3FF),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.5.dp))
                                        Text(
                                            text = "$todayPopVal%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = subText,
                                            maxLines = 1
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                val pm10Color = if (weather.lastUpdated > 0 && weather.pm10 >= 0) weather.getPm10Color() else Color(0xFF4AA3FF)
                                val pm25Color = if (weather.lastUpdated > 0 && weather.pm25 >= 0) weather.getPm25Color() else Color(0xFF4AA3FF)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_circle_dot),
                                        contentDescription = "미세먼지",
                                        tint = pm10Color,
                                        modifier = Modifier.size(8.dp)
                                    )
                                    Spacer(modifier = Modifier.height(5.dp))
                                    Icon(
                                        painter = painterResource(R.drawable.ic_circle_dot),
                                        contentDescription = "초미세먼지",
                                        tint = pm25Color,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(5.dp))
                        }

                        // 3. 하단: 내일 & 모레 예보 (좌우 50% 균등 분할)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 내일 (50%)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "내일", fontSize = 10.5.sp, color = subText, maxLines = 1)
                                Spacer(modifier = Modifier.height(1.dp))
                                Image(
                                    painter = painterResource(tomorrowCondition.iconRes),
                                    contentDescription = tomorrowCondition.label,
                                    modifier = Modifier.size(29.dp)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (weather.lastUpdated > 0) "${weather.tomorrowMin}° / ${weather.tomorrowMax}°" else "19° / 30°",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = parsedText,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                ComposePopBar(
                                    pop = if (weather.lastUpdated > 0) weather.tomorrowPop else 20,
                                    textColor = parsedText,
                                    blockSize = 4.dp
                                )
                            }

                            // 모레 (50%)
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "모레", fontSize = 10.5.sp, color = subText, maxLines = 1)
                                Spacer(modifier = Modifier.height(1.dp))
                                Image(
                                    painter = painterResource(dayAfterCondition.iconRes),
                                    contentDescription = dayAfterCondition.label,
                                    modifier = Modifier.size(29.dp)
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Text(
                                    text = if (weather.lastUpdated > 0) "${weather.dayAfterMin}° / ${weather.dayAfterMax}°" else "19° / 29°",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = parsedText,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                ComposePopBar(
                                    pop = if (weather.lastUpdated > 0) weather.dayAfterPop else 10,
                                    textColor = parsedText,
                                    blockSize = 4.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 4칸 정사각형 강수확률 가로 막대바 (Compose 미리보기용)
 * 0칸: 0~19%, 1칸: 20~39%, 2칸: 40~59%, 3칸: 60~79%, 4칸: 80~100%
 */
@Composable
fun ComposePopBar(
    pop: Int,
    textColor: Color,
    blockSize: androidx.compose.ui.unit.Dp = 4.dp
) {
    val filled = when {
        pop < 20 -> 0
        pop < 40 -> 1
        pop < 60 -> 2
        pop < 80 -> 3
        else -> 4
    }
    val activeColor = Color(0xFF4AA3FF)
    val inactiveColor = textColor.copy(alpha = 0.25f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        for (i in 0 until 4) {
            Box(
                modifier = Modifier
                    .size(blockSize)
                    .clip(RoundedCornerShape(1.dp))
                    .background(if (i < filled) activeColor else inactiveColor)
            )
            if (i < 3) {
                Spacer(modifier = Modifier.width(1.5.dp))
            }
        }
    }
}

/**
 * 고감도 2D 컬러 스펙트럼 및 밝기 슬라이더를 지원하는 컬러 피커 다이얼로그
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initialColorHex: String,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val initialColorInt = try {
        android.graphics.Color.parseColor(initialColorHex)
    } catch (_: Exception) {
        android.graphics.Color.parseColor("#261643")
    }

    val initialHsv = remember(initialColorHex) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColorInt, hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) } // 0..360
    var sat by remember { mutableFloatStateOf(initialHsv[1]) } // 0..1
    var value by remember { mutableFloatStateOf(initialHsv[2]) } // 0..1

    val currentColorInt = remember(hue, sat, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
    }
    val currentComposeColor = remember(currentColorInt) { Color(currentColorInt) }
    val currentHex = remember(currentColorInt) {
        String.format("#%06X", 0xFFFFFF and currentColorInt)
    }

    var hexInput by remember { mutableStateOf(currentHex.removePrefix("#")) }

    LaunchedEffect(currentHex) {
        if (!hexInput.equals(currentHex.removePrefix("#"), ignoreCase = true)) {
            hexInput = currentHex.removePrefix("#")
        }
    }

    val quickColors = remember {
        listOf(
            "#E53935" to "레드",
            "#FB8C00" to "오렌지",
            "#FDD835" to "옐로우",
            "#43A047" to "그린",
            "#00ACC1" to "민트",
            "#1E88E5" to "블루",
            "#8E24AA" to "퍼플",
            "#261643" to "미드나잇",
            "#000000" to "블랙",
            "#FFFFFF" to "화이트"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                // 상단 우측: 현재 선택 색상 프리뷰 및 HEX 텍스트
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(currentComposeColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentHex,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 빠른 추천 기본 색상 스와치
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    quickColors.forEach { (hex, _) ->
                        val swatchColor = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Black }
                        val isSwatchSelected = currentHex.equals(hex, ignoreCase = true)

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSwatchSelected) 2.5.dp else 1.dp,
                                    color = if (isSwatchSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    val parsed = android.graphics.Color.parseColor(hex)
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(parsed, hsv)
                                    hue = hsv[0]
                                    sat = hsv[1]
                                    value = hsv[2]
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSwatchSelected) {
                                Text(
                                    text = "✓",
                                    color = if (swatchColor == Color.White) Color.Black else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // 2. 2D 컬러 스펙트럼 캔버스 (가로: 색상 Hue 0~360°, 세로: 채도 Saturation 0~1)
                var canvasWidth by remember { mutableFloatStateOf(1f) }
                var canvasHeight by remember { mutableFloatStateOf(1f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged {
                                canvasWidth = it.width.toFloat()
                                canvasHeight = it.height.toFloat()
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    if (canvasWidth > 0 && canvasHeight > 0) {
                                        hue = (offset.x / canvasWidth).coerceIn(0f, 1f) * 360f
                                        sat = (offset.y / canvasHeight).coerceIn(0f, 1f)
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    if (canvasWidth > 0 && canvasHeight > 0) {
                                        hue = (change.position.x / canvasWidth).coerceIn(0f, 1f) * 360f
                                        sat = (change.position.y / canvasHeight).coerceIn(0f, 1f)
                                    }
                                }
                            }
                    ) {
                        // 가로: 무지개 색상 스펙트럼
                        drawRect(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFF0000),
                                    Color(0xFFFFFF00),
                                    Color(0xFF00FF00),
                                    Color(0xFF00FFFF),
                                    Color(0xFF0000FF),
                                    Color(0xFFFF00FF),
                                    Color(0xFFFF0000)
                                )
                            )
                        )
                        // 세로: 상단 화이트(채도 0) -> 하단 투명(순수 색상 채도 1)
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.White, Color.Transparent)
                            )
                        )
                        // 명도(Value) 어둡기 오버레이
                        if (value < 1.0f) {
                            drawRect(
                                color = Color.Black.copy(alpha = (1.0f - value).coerceIn(0f, 1f))
                            )
                        }

                        // 커서(Thumb) 위치
                        val thumbX = (hue / 360f).coerceIn(0f, 1f) * size.width
                        val thumbY = sat.coerceIn(0f, 1f) * size.height
                        val cursorCenter = Offset(thumbX, thumbY)

                        // 원형 커서 그리기 (화이트 테두리 + 내부 선택 색상)
                        drawCircle(
                            color = Color.White,
                            radius = 12.dp.toPx(),
                            center = cursorCenter
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.35f),
                            radius = 12.dp.toPx(),
                            center = cursorCenter,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            color = currentComposeColor,
                            radius = 9.dp.toPx(),
                            center = cursorCenter
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. 밝기(명도) 슬라이더
                val pureHueSatColor = remember(hue, sat) {
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1.0f)))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "밝기",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Black,
                                        pureHueSatColor
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Slider(
                            value = value,
                            onValueChange = { value = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. 직접 HEX 코드 입력
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { input ->
                        val cleaned = input.filter { it.isLetterOrDigit() }.take(6).uppercase()
                        hexInput = cleaned
                        if (cleaned.length == 6) {
                            try {
                                val parsed = android.graphics.Color.parseColor("#$cleaned")
                                val hsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed, hsv)
                                hue = hsv[0]
                                sat = hsv[1]
                                value = hsv[2]
                            } catch (_: Exception) {}
                        }
                    },
                    label = { Text("직접 HEX 코드 입력", fontSize = 12.sp) },
                    placeholder = { Text("예: 261643", fontSize = 12.sp) },
                    prefix = { Text("#", fontWeight = FontWeight.Bold) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorSelected(currentHex)
                    onDismiss()
                }
            ) {
                Text("선택 완료")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
