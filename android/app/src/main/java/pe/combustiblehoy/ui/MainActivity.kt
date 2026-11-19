package pe.combustiblehoy.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.combustiblehoy.ui.screens.FavoritesScreen
import pe.combustiblehoy.ui.screens.NearbyScreen
import pe.combustiblehoy.ui.screens.OnboardingScreen
import pe.combustiblehoy.ui.screens.SettingsScreen
import pe.combustiblehoy.ui.theme.CombustibleTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CombustibleTheme { Root(vm) } }
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    NEARBY("Cercanos", Icons.Filled.LocationOn),
    FAVORITES("Favoritos", Icons.Filled.Star),
    SETTINGS("Ajustes", Icons.Filled.Settings),
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
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (Tab.entries[tab]) {
                Tab.NEARBY -> NearbyScreen(vm, s)
                Tab.FAVORITES -> FavoritesScreen(vm, s, onAddFavorites = { tab = Tab.NEARBY.ordinal })
                Tab.SETTINGS -> SettingsScreen(vm, s)
            }
        }
    }
}
