package com.statproj.wifispatial.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.statproj.wifispatial.data.WifiMeasurement
import com.statproj.wifispatial.ui.theme.ChartBlue
import com.statproj.wifispatial.ui.theme.ChartGreen
import com.statproj.wifispatial.ui.theme.DownloadColor
import com.statproj.wifispatial.ui.theme.Error
import com.statproj.wifispatial.ui.theme.ErrorLight
import com.statproj.wifispatial.ui.theme.OnSurfaceVariant
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.PrimaryLight
import com.statproj.wifispatial.ui.theme.Secondary
import com.statproj.wifispatial.ui.theme.SurfaceBlack
import com.statproj.wifispatial.ui.theme.SurfaceContainer
import com.statproj.wifispatial.ui.theme.SurfaceContainerHigh
import com.statproj.wifispatial.ui.theme.Tertiary
import com.statproj.wifispatial.ui.theme.UploadColor
import com.statproj.wifispatial.ui.theme.Warning
import com.statproj.wifispatial.viewmodel.ReportViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Data screen showing raw measurements in a scrollable list
 * with CSV export options.
 */
@Composable
fun DataScreen(viewModel: ReportViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importJsonData(context, uri)
        }
    }

    // Refresh data when screen is composed
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Storage,
                contentDescription = null,
                tint = Secondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Raw Data",
                style = MaterialTheme.typography.headlineSmall,
                color = Secondary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "${uiState.totalCount} measurements",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Export Buttons ───────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.exportRawCsv(context) },
                enabled = uiState.totalCount > 0,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export Raw", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { viewModel.exportAdvancedZip(context) },
                enabled = uiState.totalCount > 0,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export ZIP", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { importLauncher.launch("application/json") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Backup (JSON)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // ── Error Message ───────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.errorMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Error.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = Error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = ErrorLight,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Data List ───────────────────────────────────────────────
        if (uiState.measurements.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Storage,
                    contentDescription = null,
                    tint = OnSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No measurements yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "Collect data from the Collect tab",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.measurements) { measurement ->
                    MeasurementCard(measurement)
                }
            }
        }
    }
}

@Composable
private fun MeasurementCard(measurement: WifiMeasurement) {
    val dateFormat = SimpleDateFormat("MM/dd HH:mm:ss", Locale.US)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top row: ID + timestamp + location
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${measurement.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormat.format(Date(measurement.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Location chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniChip(measurement.building_pos, ChartBlue)
                MiniChip(measurement.floor_lvl, ChartGreen)
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = SurfaceContainerHigh)
            Spacer(modifier = Modifier.height(8.dp))

            // Metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniMetric(
                    icon = Icons.Filled.SignalWifi4Bar,
                    value = "${measurement.wifi_dbm} dBm",
                    color = Warning
                )
                MiniMetric(
                    icon = Icons.Filled.CloudDownload,
                    value = measurement.dl_mbps?.let { "${"%.1f".format(it)}" } ?: "N/A",
                    color = DownloadColor
                )
                MiniMetric(
                    icon = Icons.Filled.CloudUpload,
                    value = measurement.ul_mbps?.let { "${"%.1f".format(it)}" } ?: "N/A",
                    color = UploadColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Coordinates
            Text(
                text = "Pos: (${"%.2f".format(measurement.x_coord)}, ${"%.2f".format(measurement.y_coord)})",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MiniChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontSize = 10.sp,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun MiniMetric(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}
