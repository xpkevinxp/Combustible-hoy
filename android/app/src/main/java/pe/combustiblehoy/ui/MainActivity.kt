package pe.combustiblehoy.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.combustiblehoy.ui.screens.FavoritesScreen
import pe.combustiblehoy.ui.screens.LocalSnackbar
import pe.combustiblehoy.ui.screens.NearbyScreen
import pe.combustiblehoy.ui.screens.OnboardingScreen
import pe.combustiblehoy.ui.screens.SettingsScreen
import pe.combustiblehoy.ui.theme.CombustibleTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) vm.requestOpenStation(intent?.getStringExtra(EXTRA_STATION_ID))
        setContent { CombustibleTheme { Root(vm) } }
    }

    // singleTop: si la app ya estaba abierta, el toque en el widget llega aquí.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.requestOpenStation(intent.getStringExtra(EXTRA_STATION_ID))
    }

    companion object {
        const val EXTRA_STATION_ID = "station_id"
    }
}

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    NEARBY("Cercanos", Icons.Outlined.Explore, Icons.Filled.Explore),
    FAVORITES("Mis grifos", Icons.Outlined.StarOutline, Icons.Filled.Star),
    SETTINGS("Ajustes", Icons.Outlined.Settings, Icons.Filled.Settings),
}

@Composable
private fun Root(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (!s.onboarded) {
        OnboardingScreen(vm)
        return
    }
    var tab by rememberSaveable { mutableIntStateOf(Tab.FAVORITES.ordinal) }
    val openStation by vm.openStation.collectAsStateWithLifecycle()
    LaunchedEffect(openStation) { if (openStation != null) tab = Tab.FAVORITES.ordinal }
    val snackbar = remember { SnackbarHostState() }
    // Cada pantalla maneja su barra superior (y el inset de la barra de estado); aquí solo la navegación
    // y el snackbar, que así queda siempre sobre la barra inferior y sobrevive al cambiar de pestaña.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(if (tab == t.ordinal) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            // Fundido corto entre pestañas: da continuidad sin retrasar la navegación.
            Crossfade(
                targetState = Tab.entries[tab],
                modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                animationSpec = tween(180),
                label = "tab",
            ) { current ->
                when (current) {
                    Tab.NEARBY -> NearbyScreen(vm, s, onOpenFavorites = { tab = Tab.FAVORITES.ordinal })
                    Tab.FAVORITES -> FavoritesScreen(
                        vm,
                        s,
                        onAddFavorites = { tab = Tab.NEARBY.ordinal },
                        openStationId = openStation,
                        onStationOpened = vm::consumeOpenStation,
                    )
                    Tab.SETTINGS -> SettingsScreen(vm, s)
                }
            }
        }
    }
}
