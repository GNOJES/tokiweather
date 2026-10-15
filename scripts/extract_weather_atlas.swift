// Mechanical extraction of a transparent ImageGen atlas, without repainting artwork.
// swift scripts/extract_weather_atlas.swift <pack> <source.png> <output-directory> [sun]
// Default: full 5×2 pack. sun: approved 3×1 clear/cloudy/shower replacement strip only.
import AppKit
import Foundation

let args = CommandLine.arguments
guard (args.count == 4 || (args.count == 5 && args[4] == "sun")), let data = FileManager.default.contents(atPath: args[2]),
      let bitmap = NSBitmapImageRep(data: data),
      let source = bitmap.cgImage, let pixels = bitmap.bitmapData,
      bitmap.bitsPerSample == 8, bitmap.hasAlpha, !bitmap.isPlanar else {
    fatalError("Expected an RGBA PNG atlas and three arguments")
}
let width = bitmap.pixelsWide, height = bitmap.pixelsHigh
let sunOnly = args.count == 5
let columns = sunOnly ? 3 : 5
let rows = sunOnly ? 1 : 2
let alphaIndex = bitmap.bitmapFormat.contains(.alphaFirst) ? 0 : bitmap.samplesPerPixel - 1
func alpha(_ x: Int, _ y: Int) -> Int {
    Int(pixels[y * bitmap.bytesPerRow + x * bitmap.samplesPerPixel + alphaIndex])
}
// Choose a transparent gutter close to each nominal grid boundary, preserving full silhouettes.
let row = ((height * 43 / 100)...(height * 57 / 100)).min { a, b in
    (0..<width).reduce(0) { $0 + (alpha($1, a) >= 64 ? 1 : 0) } <
    (0..<width).reduce(0) { $0 + (alpha($1, b) >= 64 ? 1 : 0) }
}!
let names = sunOnly ? ["clear", "cloudy", "shower"] :
    ["clear", "cloudy", "overcast", "rain", "snow", "clear_night", "cloudy_night", "sleet", "shower", "unknown"]
try FileManager.default.createDirectory(atPath: args[3], withIntermediateDirectories: true)
for r in 0..<rows {
    let y0 = r == 0 ? 0 : row, y1 = sunOnly ? height : (r == 0 ? row : height)
    var cuts = [0]
    for column in 1..<columns {
        let nominal = width * column / columns, margin = width / 40
        let cut = ((nominal - margin)...(nominal + margin)).min { a, b in
            (y0..<y1).reduce(0) { $0 + (alpha(a, $1) >= 64 ? 1 : 0) } <
            (y0..<y1).reduce(0) { $0 + (alpha(b, $1) >= 64 ? 1 : 0) }
        }!
        cuts.append(cut)
    }
    cuts.append(width)
    for column in 0..<columns {
        let x0 = cuts[column], x1 = cuts[column + 1]
        var left = x1, right = x0, top = y1, bottom = y0
        for y in y0..<y1 { for x in x0..<x1 where alpha(x, y) >= 64 {
            left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
        } }
        guard right > left && bottom > top else { fatalError("Empty atlas cell") }
        left = max(x0, left - 3); right = min(x1 - 1, right + 3)
        top = max(y0, top - 3); bottom = min(y1 - 1, bottom + 3)
        let rect = CGRect(x: left, y: top, width: right - left + 1, height: bottom - top + 1)
        guard let cropped = source.cropping(to: rect),
              let context = CGContext(data: nil, width: 256, height: 256, bitsPerComponent: 8,
                bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { fatalError("Cannot extract cell") }
        let scale = 240.0 / max(rect.width, rect.height)
        let w = rect.width * scale, h = rect.height * scale
        context.interpolationQuality = args[1] == "pixel" ? .none : .high
        context.draw(cropped, in: CGRect(x: (256 - w) / 2, y: (256 - h) / 2, width: w, height: h))
        let result = NSBitmapImageRep(cgImage: context.makeImage()!)
        let name = "ic_pack_\(args[1])_\(names[r * columns + column]).png"
        try result.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: args[3]).appendingPathComponent(name))
        print(name)
    }
}
