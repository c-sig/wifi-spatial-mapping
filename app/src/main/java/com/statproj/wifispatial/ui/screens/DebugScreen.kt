package com.statproj.wifispatial.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.statproj.wifispatial.network.SpeedTestEngine
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.statproj.wifispatial.debug.DebugLogger
import com.statproj.wifispatial.ui.theme.Primary
import com.statproj.wifispatial.ui.theme.SurfaceBlack

@Composable
fun DebugScreen() {
    val logs by DebugLogger.logs.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var isStressTesting by remember { mutableStateOf(false) }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    val runStressTest: (Int) -> Unit = { count ->
        isStressTesting = true
        scope.launch {
            DebugLogger.log("=== STARTING ${count}x STRESS TEST ===")
            DebugLogger.log("Each: Download + Upload + 3-5s walk")
            var successCount = 0
            var failureCount = 0
            for (i in 1..count) {
                DebugLogger.log("── Point $i / $count ──")
                val dl = SpeedTestEngine.runDownloadTest()
                DebugLogger.log("  DL: ${dl?.let { "%.1f Mbps".format(it) } ?: "FAILED"}")
                val ul = SpeedTestEngine.runUploadTest()
                DebugLogger.log("  UL: ${ul?.let { "%.1f Mbps".format(it) } ?: "FAILED"}")
                
                if (dl != null && ul != null) {
                    successCount++
                } else {
                    failureCount++
                }
                
                val rate = if (i > 0) (successCount.toDouble() / i * 100).toInt() else 100
                DebugLogger.log("  [Stats] Success: $successCount | Fail: $failureCount | Rate: $rate%")
                
                // Simulate walking a few steps to the next point
                val walkMs = (3000L + (Math.random() * 2000).toLong())
                DebugLogger.log("  Walking ${walkMs/1000.0}s...")
                delay(walkMs)
            }
            DebugLogger.log("=== STRESS TEST COMPLETE ===")
            DebugLogger.log("Total: $count | Successes: $successCount | Failures: $failureCount")
            isStressTesting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .padding(16.dp)
    ) {
        Text(
            text = "Terminal Debug",
            style = MaterialTheme.typography.headlineMedium,
            color = Primary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { runStressTest(35) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = !isStressTesting,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isStressTesting) "Running..." else "STRESS (35x)", color = Color.White, fontSize = 11.sp)
            }
            
            Button(
                onClick = { runStressTest(600) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                enabled = !isStressTesting,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isStressTesting) "Running..." else "STRESS (600x)", color = Color.White, fontSize = 11.sp)
            }
            
            Button(
                onClick = { DebugLogger.clear() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear", color = Color.White, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color(0xFF0F0F0F))
                .padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { logMsg ->
                    Text(
                        text = logMsg,
                        color = Color(0xFF00FF00), // Hacker green
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
