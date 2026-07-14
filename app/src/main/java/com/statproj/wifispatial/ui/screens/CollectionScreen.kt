package com.statproj.wifispatial.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import com.statproj.wifispatial.ui.theme.DownloadColor
import com.statproj.wifispatial.ui.theme.Error
import com.statproj.wifispatial.ui.theme.ErrorLight
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.PrimaryLight
import com.statproj.wifispatial.ui.theme.ProximityDangerBg
import com.statproj.wifispatial.ui.theme.ProximityDangerBorder
import com.statproj.wifispatial.ui.theme.ProximitySafeBg
import com.statproj.wifispatial.ui.theme.ProximitySafeBorder
import com.statproj.wifispatial.ui.theme.Success
import com.statproj.wifispatial.ui.theme.SuccessLight
import com.statproj.wifispatial.ui.theme.SurfaceBlack
import com.statproj.wifispatial.ui.theme.SurfaceContainer
import com.statproj.wifispatial.ui.theme.SurfaceContainerHigh
import com.statproj.wifispatial.ui.theme.UploadColor
import com.statproj.wifispatial.ui.theme.Warning
import com.statproj.wifispatial.viewmodel.CollectionPhase
import com.statproj.wifispatial.viewmodel.CollectionViewModel
import com.statproj.wifispatial.viewmodel.ConfigViewModel
import com.statproj.wifispatial.viewmodel.DPadDirection
import com.statproj.wifispatial.data.CoordinatePoint
import com.statproj.wifispatial.network.SpeedTestEngine

@Composable
fun CollectionScreen(
    collectionViewModel: CollectionViewModel = viewModel(),
    configViewModel: ConfigViewModel = viewModel()
) {
    val uiState by collectionViewModel.uiState.collectAsState()
    val selectedBuilding by configViewModel.selectedBuilding.collectAsState()
    val selectedFloor by configViewModel.selectedFloor.collectAsState()
    val cooldownSeconds by SpeedTestEngine.cooldownSeconds.collectAsState()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                collectionViewModel.checkAutoStartHandover(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var permissionsGranted by remember { mutableStateOf(false) }
    val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            collectionViewModel.startCollection(context, selectedBuilding, selectedFloor)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Data Collection",
            style = MaterialTheme.typography.headlineSmall,
            color = Primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "$selectedBuilding · $selectedFloor",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        StatusBanner(phase = uiState.phase, message = uiState.statusMessage)

        if (cooldownSeconds > 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3E2723)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Rate Limit Cooldown: ${cooldownSeconds}s remaining. D-Pad disabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFB300),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.phase != CollectionPhase.IDLE) {
            // ── Grid & D-Pad Card ───────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Live Grid Map",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryLight,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Grid Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .background(SurfaceContainerHigh, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, Primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val gridSize = 40f

                            // Draw grid lines
                            val gridColor = Color.White.copy(alpha = 0.1f)
                            for (x in 0..(size.width / gridSize).toInt()) {
                                drawLine(gridColor, Offset(x * gridSize, 0f), Offset(x * gridSize, size.height))
                            }
                            for (y in 0..(size.height / gridSize).toInt()) {
                                drawLine(gridColor, Offset(0f, y * gridSize), Offset(size.width, y * gridSize))
                            }

                            // Draw logged coordinates relative to currentX, currentY
                            uiState.measurements.forEach { pt ->
                                val dx = (pt.x_coord - uiState.currentX).toFloat()
                                val dy = (pt.y_coord - uiState.currentY).toFloat()
                                val px = cx + dx * gridSize
                                val py = cy - dy * gridSize
                                
                                drawRect(
                                    color = Primary.copy(alpha = 0.3f),
                                    topLeft = Offset(px - gridSize/2, py - gridSize/2),
                                    size = androidx.compose.ui.geometry.Size(gridSize, gridSize)
                                )
                                drawCircle(color = Primary, radius = 4f, center = Offset(px, py))
                            }

                            // Draw Avatar at center
                            drawCircle(color = Success, radius = 12f, center = Offset(cx, cy))
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // D-Pad Navigation
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = { collectionViewModel.step(context, DPadDirection.FORWARD) },
                            enabled = (uiState.phase == CollectionPhase.WALKING || uiState.phase == CollectionPhase.TOO_CLOSE) && cooldownSeconds == 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Forward", tint = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Button(
                                onClick = { collectionViewModel.step(context, DPadDirection.LEFT) },
                                enabled = (uiState.phase == CollectionPhase.WALKING || uiState.phase == CollectionPhase.TOO_CLOSE) && cooldownSeconds == 0,
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Left", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(48.dp))
                            Button(
                                onClick = { collectionViewModel.step(context, DPadDirection.RIGHT) },
                                enabled = (uiState.phase == CollectionPhase.WALKING || uiState.phase == CollectionPhase.TOO_CLOSE) && cooldownSeconds == 0,
                                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape
                            ) {
                                Icon(Icons.Filled.ArrowForward, contentDescription = "Right", tint = Color.White)
                            }
                        }
                        Button(
                            onClick = { collectionViewModel.step(context, DPadDirection.BACKWARD) },
                            enabled = (uiState.phase == CollectionPhase.WALKING || uiState.phase == CollectionPhase.TOO_CLOSE) && cooldownSeconds == 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Backward", tint = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                    text = "Point ${uiState.currentCount} / ${uiState.maxSamples}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { uiState.currentCount.toFloat() / uiState.maxSamples },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(horizontal = 16.dp),
                    color = Primary,
                    trackColor = SurfaceContainerHigh,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Last Test Results Card ──────────────────────────────────
        AnimatedVisibility(
            visible = uiState.lastDbm != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Last Measurement",
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryLight,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricDisplay(
                            label = "Signal",
                            value = "${uiState.lastDbm ?: "--"} dBm",
                            color = Warning
                        )
                        MetricDisplay(
                            label = "Download",
                            value = uiState.lastDlMbps?.let { "${"%.1f".format(it)} Mbps" } ?: "--",
                            color = DownloadColor
                        )
                        MetricDisplay(
                            label = "Upload",
                            value = uiState.lastUlMbps?.let { "${"%.1f".format(it)} Mbps" } ?: "--",
                            color = UploadColor
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Control Buttons ─────────────────────────────────────────
        when (uiState.phase) {
            CollectionPhase.IDLE -> {
                Button(
                    onClick = {
                        val hasPerms = requiredPermissions.all {
                            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                        }
                        if (hasPerms) {
                            collectionViewModel.startCollection(
                                context, selectedBuilding, selectedFloor
                            )
                        } else {
                            permissionLauncher.launch(requiredPermissions)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Collection Setup", fontWeight = FontWeight.SemiBold)
                }
            }

            CollectionPhase.WALKING, CollectionPhase.TOO_CLOSE -> {
                OutlinedButton(
                    onClick = { collectionViewModel.stopCollection() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = com.statproj.wifispatial.ui.theme.Error
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop Collection", fontWeight = FontWeight.SemiBold)
                }
            }

            CollectionPhase.TEST_FAILED -> {
                Button(
                    onClick = { collectionViewModel.resumeWalking() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Success),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.DirectionsWalk, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resume Walking", fontWeight = FontWeight.SemiBold)
                }
            }

            else -> {
                // Testing phases — show spinner
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = Primary
                )
            }
        }

        if (uiState.phase != CollectionPhase.IDLE) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { collectionViewModel.undoLastMeasurement() },
                enabled = uiState.measurements.isNotEmpty() && 
                          uiState.phase != CollectionPhase.TESTING_DOWNLOAD && 
                          uiState.phase != CollectionPhase.TESTING_UPLOAD,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Undo, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Undo Last Point", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun StepperInput(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (value > 0) onValueChange(value - 1) },
                modifier = Modifier.background(SurfaceContainerHigh, CircleShape).size(36.dp)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "-", tint = Primary)
            }
            Text(
                text = "$value",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            IconButton(
                onClick = { onValueChange(value + 1) },
                modifier = Modifier.background(SurfaceContainerHigh, CircleShape).size(36.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "+", tint = Primary)
            }
        }
    }
}

@Composable
private fun StatusBanner(phase: CollectionPhase, message: String) {
    val (bgColor, borderColor, textColor, icon) = when (phase) {
        CollectionPhase.TOO_CLOSE -> listOf(
            ProximityDangerBg, ProximityDangerBorder, ErrorLight, Icons.Filled.Warning
        )
        CollectionPhase.READY_TO_TEST, CollectionPhase.TESTING_DOWNLOAD,
        CollectionPhase.TESTING_UPLOAD -> listOf(
            ProximitySafeBg, ProximitySafeBorder, SuccessLight, Icons.Filled.CloudDownload
        )
        CollectionPhase.TEST_COMPLETE -> listOf(
            ProximitySafeBg, ProximitySafeBorder, SuccessLight, Icons.Filled.CloudUpload
        )
        CollectionPhase.TEST_FAILED -> listOf(
            ProximityDangerBg, ProximityDangerBorder, ErrorLight, Icons.Filled.Error
        )
        else -> listOf(
            SurfaceContainer, SurfaceContainerHigh, PrimaryLight, Icons.Filled.DirectionsWalk
        )
    }

    val pulseAlpha = if (phase == CollectionPhase.TOO_CLOSE ||
        phase == CollectionPhase.TESTING_DOWNLOAD ||
        phase == CollectionPhase.TESTING_UPLOAD
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )
        alpha
    } else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(pulseAlpha)
            .border(
                1.dp,
                borderColor as Color,
                RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = bgColor as Color),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = null,
                tint = textColor as Color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MetricDisplay(
    label: String,
    value: String,
    color: Color = Primary
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}
