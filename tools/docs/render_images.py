"""Renders the README mockups (docs/widgets_mock[.uk].svg/.png), docs/logo_variants.png and docs/palette.png.

    python tools/docs/render_images.py

Needs rsvg-convert (librsvg) and Pillow; install the Unbounded and Manrope fonts from
app/src/main/res/font first so the text renders in the app's typefaces.
"""
import math, os, re, subprocess, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
DOCS = os.path.join(ROOT, "docs")
LOGO = open(os.path.join(DOCS, "ecowi_neon.svg")).read()

STRINGS = {
    "en": dict(station="Station", phone="Phone", camera="Camera", caption1="~6h 10m · 16:42", caption2="+320 W · ~1h 05m",
               w245="245 W", w18="18 W", in320="+320 W", ac245="AC 245 W", date="Fri, November 6",
               home_caption="Home screen: bar (dark / light at 60%), ring, mini, 4×2 ring with the phone",
               mini_caption="Mini on the lock screen, where One UI allows it",
               n_port_title="A device was plugged into Type-C 1", n_port_text="Drawing 18 W",
               n_low_title="15% left", n_low_text="Time to think about charging. ~1h 20m remaining.",
               live_title="Station 78%", live_text="AC 245 W · ~6h 10m", nowbar_caption="Now Bar (Android 16 Live Update)",
               lock_caption="Lock screen: alerts and live status"),
    "uk": dict(station="Станція", phone="Телефон", camera="Камера", caption1="~6 год 10 хв · 16:42", caption2="+320 Вт · ~1 год 05 хв",
               w245="245 Вт", w18="18 Вт", in320="+320 Вт", ac245="AC 245 Вт", date="пт, 6 листопада",
               home_caption="Головний екран: смуга (темна / світла на 60%), кільце, міні, кільце 4×2 з телефоном",
               mini_caption="Міні на екрані блокування — де One UI це дозволяє",
               n_port_title="Під’єднано прилад до порту Type-C 1", n_port_text="Споживання 18 Вт",
               n_low_title="Залишилось 15%", n_low_text="Час подумати про заряджання. Вистачить на ~1 год 20 хв.",
               live_title="Станція 78%", live_text="AC 245 Вт · ~6 год 10 хв", nowbar_caption="Now Bar (Live Update Android 16)",
               lock_caption="Екран блокування: сповіщення і живий статус"),
}

AC = "M8.5,2h2v5h-2z M13.5,2h2v5h-2z M6,8h12v4c0,3.31 -2.69,6 -6,6s-6,-2.69 -6,-6z M11,18h2v4h-2z"
DC = "M7,2h10v6h-10z M9,4h2v2h-2z M13,4h2v2h-2z M6,9h12v3.5c0,3.04 -2.46,5.5 -5.5,5.5h-1c-3.04,0 -5.5,-2.46 -5.5,-5.5z M11,18h2v4h-2z"
BOLT = "M13.5,2L5,13.5h5.5L9.5,22L19,10h-5.5z"

# Widget palettes: same values as res/values/colors.xml (ew_w_*, ew_batt_*), see docs/BRAND.md
DARK = dict(bg="#1F2328", text="#EEF1F4", text2="#9AA3AD", track="#343B43", high="#C9EE85", med="#FFD23F", low="#FF9F43", crit="#FF5C5C")
LIGHT = dict(bg="#F6F8F3", text="#15181C", text2="#5D6670", track="#E1E6DC", high="#4C8A12", med="#C98F00", low="#E36A00", crit="#D32F2F")
H = "Unbounded"
B = "Manrope"

def band(p, soc):
    return p["high"] if soc >= 50 else p["med"] if soc >= 25 else p["low"] if soc > 10 else p["crit"]

def icon(path, x, y, size, color):
    s = size / 24
    return f'<path d="{path}" transform="translate({x},{y}) scale({s})" fill="{color}"/>'

def text(x, y, s, size, color, font=B, weight=600, anchor="start", opacity=1):
    return (f'<text x="{x}" y="{y}" font-family="{font}" font-size="{size}" font-weight="{weight}" '
            f'fill="{color}" text-anchor="{anchor}" opacity="{opacity}">{s}</text>')

def card(x, y, w, h, p, alpha=1.0, r=28):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{p["bg"]}" fill-opacity="{alpha}"/>'

def ring(cx, cy, r, sw, soc, p, track=None):
    c = 2 * math.pi * r
    return (f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{track or p["track"]}" stroke-width="{sw}"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="none" stroke="{band(p, soc)}" stroke-width="{sw}" stroke-linecap="round" '
            f'stroke-dasharray="{c * soc / 100:.1f} {c:.1f}" transform="rotate(-90 {cx} {cy})"/>')

def bar_widget(x, y, w, h, p, soc, caption, load_w, load_label, path, alpha=1.0, charging=False):
    g = [card(x, y, w, h, p, alpha)]
    g.append(text(x + 30, y + h / 2 + 15, f"{soc}%", 42, p["text"], H, 700))
    bx = x + 170
    bw = w - 170 - 210
    g.append(f'<rect x="{bx}" y="{y + 40}" width="{bw}" height="14" rx="7" fill="{p["track"]}"/>')
    g.append(f'<rect x="{bx}" y="{y + 40}" width="{bw * soc / 100:.0f}" height="14" rx="7" fill="{band(p, soc)}"/>')
    if charging:
        g.append(icon(BOLT, bx - 4, y + 62, 26, band(p, soc)))
        g.append(text(bx + 24, y + 84, caption, 19, p["text2"]))
    else:
        g.append(text(bx, y + 84, caption, 19, p["text2"]))
    dx = x + w - 190
    g.append(f'<rect x="{dx}" y="{y + 26}" width="2" height="{h - 52}" rx="1" fill="{p["track"]}"/>')
    g.append(icon(path, dx + 22, y + 34, 32, p["text"]))
    g.append(text(dx + 62, y + 58, load_w, 24, p["text"], H, 600))
    g.append(text(dx + 62, y + 86, load_label, 17, p["text2"]))
    return "".join(g)

def ring_widget(x, y, s, p, soc, load):
    g = [card(x, y, s, s, p, 1.0, 36)]
    cx, cy = x + s / 2, y + s / 2 - 14
    g.append(ring(cx, cy, 92, 18, soc, p))
    g.append(text(cx, cy + 16, f"{soc}%", 44, p["text"], H, 700, "middle"))
    g.append(icon(AC, x + s / 2 - 70, y + s - 64, 26, p["text2"]))
    g.append(text(x + s / 2 - 38, y + s - 43, load, 21, p["text"], H, 600))
    return "".join(g)

def duo_widget(x, y, w, h, p, soc, phone):
    g = [card(x, y, w, h, p, 1.0, 36)]
    for i, (v, label, charging) in enumerate([(soc, T["station"], False), (phone, T["phone"], True)]):
        cx = x + w * (0.27 + 0.46 * i)
        cy = y + h / 2 - 22
        g.append(ring(cx, cy, 82, 16, v, p))
        g.append(text(cx, cy + 14, f"{v}%", 38, p["text"], H, 700, "middle"))
        if charging:
            g.append(icon(BOLT, cx - 13, cy - 62, 26, band(p, v)))
        g.append(text(cx, y + h - 34, label, 20, p["text2"], B, 600, "middle"))
    return "".join(g)

def mini_widget(x, y, s, p, soc, alpha=1.0):
    return card(x, y, s, s, p, alpha, s / 2) + ring(x + s / 2, y + s / 2, s / 2 - 22, 10, soc, p) + \
        text(x + s / 2, y + s / 2 + s * 0.06, f"{soc}%", round(s * 0.15), p["text"], H, 700, "middle")

def pill_widget(x, y, w, h, p, soc, caption, charging=True):
    g = [card(x, y, w, h, p, 1.0, h / 2)]
    g.append(ring(x + h / 2, y + h / 2, h / 2 - 22, 9, soc, p))
    if charging:
        g.append(icon(BOLT, x + h / 2 - 11, y + h / 2 - 11, 22, band(p, soc)))
    else:
        g.append(icon(AC, x + h / 2 - 12, y + h / 2 - 12, 24, p["text2"]))
    g.append(text(x + h + 4, y + h / 2 - 2, f"{soc}%", 30, p["text"], H, 700))
    g.append(text(x + h + 4, y + h / 2 + 28, caption, 17, p["text2"]))
    return "".join(g)

def logo_icon(x, y, s):
    inner = re.sub(r"^<svg[^>]*>|</svg>\s*$", "", LOGO.strip())
    return f'<svg x="{x}" y="{y}" width="{s}" height="{s}" viewBox="0 0 512 512">{inner}</svg>'

def app_icon(x, y, s, color, label):
    return f'<rect x="{x}" y="{y}" width="{s}" height="{s}" rx="{s * 0.28}" fill="{color}"/>' + \
        text(x + s / 2, y + s + 30, label, 18, "#E9ECF2", B, 600, "middle")

def mockup(T):
    W, HGT = 1760, 1180
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{HGT}" viewBox="0 0 {W} {HGT}">']
    svg.append('''<defs>
    <linearGradient id="wall" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#5A616A"/><stop offset="0.55" stop-color="#3A4048"/><stop offset="1" stop-color="#1F2328"/></linearGradient>
    <radialGradient id="blob1" cx="0.2" cy="0.25" r="0.5"><stop offset="0" stop-color="#9EDC4E" stop-opacity="0.22"/><stop offset="1" stop-color="#9EDC4E" stop-opacity="0"/></radialGradient>
    <radialGradient id="blob2" cx="0.85" cy="0.8" r="0.55"><stop offset="0" stop-color="#DCFB90" stop-opacity="0.10"/><stop offset="1" stop-color="#DCFB90" stop-opacity="0"/></radialGradient>
    <linearGradient id="lock" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#2C3239"/><stop offset="1" stop-color="#15181C"/></linearGradient>
    <clipPath id="ph1"><rect x="40" y="40" width="860" height="1060" rx="60"/></clipPath>
    <clipPath id="ph2"><rect x="960" y="40" width="760" height="1060" rx="60"/></clipPath>
    </defs>''')
    svg.append(f'<rect width="{W}" height="{HGT}" fill="#0B0D10"/>')

    # ---- home screen
    svg.append('<g clip-path="url(#ph1)"><rect x="40" y="40" width="860" height="1060" fill="url(#wall)"/>'
               '<rect x="40" y="40" width="860" height="1060" fill="url(#blob1)"/><rect x="40" y="40" width="860" height="1060" fill="url(#blob2)"/>')
    svg.append(text(90, 92, "16:42", 22, "#FFFFFF", B, 700))
    svg.append(text(850, 92, "84%", 20, "#FFFFFF", B, 600, "end"))
    L, CW = 70, 160  # left, cell width (5 columns)
    svg.append(bar_widget(L, 130, 5 * CW - 0, 130, DARK, 78, T["caption1"], T["w245"], "AC", AC))
    svg.append(bar_widget(L, 290, 5 * CW, 130, LIGHT, 41, T["caption2"], T["w18"], "DC", DC, alpha=0.62, charging=True))
    svg.append(ring_widget(L, 450, 2 * CW - 20, DARK, 78, T["w245"]))
    svg.append(mini_widget(L + 2 * CW + 10, 450, CW - 20, DARK, 78))
    svg.append(pill_widget(L + 3 * CW + 10, 450, 2 * CW - 20, CW - 20, DARK, 41, T["in320"]))
    svg.append(logo_icon(L + 2 * CW + 22, 618, 116))
    svg.append(text(L + 2 * CW + 80, 764, "EcoWi", 18, "#E9ECF2", B, 600, "middle"))
    svg.append(app_icon(L + 3 * CW + 22, 618, 116, "#5B6CFF", T["camera"]))
    svg.append(app_icon(L + 4 * CW + 22, 618, 116, "#2EB872", T["phone"]))
    svg.append(duo_widget(L, 800, 5 * CW, 2 * CW - 30, DARK, 78, 64))
    svg.append('</g>')
    svg.append('<rect x="40" y="40" width="860" height="1060" rx="60" fill="none" stroke="#2B3038" stroke-width="3"/>')
    svg.append(text(470, 1150, T["home_caption"], 20, "#98A2AE", B, 600, "middle"))

    # ---- lock screen
    svg.append('<g clip-path="url(#ph2)"><rect x="960" y="40" width="760" height="1060" fill="url(#lock)"/>'
               '<rect x="960" y="40" width="760" height="1060" fill="url(#blob1)"/>')
    svg.append(text(1340, 150, T["date"], 26, "#E9ECF2", B, 600, "middle"))
    svg.append(text(1340, 330, "16:42", 170, "#FFFFFF", H, 700, "middle"))
    svg.append(mini_widget(1150, 390, 130, DARK, 78, 0.55))
    svg.append(pill_widget(1300, 390, 250, 130, DARK, 78, T["ac245"], charging=False))
    svg.append(text(1340, 560, T["mini_caption"], 18, "#98A2AE", B, 600, "middle"))
    # notifications
    def notif(y, title, body, color):
        return (f'<rect x="1000" y="{y}" width="680" height="108" rx="30" fill="#1C2028" fill-opacity="0.92"/>'
                + logo_icon(1022, y + 24, 60) + text(1102, y + 46, title, 22, "#F3F5F7", B, 700)
                + text(1102, y + 78, body, 18, "#98A2AE", B, 600)
                + f'<circle cx="1650" cy="{y + 30}" r="6" fill="{color}"/>')
    svg.append(notif(620, T["n_port_title"], T["n_port_text"], "#9EDC4E"))
    svg.append(notif(742, T["n_low_title"], T["n_low_text"], "#FF9F43"))
    # Now Bar
    svg.append('<rect x="1060" y="920" width="560" height="110" rx="55" fill="#101318" stroke="#2B3038" stroke-width="2"/>')
    svg.append(logo_icon(1080, 940, 70))
    svg.append(text(1168, 970, T["live_title"], 24, "#F3F5F7", H, 600))
    svg.append(text(1168, 1000, T["live_text"], 17, "#98A2AE", B, 600))
    svg.append('<rect x="1168" y="1010" width="360" height="8" rx="4" fill="#2B3038"/><rect x="1168" y="1010" width="281" height="8" rx="4" fill="#B4EE3C"/>')
    svg.append(text(1595, 985, "78%", 22, "#B4EE3C", H, 700, "end"))
    svg.append(text(1340, 1070, T["nowbar_caption"], 18, "#98A2AE", B, 600, "middle"))
    svg.append('</g>')
    svg.append('<rect x="960" y="40" width="760" height="1060" rx="60" fill="none" stroke="#2B3038" stroke-width="3"/>')
    svg.append(text(1340, 1150, T["lock_caption"], 20, "#98A2AE", B, 600, "middle"))
    svg.append('</svg>')
    return "\n".join(svg)


def logo_variants():
    from PIL import Image, ImageDraw, ImageFont
    names = [("neon", "Neon — app icon"), ("volt", "Volt"), ("ice", "Ice")]
    font_path = os.path.join(ROOT, "app", "src", "main", "res", "font", "manrope_semibold.ttf")
    f = ImageFont.truetype(font_path, 26)
    sheet = Image.new("RGB", (3 * 360 + 4 * 40, 360 + 120), "#0B0D10")
    dr = ImageDraw.Draw(sheet)
    for i, (n, label) in enumerate(names):
        png = os.path.join(DOCS, f".tmp_{n}.png")
        subprocess.run(["rsvg-convert", "-w", "360", os.path.join(DOCS, f"ecowi_{n}.svg"), "-o", png], check=True)
        im = Image.open(png).convert("RGBA")
        x = 40 + i * 400
        sheet.paste(im, (x, 40), im)
        tw = dr.textlength(label, font=f)
        dr.text((x + 180 - tw / 2, 420), label, font=f, fill="#C9D1D9")
        os.remove(png)
    sheet.save(os.path.join(DOCS, "logo_variants.png"))


PALETTE = [
    ("Brand", [("Glow", "#9EDC4E"), ("Tube", "#C9EE85"), ("Bolt", "#DCFB90"), ("Core", "#EBFEB3"),
               ("Wall top", "#4A5057"), ("Wall bottom", "#1F2328")]),
    ("Dark", [("Background", "#15181C"), ("Surface", "#1C2025"), ("Card", "#23282E"), ("Raised", "#2C3239"),
              ("Line", "#343B43"), ("Muted", "#9AA3AD")]),
    ("Light", [("Primary", "#4C7F12"), ("Secondary", "#6FA82A"), ("Container", "#E2F4C3"), ("Background", "#F4F6F1"),
               ("Outline", "#D6DCD2"), ("Text", "#15181C")]),
    ("States", [("Charged", "#C9EE85"), ("Medium", "#FFD23F"), ("Low", "#FF9F43"), ("Critical", "#FF5C5C"),
                ("Notification", "#8CC63F")]),
]


def palette_sheet():
    from PIL import Image, ImageDraw, ImageFont
    fonts = os.path.join(ROOT, "app", "src", "main", "res", "font")
    title = ImageFont.truetype(os.path.join(fonts, "unbounded_semibold.ttf"), 26)
    label = ImageFont.truetype(os.path.join(fonts, "manrope_semibold.ttf"), 20)
    code = ImageFont.truetype(os.path.join(fonts, "manrope_regular.ttf"), 18)
    sw, gap, row_h = 150, 18, 230
    sheet = Image.new("RGB", (60 + 6 * (sw + gap), 40 + len(PALETTE) * row_h), "#0B0D10")
    dr = ImageDraw.Draw(sheet)
    for r, (group, colours) in enumerate(PALETTE):
        y = 30 + r * row_h
        dr.text((30, y), group, font=title, fill="#EEF1F4")
        for i, (name, hexv) in enumerate(colours):
            x = 30 + i * (sw + gap)
            dr.rounded_rectangle((x, y + 48, x + sw, y + 148), radius=22, fill=hexv, outline="#343B43", width=2)
            dr.text((x + 4, y + 158), name, font=label, fill="#C9D1D9")
            dr.text((x + 4, y + 184), hexv, font=code, fill="#9AA3AD")
    sheet.save(os.path.join(DOCS, "palette.png"))


if __name__ == "__main__":
    for lang, T in STRINGS.items():
        suffix = "" if lang == "en" else "." + lang
        svg_path = os.path.join(DOCS, f"widgets_mock{suffix}.svg")
        open(svg_path, "w").write(mockup(T))
        subprocess.run(["rsvg-convert", "-w", "1760", svg_path, "-o", svg_path[:-4] + ".png"], check=True)
    logo_variants()
    palette_sheet()
    print("ok")
