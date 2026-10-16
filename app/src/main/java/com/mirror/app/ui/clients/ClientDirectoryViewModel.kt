package com.mirror.app.ui.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mirror.app.core.result.AppError
import com.mirror.app.core.result.AppResult
import com.mirror.app.data.auth.SessionStore
import com.mirror.app.data.clients.ClientRepository
import com.mirror.app.domain.model.ClientProfile
import com.mirror.app.domain.model.ClientTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClientDirectoryUiState(
    val clients: List<ClientProfile> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ClientDirectoryViewModel(
    private val clientRepository: ClientRepository,
    private val sessionStore: SessionStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClientDirectoryUiState(isLoading = true))
    val uiState: StateFlow<ClientDirectoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        val token = sessionStore.loadAccessToken()
        if (token == null) {
            _uiState.value = ClientDirectoryUiState(errorMessage = "Sign in to view your clients.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            when (val result = clientRepository.listClients(token)) {
                is AppResult.Success -> _uiState.value = ClientDirectoryUiState(clients = result.data)
                is AppResult.Failure -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = result.error.toMessage())
            }
        }
    }

    fun addClient(name: String, notes: String, tag: ClientTag) {
        val token = sessionStore.loadAccessToken() ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            when (val result = clientRepository.createClient(token, name.trim(), notes.trim(), tag)) {
                is AppResult.Success -> _uiState.value = _uiState.value.copy(
                    clients = _uiState.value.clients + result.data,
                    errorMessage = null
                )
                is AppResult.Failure -> _uiState.value = _uiState.value.copy(errorMessage = result.error.toMessage())
            }
        }
    }

    fun deleteClient(clientId: String) {
        val token = sessionStore.loadAccessToken() ?: return
        val previous = _uiState.value.clients
        _uiState.value = _uiState.value.copy(clients = previous.filterNot { it.id == clientId })
        viewModelScope.launch {
            val result = clientRepository.deleteClient(token, clientId)
            if (result is AppResult.Failure) {
                _uiState.value = _uiState.value.copy(clients = previous, errorMessage = result.error.toMessage())
            }
        }
    }

    private fun AppError.toMessage(): String = when (this) {
        AppError.NetworkUnavailable -> "No internet connection. Please try again."
        is AppError.RequestFailed -> message
        else -> "Something went wrong. Please try again."
    }
}
