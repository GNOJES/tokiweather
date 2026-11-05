package com.toki.weather.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.webkit.GeolocationPermissions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 네이버 날씨 초단기 강수예측 레이더 지도 전체 화면 웹뷰
 * URL: https://weather.naver.com/map?visualMapType=maple
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RadarWebViewScreen(
    url: String = "https://weather.naver.com/map?visualMapType=maple",
    onBackPressed: () -> Unit = {}
) {
    val context = LocalContext.current
    val locationPermission = remember { RadarLocationPermission() }
    var permissionRequestInFlight by remember { mutableStateOf(false) }
    var showLocationDenied by remember { mutableStateOf(false) }
    fun hasLocationPermission(): Boolean = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION
    ).any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        permissionRequestInFlight = false
        val granted = hasLocationPermission()
        if (locationPermission.complete(granted) && !granted) showLocationDenied = true
    }
    DisposableEffect(locationPermission) {
        onDispose { locationPermission.cancel() }
    }
    var webView: WebView? by remember { mutableStateOf(null) }
    var loadState by remember { mutableStateOf(RadarLoadState()) }
    var navigation by remember { mutableStateOf(0) }
    LaunchedEffect(navigation) {
        delay(20_000)
        if (loadState.loading) {
            loadState = loadState.failed(true)
            webView?.stopLoading()
        }
    }

    BackHandler { onBackPressed() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        setGeolocationEnabled(true)
                        domStorageEnabled = true
                        databaseEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            locationPermission.cancel()
                            showLocationDenied = false
                            super.onPageStarted(view, url, favicon)
                            loadState = RadarLoadState()
                            navigation++
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            loadState = loadState.finished()
                        }
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            loadState = loadState.failed(request?.isForMainFrame == true)
                        }
                        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                            loadState = loadState.failed(request?.isForMainFrame == true)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String, callback: GeolocationPermissions.Callback
                        ) {
                            val needsPermission = locationPermission.request(
                                origin, this@apply.url, hasLocationPermission()
                            ) { allowed -> callback.invoke(origin, allowed, false) }
                            if (needsPermission && !permissionRequestInFlight) {
                                permissionRequestInFlight = true
                                permissionLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ))
                            }
                        }

                        override fun onGeolocationPermissionsHidePrompt() {
                            locationPermission.cancel()
                        }
                    }
                    loadUrl(url)
                    webView = this
                }
            },
            onRelease = { view ->
                locationPermission.cancel()
                webView = null
                view.stopLoading()
                view.webChromeClient = null
                view.webViewClient = WebViewClient()
                view.removeAllViews()
                view.destroy()
            },
            update = {
                // Keep reference
                webView = it
            }
        )

        if (showLocationDenied) {
            AlertDialog(
                onDismissRequest = { showLocationDenied = false },
                title = { Text("위치 권한이 필요합니다") },
                text = { Text("네이버 지도에서 현재 위치를 찾으려면 앱의 위치 권한을 허용해 주세요. 허용 후 지도에서 현재 위치 버튼을 다시 눌러 주세요.") },
                confirmButton = {
                    TextButton(onClick = {
                        showLocationDenied = false
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")))
                    }) { Text("앱 설정") }
                },
                dismissButton = {
                    TextButton(onClick = { showLocationDenied = false }) { Text("닫기") }
                }
            )
        }
        if (loadState.error) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("초단기 지도를 불러오지 못했습니다.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    loadState = RadarLoadState()
                    navigation++
                    webView?.loadUrl(url)
                }) { Text("다시 시도") }
            }
        }
        if (loadState.loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
