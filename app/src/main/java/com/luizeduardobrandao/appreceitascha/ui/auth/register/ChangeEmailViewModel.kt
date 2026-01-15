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
 * ViewModel responsável pela alteração de e-mail do usuário.
 *
 * Fluxo de operação:
 * 1. Verifica se é usuário Google (bloqueia se for)
 * 2. Reautentica o usuário com senha atual
 * 3. Atualiza para o novo e-mail com verificação automática
 *
 * IMPORTANTE: O Firebase envia um e-mail de verificação para o novo endereço.
 * O e-mail só será efetivamente atualizado após o usuário clicar no link de verificação.
 *
 * Utiliza [AuthErrorCode] para comunicação type-safe de erros,
 * delegando a tradução de mensagens para a camada de UI.
 */
@HiltViewModel
class ChangeEmailViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChangeEmailUiState())
    val uiState: StateFlow<ChangeEmailUiState> = _uiState.asStateFlow()

    init {
        checkUserProvider()
    }

    /**
     * Verifica se o usuário logado é de provedor Google.
     * Usuários Google não podem alterar e-mail via Firebase Auth.
     */
    private fun checkUserProvider() {
        val currentUser = authRepository.getCurrentUser()
        val isGoogleUser = currentUser?.provider == "google.com"

        _uiState.update { it.copy(isGoogleUser = isGoogleUser) }
    }

    /**
     * Inicia o processo de alteração de e-mail.
     *
     * @param currentPassword Senha atual do usuário (para reautenticação)
     * @param newEmail Novo e-mail desejado
     */
    fun changeEmail(currentPassword: String, newEmail: String) {
        // Bloqueia operação para usuários Google
        if (_uiState.value.isGoogleUser) {
            _uiState.update {
                it.copy(
                    errorCode = AuthErrorCode.GOOGLE_USER_CANNOT_CHANGE_EMAIL
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorCode = null) }

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

            // Etapa 2: Atualiza e-mail com verificação automática
            val updateResult = authRepository.updateUserEmail(newEmail)

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
                    val errorCode = mapEmailUpdateError(error)

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
     *
     * Erros comuns do Firebase Auth:
     * - "wrong-password" → senha incorreta
     * - "user-mismatch" → credencial não corresponde ao usuário atual
     */
    private fun mapReauthenticationError(exception: Throwable?): AuthErrorCode {
        return when {
            exception?.message?.contains("password", ignoreCase = true) == true ->
                AuthErrorCode.CHANGE_EMAIL_INCORRECT_PASSWORD

            exception?.message?.contains("wrong-password", ignoreCase = true) == true ->
                AuthErrorCode.CHANGE_EMAIL_INCORRECT_PASSWORD

            exception?.message?.contains("user-mismatch", ignoreCase = true) == true ->
                AuthErrorCode.CHANGE_EMAIL_USER_MISMATCH

            else -> AuthErrorCode.CHANGE_EMAIL_INCORRECT_PASSWORD
        }
    }

    /**
     * Mapeia exceções de atualização de e-mail para códigos de erro tipados.
     *
     * Erros comuns do Firebase Auth:
     * - "email-already-in-use" → e-mail já cadastrado
     * - "invalid-email" → formato de e-mail inválido
     * - "requires-recent-login" → sessão expirada, requer novo login
     */
    private fun mapEmailUpdateError(error: Throwable): AuthErrorCode {
        return when {
            error.message?.contains("email-already-in-use", ignoreCase = true) == true ->
                AuthErrorCode.CHANGE_EMAIL_ALREADY_IN_USE

            error.message?.contains("invalid-email", ignoreCase = true) == true ->
                AuthErrorCode.CHANGE_EMAIL_INVALID

            error.message?.contains("requires-recent-login", ignoreCase = true) == true ->
                AuthErrorCode.CHANGE_EMAIL_REQUIRES_RECENT_LOGIN

            else -> AuthErrorCode.CHANGE_EMAIL_GENERIC
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
 * Estado da UI para alteração de e-mail.
 *
 * @property isLoading Indica operação em andamento
 * @property isSuccess Indica que o e-mail foi atualizado com sucesso
 * @property errorCode Código de erro tipado (ou null se não houver erro)
 * @property isGoogleUser Indica se é usuário de provedor Google (bloqueia alteração)
 */
data class ChangeEmailUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorCode: AuthErrorCode? = null,
    val isGoogleUser: Boolean = false
)