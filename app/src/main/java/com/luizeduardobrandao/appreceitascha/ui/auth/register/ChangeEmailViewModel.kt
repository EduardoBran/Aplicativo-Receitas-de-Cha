package com.luizeduardobrandao.appreceitascha.ui.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangeEmailUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isGoogleUser: Boolean = false
)

@HiltViewModel
class ChangeEmailViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeEmailUiState())
    val uiState: StateFlow<ChangeEmailUiState> = _uiState.asStateFlow()

    init {
        checkUserProvider()
    }

    private fun checkUserProvider() {
        val currentUser = authRepository.getCurrentUser()
        val isGoogleUser = currentUser?.provider == "google.com"

        _uiState.update { it.copy(isGoogleUser = isGoogleUser) }
    }

    fun changeEmail(currentPassword: String, newEmail: String) {
        // Bloqueia se for usuário Google
        if (_uiState.value.isGoogleUser) {
            _uiState.update {
                it.copy(
                    errorMessage = "Esta operação não está disponível para contas Google. Gerencie seu e-mail nas configurações da sua conta Google."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 1) Reautentica
            val reauthResult = authRepository.reauthenticateUser(currentPassword)
            if (reauthResult.isFailure) {
                val errorMsg = when {
                    reauthResult.exceptionOrNull()?.message?.contains("password") == true ->
                        "Senha atual incorreta."

                    reauthResult.exceptionOrNull()?.message?.contains("user-mismatch") == true ->
                        "Esta operação não está disponível para sua conta."

                    else -> "Senha atual incorreta."
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = errorMsg
                    )
                }
                return@launch
            }

            // 2) Atualiza e-mail com verificação
            val updateResult = authRepository.updateUserEmail(newEmail)
            updateResult
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true
                        )
                    }
                }
                .onFailure { error ->
                    val errorMsg = when {
                        error.message?.contains("email-already-in-use") == true ->
                            "Este e-mail já está em uso."

                        error.message?.contains("invalid-email") == true ->
                            "E-mail inválido."

                        error.message?.contains("requires-recent-login") == true ->
                            "Por segurança, faça login novamente antes de alterar o e-mail."

                        else -> error.message ?: "Erro ao alterar e-mail. Tente novamente."
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorMsg
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}