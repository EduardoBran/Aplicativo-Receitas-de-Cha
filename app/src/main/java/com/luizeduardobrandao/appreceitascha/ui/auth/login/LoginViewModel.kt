package com.luizeduardobrandao.appreceitascha.ui.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.User
import com.luizeduardobrandao.appreceitascha.ui.auth.AuthErrorCode
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
                emailError = null,
                errorCode = null
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
                errorCode = null
            )
        }
    }

    /**
     * Limpa mensagem de erro geral.
     */
    fun clearErrorMessage() {
        _uiState.update { current ->
            current.copy(errorCode = null)
        }
    }

    /**
     * Tenta realizar o login com os dados atuais do formulário.
     */
    fun submitLogin() {
        val current = _uiState.value
        val email = current.email.trim()
        val password = current.password

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorCode = null) }

            val result = authRepository.login(email, password)

            result
                .onSuccess { user ->
                    val currentUser = authRepository.getCurrentUser()
                    val isPasswordProvider = currentUser != null &&
                            !currentUser.isEmailVerified

                    if (!user.isEmailVerified && isPasswordProvider) {
                        authRepository.logout()

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoggedIn = false,
                                loggedUser = null,
                                errorCode = AuthErrorCode.EMAIL_NOT_VERIFIED,
                                emailNotVerified = true,
                                userEmail = user.email
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isLoggedIn = true,
                                loggedUser = user,
                                errorCode = null,
                                emailNotVerified = false
                            )
                        }
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = false,
                            loggedUser = null,
                            errorCode = AuthErrorCode.INVALID_CREDENTIALS,
                            emailNotVerified = false
                        )
                    }
                }
        }
    }

    /**
     * Login com Google usando idToken
     */
    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorCode = null) }

            val result = authRepository.signInWithGoogle(idToken)

            result
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            loggedUser = user,
                            errorCode = null
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoggedIn = false,
                            loggedUser = null,
                            errorCode = AuthErrorCode.GOOGLE_SIGNIN_FAILED
                        )
                    }
                }
        }
    }
}

/**
 * Estado da tela de Login.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val loggedUser: User? = null,
    val errorCode: AuthErrorCode? = null,
    val emailNotVerified: Boolean = false,
    val userEmail: String? = null
)