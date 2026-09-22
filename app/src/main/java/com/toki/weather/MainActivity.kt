package com.toki.weather

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.toki.weather.data.cache.WeatherDataStore
import com.toki.weather.data.model.CachedWeather
import com.toki.weather.data.repository.WeatherRepository
import com.toki.weather.ui.screen.RadarWebViewScreen
import com.toki.weather.ui.screen.SettingsScreen
import com.toki.weather.ui.screen.WeatherPlaceholderScreen
import com.toki.weather.widget.TokiWeatherWidget
import com.toki.weather.widget.TokiWeatherWidgetLarge
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
    val cachedWeather by dataStore.weatherFlow.collectAsState(initial = CachedWeather.EMPTY)
    var isRefreshing by remember { mutableStateOf(false) }

    // 뒤로가기 제어: 1번(초단기), 2번(설정) 탭일 때는 0번(날씨) 탭으로 이동
    // 0번(날씨) 탭일 때는 앱 종료(시스템 기본 동작)
    BackHandler(enabled = selectedTab != 0) {
        onTabSelected(0)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    icon = {
                        Image(
                            painter = painterResource(
                                if (selectedTab == 0) R.drawable.ic_tab_weather_selected
                                else R.drawable.ic_tab_weather
                            ),
                            contentDescription = "날씨",
                            modifier = Modifier
                                .size(30.dp)
                                .alpha(if (selectedTab == 0) 1.0f else 0.6f)
                        )
                    },
                    label = {
                        Text(
                            text = "날씨",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = {
                        Image(
                            painter = painterResource(
                                if (selectedTab == 1) R.drawable.ic_tab_radar_selected
                                else R.drawable.ic_tab_radar
                            ),
                            contentDescription = "초단기",
                            modifier = Modifier
                                .size(30.dp)
                                .alpha(if (selectedTab == 1) 1.0f else 0.6f)
                        )
                    },
                    label = {
                        Text(
                            text = "초단기",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { onTabSelected(2) },
                    icon = {
                        Image(
                            painter = painterResource(
                                if (selectedTab == 2) R.drawable.ic_tab_settings_selected
                                else R.drawable.ic_tab_settings
                            ),
                            contentDescription = "설정",
                            modifier = Modifier
                                .size(30.dp)
                                .alpha(if (selectedTab == 2) 1.0f else 0.6f)
                        )
                    },
                    label = {
                        Text(
                            text = "설정",
                            fontSize = 12.sp,
                            fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
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
                    onRefresh = {
                        coroutineScope.launch {
                            isRefreshing = true
                            try {
                                withContext(Dispatchers.IO) {
                                    val repo = WeatherRepository(context)
                                    repo.fetchAndSave()
                                }
                                TokiWeatherWidget().updateAll(context)
                                TokiWeatherWidgetLarge().updateAll(context)
                                Toast.makeText(context, "날씨 정보가 갱신되었습니다.", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "날씨 갱신 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isRefreshing = false
                            }
                        }
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
