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

data class ChangePasswordUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isGoogleUser: Boolean = false
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    init {
        checkUserProvider()
    }

    private fun checkUserProvider() {
        val currentUser = authRepository.getCurrentUser()
        val isGoogleUser = currentUser?.provider == "google.com"

        _uiState.update { it.copy(isGoogleUser = isGoogleUser) }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        // ✅ BLOQUEIA se for usuário Google
        if (_uiState.value.isGoogleUser) {
            _uiState.update {
                it.copy(
                    errorMessage = "Esta operação não está disponível para contas Google. Gerencie sua senha nas configurações da sua conta Google."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // Validação: senha nova não pode ser igual à atual
            if (currentPassword == newPassword) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "A nova senha deve ser diferente da atual."
                    )
                }
                return@launch
            }

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

            // 2) Atualiza senha
            val updateResult = authRepository.updateUserPassword(newPassword)
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
                        error.message?.contains("weak-password") == true ->
                            "A senha é muito fraca. Use pelo menos 6 caracteres."

                        error.message?.contains("requires-recent-login") == true ->
                            "Por segurança, faça login novamente antes de alterar a senha."

                        else -> error.message ?: "Erro ao alterar senha. Tente novamente."
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