package com.luizeduardobrandao.appreceitascha.ui.auth.login

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsável pela tela de Login.
 *
 * - Controla campos de e-mail e senha.
 * - Faz validações básicas de formulário.
 * - Chama o [AuthRepository] para autenticar o usuário.
 * - Expõe um [StateFlow] observável pela UI (Fragment).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * Atualiza o campo de e-mail no estado da tela.
     */
    fun onEmailChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                email = value,
                emailError = null,      // Limpa erro ao digitar novamente
                errorMessage = null     // Limpa erro geral
            )
        }
    }

    /**
     * Atualiza o campo de senha no estado da tela.
     */
    fun onPasswordChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                password = value,
                passwordError = null,
                errorMessage = null
            )
        }
    }

    /**
     * Limpa mensagem de erro geral (por exemplo, após exibir em Snackbar/Toast).
     */
    fun clearErrorMessage() {
        _uiState.update { current ->
            current.copy(errorMessage = null)
        }
    }

    /**
     * Tenta realizar o login com os dados atuais do formulário.
     *
     * 1) Valida campos (e-mail e senha).
     * 2) Se estiver ok, chama o [AuthRepository.login].
     * 3) Atualiza o estado com loading, erro ou sucesso.
     */
    fun submitLogin() {
        val current = _uiState.value
        val email = current.email.trim()
        val password = current.password

        // 1) Validação de formulário
        var hasError = false
        var emailError: String? = null
        var passwordError: String? = null

        if (email.isBlank()) {
            emailError = "Informe o e-mail."
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "E-mail inválido."
            hasError = true
        }

        if (password.isBlank()) {
            passwordError = "Informe a senha."
            hasError = true
        }

        if (hasError) {
            _uiState.update { currentState ->
                currentState.copy(
                    emailError = emailError,
                    passwordError = passwordError
                )
            }
            return
        }

        // 2) Chama o repositório de autenticação
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = authRepository.login(email, password)

            result
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            loggedUser = user,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = false,
                            loggedUser = null,
                            errorMessage = throwable.message
                                ?: "Erro ao realizar login. Tente novamente."
                        )
                    }
                }
        }
    }
}

/**
 * Estado da tela de Login.
 *
 * A UI deve observar esse [StateFlow] para atualizar os componentes
 * (TextInputLayout, botões, progress bar, mensagens de erro, etc.).
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val loggedUser: User? = null,
    val errorMessage: String? = null
)