package pe.combustiblehoy.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.combustiblehoy.core.Fuel
import pe.combustiblehoy.core.Geo
import pe.combustiblehoy.ui.MainViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(vm: MainViewModel) {
    val departments by vm.departments.collectAsStateWithLifecycle()
    val location by vm.location.collectAsStateWithLifecycle()
    var fuel by remember { mutableStateOf(Fuel.GHR) }
    var dept by remember { mutableStateOf("lima") }

    LaunchedEffect(location, departments) {
        location?.let { here ->
            val available = departments.map { it.slug }.ifEmpty { Geo.departmentCenters.keys.toList() }
            Geo.nearestDepartment(here, available)?.let { dept = it }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) vm.locate(autoSelectDepartment = false)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.completeOnboarding(fuel, dept)
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Combustible Hoy", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Mira el precio de tu combustible en tus grifos favoritos, directo en tu pantalla de inicio. " +
                "Precios declarados por los grifos ante Osinergmin, actualizados varias veces al día.",
            style = MaterialTheme.typography.bodyLarge,
        )

        Text("¿Qué combustible usas?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fuel.entries.forEach { f ->
                FilterChip(selected = f == fuel, onClick = { fuel = f }, label = { Text(f.label) })
            }
        }

        Text("¿Dónde cargas combustible?", style = MaterialTheme.typography.titleMedium)
        DepartmentPicker(dept, departments) { dept = it }
        OutlinedButton(onClick = {
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }) { Text(if (location != null) "Ubicación detectada ✓" else "Usar mi ubicación") }

        Spacer(Modifier.height(8.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    vm.completeOnboarding(fuel, dept)
                }
            },
        ) { Text("Empezar") }
        Text(
            Legal.DISCLAIMER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
