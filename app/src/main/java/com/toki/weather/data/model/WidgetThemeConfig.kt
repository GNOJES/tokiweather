package com.toki.weather.data.model

/**
 * 위젯 스타일 커스텀 설정
 */
data class WidgetThemeConfig(
    val backgroundColorHex: String = "#261643", // 기본 미드나잇 퍼플
    val backgroundAlpha: Float = 0.8f,          // 0.0(완전 투명) ~ 1.0(불투명)
    val textColorHex: String = "#FFFFFF",       // 기본 화이트
    val isShadowEnabled: Boolean = true         // 음영 효과 사용 여부
) {
    companion object {
        val DEFAULT = WidgetThemeConfig()

        // 배경 색상 기본 프리셋 (4개 + 5번째 직접 선택)
        val PRESET_COLORS = listOf(
            "#261643" to "미드나잇 퍼플",
            "#1A1A2E" to "네이비 다크",
            "#2C3E50" to "미드나잇",
            "#0F2027" to "딥 블루"
        )
    }
}
