package pe.combustiblehoy.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pe.combustiblehoy.BuildConfig
import pe.combustiblehoy.core.Dates
import pe.combustiblehoy.data.UserSettings
import pe.combustiblehoy.ui.MainViewModel
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

private val THRESHOLDS = listOf(0.05, 0.10, 0.20, 0.50)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, settings: UserSettings) {
    val departments by vm.departments.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    val scroll = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { ScreenTopBar("Ajustes", scrollBehavior = scroll) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            SectionLabel("Preferencias")
            SettingsGroup {
                RowItem(Icons.Outlined.LocalGasStation, "Combustible", "Se usa en la app, el widget y las alertas")
                FuelGrid(settings.fuel, vm::setFuel, Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp))
                GroupDivider()
                RowItem(Icons.Outlined.Place, "Departamento", "Dónde buscar grifos cercanos")
                DepartmentPicker(
                    settings.department,
                    departments,
                    vm::setDepartment,
                    Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            SectionLabel("Alertas de precio")
            SettingsGroup {
                SwitchItem(
                    Icons.Outlined.NotificationsActive,
                    "Avisarme si cambia el precio",
                    "En tus grifos favoritos",
                    settings.alertsEnabled,
                    vm::setAlertsEnabled,
                )
                GroupDivider()
                SwitchItem(
                    Icons.AutoMirrored.Outlined.TrendingDown,
                    "Solo cuando baje",
                    "Ignorar las subidas",
                    settings.onlyDrops,
                    vm::setOnlyDrops,
                    enabled = settings.alertsEnabled,
                )
                GroupDivider()
                RowItem(null, "Cambio mínimo", "Por galón, para no avisarte por céntimos", enabled = settings.alertsEnabled)
                FlowRow(
                    Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    THRESHOLDS.forEach { t ->
                        FilterChip(
                            selected = abs(settings.alertThreshold - t) < 1e-6,
                            onClick = { vm.setAlertThreshold(t) },
                            enabled = settings.alertsEnabled,
                            label = { Text("S/ ${amount(t)}") },
                        )
                    }
                }
            }

            SectionLabel("Datos")
            SettingsGroup {
                RowItem(
                    Icons.Outlined.Storage,
                    "Osinergmin – SCOP",
                    "Últimos precios declarados por cada grifo. Algunos no los actualizan seguido: " +
                        "por eso mostramos la antigüedad. Verifica el precio en el surtidor.",
                )
                if (settings.lastDataDate.isNotBlank() || settings.lastSyncAt > 0) {
                    GroupDivider()
                    RowItem(
                        Icons.Outlined.Sync,
                        "Datos al ${Dates.pretty(settings.lastDataDate)}",
                        settings.lastSyncAt.takeIf { it > 0 }?.let {
                            "Sincronizado: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
                        },
                    )
                }
                GroupDivider()
                LinkItem(Icons.Outlined.Info, "Ubicación de los grifos", "Mapa de Facilito; si falta, se aproxima por distrito") {
                    uri.openUri(Legal.SOURCE_URL)
                }
            }

            SectionLabel("Acerca de")
            SettingsGroup {
                LinkItem(Icons.Outlined.Policy, "Política de privacidad", null) { uri.openUri(Legal.PRIVACY_URL) }
            }
            Text(
                Legal.DISCLAIMER + "\n\nVersión ${BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun RowItem(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        modifier = modifier,
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)) }
        } else {
            null
        },
        leadingContent = {
            // Siempre se reserva el espacio del ícono para que los textos queden alineados.
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    // Toda la fila es el objetivo táctil (no solo el switch).
    RowItem(
        icon,
        title,
        subtitle,
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        enabled = enabled,
    ) {
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun LinkItem(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    RowItem(icon, title, subtitle, modifier = Modifier.clickable(onClick = onClick)) {
        Icon(
            Icons.AutoMirrored.Outlined.OpenInNew,
            contentDescription = "Abrir en el navegador",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
