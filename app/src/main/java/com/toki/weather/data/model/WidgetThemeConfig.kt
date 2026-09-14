package com.toki.weather.data.model

data class WidgetThemeConfig(
    val backgroundColorHex: String = "#1A1A2E",
    val backgroundAlpha: Float = 0.8f,
    val textColorHex: String = "#FFFFFF",
    val isShadowEnabled: Boolean = true
) {
    companion object {
        val DEFAULT = WidgetThemeConfig()
        val PRESET_COLORS = listOf(
            "#1A1A2E" to "네이비 다크",
            "#212121" to "차콜 그레이",
            "#2C3E50" to "미드나잇",
            "#0F2027" to "딥 블루"
        )
    }
}
