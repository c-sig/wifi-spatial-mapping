package com.statproj.wifispatial.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.SignalWifiBad
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.statproj.wifispatial.ui.components.HeatmapCanvas
import com.statproj.wifispatial.ui.components.HeatmapVariable
import com.statproj.wifispatial.ui.theme.OnSurface
import com.statproj.wifispatial.ui.theme.OnSurfaceVariant
import com.statproj.wifispatial.ui.theme.Outline
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.PrimaryDark
import com.statproj.wifispatial.ui.theme.PrimaryLight
import com.statproj.wifispatial.ui.theme.SurfaceBlack
import com.statproj.wifispatial.ui.theme.SurfaceContainer
import com.statproj.wifispatial.ui.theme.SurfaceContainerHigh
import com.statproj.wifispatial.ui.theme.Warning
import com.statproj.wifispatial.viewmodel.ConfigViewModel
import com.statproj.wifispatial.viewmodel.MapViewModel

/**
 * Map / Spatial Heatmap screen.
 *
 * Displays a floor-plan polygon overlaid with color-coded Wi-Fi
 * measurement points. Supports building/floor selection, variable
 * toggle (dBm / DL / UL), and a gradient overlay switch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val selectedBuilding by viewModel.selectedBuilding.collectAsState()
    val selectedFloor by viewModel.selectedFloor.collectAsState()
    val corners by viewModel.corners.collectAsState()
    val measurements by viewModel.measurements.collectAsState()

    var selectedVariable by remember { mutableStateOf(HeatmapVariable.DBM) }
    var showGradient by remember { mutableStateOf(true) }

    // Reload data whenever the screen is composed
    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .padding(20.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Spatial Map",
            style = MaterialTheme.typography.headlineLarge,
            color = Primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Wi-Fi Signal Heatmap",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Selectors Card ──────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Building dropdown
                MapDropdownSelector(
                    label = "Building",
                    options = ConfigViewModel.BUILDINGS,
                    selectedOption = selectedBuilding,
                    onOptionSelected = { viewModel.selectBuilding(it) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Floor dropdown
                MapDropdownSelector(
                    label = "Floor",
                    options = ConfigViewModel.FLOORS,
                    selectedOption = selectedFloor,
                    onOptionSelected = { viewModel.selectFloor(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Controls Row ────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Segmented toggle buttons
            SegmentedToggle(
                options = HeatmapVariable.entries,
                selected = selectedVariable,
                onSelected = { selectedVariable = it },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Gradient toggle
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Gradient",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    fontSize = 10.sp
                )
                Switch(
                    checked = showGradient,
                    onCheckedChange = { showGradient = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Primary,
                        checkedTrackColor = PrimaryDark.copy(alpha = 0.5f),
                        uncheckedThumbColor = OnSurfaceVariant,
                        uncheckedTrackColor = SurfaceContainerHigh
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Content area ────────────────────────────────────────────
        when {
            // No corners defined
            corners.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.GridOff,
                    title = "No Floor Plan",
                    message = "Define corners on Config tab first",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
            // No measurements
            measurements.isEmpty() -> {
                EmptyState(
                    icon = Icons.Filled.SignalWifiBad,
                    title = "No Data",
                    message = "No measurements collected for this group",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
            // Heatmap
            else -> {
                HeatmapCanvas(
                    corners = corners,
                    measurements = measurements,
                    selectedVariable = selectedVariable,
                    showGradient = showGradient,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

// ── Dropdown (matches ConfigScreen style) ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapDropdownSelector(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = Primary,
                cursorColor = Primary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = SurfaceContainerHigh
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

// ── Segmented Toggle Buttons ────────────────────────────────────────

@Composable
private fun SegmentedToggle(
    options: List<HeatmapVariable>,
    selected: HeatmapVariable,
    onSelected: (HeatmapVariable) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Outline, RoundedCornerShape(10.dp))
            .background(SurfaceContainer)
    ) {
        options.forEach { variable ->
            val isSelected = variable == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) Primary.copy(alpha = 0.2f) else SurfaceContainer
                    )
                    .clickable { onSelected(variable) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (variable) {
                        HeatmapVariable.DBM -> "dBm"
                        HeatmapVariable.DOWNLOAD -> "DL"
                        HeatmapVariable.UPLOAD -> "UL"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) PrimaryLight else OnSurfaceVariant
                )
            }
        }
    }
}

// ── Empty State Placeholder ─────────────────────────────────────────

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = OnSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
