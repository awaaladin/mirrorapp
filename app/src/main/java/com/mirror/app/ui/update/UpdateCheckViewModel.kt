package com.mirror.app.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirror.app.BuildConfig
import com.mirror.app.data.network.AppVersionDto
import com.mirror.app.data.network.MirrorBackendApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class AvailableUpdate(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val isMandatory: Boolean
)

/** Checks mirrorcentral's /app/version once per app launch and surfaces a non-blocking (or, for
 *  a mandatory release, blocking) "update available" prompt - there's no Play Store auto-update
 *  to rely on, since the app isn't distributed through it. */
class UpdateCheckViewModel(
    private val localApi: MirrorBackendApi,
    private val deployedApi: MirrorBackendApi
) : ViewModel() {

    private val _availableUpdate = MutableStateFlow<AvailableUpdate?>(null)
    val availableUpdate: StateFlow<AvailableUpdate?> = _availableUpdate.asStateFlow()

    private var dismissed = false

    fun checkForUpdate() {
        if (dismissed) return
        viewModelScope.launch {
            val dto = fetchLatestVersion() ?: return@launch
            if (dto.versionCode > BuildConfig.VERSION_CODE) {
                _availableUpdate.value = AvailableUpdate(
                    versionName = dto.versionName,
                    downloadUrl = dto.downloadUrl,
                    releaseNotes = dto.releaseNotes,
                    isMandatory = dto.isMandatory
                )
            }
        }
    }

    /** No-op for a mandatory update - it reopens on the next [checkForUpdate] call (e.g. next
     *  app launch) since the user can't actually use the app on a build the backend has marked
     *  as requiring an update. */
    fun dismiss() {
        val current = _availableUpdate.value ?: return
        if (current.isMandatory) return
        dismissed = true
        _availableUpdate.value = null
    }

    private suspend fun fetchLatestVersion(): AppVersionDto? {
        val response = try {
            localApi.getLatestAppVersion()
        } catch (e: IOException) {
            try {
                deployedApi.getLatestAppVersion()
            } catch (e2: IOException) {
                return null
            }
        }
        return if (response.isSuccessful) response.body() else null
    }
}
