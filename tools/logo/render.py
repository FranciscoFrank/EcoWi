"""Writes EcoWi icon resources (VectorDrawable) and SVGs from geometry.py.

    python tools/logo/render.py            # writes into build/logo/
    python tools/logo/render.py --install  # also copies into app/src/main/res/drawable and docs/

Colours: PALETTES below ("neon" is what the app uses). Shape: geometry.py.
"""
import os, sys, shutil
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geometry as g

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(ROOT, "build", "logo")
os.makedirs(OUT, exist_ok=True)

PALETTES = {
    # The app icon. These values are the brand palette shared with the app (docs/BRAND.md).
    # "volt" and "ice" below are alternative logo palettes for docs only; the app doesn't use them.
    "neon": dict(tube="#C9EE85", core="#EBFEB3", glow="#9EDC4E", bolt="#DCFB90", bolt_core="#F3FFC8",
                 bg_top="#4A5057", bg_bottom="#1F2328", ambient="#8FD14F", label="Neon (matches the reference)"),
    "volt": dict(tube="#7CF2C8", core="#E6FFF6", glow="#2CF5D0", bolt="#FFD23F", bolt_core="#FFF2B8",
                 bg_top="#232830", bg_bottom="#0A0C0F", ambient="#2CF5D0", label="Volt (mint + amber)"),
    "ice": dict(tube="#9FDCFF", core="#F0FAFF", glow="#5CC8FF", bolt="#FFFFFF", bolt_core="#FFFFFF",
                bg_top="#2A3442", bg_bottom="#0D1119", ambient="#5CC8FF", label="Ice"),
}

# glow approximation for VectorDrawable (no blur): (width multiplier of TUBE, alpha)
GLOW_LAYERS = [(4.2, 0.05), (3.0, 0.09), (2.0, 0.16), (1.45, 0.30)]


def paths(cx, cy, scale):
    tf = g.fit(cx, cy, scale)
    main, hook, _ = g.tube_paths(tf, scale)
    return main, hook, g.bolt_path(tf)


def argb(hex_rgb, alpha=1.0):
    return "#%02X%s" % (round(alpha * 255), hex_rgb.lstrip("#").upper())


# ------------------------------------------------------------------ VectorDrawable

def vd_stroke(d, color, width, alpha=1.0):
    return (f'    <path android:pathData="{d}"\n'
            f'        android:strokeColor="{argb(color)}" android:strokeWidth="{width:.2f}"'
            + (f' android:strokeAlpha="{alpha:.2f}"' if alpha < 1 else "") +
            '\n        android:strokeLineCap="round" android:strokeLineJoin="round" android:fillColor="#00000000" />\n')


def vd_fill(d, color, alpha=1.0, stroke=None, stroke_w=0.0):
    s = f'    <path android:pathData="{d}"\n        android:fillColor="{argb(color)}"'
    if alpha < 1:
        s += f' android:fillAlpha="{alpha:.2f}"'
    if stroke:
        s += (f'\n        android:strokeColor="{argb(stroke)}" android:strokeWidth="{stroke_w:.2f}"'
              + (f' android:strokeAlpha="{alpha:.2f}"' if alpha < 1 else "")
              + ' android:strokeLineJoin="round"')
    return s + " />\n"


def neon_layers(pal, cx, cy, scale, glow=True):
    """List of ('stroke'|'fill', d, color, width, alpha, extra) — shared by VD and preview SVG."""
    main, hook, bolt = paths(cx, cy, scale)
    t = g.TUBE * scale
    L = []
    if glow:
        for mul, a in GLOW_LAYERS:
            for d in (main, hook, bolt):
                L.append(("stroke", d, pal["glow"], t * mul, a))
    for d in (main, hook):
        L.append(("stroke", d, pal["tube"], t, 1.0))
    L.append(("fillstroke", bolt, pal["bolt"], t * 0.35, 1.0))
    for d in (main, hook):
        L.append(("stroke", d, pal["core"], g.CORE * scale, 1.0))
    return L


def vd_doc(w_dp, h_dp, vw, vh, body, comment):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f"<!-- {comment} -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            f'    android:width="{w_dp}dp" android:height="{h_dp}dp" android:viewportWidth="{vw}" android:viewportHeight="{vh}">\n'
            + body + "</vector>\n")


def vd_from_layers(layers):
    out = ""
    for kind, d, color, width, alpha in layers:
        if kind == "stroke":
            out += vd_stroke(d, color, width, alpha)
        else:
            out += vd_fill(d, color, alpha, stroke=color, stroke_w=width)
    return out


# ------------------------------------------------------------------ SVG

def svg_from_layers(layers):
    out = ""
    for kind, d, color, width, alpha in layers:
        if kind == "stroke":
            out += (f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width:.2f}" stroke-opacity="{alpha:.2f}" '
                    'stroke-linecap="round" stroke-linejoin="round"/>\n')
        else:
            out += (f'<path d="{d}" fill="{color}" fill-opacity="{alpha:.2f}" stroke="{color}" stroke-opacity="{alpha:.2f}" '
                    f'stroke-width="{width:.2f}" stroke-linejoin="round"/>\n')
    return out


def svg_icon(pal, size=512, blur=True):
    """Rounded-square app icon with a real blurred glow (for docs / marketing)."""
    scale = size * 0.70 / (g.BBOX[2] - g.BBOX[0])
    c = size / 2
    main, hook, bolt = paths(c, c, scale)
    t = g.TUBE * scale
    rx = size * 0.225
    glow = f'''<g filter="url(#blur)" opacity="0.6">
<path d="{main}" fill="none" stroke="{pal["glow"]}" stroke-width="{t * 1.9:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{hook}" fill="none" stroke="{pal["glow"]}" stroke-width="{t * 1.9:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{bolt}" fill="{pal["glow"]}" stroke="{pal["glow"]}" stroke-width="{t * 1.5:.2f}" stroke-linejoin="round"/>
</g>'''
    body = f'''<path d="{main}" fill="none" stroke="{pal["tube"]}" stroke-width="{t:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{hook}" fill="none" stroke="{pal["tube"]}" stroke-width="{t:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{bolt}" fill="{pal["bolt"]}" stroke="{pal["bolt"]}" stroke-width="{t * 0.35:.2f}" stroke-linejoin="round"/>
<path d="{main}" fill="none" stroke="{pal["core"]}" stroke-width="{g.CORE * scale:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{hook}" fill="none" stroke="{pal["core"]}" stroke-width="{g.CORE * scale:.2f}" stroke-linecap="round" stroke-linejoin="round"/>'''
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" width="{size}" height="{size}">
<defs>
<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{pal["bg_top"]}"/><stop offset="1" stop-color="{pal["bg_bottom"]}"/></linearGradient>
<radialGradient id="amb" cx="0.5" cy="0.48" r="0.5"><stop offset="0" stop-color="{pal["ambient"]}" stop-opacity="0.22"/><stop offset="1" stop-color="{pal["ambient"]}" stop-opacity="0"/></radialGradient>
<filter id="blur" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="{t * 1.6:.2f}"/></filter>
<clipPath id="clip"><rect width="{size}" height="{size}" rx="{rx:.1f}"/></clipPath>
</defs>
<g clip-path="url(#clip)">
<rect width="{size}" height="{size}" fill="url(#bg)"/>
<rect width="{size}" height="{size}" fill="url(#amb)"/>
{glow}
{body}
</g>
</svg>
'''


def svg_mark(pal, width=600):
    """Mark only (transparent), e.g. for README / dark surfaces."""
    bw, bh = g.BBOX[2] - g.BBOX[0], g.BBOX[3] - g.BBOX[1]
    pad = 0.12
    scale = width * (1 - 2 * pad) / bw
    height = round(bh * scale + width * 2 * pad)
    main, hook, bolt = paths(width / 2, height / 2, scale)
    t = g.TUBE * scale
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}">
<defs><filter id="blur" x="-20%" y="-20%" width="140%" height="140%"><feGaussianBlur stdDeviation="{t * 1.6:.2f}"/></filter></defs>
<g filter="url(#blur)" opacity="0.6">
<path d="{main}" fill="none" stroke="{pal["glow"]}" stroke-width="{t * 1.9:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{hook}" fill="none" stroke="{pal["glow"]}" stroke-width="{t * 1.9:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{bolt}" fill="{pal["glow"]}" stroke="{pal["glow"]}" stroke-width="{t * 1.5:.2f}" stroke-linejoin="round"/>
</g>
<path d="{main}" fill="none" stroke="{pal["tube"]}" stroke-width="{t:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{hook}" fill="none" stroke="{pal["tube"]}" stroke-width="{t:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{bolt}" fill="{pal["bolt"]}" stroke="{pal["bolt"]}" stroke-width="{t * 0.35:.2f}" stroke-linejoin="round"/>
<path d="{main}" fill="none" stroke="{pal["core"]}" stroke-width="{g.CORE * scale:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
<path d="{hook}" fill="none" stroke="{pal["core"]}" stroke-width="{g.CORE * scale:.2f}" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
'''


# ------------------------------------------------------------------ outputs

pal = PALETTES["neon"]
FG_SCALE = 0.225          # mark width ≈ 55.7dp: fits the 66dp safe zone of a 108dp adaptive icon

# adaptive icon: foreground
fg_layers = neon_layers(pal, 54, 54, FG_SCALE)
open(f"{OUT}/ic_launcher_foreground.xml", "w").write(vd_doc(
    108, 108, 108, 108, vd_from_layers(fg_layers),
    "EcoWi mark: one neon tube tracing a \"W\" outline, broken by a lightning bolt (vector redraw of the\n"
    "     reference). 108dp adaptive canvas, mark inside the 66dp safe zone. Glow = layered translucent strokes\n"
    "     (VectorDrawable has no blur). Generated by tools/logo/render.py: edit geometry/colours there."))

# adaptive icon: background (grey wall + faint green light from the tube)
bg_body = f'''    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108"
                android:startColor="{argb(pal["bg_top"])}" android:endColor="{argb(pal["bg_bottom"])}" />
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="54" android:centerY="52" android:gradientRadius="46"
                android:startColor="{argb(pal["ambient"], 0.20)}" android:endColor="{argb(pal["ambient"], 0.0)}" />
        </aapt:attr>
    </path>
'''
open(f"{OUT}/ic_launcher_background.xml", "w").write(vd_doc(
    108, 108, 108, 108, bg_body, "Graphite wall lit by the neon (gradient taken from the reference photo)."))

# themed (monochrome) icon: tube + bolt only, system tints it
m_main, m_hook, m_bolt = paths(54, 54, FG_SCALE)
mono = (vd_stroke(m_main, "#FFFFFF", g.TUBE * FG_SCALE * 1.25) + vd_stroke(m_hook, "#FFFFFF", g.TUBE * FG_SCALE * 1.25)
        + vd_fill(m_bolt, "#FFFFFF", stroke="#FFFFFF", stroke_w=g.TUBE * FG_SCALE * 0.35))
open(f"{OUT}/ic_launcher_monochrome.xml", "w").write(vd_doc(108, 108, 108, 108, mono, "Themed-icon (monochrome) version."))

# in-app logo (no background), viewport hugging the mark
bw, bh = g.BBOX[2] - g.BBOX[0], g.BBOX[3] - g.BBOX[1]
LOGO_W = 120
lscale = LOGO_W * 0.86 / bw
lh = round(bh * lscale + LOGO_W * 0.14)
logo_layers = neon_layers(pal, LOGO_W / 2, lh / 2, lscale)
open(f"{OUT}/ic_logo.xml", "w").write(vd_doc(
    round(34 * LOGO_W / LOGO_W), round(34 * lh / LOGO_W), LOGO_W, lh, vd_from_layers(logo_layers),
    "EcoWi mark for in-app use (no background)."))

# status-bar icon: white only, thicker tube so it survives 24dp
SB = 100
sscale = SB * 0.94 / bw
s_main, s_hook, s_bolt = paths(SB / 2, SB / 2, sscale)
sw = 4.6
stat = (vd_stroke(s_main, "#FFFFFF", sw) + vd_stroke(s_hook, "#FFFFFF", sw)
        + vd_fill(s_bolt, "#FFFFFF", stroke="#FFFFFF", stroke_w=1.6))
open(f"{OUT}/ic_stat_ecowi.xml", "w").write(
    vd_doc(24, 24, SB, SB, stat, "Status-bar icon: white only (the system tints it)."))

# SVGs for docs
for name, p in PALETTES.items():
    open(f"{OUT}/ecowi_{name}.svg", "w").write(svg_icon(p))
open(f"{OUT}/ecowi_mark.svg", "w").write(svg_mark(pal))

# preview of what Android renders (same layers as the VectorDrawables, no blur)
prev_fg = svg_from_layers(fg_layers)
open(f"{OUT}/android_preview.svg", "w").write(f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="432" height="432">
<defs>
<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{pal["bg_top"]}"/><stop offset="1" stop-color="{pal["bg_bottom"]}"/></linearGradient>
<radialGradient id="amb" cx="54" cy="52" r="46" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{pal["ambient"]}" stop-opacity="0.20"/><stop offset="1" stop-color="{pal["ambient"]}" stop-opacity="0"/></radialGradient>
<clipPath id="squircle"><rect x="18" y="18" width="72" height="72" rx="22"/></clipPath>
</defs>
<g clip-path="url(#squircle)">
<rect width="108" height="108" fill="url(#bg)"/><rect width="108" height="108" fill="url(#amb)"/>
{prev_fg}
</g>
</svg>
''')
open(f"{OUT}/stat_preview.svg", "w").write(f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="96" height="96">
<rect width="100" height="100" fill="#202124"/>
{svg_from_layers([("stroke", s_main, "#FFFFFF", sw, 1), ("stroke", s_hook, "#FFFFFF", sw, 1), ("fillstroke", s_bolt, "#FFFFFF", 1.6, 1)])}
</svg>
''')
if "--install" in sys.argv:
    res = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
    for f in ("ic_launcher_foreground", "ic_launcher_background", "ic_launcher_monochrome", "ic_logo", "ic_stat_ecowi"):
        shutil.copy(os.path.join(OUT, f + ".xml"), res)
    for f in ["ecowi_mark"] + ["ecowi_" + n for n in PALETTES]:
        shutil.copy(os.path.join(OUT, f + ".svg"), os.path.join(ROOT, "docs"))
print("ok", OUT)
