package com.statproj.wifispatial.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.statproj.wifispatial.WifiSpatialApp
import com.statproj.wifispatial.data.WifiMeasurement
import com.statproj.wifispatial.data.WifiMeasurementDao
import com.statproj.wifispatial.export.CsvExporter
import com.statproj.wifispatial.export.DataExportEngine
import com.statproj.wifispatial.export.DataImportEngine
import com.statproj.wifispatial.stats.StatisticsEngine
import com.statproj.wifispatial.stats.StatsReport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State for the report screen.
 */
data class ReportUiState(
    val isLoading: Boolean = false,
    val measurements: List<WifiMeasurement> = emptyList(),
    val report: StatsReport? = null,
    val errorMessage: String? = null,
    val totalCount: Int = 0
)

/**
 * ViewModel for the Report and Data screens.
 * Handles statistical analysis generation, data retrieval, and CSV export.
 */
class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: WifiMeasurementDao =
        (application as WifiSpatialApp).database.wifiMeasurementDao()

    private val statsEngine = StatisticsEngine()

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    // ── Data Loading ────────────────────────────────────────────────────

    fun loadData() {
        viewModelScope.launch {
            try {
                val measurements = dao.getAll()
                _uiState.value = _uiState.value.copy(
                    measurements = measurements,
                    totalCount = measurements.size
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to load data: ${e.message}"
                )
            }
        }
    }

    // ── Report Generation ───────────────────────────────────────────────

    /**
     * Generate the full statistical report.
     * Runs the statistical engine on all measurements.
     */
    fun generateReport() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            try {
                val measurements = dao.getAll()

                if (measurements.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No data to analyze. Collect measurements first."
                    )
                    return@launch
                }

                val report = withContext(Dispatchers.Default) {
                    statsEngine.generateReport(measurements)
                }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    measurements = measurements,
                    report = report,
                    totalCount = measurements.size
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Analysis failed: ${e.message}"
                )
            }
        }
    }

    // ── CSV Export ───────────────────────────────────────────────────────

    /**
     * Export raw data as CSV via ShareSheet.
     */
    fun exportRawCsv(context: Context) {
        viewModelScope.launch {
            try {
                val measurements = dao.getAll()
                if (measurements.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "No data to export."
                    )
                    return@launch
                }
                val csv = CsvExporter.generateRawCsv(measurements)
                CsvExporter.shareCsv(context, csv, "wifi_spatial_raw_data.csv")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Export failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Export full report (raw data + statistics) as CSV via ShareSheet.
     */
    fun exportFullReportCsv(context: Context) {
        viewModelScope.launch {
            try {
                val measurements = dao.getAll()
                if (measurements.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "No data to export."
                    )
                    return@launch
                }
                val csv = CsvExporter.generateFullReportCsv(measurements, _uiState.value.report)
                CsvExporter.shareCsv(context, csv, "wifi_spatial_full_report.csv")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Export failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Export advanced ZIP report with native CSVs and raw data.
     */
    fun exportAdvancedZip(context: Context) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            try {
                val zipFile = DataExportEngine.createExportZip(context)
                if (zipFile != null) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        zipFile
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/zip"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Save Export ZIP"))
                } else {
                    _uiState.value = _uiState.value.copy(errorMessage = "Failed to create ZIP")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Export ZIP failed: ${e.message}")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    /**
     * Import JSON data backup.
     */
    fun importJsonData(context: Context, uri: Uri) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val success = DataImportEngine.importFromJson(context, uri)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = if (success) "Import successful!" else "Import failed.",
                totalCount = if (success) dao.getAll().size else _uiState.value.totalCount
            )
            if (success) {
                loadData()
            }
        }
    }
}
