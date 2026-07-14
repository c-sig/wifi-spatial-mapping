package com.statproj.wifispatial.export

import android.content.Context
import android.net.Uri
import android.util.Log
import com.statproj.wifispatial.WifiSpatialApp
import com.statproj.wifispatial.data.FloorPlanCorner
import com.statproj.wifispatial.data.WifiMeasurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object DataImportEngine {
    private const val TAG = "DataImportEngine"

    suspend fun importFromJson(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext false
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.use { it.readText() }
            
            val root = JSONObject(jsonString)
            val mArray = root.getJSONArray("measurements")
            val cArray = root.getJSONArray("corners")

            val measurements = mutableListOf<WifiMeasurement>()
            for (i in 0 until mArray.length()) {
                val obj = mArray.getJSONObject(i)
                measurements.add(
                    WifiMeasurement(
                        id = obj.getLong("id"),
                        timestamp = obj.getLong("timestamp"),
                        building_pos = obj.getString("building"),
                        floor_lvl = obj.getString("floor"),
                        wifi_dbm = obj.getInt("dbm"),
                        dl_mbps = if (obj.isNull("dl_mbps")) null else obj.getDouble("dl_mbps"),
                        ul_mbps = if (obj.isNull("ul_mbps")) null else obj.getDouble("ul_mbps"),
                        x_coord = obj.getDouble("x"),
                        y_coord = obj.getDouble("y")
                    )
                )
            }

            val corners = mutableListOf<FloorPlanCorner>()
            for (i in 0 until cArray.length()) {
                val obj = cArray.getJSONObject(i)
                corners.add(
                    FloorPlanCorner(
                        id = obj.getLong("id"),
                        building_pos = obj.getString("building"),
                        floor_lvl = obj.getString("floor"),
                        x_coord = obj.getDouble("x"),
                        y_coord = obj.getDouble("y"),
                        corner_order = obj.getInt("order")
                    )
                )
            }

            val db = (context.applicationContext as WifiSpatialApp).database
            
            // Clear existing and replace
            db.wifiMeasurementDao().deleteAll()
            measurements.forEach { db.wifiMeasurementDao().insert(it) }
            
            db.floorPlanCornerDao().deleteAll()
            db.floorPlanCornerDao().insertAll(corners)

            Log.i(TAG, "Successfully imported \${measurements.size} measurements and \${corners.size} corners.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import JSON data", e)
            false
        }
    }
}
