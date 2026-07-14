package com.statproj.wifispatial.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.util.Log
import com.statproj.wifispatial.WifiSpatialApp
import com.statproj.wifispatial.data.FloorPlanCorner
import com.statproj.wifispatial.data.WifiMeasurement
import com.statproj.wifispatial.viewmodel.ConfigViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

object DataExportEngine {
    private const val TAG = "DataExportEngine"

    suspend fun createExportZip(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val db = (context.applicationContext as WifiSpatialApp).database
            val measurements = db.wifiMeasurementDao().getAll()
            val corners = db.floorPlanCornerDao().getAll()

            val cacheDir = File(context.cacheDir, "exports")
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val zipFile = File(cacheDir, "WifiSpatial_Export_${System.currentTimeMillis()}.zip")
            val zos = ZipOutputStream(FileOutputStream(zipFile))

            // 1. RawData.json
            val rawDataBytes = generateRawDataJson(measurements, corners)
            addEntryToZip(zos, "RawData.json", rawDataBytes)

            // 2. Regression CSVs
            addEntryToZip(zos, "Regression_DL.csv", generateRegressionCsv(measurements, "Download Speed (Mbps)") { it.dl_mbps })
            addEntryToZip(zos, "Regression_UL.csv", generateRegressionCsv(measurements, "Upload Speed (Mbps)") { it.ul_mbps })

            // 3. ANOVA CSVs
            addEntryToZip(zos, "ANOVA_dBm.csv", generateAnovaCsv(measurements, "dBm") { it.wifi_dbm.toDouble() })
            addEntryToZip(zos, "ANOVA_DL.csv", generateAnovaCsv(measurements, "DL Mbps") { it.dl_mbps })
            addEntryToZip(zos, "ANOVA_UL.csv", generateAnovaCsv(measurements, "UL Mbps") { it.ul_mbps })

            // 4. Heatmap Images
            val bitmapBytesMap = generateHeatmapImages(measurements, corners)
            for ((filename, bytes) in bitmapBytesMap) {
                addEntryToZip(zos, "heatmaps/$filename", bytes)
            }

            zos.close()
            Log.i(TAG, "Export ZIP created at \${zipFile.absolutePath}")
            zipFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create export zip", e)
            null
        }
    }

    private fun addEntryToZip(zos: ZipOutputStream, entryName: String, data: ByteArray) {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(data)
        zos.closeEntry()
    }

    private fun generateRawDataJson(measurements: List<WifiMeasurement>, corners: List<FloorPlanCorner>): ByteArray {
        val root = JSONObject()
        val mArray = JSONArray()
        measurements.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("timestamp", m.timestamp)
            obj.put("building", m.building_pos)
            obj.put("floor", m.floor_lvl)
            obj.put("dbm", m.wifi_dbm)
            obj.put("dl_mbps", m.dl_mbps)
            obj.put("ul_mbps", m.ul_mbps)
            obj.put("x", m.x_coord)
            obj.put("y", m.y_coord)
            mArray.put(obj)
        }
        val cArray = JSONArray()
        corners.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("building", c.building_pos)
            obj.put("floor", c.floor_lvl)
            obj.put("x", c.x_coord)
            obj.put("y", c.y_coord)
            obj.put("order", c.corner_order)
            cArray.put(obj)
        }
        root.put("measurements", mArray)
        root.put("corners", cArray)
        return root.toString(2).toByteArray()
    }

    private fun generateRegressionCsv(measurements: List<WifiMeasurement>, depLabel: String, selector: (WifiMeasurement) -> Double?): ByteArray {
        val sb = StringBuilder()
        sb.append("dBm (X),$depLabel (Y)\n")
        
        val validM = measurements.filter { selector(it) != null }
        val rows = validM.size
        for (m in validM) {
            sb.append("${m.wifi_dbm},${selector(m)}\n")
        }
        
        sb.append("\n\n")
        sb.append("STATISTIC,VALUE\n")
        if (rows > 2) {
            sb.append("SLOPE,\"=SLOPE(B2:B${rows+1}, A2:A${rows+1})\"\n")
            sb.append("INTERCEPT,\"=INTERCEPT(B2:B${rows+1}, A2:A${rows+1})\"\n")
            sb.append("R-SQUARED,\"=RSQ(B2:B${rows+1}, A2:A${rows+1})\"\n")
            sb.append("F-STATISTIC,\"=((RSQ(B2:B${rows+1}, A2:A${rows+1}))/1) / ((1-RSQ(B2:B${rows+1}, A2:A${rows+1}))/(${rows}-2))\"\n")
            sb.append("P-VALUE,\"=F.DIST.RT(((RSQ(B2:B${rows+1}, A2:A${rows+1}))/1) / ((1-RSQ(B2:B${rows+1}, A2:A${rows+1}))/(${rows}-2)), 1, ${rows}-2)\"\n")
        }
        
        return sb.toString().toByteArray()
    }

    private fun generateAnovaCsv(measurements: List<WifiMeasurement>, metricLabel: String, selector: (WifiMeasurement) -> Double?): ByteArray {
        val sb = StringBuilder()
        sb.append("TWO-WAY ANOVA VERIFICATION ($metricLabel)\n")
        sb.append("Data is grouped by Building (Rows) and Floor (Columns)\n\n")
        
        val floors = ConfigViewModel.FLOORS
        val buildings = ConfigViewModel.BUILDINGS
        
        // Header
        sb.append("Building")
        for (f in floors) sb.append(",$f")
        sb.append("\n")
        
        // Grid Data
        var currentRow = 5
        // Calculate max n to assume balanced design
        val nList = mutableListOf<Int>()
        for (b in buildings) {
            for (f in floors) {
                nList.add(measurements.count { it.building_pos == b && it.floor_lvl == f && selector(it) != null })
            }
        }
        val n = nList.maxOrNull() ?: 30
        
        for (b in buildings) {
            for (i in 0 until n) {
                sb.append(if (i == 0) b else "")
                for (f in floors) {
                    val mList = measurements.filter { it.building_pos == b && it.floor_lvl == f }.mapNotNull(selector)
                    if (i < mList.size) {
                        sb.append(",${mList[i]}")
                    } else {
                        sb.append(",")
                    }
                }
                sb.append("\n")
                currentRow++
            }
        }
        
        // HELPER TABLES
        val F = floors.size
        val B = buildings.size
        val gridEnd = currentRow - 1
        
        // Helper: Cell Means
        sb.append("\nCELL MEANS (Helper Table)\n")
        sb.append("Building")
        for (f in floors) sb.append(",$f")
        sb.append(",BUILDING MEAN\n")
        
        val cellMeansStart = gridEnd + 4
        var helperRow = cellMeansStart
        
        for (bIndex in 0 until B) {
            val bName = buildings[bIndex]
            sb.append(bName)
            val dataStart = 5 + (bIndex * n)
            val dataEnd = dataStart + n - 1
            for (fIndex in 0 until F) {
                val colLetter = ('B' + fIndex).toChar()
                sb.append(",\"=AVERAGE(${colLetter}${dataStart}:${colLetter}${dataEnd})\"")
            }
            // Row mean (Building Marginal Mean)
            val lastCol = ('B' + F - 1).toChar()
            sb.append(",\"=AVERAGE(B${helperRow}:${lastCol}${helperRow})\"\n")
            helperRow++
        }
        
        sb.append("FLOOR MEAN")
        for (fIndex in 0 until F) {
            val colLetter = ('B' + fIndex).toChar()
            sb.append(",\"=AVERAGE(${colLetter}${cellMeansStart}:${colLetter}${helperRow - 1})\"")
        }
        val lastCol2 = ('B' + F - 1).toChar()
        sb.append(",\"=AVERAGE(B${cellMeansStart}:${lastCol2}${helperRow - 1})\"") // Grand Mean
        sb.append("\n")
        
        // ANOVA TABLE
        sb.append("\nANOVA TABLE (Native Excel Formulas)\n")
        sb.append("Source,SS,df,MS,F,P-value\n")
        
        val anovaStartRow = helperRow + 4
        
        val buildingMeansRange = "${('B' + F).toChar()}${cellMeansStart}:${('B' + F).toChar()}${helperRow - 1}"
        val floorMeansRange = "B${helperRow}:${lastCol2}${helperRow}"
        val cellMeansRange = "B${cellMeansStart}:${lastCol2}${helperRow - 1}"
        val rawGridRange = "B5:${lastCol2}${gridEnd}"
        
        val dfBuilding = B - 1
        val dfFloor = F - 1
        val dfInteraction = dfBuilding * dfFloor
        val dfTotal = B * F * n - 1
        val dfWithin = dfTotal - dfBuilding - dfFloor - dfInteraction
        
        // Building row
        val ssBuilding = "\"=DEVSQ(${buildingMeansRange}) * ${n * F}\""
        sb.append("Building,${ssBuilding},${dfBuilding},\"=B${anovaStartRow}/C${anovaStartRow}\",\"=D${anovaStartRow}/D${anovaStartRow+3}\",\"=F.DIST.RT(E${anovaStartRow}, C${anovaStartRow}, C${anovaStartRow+3})\"\n")
        
        // Floor row
        val ssFloor = "\"=DEVSQ(${floorMeansRange}) * ${n * B}\""
        sb.append("Floor,${ssFloor},${dfFloor},\"=B${anovaStartRow+1}/C${anovaStartRow+1}\",\"=D${anovaStartRow+1}/D${anovaStartRow+3}\",\"=F.DIST.RT(E${anovaStartRow+1}, C${anovaStartRow+1}, C${anovaStartRow+3})\"\n")
        
        // Interaction row
        val rawSsCells = "DEVSQ(${cellMeansRange}) * $n"
        val ssInteraction = "\"=($rawSsCells) - B${anovaStartRow} - B${anovaStartRow+1}\""
        sb.append("Interaction,${ssInteraction},${dfInteraction},\"=B${anovaStartRow+2}/C${anovaStartRow+2}\",\"=D${anovaStartRow+2}/D${anovaStartRow+3}\",\"=F.DIST.RT(E${anovaStartRow+2}, C${anovaStartRow+2}, C${anovaStartRow+3})\"\n")
        
        // Within row
        val rawSsTotal = "DEVSQ(${rawGridRange})"
        val ssWithin = "\"=($rawSsTotal) - ($rawSsCells)\""
        sb.append("Within,${ssWithin},${dfWithin},\"=B${anovaStartRow+3}/C${anovaStartRow+3}\",,\n")
        
        // Total row
        val ssTotal = "\"=${rawSsTotal}\""
        sb.append("Total,${ssTotal},${dfTotal},,,\n")
        
        return sb.toString().toByteArray()
    }

    private val HEATMAP_COLORS = listOf(
        0.00f to android.graphics.Color.parseColor("#000040"), // Dark Navy
        0.25f to android.graphics.Color.parseColor("#4B0082"), // Purple
        0.50f to android.graphics.Color.parseColor("#B22222"), // Magenta
        0.75f to android.graphics.Color.parseColor("#FF8C00"), // Orange
        1.00f to android.graphics.Color.parseColor("#FFD700")  // Yellow
    )

    private fun getGradientColor(fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        for (i in 0 until HEATMAP_COLORS.size - 1) {
            val (startF, startC) = HEATMAP_COLORS[i]
            val (endF, endC) = HEATMAP_COLORS[i + 1]
            if (t in startF..endF) {
                val local = if (endF == startF) 0f else (t - startF) / (endF - startF)
                
                val a = android.graphics.Color.alpha(startC) + (android.graphics.Color.alpha(endC) - android.graphics.Color.alpha(startC)) * local
                val r = android.graphics.Color.red(startC) + (android.graphics.Color.red(endC) - android.graphics.Color.red(startC)) * local
                val g = android.graphics.Color.green(startC) + (android.graphics.Color.green(endC) - android.graphics.Color.green(startC)) * local
                val b = android.graphics.Color.blue(startC) + (android.graphics.Color.blue(endC) - android.graphics.Color.blue(startC)) * local
                
                return android.graphics.Color.argb(a.toInt(), r.toInt(), g.toInt(), b.toInt())
            }
        }
        return HEATMAP_COLORS.last().second
    }

    private fun generateHeatmapImages(measurements: List<WifiMeasurement>, corners: List<FloorPlanCorner>): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        val canvasW = 1000f
        val canvasH = 1000f

        val minValue = -80.0
        val maxValue = -30.0

        for (b in ConfigViewModel.BUILDINGS) {
            for (f in ConfigViewModel.FLOORS) {
                val bCorners = corners.filter { it.building_pos == b && it.floor_lvl == f }.sortedBy { it.corner_order }
                val bMeasurements = measurements.filter { it.building_pos == b && it.floor_lvl == f }

                if (bCorners.isEmpty() && bMeasurements.isEmpty()) continue

                val bmp = Bitmap.createBitmap(canvasW.toInt(), canvasH.toInt(), Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                
                // Background is transparent


                val allX = bCorners.map { it.x_coord } + bMeasurements.map { it.x_coord }
                val allY = bCorners.map { it.y_coord } + bMeasurements.map { it.y_coord }
                
                if (allX.isNotEmpty() && allY.isNotEmpty()) {
                    val dataMinX = allX.minOrNull() ?: 0.0
                    val dataMaxX = allX.maxOrNull() ?: 0.0
                    val dataMinY = allY.minOrNull() ?: 0.0
                    val dataMaxY = allY.maxOrNull() ?: 0.0

                    val dataW = (dataMaxX - dataMinX).coerceAtLeast(0.001)
                    val dataH = (dataMaxY - dataMinY).coerceAtLeast(0.001)
                    val margin = max(dataW, dataH) * 0.1

                    val worldMinX = dataMinX - margin
                    val worldMaxX = dataMaxX + margin
                    val worldMinY = dataMinY - margin
                    val worldMaxY = dataMaxY + margin
                    val worldW = worldMaxX - worldMinX
                    val worldH = worldMaxY - worldMinY

                    val scaleX = canvasW / worldW.toFloat()
                    val scaleY = canvasH / worldH.toFloat()
                    val uniformScale = min(scaleX, scaleY)
                    val offsetX = (canvasW - worldW.toFloat() * uniformScale) / 2f
                    val offsetY = (canvasH - worldH.toFloat() * uniformScale) / 2f

                    fun mapX(x: Double): Float = offsetX + ((x - worldMinX) * uniformScale).toFloat()
                    fun mapY(y: Double): Float = offsetY + ((worldMaxY - y) * uniformScale).toFloat()

                    // Draw Polygon
                    if (bCorners.size >= 3) {
                        val path = android.graphics.Path()
                        path.moveTo(mapX(bCorners[0].x_coord), mapY(bCorners[0].y_coord))
                        for (i in 1 until bCorners.size) {
                            path.lineTo(mapX(bCorners[i].x_coord), mapY(bCorners[i].y_coord))
                        }
                        path.close()

                        val polyFill = Paint().apply {
                            color = android.graphics.Color.parseColor("#20FFFFFF")
                            style = Paint.Style.FILL
                        }
                        val polyStroke = Paint().apply {
                            color = android.graphics.Color.parseColor("#60FFFFFF")
                            style = Paint.Style.STROKE
                            strokeWidth = 4f
                        }
                        canvas.drawPath(path, polyFill)
                        canvas.drawPath(path, polyStroke)
                        
                        // Draw corners
                        val cornerPaint = Paint().apply {
                            color = android.graphics.Color.WHITE
                            style = Paint.Style.FILL
                        }
                        for (c in bCorners) {
                            val cx = mapX(c.x_coord)
                            val cy = mapY(c.y_coord)
                            canvas.drawRect(cx - 8f, cy - 8f, cx + 8f, cy + 8f, cornerPaint)
                        }
                    }

                    // Draw Measurements
                    for (m in bMeasurements) {
                        val fraction = ((m.wifi_dbm - minValue) / (maxValue - minValue)).toFloat()
                        val color = getGradientColor(fraction)
                        val pointPaint = Paint().apply {
                            this.color = color
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        canvas.drawCircle(mapX(m.x_coord), mapY(m.y_coord), 16f, pointPaint)
                    }
                }

                val stream = java.io.ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                map["${b}_${f}.png"] = stream.toByteArray()
            }
        }
        return map
    }
}
