package com.toki.weather

import android.content.Intent
import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.repository.WeatherRepository
import com.toki.weather.ui.screen.RadarWebViewScreen
import com.toki.weather.ui.screen.SettingsScreen
import com.toki.weather.ui.screen.WeatherPlaceholderScreen
import com.toki.weather.widget.TokiWeatherWidget
import com.toki.weather.widget.TokiWeatherWidgetLarge
import com.toki.weather.worker.WidgetDisplayException
import com.toki.weather.worker.requestWeatherWidgetDisplay
import com.toki.weather.data.model.RefreshSource
import com.toki.weather.worker.refreshAndUpdate
import com.toki.weather.worker.WeatherWorkScheduler
import com.toki.weather.util.LocationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var initialWidgetType by mutableStateOf<String?>(null)
    private var selectedTab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initialWidgetType = intent?.getStringExtra("widget_type")
        selectedTab = 0
        lifecycleScope.launch(Dispatchers.IO) {
            WeatherWorkScheduler.ensureScheduled(applicationContext)
        }

        setContent {
            MaterialTheme {
                MainScreen(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    initialWidgetType = initialWidgetType
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialWidgetType = intent.getStringExtra("widget_type")
        selectedTab = 0
    }
}

@Composable
fun MainScreen(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    initialWidgetType: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dataStore = remember { WeatherDataStore(context) }
    val loadedWeather by dataStore.weatherFlow.collectAsState(initial = null)
    val cachedWeather = loadedWeather ?: CachedWeather.EMPTY
    val hourlyIntervalHours by dataStore.hourlyIntervalFlow.collectAsState(initial = 1)
    var isRefreshing by remember { mutableStateOf(false) }
    var initialRefreshAttempted by remember { mutableStateOf(false) }

    var pendingRefreshSource by remember { mutableStateOf(RefreshSource.MANUAL) }
    val refreshWeather: (RefreshSource) -> Unit = { source ->
        if (!isRefreshing) {
            isRefreshing = true
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        refreshAndUpdate(
                            fetch = { WeatherRepository(context).fetchAndSave(source = source,
                                                    onWeatherSaved = { requestWeatherWidgetDisplay(context) }) },
                            updateWidgets = {
                                requestWeatherWidgetDisplay(context)
                            }
                        )
                    }
                    Toast.makeText(context, "날씨 정보가 갱신되었습니다.", Toast.LENGTH_SHORT).show()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: WidgetDisplayException) {
                    Toast.makeText(context, "날씨는 갱신됐습니다. 위젯 표시를 다시 시도합니다.", Toast.LENGTH_LONG).show()
                } catch (_: Exception) {
                    Toast.makeText(context, "날씨 갱신에 실패했습니다. 잠시 후 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                } finally {
                    isRefreshing = false
                }
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) refreshWeather(pendingRefreshSource)
        else Toast.makeText(context, "현재 동네 날씨를 보려면 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }
    val refreshWithPermission: (RefreshSource) -> Unit = { source ->
        pendingRefreshSource = source
        if (LocationHelper.hasLocationPermission(context)) refreshWeather(pendingRefreshSource)
        else locationPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    androidx.compose.runtime.LaunchedEffect(loadedWeather?.lastUpdated, loadedWeather?.locationName) {
        if (loadedWeather != null &&
            (loadedWeather?.lastUpdated == 0L || loadedWeather?.locationName == "설정 위치") &&
            !initialRefreshAttempted
        ) {
            initialRefreshAttempted = true
            refreshWithPermission(RefreshSource.INITIAL)
        }
    }

    // 뒤로가기 제어: 1번(초단기), 2번(설정) 탭일 때는 0번(날씨) 탭으로 이동
    // 0번(날씨) 탭일 때는 앱 종료(시스템 기본 동작)
    BackHandler(enabled = selectedTab != 0) {
        onTabSelected(0)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .height(64.dp)
            ) {
                val tabs = listOf(
                    Triple(R.drawable.ic_tab_weather, R.drawable.ic_tab_weather_selected, "날씨"),
                    Triple(R.drawable.ic_tab_radar, R.drawable.ic_tab_radar_selected, "초단기"),
                    Triple(R.drawable.ic_tab_settings, R.drawable.ic_tab_settings_selected, "설정")
                )

                tabs.forEachIndexed { index, (unselectedIcon, selectedIcon, label) ->
                    val isSelected = selectedTab == index
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .selectable(
                                selected = isSelected,
                                onClick = { onTabSelected(index) },
                                role = Role.Tab,
                                interactionSource = interactionSource,
                                indication = null
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 112.dp, height = 64.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else Color.Transparent
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(if (isSelected) selectedIcon else unselectedIcon),
                                contentDescription = label,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (selectedTab) {
                0 -> WeatherPlaceholderScreen(
                    weather = cachedWeather,
                    isRefreshing = isRefreshing,
                    onRefresh = { refreshWithPermission(RefreshSource.MANUAL) },
                    hourlyIntervalHours = hourlyIntervalHours,
                    onHourlyIntervalSelected = { hours ->
                        coroutineScope.launch { dataStore.saveHourlyInterval(hours) }
                    }
                )
                1 -> RadarWebViewScreen(
                    onBackPressed = { onTabSelected(0) }
                )
                2 -> SettingsScreen(
                    initialWidgetType = initialWidgetType,
                    onSaved = { (context as? android.app.Activity)?.finish() }
                )
            }
        }
    }
}
