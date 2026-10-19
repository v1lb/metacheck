#!/usr/bin/env python3
"""Contrast regression gate for canonical UI text/surface colors (WCAG AAA normal text >=7:1).

This checks named palette pairs, not all possible composited states or device brightness.
"""

def luminance(hex_rgb: str) -> float:
    channels = [int(hex_rgb[index:index + 2], 16) / 255 for index in (1, 3, 5)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in channels]
    return sum(a * b for a, b in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(foreground: str, background: str) -> float:
    a, b = sorted((luminance(foreground), luminance(background)), reverse=True)
    return (a + .05) / (b + .05)


PAIRS = {
    "dark.primary_on_card": ("#00F3FF", "#121A26"),
    "dark.secondary_on_card": ("#E0BBFF", "#121A26"),
    "dark.tertiary_on_card": ("#00FFB2", "#121A26"),
    "dark.text_on_card": ("#FFFFFF", "#121A26"),
    "dark.secondary_text_on_card": ("#C5D4E8", "#121A26"),
    "gps_alert.title": ("#FFFFFF", "#291322"),
    "gps_alert.description": ("#F0E1E8", "#291322"),
    "gps_alert.eyebrow": ("#FFB0C8", "#291322"),
    "gps_alert.small_print": ("#E1D0DA", "#120E1A"),
    "gps_map.note": ("#CDD9E7", "#07101A"),
    "light.primary_on_background": ("#00556B", "#F4F8FC"),
    "light.secondary_on_background": ("#63339C", "#F4F8FC"),
    "light.tertiary_on_background": ("#00583D", "#F4F8FC"),
    "light.secondary_text_on_background": ("#334D60", "#F4F8FC"),
    "light.text_on_card": ("#0A2233", "#FFFFFF"),
}


if __name__ == "__main__":
    failures = []
    for label, (fg, bg) in PAIRS.items():
        ratio = contrast(fg, bg)
        print(f"{label}: {ratio:.2f}:1")
        if ratio < 7.0:
            failures.append(label)
    if failures:
        raise SystemExit("AAA contrast gate failed: " + ", ".join(failures))
    print(f"PASS: {len(PAIRS)} canonical color pairs meet >=7:1")
