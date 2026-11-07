package ua.ecowi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ua.ecowi.R
import ua.ecowi.live.LiveService
import ua.ecowi.notify.Notifier
import ua.ecowi.ui.components.EcoIcon
import ua.ecowi.ui.screens.AlertsScreen
import ua.ecowi.ui.screens.ConnectionScreen
import ua.ecowi.ui.screens.StationScreen
import ua.ecowi.ui.screens.WidgetsScreen
import ua.ecowi.ui.theme.EcoWiTheme
import ua.ecowi.ui.theme.LocalEco

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannels(this)
        LiveService.sync(this) // allowed here: the activity is in the foreground
        setContent { EcoWiTheme { EcoWiApp() } }
    }
}

enum class Tab(@StringRes val title: Int, @DrawableRes val icon: Int) {
    STATION(R.string.tab_station, R.drawable.ic_nav_station),
    WIDGETS(R.string.tab_widgets, R.drawable.ic_nav_widgets),
    ALERTS(R.string.tab_alerts, R.drawable.ic_nav_bell),
    CONNECTION(R.string.tab_connection, R.drawable.ic_ac),
}

@Composable
fun EcoWiApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ui by rememberStationUi()
    val eco = LocalEco.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { EcoIcon(t.icon, if (tab == i) MaterialTheme.colorScheme.onPrimary else eco.muted) },
                        label = { Text(stringResource(t.title), style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            unselectedTextColor = eco.muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(targetState = tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "tab") { t ->
                when (Tab.entries[t]) {
                    Tab.STATION -> StationScreen(ui, onOpenConnection = { tab = Tab.CONNECTION.ordinal })
                    Tab.WIDGETS -> WidgetsScreen()
                    Tab.ALERTS -> AlertsScreen(ui)
                    Tab.CONNECTION -> ConnectionScreen()
                }
            }
        }
    }
}

