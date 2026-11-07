# Tools

Small Python scripts that generate assets. Don't edit their outputs by hand: change the scripts and re-run them.

## `logo/`: logo and app icons

```bash
python tools/logo/render.py            # writes everything into build/logo/
python tools/logo/render.py --install  # also copies into app/src/main/res/drawable and docs/
```

- `geometry.py` holds the shape of the neon mark: stroke lines, bend radii, the two breaks and the bolt, all measured on the original reference.
- `render.py` holds the colours (`PALETTES`; `neon` is the app icon) and writes:
  - `ic_launcher_foreground.xml`, `ic_launcher_background.xml`, `ic_launcher_monochrome.xml` (adaptive and themed launcher icon),
  - `ic_logo.xml` (in-app header) and `ic_stat_ecowi.xml` (status bar),
  - `docs/ecowi_{neon,volt,ice}.svg` and `docs/ecowi_mark.svg`.

Needs Python 3.9+ only.

## `docs/`: README images

```bash
python tools/docs/render_images.py
```

Writes `docs/widgets_mock.svg/.png` (English), `docs/widgets_mock.uk.svg/.png` (Ukrainian), `docs/logo_variants.png` and `docs/palette.png` (the Neon palette from [docs/BRAND.md](../docs/BRAND.md)).

Needs `rsvg-convert` (librsvg) and Pillow (`pip install pillow`). Install the Unbounded and Manrope fonts from `app/src/main/res/font/` on your system first, so the text renders in the app's typefaces. Run `tools/logo/render.py --install` before it if you changed the logo.

## `fonts/`: widget-picker preview

```bash
python tools/fonts/preview_text.py
```

Widget picker previews are static layouts that can't run code, so the sample charge ("78%") is turned into a VectorDrawable from the Unbounded glyphs: `app/src/main/res/drawable/preview_soc.xml`. The live widgets draw the same text at runtime (`appwidget/SocText.kt`). Needs `pip install fonttools`.

## `site/`: the website

```bash
python tools/site/build.py                     # site/ -> _site/ (English at /, Ukrainian at /uk/)
python -m http.server -d _site 8000            # preview at http://localhost:8000
```

Fills in the bilingual template `site/index.html`, inlines the logo and the widget mockup from `docs/`, and copies the app's fonts with their licences. Standard library only. GitHub Actions runs it on deploy (`.github/workflows/pages.yml`, `--base-url` comes from the Pages settings).
