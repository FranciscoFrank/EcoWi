package ua.ecowi.data

import android.content.Context
import ua.ecowi.core.DisplayMode
import ua.ecowi.core.EventConfig
import java.util.UUID

/** Where station data comes from. */
enum class DataSourceKind {
    /** Same login and MQTT channel as the EcoFlow app (unofficial, works for DELTA 3 Classic). */
    APP_ACCOUNT,

    /** Official developer portal keys (REST + Open MQTT). */
    DEVELOPER_API,
}

enum class ApiRegion(val host: String) {
    /** developer-eu.ecoflow.com accounts (Ukraine, EU). */
    EUROPE("api-e.ecoflow.com"),
    AMERICAS("api-a.ecoflow.com"),
    GLOBAL("api.ecoflow.com"),
}

/** Developer-portal keys. */
data class Credentials(
    val accessKey: String,
    val secretKey: String,
    val serialNumber: String,
    val region: ApiRegion,
) {
    val isComplete: Boolean
        get() = accessKey.isNotBlank() && secretKey.isNotBlank() && serialNumber.isNotBlank()
}

/** EcoFlow app account. */
data class AccountCredentials(
    val email: String,
    val password: String,
    val serialNumber: String,
    val host: String = DEFAULT_APP_HOST,
) {
    val isComplete: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && serialNumber.isNotBlank()

    companion object {
        const val DEFAULT_APP_HOST = "api.ecoflow.com"
    }
}

enum class WidgetTheme { SYSTEM, LIGHT, DARK }

/** Look of one widget instance. */
data class WidgetAppearance(
    val theme: WidgetTheme = WidgetTheme.SYSTEM,
    /** Background opacity 0–100 %. */
    val backgroundOpacity: Int = 100,
    /** Wallpaper-based colours when the system supports them (Android 12+). */
    val dynamicColor: Boolean = true,
)

/** App-level and per-widget settings. Secrets are stored encrypted, the rest in plain prefs. */
class AppSettings(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ecowi_settings", Context.MODE_PRIVATE)
    private val secure = SecureStore(app)

    var source: DataSourceKind
        get() = prefs.getString(K_SOURCE, null)
            ?.let { runCatching { DataSourceKind.valueOf(it) }.getOrNull() } ?: DataSourceKind.APP_ACCOUNT
        set(value) = prefs.edit().putString(K_SOURCE, value.name).apply()

    // ------------------------------------------------------------------ developer API

    var credentials: Credentials?
        get() {
            val ak = secure.getString(K_ACCESS) ?: return null
            val sk = secure.getString(K_SECRET) ?: return null
            val sn = prefs.getString(K_SN, null) ?: return null
            val region = prefs.getString(K_REGION, null)
                ?.let { runCatching { ApiRegion.valueOf(it) }.getOrNull() } ?: ApiRegion.EUROPE
            return Credentials(ak, sk, sn, region).takeIf { it.isComplete }
        }
        set(value) {
            secure.putString(K_ACCESS, value?.accessKey?.trim())
            secure.putString(K_SECRET, value?.secretKey?.trim())
            prefs.edit()
                .putString(K_SN, value?.serialNumber?.trim())
                .putString(K_REGION, value?.region?.name)
                .remove(K_REST_BLOCKED_UNTIL)
                .apply()
        }

    // ------------------------------------------------------------------ app account

    var account: AccountCredentials?
        get() {
            val email = prefs.getString(K_ACC_EMAIL, null) ?: return null
            val password = secure.getString(K_ACC_PASSWORD) ?: return null
            val sn = prefs.getString(K_ACC_SN, null) ?: return null
            val host = prefs.getString(K_ACC_HOST, null) ?: AccountCredentials.DEFAULT_APP_HOST
            return AccountCredentials(email, password, sn, host).takeIf { it.isComplete }
        }
        set(value) {
            secure.putString(K_ACC_PASSWORD, value?.password)
            prefs.edit()
                .putString(K_ACC_EMAIL, value?.email?.trim())
                .putString(K_ACC_SN, value?.serialNumber?.trim()?.uppercase())
                .putString(K_ACC_HOST, value?.host?.trim())
                .apply()
            appSession = null
        }

    /** Cached login (token + MQTT certificate) so we don't log in on every refresh. */
    var appSession: AppSession?
        get() = secure.getString(K_ACC_SESSION)?.let(AppSession::fromJson)
        set(value) = secure.putString(K_ACC_SESSION, value?.toJson())

    /** Serial of the station for whichever source is active. */
    val serialNumber: String?
        get() = when (source) {
            DataSourceKind.APP_ACCOUNT -> account?.serialNumber
            DataSourceKind.DEVELOPER_API -> credentials?.serialNumber
        }

    val isConfigured: Boolean get() = serialNumber != null

    /**
     * EcoFlow limits unique MQTT client IDs per account per day (~10), so every ID is derived
     * from one per-install UUID and reused for every connection.
     */
    private val installId: String
        get() = prefs.getString(K_INSTALL_ID, null)
            ?: UUID.randomUUID().toString().replace("-", "").uppercase()
                .also { prefs.edit().putString(K_INSTALL_ID, it).apply() }

    /** Developer Open MQTT. */
    val mqttClientId: String get() = "ecowi-android-" + installId.take(8).lowercase()

    /** App MQTT expects `ANDROID_<HEX>_<userId>`. Live service and snapshots use distinct suffixes. */
    fun appMqttClientId(userId: String, live: Boolean): String =
        "ANDROID_${installId}${if (live) "" else "S"}_$userId"

    /** When REST quota/all is rejected by the API (DELTA 3 series), skip it for a while. */
    var restBlockedUntilMs: Long
        get() = prefs.getLong(K_REST_BLOCKED_UNTIL, 0L)
        set(value) = prefs.edit().putLong(K_REST_BLOCKED_UNTIL, value).apply()

    // ------------------------------------------------------------------ refresh, live, alerts

    /** Minutes between background refreshes when live mode is off. */
    var refreshIntervalMin: Int
        get() = prefs.getInt(K_INTERVAL, 15)
        set(value) = prefs.edit().putInt(K_INTERVAL, value).apply()

    /** Keep a persistent connection (foreground service) for instant widgets and alerts. */
    var liveMode: Boolean
        get() = prefs.getBoolean(K_LIVE, false)
        set(value) = prefs.edit().putBoolean(K_LIVE, value).apply()

    var eventConfig: EventConfig
        get() = EventConfig(
            notifyLow = prefs.getBoolean(K_N_LOW, true),
            notifyFull = prefs.getBoolean(K_N_FULL, true),
            notifyChargingStarted = prefs.getBoolean(K_N_CHARGING, true),
            notifyGrid = prefs.getBoolean(K_N_GRID, true),
            notifyPorts = prefs.getBoolean(K_N_PORTS, true),
        )
        set(value) = prefs.edit()
            .putBoolean(K_N_LOW, value.notifyLow)
            .putBoolean(K_N_FULL, value.notifyFull)
            .putBoolean(K_N_CHARGING, value.notifyChargingStarted)
            .putBoolean(K_N_GRID, value.notifyGrid)
            .putBoolean(K_N_PORTS, value.notifyPorts)
            .apply()

    // ------------------------------------------------------------------ widgets

    fun displayMode(widgetId: Int): DisplayMode =
        prefs.getString("mode_$widgetId", null)
            ?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() } ?: DisplayMode.CYCLE

    fun cycleSeconds(widgetId: Int): Int = prefs.getInt("cycle_$widgetId", DEFAULT_CYCLE_SEC)

    fun appearance(widgetId: Int): WidgetAppearance = WidgetAppearance(
        theme = prefs.getString("theme_$widgetId", null)
            ?.let { runCatching { WidgetTheme.valueOf(it) }.getOrNull() } ?: defaultAppearance.theme,
        backgroundOpacity = prefs.getInt("opacity_$widgetId", defaultAppearance.backgroundOpacity),
        dynamicColor = prefs.getBoolean("dyn_$widgetId", defaultAppearance.dynamicColor),
    )

    /** What new widgets start with (editable in the app). */
    var defaultAppearance: WidgetAppearance
        get() = WidgetAppearance(
            theme = prefs.getString(K_DEF_THEME, null)
                ?.let { runCatching { WidgetTheme.valueOf(it) }.getOrNull() } ?: WidgetTheme.SYSTEM,
            backgroundOpacity = prefs.getInt(K_DEF_OPACITY, 100),
            dynamicColor = prefs.getBoolean(K_DEF_DYN, true),
        )
        set(value) = prefs.edit()
            .putString(K_DEF_THEME, value.theme.name)
            .putInt(K_DEF_OPACITY, value.backgroundOpacity)
            .putBoolean(K_DEF_DYN, value.dynamicColor)
            .apply()

    fun saveWidget(widgetId: Int, mode: DisplayMode, cycleSeconds: Int, appearance: WidgetAppearance) {
        prefs.edit()
            .putString("mode_$widgetId", mode.name)
            .putInt("cycle_$widgetId", cycleSeconds)
            .putString("theme_$widgetId", appearance.theme.name)
            .putInt("opacity_$widgetId", appearance.backgroundOpacity.coerceIn(0, 100))
            .putBoolean("dyn_$widgetId", appearance.dynamicColor)
            .apply()
    }

    fun removeWidget(widgetId: Int) {
        prefs.edit()
            .remove("mode_$widgetId").remove("cycle_$widgetId")
            .remove("theme_$widgetId").remove("opacity_$widgetId").remove("dyn_$widgetId")
            .apply()
    }

    companion object {
        const val DEFAULT_CYCLE_SEC = 5
        val INTERVAL_CHOICES = listOf(5, 10, 15, 30, 60)
        val CYCLE_CHOICES = listOf(3, 5, 8, 12)

        private const val K_SOURCE = "source"
        private const val K_ACCESS = "access_key"
        private const val K_SECRET = "secret_key"
        private const val K_SN = "serial_number"
        private const val K_REGION = "region"
        private const val K_ACC_EMAIL = "acc_email"
        private const val K_ACC_PASSWORD = "acc_password"
        private const val K_ACC_SN = "acc_sn"
        private const val K_ACC_HOST = "acc_host"
        private const val K_ACC_SESSION = "acc_session"
        private const val K_INSTALL_ID = "install_id"
        private const val K_INTERVAL = "refresh_interval_min"
        private const val K_REST_BLOCKED_UNTIL = "rest_blocked_until"
        private const val K_LIVE = "live_mode"
        private const val K_N_LOW = "notify_low"
        private const val K_N_FULL = "notify_full"
        private const val K_N_CHARGING = "notify_charging"
        private const val K_N_GRID = "notify_grid"
        private const val K_N_PORTS = "notify_ports"
        private const val K_DEF_THEME = "def_theme"
        private const val K_DEF_OPACITY = "def_opacity"
        private const val K_DEF_DYN = "def_dynamic"
    }
}
