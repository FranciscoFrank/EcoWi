#!/usr/bin/env python3
"""Build the GitHub Pages site: site/ -> _site/ (English at /, Ukrainian at /uk/).

    python tools/site/build.py [out_dir] [--base-url https://user.github.io/EcoWi]
    python -m http.server -d _site 8000        # preview at http://localhost:8000

site/index.html is a template with two kinds of markers:

    {{English text|Український текст}}   the text for the page's language
    {{:name}}                            a value filled in here (see `values` below)

Fonts are copied from app/src/main/res/font (with their OFL licences) and pictures from
docs/, so the site always matches the app. Standard library only.
"""
from __future__ import annotations

import argparse
import html
import pathlib
import re
import shutil
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SITE = ROOT / "site"
DOCS = ROOT / "docs"
DEFAULT_BASE = "https://franciscofrank.github.io/EcoWi"

FONTS = {
    "unbounded_semibold.ttf": "unbounded-semibold.ttf",
    "manrope_regular.ttf": "manrope-regular.ttf",
    "manrope_semibold.ttf": "manrope-semibold.ttf",
}
LICENCES = ["OFL-Unbounded.txt", "OFL-Manrope.txt"]

BILINGUAL = re.compile(r"\{\{(?!:)([^{}|]*)\|([^{}]*)\}\}")
VALUE = re.compile(r"\{\{:([a-z_]+)\}\}")


def inline_svg(path: pathlib.Path, prefix: str, attrs: str) -> str:
    """An SVG file as inline markup with ids scoped by [prefix], so several can share a page."""
    svg = re.sub(r"<\?xml[^>]*\?>\s*", "", path.read_text(encoding="utf-8")).strip()
    for name in sorted(set(re.findall(r'\bid="([^"]+)"', svg)), key=len, reverse=True):
        scoped = f"{prefix}-{name}"
        svg = (svg.replace(f'id="{name}"', f'id="{scoped}"')
                  .replace(f"url(#{name})", f"url(#{scoped})")
                  .replace(f'href="#{name}"', f'href="#{scoped}"'))
    # Size comes from CSS: drop the root width/height, keep the viewBox.
    head, rest = svg.split(">", 1)
    head = re.sub(r'\s(width|height)="[^"]*"', "", head)
    return f"{head} {attrs}>{rest}"


def render(template: str, lang: str, values: dict[str, str]) -> str:
    pick = 0 if lang == "en" else 1
    out = BILINGUAL.sub(lambda m: m.group(1 + pick), template)

    def value(m: re.Match) -> str:
        if m.group(1) not in values:
            sys.exit(f"site/index.html: unknown value {{{{:{m.group(1)}}}}}")
        return values[m.group(1)]

    out = VALUE.sub(value, out)
    if "{{" in out or "}}" in out:
        line = next(l for l in out.splitlines() if "{{" in l or "}}" in l)
        sys.exit(f"site/index.html: unbalanced marker near: {line.strip()[:120]}")
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out", nargs="?", default=str(ROOT / "_site"))
    ap.add_argument("--base-url", default=DEFAULT_BASE, help="public URL of the site, without a trailing slash")
    args = ap.parse_args()

    out = pathlib.Path(args.out).resolve()
    base = (args.base_url or DEFAULT_BASE).rstrip("/")
    if out == ROOT or out == SITE:
        sys.exit("refusing to build into the repository root or site/")
    if out.exists():
        shutil.rmtree(out)
    (out / "assets" / "fonts").mkdir(parents=True)

    for src in (SITE / "assets").iterdir():
        shutil.copy2(src, out / "assets" / src.name)
    for src, dst in FONTS.items():
        shutil.copy2(ROOT / "app/src/main/res/font" / src, out / "assets/fonts" / dst)
    for name in LICENCES:
        shutil.copy2(ROOT / "app/src/main/assets/licenses" / name, out / "assets/fonts" / name)
    shutil.copy2(DOCS / "ecowi_neon.svg", out / "assets/icon.svg")
    shutil.copy2(DOCS / "widgets_mock.png", out / "assets/widgets.png")
    shutil.copy2(DOCS / "widgets_mock.uk.png", out / "assets/widgets.uk.png")

    template = (SITE / "index.html").read_text(encoding="utf-8")
    urls = {"en": f"{base}/", "uk": f"{base}/uk/"}
    for lang in ("en", "uk"):
        other = "uk" if lang == "en" else "en"
        mock_label = ("EcoWi widgets on a phone home screen and lock screen" if lang == "en"
                      else "Віджети EcoWi на головному екрані й екрані блокування телефона")
        values = {
            "lang": lang,
            "root": "" if lang == "en" else "../",
            "url": urls[lang],
            "url_en": urls["en"],
            "url_uk": urls["uk"],
            "other": "uk/" if lang == "en" else "../",
            "other_lang": other,
            "og_image": f"{base}/assets/{'widgets.png' if lang == 'en' else 'widgets.uk.png'}",
            "logo": inline_svg(DOCS / "ecowi_mark.svg", "logo", 'class="brand-mark" aria-hidden="true" focusable="false"'),
            "mock": inline_svg(DOCS / ("widgets_mock.svg" if lang == "en" else "widgets_mock.uk.svg"), "mock",
                               f'class="mock" role="img" aria-label="{html.escape(mock_label)}"'),
        }
        page = render(template, lang, values)
        target = out / ("index.html" if lang == "en" else "uk/index.html")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(page, encoding="utf-8")

    print(f"built {out} for {base}/")


if __name__ == "__main__":
    main()
