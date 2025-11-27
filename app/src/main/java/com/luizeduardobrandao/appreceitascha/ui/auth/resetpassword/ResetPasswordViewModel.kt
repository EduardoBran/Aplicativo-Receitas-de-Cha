package com.luizeduardobrandao.appreceitascha.ui.auth.resetpassword

import android.util.Patterns
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

/**
 * ViewModel responsável pela tela de Recuperação de Senha.
 *
 * - Controla o campo de e-mail.
 * - Faz validação básica de e-mail.
 * - Chama o [AuthRepository.sendPasswordReset] para enviar o e-mail de redefinição.
 */
@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResetPasswordUiState())
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                email = value,
                emailError = null,
                errorMessage = null,
                isSuccess = false         // Se o usuário alterar o e-mail, reseta sucesso
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { current ->
            current.copy(errorMessage = null)
        }
    }

    /**
     * Tenta enviar o e-mail de redefinição de senha.
     *
     * 1) Valida o e-mail.
     * 2) Chama o [AuthRepository.sendPasswordReset].
     * 3) Atualiza estado com loading / sucesso / erro.
     */
    fun submitResetPassword() {
        val current = _uiState.value
        val email = current.email.trim()

        var hasError = false
        var emailError: String? = null

        if (email.isBlank()) {
            emailError = "Informe o e-mail."
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "E-mail inválido."
            hasError = true
        }

        if (hasError) {
            _uiState.update { state ->
                state.copy(emailError = emailError)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    isSuccess = false
                )
            }

            val result = authRepository.sendPasswordReset(email)

            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = true,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isSuccess = false,
                            errorMessage = throwable.message
                                ?: "Erro ao enviar e-mail de redefinição."
                        )
                    }
                }
        }
    }
}

/**
 * Estado da tela de Recuperação de Senha.
 */
data class ResetPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,  // true quando o e-mail for enviado com sucesso
    val errorMessage: String? = null
)