package com.luizeduardobrandao.appreceitascha.ui.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.ui.auth.AuthErrorCode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsável pela alteração de senha do usuário.
 *
 * Fluxo:
 * 1. Verifica se é usuário Google (bloqueia se for)
 * 2. Valida senha nova != senha atual
 * 3. Reautentica com senha atual
 * 4. Atualiza para nova senha
 *
 * Utiliza [AuthErrorCode] para comunicação type-safe de erros,
 * delegando a tradução de mensagens para a camada de UI.
 */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangePasswordUiState())
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    init {
        checkUserProvider()
    }

    /**
     * Verifica se o usuário logado é de provedor Google.
     * Usuários Google não podem alterar senha via Firebase Auth.
     */
    private fun checkUserProvider() {
        val currentUser = authRepository.getCurrentUser()
        val isGoogleUser = currentUser?.provider == "google.com"

        _uiState.update { it.copy(isGoogleUser = isGoogleUser) }
    }

    /**
     * Inicia o processo de alteração de senha.
     *
     * @param currentPassword Senha atual do usuário (para reautenticação)
     * @param newPassword Nova senha desejada
     */
    fun changePassword(currentPassword: String, newPassword: String) {
        // Bloqueia operação para usuários Google
        if (_uiState.value.isGoogleUser) {
            _uiState.update {
                it.copy(
                    errorCode = AuthErrorCode.GOOGLE_USER_CANNOT_CHANGE_PASSWORD
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorCode = null) }

            // Validação: senha nova não pode ser igual à atual
            if (currentPassword == newPassword) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorCode = AuthErrorCode.CHANGE_PASSWORD_SAME_PASSWORD
                    )
                }
                return@launch
            }

            // Etapa 1: Reautentica o usuário com a senha atual
            val reauthResult = authRepository.reauthenticateUser(currentPassword)

            if (reauthResult.isFailure) {
                val errorCode = mapReauthenticationError(reauthResult.exceptionOrNull())

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorCode = errorCode
                    )
                }
                return@launch
            }

            // Etapa 2: Atualiza para a nova senha
            val updateResult = authRepository.updateUserPassword(newPassword)

            updateResult
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true,
                            errorCode = null
                        )
                    }
                }
                .onFailure { error ->
                    val errorCode = mapPasswordUpdateError(error)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorCode = errorCode
                        )
                    }
                }
        }
    }

    /**
     * Mapeia exceções de reautenticação para códigos de erro tipados.
     */
    private fun mapReauthenticationError(exception: Throwable?): AuthErrorCode {
        return when {
            exception?.message?.contains("password", ignoreCase = true) == true ->
                AuthErrorCode.CHANGE_PASSWORD_INCORRECT_PASSWORD

            exception?.message?.contains("user-mismatch", ignoreCase = true) == true ->
                AuthErrorCode.CHANGE_PASSWORD_USER_MISMATCH

            else -> AuthErrorCode.CHANGE_PASSWORD_INCORRECT_PASSWORD
        }
    }

    /**
     * Mapeia exceções de atualização de senha para códigos de erro tipados.
     */
    private fun mapPasswordUpdateError(error: Throwable): AuthErrorCode {
        return when {
            error.message?.contains("weak-password", ignoreCase = true) == true ->
                AuthErrorCode.CHANGE_PASSWORD_WEAK

            error.message?.contains("requires-recent-login", ignoreCase = true) == true ->
                AuthErrorCode.CHANGE_PASSWORD_REQUIRES_RECENT_LOGIN

            else -> AuthErrorCode.CHANGE_PASSWORD_GENERIC
        }
    }

    /**
     * Limpa o estado de erro atual.
     * Deve ser chamado após exibir a mensagem ao usuário.
     */
    fun clearError() {
        _uiState.update { it.copy(errorCode = null) }
    }
}

/**
 * Estado da UI para alteração de senha.
 *
 * @property isLoading Indica operação em andamento
 * @property isSuccess Indica que a senha foi alterada com sucesso
 * @property errorCode Código de erro tipado (ou null se não houver erro)
 * @property isGoogleUser Indica se é usuário de provedor Google (bloqueia alteração)
 */
data class ChangePasswordUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorCode: AuthErrorCode? = null,
    val isGoogleUser: Boolean = false
)