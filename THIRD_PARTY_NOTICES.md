# Third-party notices

EcoWi's own code is released into the public domain under [The Unlicense](LICENSE). The following third-party material is included in the repository or linked into the app, and remains under its own license.

## Included in this repository

| Material | Where | License |
|---|---|---|
| Unbounded typeface, © 2022 The Unbounded Project Authors | `app/src/main/res/font/unbounded_*.ttf` | [SIL Open Font License 1.1](app/src/main/assets/licenses/OFL-Unbounded.txt) |
| Manrope typeface, © 2018 The Manrope Project Authors | `app/src/main/res/font/manrope_*.ttf` | [SIL Open Font License 1.1](app/src/main/assets/licenses/OFL-Manrope.txt) |
| DELTA 3 protobuf field numbers and names, derived from `ef_delta3.proto` in [tolwi/hassio-ecoflow-cloud](https://github.com/tolwi/hassio-ecoflow-cloud) | `app/src/main/kotlin/ua/ecowi/core/ecoflow/Delta3Fields.kt` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Gradle Wrapper | `gradlew`, `gradlew.bat`, `gradle/wrapper/` | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

The fonts are subsets of the original files; the website serves the same files with their licence texts next to them (`assets/fonts/`). The field table keeps only field numbers and names; the values, comments and code around them are EcoWi's own.

## Linked into the app

These libraries are downloaded by Gradle at build time and packaged into the APK.

| Library | License |
|---|---|
| AndroidX Core, Activity, Lifecycle, WorkManager | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Jetpack Compose (UI, Material 3) | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Kotlin standard library, kotlinx.coroutines | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |
| Eclipse Paho MQTT Java client (`org.eclipse.paho.client.mqttv3`) | [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/) or [Eclipse Distribution License 1.0](https://www.eclipse.org/org/documents/edl-v10.php) |

Test only (not packaged): JUnit 4, [Eclipse Public License 1.0](https://www.eclipse.org/legal/epl-v10.html).

## Trademarks

"EcoFlow" and "DELTA" are trademarks of their respective owner. They are used only to describe which devices EcoWi works with. EcoWi isn't affiliated with or endorsed by EcoFlow.
