package com.example.digitaladdictionmonitor.ui

import android.app.Application
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitaladdictionmonitor.data.SyncRepository
import com.example.digitaladdictionmonitor.model.DashboardResponse
import com.example.digitaladdictionmonitor.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface DashboardUiState {
    data object PermissionNeeded : DashboardUiState
    data object Loading : DashboardUiState
    data object NoDataYet : DashboardUiState
    data class Loaded(val dashboard: DashboardResponse, val statusMessage: String? = null) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val syncRepository = SyncRepository(application)

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var isSyncing = false

    fun onResume() {
        if (!hasUsagePermission()) {
            _uiState.value = DashboardUiState.PermissionNeeded
        } else if (_uiState.value == DashboardUiState.PermissionNeeded || _uiState.value is DashboardUiState.Loading) {
            loadDashboard()
        }
    }

    fun openUsageAccessSettings() {
        getApplication<Application>().startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun syncAndRefresh() {
        if (isSyncing) return
        isSyncing = true
        _uiState.value = DashboardUiState.Loading
        viewModelScope.launch {
            val syncResult = syncRepository.syncNow()
            val statusMessage = syncResult.fold(
                onSuccess = { response ->
                    when {
                        response == null -> "No new usage data since your last sync."
                        else -> "Synced ${response.acceptedCount} usage row(s)."
                    }
                },
                onFailure = { "Sync failed: ${it.message ?: it::class.simpleName}" }
            )
            loadDashboard(statusMessage)
            isSyncing = false
        }
    }

    private fun loadDashboard(statusMessage: String? = null) {
        viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading
            try {
                val userId = syncRepository.currentUserId()
                val dashboard = RetrofitClient.api.getDashboard(userId)
                _uiState.value = DashboardUiState.Loaded(dashboard, statusMessage)
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    _uiState.value = DashboardUiState.NoDataYet
                } else {
                    _uiState.value = DashboardUiState.Error("Backend error (${e.code()}): ${e.message()}")
                }
            } catch (e: Exception) {
                _uiState.value = DashboardUiState.Error("Could not reach the backend: ${e.message ?: e::class.simpleName}")
            }
        }
    }

    private fun hasUsagePermission(): Boolean {
        val context = getApplication<Application>()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
