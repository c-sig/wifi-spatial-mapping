package com.statproj.wifispatial.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.statproj.wifispatial.WifiSpatialApp
import com.statproj.wifispatial.debug.SimulationEngine
import com.statproj.wifispatial.ui.theme.ChartPurple
import com.statproj.wifispatial.ui.theme.Error
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.PrimaryDark
import com.statproj.wifispatial.ui.theme.PrimaryLight
import com.statproj.wifispatial.ui.theme.Success
import com.statproj.wifispatial.ui.theme.SuccessLight
import com.statproj.wifispatial.ui.theme.SurfaceBlack
import com.statproj.wifispatial.ui.theme.SurfaceContainer
import com.statproj.wifispatial.ui.theme.SurfaceContainerHigh
import com.statproj.wifispatial.ui.theme.Tertiary
import com.statproj.wifispatial.ui.theme.TertiaryLight
import com.statproj.wifispatial.ui.theme.Warning
import com.statproj.wifispatial.ui.theme.WarningLight
import com.statproj.wifispatial.data.FloorPlanCorner
import com.statproj.wifispatial.network.SpeedTestServer
import com.statproj.wifispatial.viewmodel.ConfigViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Configuration screen with building/floor selection, sample progress tracking,
 * and corner definition for floor plan mapping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onNavigateToCollect: () -> Unit = {},
    viewModel: ConfigViewModel = viewModel()
) {
    val selectedBuilding by viewModel.selectedBuilding.collectAsState()
    val selectedFloor by viewModel.selectedFloor.collectAsState()
    val currentCount by viewModel.currentCount.collectAsState()
    val maxReached by viewModel.maxReached.collectAsState()

    val progress by animateFloatAsState(
        targetValue = currentCount.toFloat() / ConfigViewModel.PRESCRIBED_SAMPLE_SIZE,
        animationSpec = tween(600),
        label = "progress"
    )

    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.navigateToCollect.collectLatest {
            onNavigateToCollect()
        }
    }

    val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    var permissionsGranted by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
        if (permissionsGranted) {
            viewModel.proceedToCollection()
        }
    }

    // We evaluate permissions directly in the onClick handlers to prevent asynchronous OS delays

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ──────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Wi-Fi Spatial",
            style = MaterialTheme.typography.headlineLarge,
            color = Primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Data Collection Setup",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        // ── Independent Variable Selection ──────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Independent Variables",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryLight,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Building dropdown
                DropdownSelector(
                    label = "Building (Factor B)",
                    options = ConfigViewModel.BUILDINGS,
                    selectedOption = selectedBuilding,
                    onOptionSelected = { viewModel.selectBuilding(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Floor dropdown
                DropdownSelector(
                    label = "Floor (Factor A)",
                    options = ConfigViewModel.FLOORS,
                    selectedOption = selectedFloor,
                    onOptionSelected = { viewModel.selectFloor(it) }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = "Speed Test Server",
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryLight,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val selectedServer by viewModel.selectedServer.collectAsState()
                val serverOptions = SpeedTestServer.entries.map { it.displayName }
                DropdownSelector(
                    label = "Server Endpoint",
                    options = serverOptions,
                    selectedOption = selectedServer.displayName,
                    onOptionSelected = { displayName ->
                        val server = SpeedTestServer.entries.find { it.displayName == displayName }
                        if (server != null) viewModel.setSpeedTestServer(server)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Action Buttons ──────────────────────────────────────────
        Button(
            onClick = {
                val hasPerms = requiredPermissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (hasPerms) {
                    viewModel.proceedToCollection()
                } else {
                    permissionLauncher.launch(requiredPermissions)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Grid Setup", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Progress Card ───────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sampling Progress",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryLight,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Circular progress indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    // Background ring
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(6.dp, SurfaceContainerHigh, CircleShape)
                    )
                    // Text overlay
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$currentCount",
                            style = MaterialTheme.typography.displaySmall,
                            color = if (maxReached) Success else Primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "/ ${ConfigViewModel.PRESCRIBED_SAMPLE_SIZE}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Linear progress bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (maxReached) Success else Primary,
                    trackColor = SurfaceContainerHigh,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Point $currentCount / ${ConfigViewModel.PRESCRIBED_SAMPLE_SIZE}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Status messages
                AnimatedVisibility(
                    visible = maxReached,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(
                                Success.copy(alpha = 0.1f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Success,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All samples collected for this group!",
                            color = SuccessLight,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Group Info Card ─────────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Current Group",
                    style = MaterialTheme.typography.titleMedium,
                    color = PrimaryLight,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    InfoChip(label = "Building", value = selectedBuilding)
                    InfoChip(label = "Floor", value = selectedFloor)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Prescribed: ${ConfigViewModel.PRESCRIBED_SAMPLE_SIZE} points per group",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))



        // ── Debug Simulation Panel ───────────────────────────────────
        DebugSimulationPanel(
            selectedBuilding = selectedBuilding,
            selectedFloor = selectedFloor,
            onDataChanged = {
                viewModel.refreshCount()
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}


// ═══════════════════════════════════════════════════════════════════════
//  Shared Components
// ═══════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownSelector(
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

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = Primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Collapsible debug panel with simulation controls.
 * Allows populating the database with fake data for testing
 * without physical hardware.
 */
@Composable
private fun DebugSimulationPanel(
    selectedBuilding: String,
    selectedFloor: String,
    onDataChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Tertiary.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header (clickable to expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.BugReport,
                        contentDescription = null,
                        tint = TertiaryLight,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Debug Simulation",
                        style = MaterialTheme.typography.titleSmall,
                        color = TertiaryLight,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = TertiaryLight
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Tertiary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Generate fake data for testing without real sensors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Button 1: Fill selected group
                    Button(
                        onClick = {
                            isRunning = true
                            scope.launch {
                                SimulationEngine.populateGroup(
                                    context, selectedBuilding, selectedFloor, 30
                                )
                                onDataChanged()
                                isRunning = false
                            }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Tertiary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Science, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fill \"$selectedBuilding · $selectedFloor\" (30 pts)",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button 2: Fill ALL groups (data + corners)
                    Button(
                        onClick = {
                            isRunning = true
                            scope.launch {
                                SimulationEngine.populateAll(context)
                                SimulationEngine.populateAllCorners(context)
                                onDataChanged()
                                isRunning = false
                            }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ChartPurple),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Science, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fill ALL Groups + Corners (600 pts)", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button 3: Known statistical effects
                    OutlinedButton(
                        onClick = {
                            isRunning = true
                            scope.launch {
                                SimulationEngine.populateWithKnownEffects(context)
                                SimulationEngine.populateAllCorners(context)
                                onDataChanged()
                                isRunning = false
                            }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🧪 Known Effects (validates ANOVA)", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button 4: Proximity test data
                    OutlinedButton(
                        onClick = {
                            isRunning = true
                            scope.launch {
                                SimulationEngine.populateProximityTestData(context)
                                onDataChanged()
                                isRunning = false
                            }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("📍 Proximity Edge Cases (3m rule)", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button 5: Generate corners only
                    OutlinedButton(
                        onClick = {
                            isRunning = true
                            scope.launch {
                                SimulationEngine.populateAllCorners(context)
                                onDataChanged()
                                isRunning = false
                            }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🗺️ Generate Corners (all groups)", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Error.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Button 6: Clear all data + corners
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val db = (context.applicationContext as WifiSpatialApp).database
                                db.wifiMeasurementDao().deleteAll()
                                db.floorPlanCornerDao().deleteAll()
                                onDataChanged()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clear All Data + Corners", fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
