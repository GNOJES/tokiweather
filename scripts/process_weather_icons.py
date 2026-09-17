#!/usr/bin/env python3
"""
Process cute 3D marshmallow weather icons for TokiWeather Android app.

Extracts subjects from white studio background, removes cast floor shadows,
crops to square aspect ratio with consistent padding, antialiases edges,
and resizes to 192x192 RGBA PNGs.
"""

import os
import sys
import argparse
from pathlib import Path
import cv2
import numpy as np
from PIL import Image

DEFAULT_SOURCES = {
    "clear": "/Users/a220330002/.gemini/antigravity/brain/e0d627ed-d0cc-4f43-96c7-83ea1671e83a/icon_weather_clear_1789611614160.jpg",
    "cloudy": "/Users/a220330002/.gemini/antigravity/brain/e0d627ed-d0cc-4f43-96c7-83ea1671e83a/sunny_3d_minimal_1789573146944.jpg",
    "overcast": "/Users/a220330002/.gemini/antigravity/brain/e0d627ed-d0cc-4f43-96c7-83ea1671e83a/icon_weather_overcast_1789611632431.jpg",
    "rain": "/Users/a220330002/.gemini/antigravity/brain/e0d627ed-d0cc-4f43-96c7-83ea1671e83a/icon_weather_rain_1789611670953.jpg",
    "sleet": "/Users/a220330002/.gemini/antigravity/brain/e0d627ed-d0cc-4f43-96c7-83ea1671e83a/icon_weather_sleet_1789611696070.jpg",
    "snow": "/Users/a220330002/.gemini/antigravity/brain/e0d627ed-d0cc-4f43-96c7-83ea1671e83a/icon_weather_snow_1789611717377.jpg",
    "shower": "/Users/a220330002/.gemini/antigravity/brain/e0d627ed-d0cc-4f43-96c7-83ea1671e83a/icon_weather_shower_1789611739772.jpg",
    "unknown": "/Users/a220330002/.gemini/antigravity/brain/e0d627ed-d0cc-4f43-96c7-83ea1671e83a/icon_weather_unknown_1789611762021.jpg",
}

DEFAULT_OUTPUT_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res" / "drawable"
TARGET_SIZE = 192
PADDING_RATIO = 0.08  # 8% uniform padding around bounding box


def extract_subject_mask(img: np.ndarray) -> np.ndarray:
    """
    Extracts foreground mask using GrabCut segmentation, preserving white cloud
    features and particles while excluding the studio background and floor cast shadows.
    """
    h, w = img.shape[:2]
    
    # GrabCut with outer margin
    margin = 25
    rect = (margin, margin, w - 2 * margin, h - 2 * margin)
    mask = np.zeros((h, w), np.uint8)
    bgd_model = np.zeros((1, 65), np.float64)
    fgd_model = np.zeros((1, 65), np.float64)
    cv2.grabCut(img, mask, rect, bgd_model, fgd_model, 3, cv2.GC_INIT_WITH_RECT)
    
    fg_binary = np.where((mask == cv2.GC_FGD) | (mask == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)
    
    # Filter connected components:
    # 1. Keep main cloud/sun body and all weather particles (raindrops, snowflakes, sleet drops, question marks)
    # 2. Exclude noise (< 200 pixels)
    # 3. Exclude floor cast shadows: faint, highly horizontal ellipses at the bottom plane (y > 800, aspect > 3.0)
    num_labels, labels, stats, _ = cv2.connectedComponentsWithStats(fg_binary)
    clean_mask = np.zeros_like(fg_binary)
    
    for i in range(1, num_labels):
        area = stats[i, cv2.CC_STAT_AREA]
        x = stats[i, cv2.CC_STAT_LEFT]
        y = stats[i, cv2.CC_STAT_TOP]
        sw = stats[i, cv2.CC_STAT_WIDTH]
        sh = stats[i, cv2.CC_STAT_HEIGHT]
        aspect = sw / max(1, sh)
        
        # Filter small noise
        if area < 200:
            continue
            
        # Filter faint floor shadow at bottom
        if y > 800 and aspect > 3.0:
            continue
            
        clean_mask[labels == i] = 255
        
    return clean_mask


def process_image(img_path: str, output_path: str, target_size: int = TARGET_SIZE, padding_ratio: float = PADDING_RATIO):
    """
    Process a single raw 3D weather icon image into an optimized transparent square PNG.
    """
    if not os.path.exists(img_path):
        raise FileNotFoundError(f"Input image not found: {img_path}")
        
    img = cv2.imread(img_path)
    if img is None:
        raise ValueError(f"Failed to load image: {img_path}")
        
    h, w = img.shape[:2]
    
    # 1. Segment foreground subject
    clean_mask = extract_subject_mask(img)
    
    # 2. Smooth mask edge slightly for antialiasing
    alpha = cv2.GaussianBlur(clean_mask, (3, 3), 0.7)
    
    # 3. Combine RGB and Alpha
    rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    rgba = np.dstack([rgb, alpha])
    pil_img = Image.fromarray(rgba)
    
    # 4. Crop to square bounding box with uniform padding
    ys, xs = np.where(clean_mask > 0)
    if len(ys) == 0 or len(xs) == 0:
        raise ValueError(f"No foreground detected for {img_path}")
        
    min_x, max_x = int(xs.min()), int(xs.max())
    min_y, max_y = int(ys.min()), int(ys.max())
    bw = max_x - min_x + 1
    bh = max_y - min_y + 1
    side = max(bw, bh)
    pad = int(side * padding_ratio)
    side_padded = side + 2 * pad
    
    cx, cy = (min_x + max_x) // 2, (min_y + max_y) // 2
    x0 = cx - side_padded // 2
    y0 = cy - side_padded // 2
    
    canvas = Image.new("RGBA", (side_padded, side_padded), (0, 0, 0, 0))
    crop_x0 = max(0, x0)
    crop_y0 = max(0, y0)
    crop_x1 = min(w, x0 + side_padded)
    crop_y1 = min(h, y0 + side_padded)
    
    crop = pil_img.crop((crop_x0, crop_y0, crop_x1, crop_y1))
    canvas.paste(crop, (crop_x0 - x0, crop_y0 - y0))
    
    # 5. High-quality resize to target size
    icon = canvas.resize((target_size, target_size), Image.Resampling.LANCZOS)
    
    # 6. Save PNG
    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    icon.save(output_path, "PNG", optimize=True)
    print(f"Saved {output_path} ({target_size}x{target_size}, bbox {bw}x{bh})")


def main():
    parser = argparse.ArgumentParser(description="Process 3D weather icons for TokiWeather")
    parser.add_argument("--output-dir", type=str, default=str(DEFAULT_OUTPUT_DIR), help="Output drawable directory")
    parser.add_argument("--size", type=int, default=TARGET_SIZE, help="Output icon size in pixels (default: 192)")
    args = parser.parse_args()
    
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"Processing 8 weather icons to {output_dir}...")
    for condition, path in DEFAULT_SOURCES.items():
        out_file = output_dir / f"ic_weather_{condition}.png"
        process_image(path, str(out_file), target_size=args.size)
        
    print("All 8 weather icons processed successfully!")


if __name__ == "__main__":
    main()
