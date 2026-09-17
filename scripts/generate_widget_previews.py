#!/usr/bin/env python3
"""
위젯 선택기(Widget Picker)용 미리보기 이미지(previewImage) 자동 생성 스크립트.
- preview_widget.png (2×1 가로형 기본 위젯)
- preview_widget_large.png (3×2 세로형 대형 위젯)
"""

import os
from PIL import Image, ImageDraw, ImageFont

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
RES_DRAWABLE = os.path.join(PROJECT_ROOT, "app", "src", "main", "res", "drawable")

FONT_PATH = "/System/Library/Fonts/AppleSDGothicNeo.ttc"
FALLBACK_FONT = "/System/Library/Fonts/Supplemental/AppleGothic.ttf"

def get_font(size, bold=False):
    index = 3 if bold else 0  # 3: SemiBold / Bold in AppleSDGothicNeo.ttc
    try:
        return ImageFont.truetype(FONT_PATH, size, index=index)
    except Exception:
        try:
            return ImageFont.truetype(FALLBACK_FONT, size)
        except Exception:
            return ImageFont.load_default()

def draw_rounded_rect(draw, bbox, radius, fill):
    draw.rounded_rectangle(bbox, radius=radius, fill=fill)

def draw_pop_bar(draw, center_x, y, filled_count, active_color, inactive_color, dot_size=6, gap=3):
    total_w = 4 * dot_size + 3 * gap
    start_x = center_x - total_w // 2
    for i in range(4):
        x = start_x + i * (dot_size + gap)
        c = active_color if i < filled_count else inactive_color
        draw_rounded_rect(draw, [x, y, x + dot_size, y + dot_size], radius=2, fill=c)

def generate_2x1_preview():
    # 380 × 190 px
    w, h = 380, 190
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 1. 배경 (보라색 반투명 라운드 카드)
    draw_rounded_rect(draw, [4, 4, w - 4, h - 4], radius=24, fill=(38, 22, 67, 220))

    # 아이콘 로드
    sun_icon = Image.open(os.path.join(RES_DRAWABLE, "ic_weather_clear.png")).convert("RGBA").resize((56, 56), Image.Resampling.LANCZOS)
    cloud_icon = Image.open(os.path.join(RES_DRAWABLE, "ic_weather_cloudy.png")).convert("RGBA").resize((34, 34), Image.Resampling.LANCZOS)
    rain_icon = Image.open(os.path.join(RES_DRAWABLE, "ic_weather_rain.png")).convert("RGBA").resize((34, 34), Image.Resampling.LANCZOS)
    pin_icon = Image.open(os.path.join(RES_DRAWABLE, "ic_weather_clear.png")).convert("RGBA").resize((16, 16), Image.Resampling.LANCZOS)

    # ── [좌측: 오늘 날씨 (5/11 비율)] ──
    today_center_x = 85
    # 해 아이콘
    img.alpha_composite(sun_icon, (today_center_x - 28, 20))

    # 기온
    f_temp = get_font(32, bold=True)
    draw.text((today_center_x, 88), "26°", fill=(255, 255, 255, 255), font=f_temp, anchor="mm")

    # 미세먼지
    f_pm = get_font(15, bold=True)
    f_dot = get_font(13, bold=True)
    draw.text((today_center_x - 18, 114), "24", fill=(74, 163, 255, 255), font=f_pm, anchor="mm")
    draw.text((today_center_x, 114), "·", fill=(255, 255, 255, 180), font=f_dot, anchor="mm")
    draw.text((today_center_x + 18, 114), "12", fill=(74, 163, 255, 255), font=f_pm, anchor="mm")

    # 오늘 강수확률 바
    draw_pop_bar(draw, today_center_x, 138, filled_count=0,
                 active_color=(74, 163, 255, 255), inactive_color=(255, 255, 255, 60), dot_size=6, gap=3)

    # ── [우측: 지역명 + 내일/모레 (6/11 비율)] ──
    # 지역명
    f_loc = get_font(15, bold=True)
    draw.text((w - 28, 26), "📍 역삼동", fill=(255, 255, 255, 200), font=f_loc, anchor="rm")

    # 구분 분할: 내일(x=225), 모레(x=315)
    tomorrow_x = 225
    day_after_x = 315

    f_sub_lbl = get_font(14, bold=False)
    f_sub_temp = get_font(15, bold=True)

    # 내일
    draw.text((tomorrow_x, 56), "내일", fill=(255, 255, 255, 180), font=f_sub_lbl, anchor="mm")
    img.alpha_composite(cloud_icon, (tomorrow_x - 17, 70))
    draw.text((tomorrow_x, 116), "18~27°", fill=(255, 255, 255, 255), font=f_sub_temp, anchor="mm")
    draw_pop_bar(draw, tomorrow_x, 138, filled_count=1,
                 active_color=(74, 163, 255, 255), inactive_color=(255, 255, 255, 60), dot_size=6, gap=3)

    # 모레
    draw.text((day_after_x, 56), "모레", fill=(255, 255, 255, 180), font=f_sub_lbl, anchor="mm")
    img.alpha_composite(rain_icon, (day_after_x - 17, 70))
    draw.text((day_after_x, 116), "15~22°", fill=(255, 255, 255, 255), font=f_sub_temp, anchor="mm")
    draw_pop_bar(draw, day_after_x, 138, filled_count=4,
                 active_color=(74, 163, 255, 255), inactive_color=(255, 255, 255, 60), dot_size=6, gap=3)

    out_path = os.path.join(RES_DRAWABLE, "preview_widget.png")
    img.save(out_path, "PNG")
    print(f"Saved: {out_path}")

def generate_3x2_preview():
    # 320 × 330 px
    w, h = 320, 330
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 1. 배경
    draw_rounded_rect(draw, [4, 4, w - 4, h - 4], radius=24, fill=(38, 22, 67, 220))

    # 아이콘 로드
    sun_icon = Image.open(os.path.join(RES_DRAWABLE, "ic_weather_clear.png")).convert("RGBA").resize((70, 70), Image.Resampling.LANCZOS)
    cloud_icon = Image.open(os.path.join(RES_DRAWABLE, "ic_weather_cloudy.png")).convert("RGBA").resize((42, 42), Image.Resampling.LANCZOS)
    rain_icon = Image.open(os.path.join(RES_DRAWABLE, "ic_weather_rain.png")).convert("RGBA").resize((42, 42), Image.Resampling.LANCZOS)

    # ── [상단: 지역명] ──
    f_loc = get_font(15, bold=True)
    draw.text((w - 24, 28), "📍 역삼동", fill=(255, 255, 255, 200), font=f_loc, anchor="rm")

    # ── [오늘 날씨 (가로 배치)] ──
    # 해 아이콘
    img.alpha_composite(sun_icon, (48, 52))

    # 기온 & 미세먼지
    f_temp = get_font(42, bold=True)
    draw.text((195, 72), "26°", fill=(255, 255, 255, 255), font=f_temp, anchor="mm")

    f_pm = get_font(17, bold=True)
    f_dot = get_font(15, bold=True)
    draw.text((178, 108), "24", fill=(74, 163, 255, 255), font=f_pm, anchor="mm")
    draw.text((195, 108), "·", fill=(255, 255, 255, 180), font=f_dot, anchor="mm")
    draw.text((212, 108), "12", fill=(74, 163, 255, 255), font=f_pm, anchor="mm")

    # 오늘 강수확률 바
    draw_pop_bar(draw, w // 2, 140, filled_count=0,
                 active_color=(74, 163, 255, 255), inactive_color=(255, 255, 255, 60), dot_size=7, gap=4)

    # 구분선 느낌의 공간
    # ── [하단: 내일 / 모레 예보 (2열)] ──
    tomorrow_x = 90
    day_after_x = 230

    f_sub_lbl = get_font(16, bold=False)
    f_sub_temp = get_font(17, bold=True)

    # 내일
    draw.text((tomorrow_x, 178), "내일", fill=(255, 255, 255, 180), font=f_sub_lbl, anchor="mm")
    img.alpha_composite(cloud_icon, (tomorrow_x - 21, 196))
    draw.text((tomorrow_x, 256), "18~27°", fill=(255, 255, 255, 255), font=f_sub_temp, anchor="mm")
    draw_pop_bar(draw, tomorrow_x, 282, filled_count=1,
                 active_color=(74, 163, 255, 255), inactive_color=(255, 255, 255, 60), dot_size=6, gap=3)

    # 모레
    draw.text((day_after_x, 178), "모레", fill=(255, 255, 255, 180), font=f_sub_lbl, anchor="mm")
    img.alpha_composite(rain_icon, (day_after_x - 21, 196))
    draw.text((day_after_x, 256), "15~22°", fill=(255, 255, 255, 255), font=f_sub_temp, anchor="mm")
    draw_pop_bar(draw, day_after_x, 282, filled_count=4,
                 active_color=(74, 163, 255, 255), inactive_color=(255, 255, 255, 60), dot_size=6, gap=3)

    out_path = os.path.join(RES_DRAWABLE, "preview_widget_large.png")
    img.save(out_path, "PNG")
    print(f"Saved: {out_path}")

if __name__ == "__main__":
    generate_2x1_preview()
    generate_3x2_preview()
