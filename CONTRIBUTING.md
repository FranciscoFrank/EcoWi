# Contributing to EcoWi

Thanks for taking the time to help. EcoWi is a small community project, so every bug report, translation, protocol finding and pull request makes a real difference.

By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Ways to contribute

- **Report a bug** or **suggest a feature**: open an [issue](https://github.com/FranciscoFrank/EcoWi/issues/new/choose) using one of the templates.
- **Report how EcoWi works with your station**: use the *Station data report* template. Raw keys from *Connection → Test* are the most useful thing you can share for new models and firmware versions.
- **Translate** EcoWi into your language (see [Translations](#translations)).
- **Send a pull request** with a fix or a feature. For anything bigger than a small fix, open an issue first so we can agree on the approach before you spend time on it.

## Development setup

1. Install Android Studio with support for AGP 9.4 and JDK 17.
2. Clone the repository and open it in Android Studio, or build from the command line:

   ```bash
   ./gradlew assembleDebug
   ./gradlew test
   ```

3. Everything under `app/src/main/kotlin/ua/ecowi/core/` is plain Kotlin with no Android dependencies, covered by JVM unit tests in `app/src/test/`. Put new parsing or decision logic there whenever you can, and test it.

See the [project structure](README.md#project-structure) in the README for where things live.

## Code style

- Kotlin official code style (`kotlin.code.style=official`); 4-space indentation; see `.editorconfig`.
- No hard-coded user-visible text: add strings to `res/values/strings.xml` (English, the base language) **and** `res/values-uk/strings.xml`. If you can't write the Ukrainian text, copy the English one and say so in the pull request.
- Widgets use `RemoteViews`, so only the view classes RemoteViews allows (`FrameLayout`, `LinearLayout`, `TextView`, `ImageView`, `ViewFlipper`, …) may appear in widget layouts. A plain `<View>` crashes the widget at runtime.
- Keep `minSdk 26` working: guard newer APIs with `Build.VERSION.SDK_INT` checks.
- Icons and README images are generated: change `tools/logo/` or `tools/docs/` and re-run them instead of editing the outputs by hand (see [tools/README.md](tools/README.md)).

## Commit messages

Commits follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/):

```
<type>(<optional scope>)<optional !>: <description>

<optional body>

<optional footer(s)>
```

- **Types**: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
- **Scopes** used in this repo: `core`, `data`, `widget`, `notify`, `live`, `ui`, `i18n`, `icon`, `tools`, `deps`.
- Write the description in the imperative mood, lower case, without a trailing period, ideally under 72 characters.
- Mark breaking changes with `!` after the type/scope and a `BREAKING CHANGE:` footer.

Examples:

```
feat(widget): add 4x2 ring with phone battery
fix(data): re-login when the MQTT certificate is rejected
docs: explain how to enable the Now Bar on One UI 8
build(deps): bump Compose BOM to 2026.08.00
```

## Pull requests

1. Fork the repository and create a branch from `master`, for example `feat/ac-toggle` or `fix/widget-radius`.
2. Make focused commits that follow the convention above.
3. Before opening the pull request:
   - `./gradlew test` passes;
   - new strings exist in both `values/` and `values-uk/`;
   - UI changes come with a screenshot or short screen recording;
   - no credentials, tokens, serial numbers or personal data are included anywhere, including test fixtures and logs.
4. Fill in the pull request template and describe how you tested the change, ideally on which phone, Android / One UI version and station model.

## Translations

English (`res/values/strings.xml`) is the source of truth. To add a language:

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml` (for example `values-de`).
2. Translate the text. Keep every format placeholder exactly as it is (`%1$d`, `%1$s`, `%2$d`, `%%`) and keep `formatted="false"` where it appears.
3. Add the language code to `localeFilters` in `app/build.gradle.kts`, for example `setOf("en", "uk", "de")`. The per-app language list on Android 13+ is generated from it.
4. Check the app in the new language: on Android 13+ via *Settings → Apps → EcoWi → Language*.

## Protocol research

EcoWi depends on reverse-engineered EcoFlow protocols, so findings are valuable. Some rules keep everyone safe:

- Never commit or post credentials, access tokens, MQTT passwords, certificates or full serial numbers. Redact them (`R351ZE1A…XXXX`) before sharing logs or captures.
- Protobuf field tables for the DELTA 3 family live in `core/ecoflow/Delta3Fields.kt`. If you add or rename fields, say where the information comes from.
- Add a unit test with a captured or generated frame (as hex) for every decoding change. Strip personal data from captured frames first.

## License of contributions

EcoWi is released under [The Unlicense](LICENSE). By submitting a contribution, you agree to dedicate it to the public domain under the same terms, and you confirm that you have the right to do so.
