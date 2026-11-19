package pe.combustiblehoy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.combustiblehoy.core.Dates
import pe.combustiblehoy.data.UserSettings
import pe.combustiblehoy.ui.MainViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val THRESHOLDS = listOf(0.05, 0.10, 0.20, 0.50)

@Composable
fun SettingsScreen(vm: MainViewModel, settings: UserSettings) {
    val departments by vm.departments.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Section("Combustible favorito")
        FuelChips(settings.fuel, vm::setFuel)

        Section("Departamento")
        Row(Modifier.padding(horizontal = 16.dp)) { DepartmentPicker(settings.department, departments, vm::setDepartment) }

        HorizontalDivider()
        Section("Alertas de precio")
        SwitchRow("Avisarme cuando cambie el precio en mis grifos", settings.alertsEnabled, vm::setAlertsEnabled)
        SwitchRow("Solo cuando baje", settings.onlyDrops, vm::setOnlyDrops, enabled = settings.alertsEnabled)
        Text("Cambio mínimo", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            THRESHOLDS.forEach { t ->
                FilterChip(
                    selected = kotlin.math.abs(settings.alertThreshold - t) < 1e-6,
                    onClick = { vm.setAlertThreshold(t) },
                    enabled = settings.alertsEnabled,
                    label = { Text(String.format(Locale.US, "S/ %.2f", t)) },
                )
            }
        }

        HorizontalDivider()
        Section("Datos")
        Text(
            buildString {
                append("Fuente: Osinergmin – SCOP, \"Últimos precios registrados\" por los establecimientos de venta al público. ")
                append("Cada grifo declara su precio; algunos no lo actualizan seguido (se muestra la antigüedad). ")
                append("La ubicación es aproximada (capital del distrito). Verifica el precio en el surtidor.\n\n")
                if (settings.lastDataDate.isNotBlank()) append("Datos al: ${Dates.pretty(settings.lastDataDate)}\n")
                if (settings.lastSyncAt > 0) {
                    append("Última sincronización: ")
                    append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(settings.lastSyncAt)))
                }
            },
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
