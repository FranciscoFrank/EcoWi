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

Writes `docs/widgets_mock.svg/.png` (English), `docs/widgets_mock.uk.svg/.png` (Ukrainian) and `docs/logo_variants.png`.

Needs `rsvg-convert` (librsvg) and Pillow (`pip install pillow`). Install the Unbounded and Manrope fonts from `app/src/main/res/font/` on your system first, so the text renders in the app's typefaces. Run `tools/logo/render.py --install` before it if you changed the logo.
