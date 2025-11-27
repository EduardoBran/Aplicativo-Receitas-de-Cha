package com.luizeduardobrandao.appreceitascha.ui.auth.register

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
 * ViewModel responsável pela tela de Cadastro.
 *
 * - Controla campos de nome, e-mail, telefone (opcional) e senha.
 * - Faz validações básicas, incluindo comprimento mínimo de senha.
 * - Chama o [AuthRepository.register] para criar o usuário.
 * - Salva o perfil automaticamente no Realtime Database (via AuthRepositoryImpl).
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
    }

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                name = value,
                nameError = null,
                errorMessage = null
            )
        }
    }

    fun onEmailChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                email = value,
                emailError = null,
                errorMessage = null
            )
        }
    }

    fun onPhoneChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                phone = value,
                phoneError = null,
                errorMessage = null
            )
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                password = value,
                passwordError = null,
                errorMessage = null
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { current ->
            current.copy(errorMessage = null)
        }
    }

    /**
     * Tenta realizar o cadastro com os dados atuais do formulário.
     *
     * Fluxo:
     * 1) Valida campos.
     * 2) Se estiver ok, chama o [AuthRepository.register].
     * 3) Atualiza estado com loading / sucesso / erro.
     */
    fun submitRegister() {
        val current = _uiState.value
        val name = current.name.trim()
        val email = current.email.trim()
        val phone = current.phone.trim()
        val password = current.password

        var hasError = false
        var nameError: String? = null
        var emailError: String? = null
        var phoneError: String? = null
        var passwordError: String? = null

        // Nome
        if (name.isBlank()) {
            nameError = "Informe o nome."
            hasError = true
        }

        // E-mail
        if (email.isBlank()) {
            emailError = "Informe o e-mail."
            hasError = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "E-mail inválido."
            hasError = true
        }

        // Telefone (opcional): valida apenas se preenchido
        if (phone.isNotBlank() && !Patterns.PHONE.matcher(phone).matches()) {
            phoneError = "Telefone inválido."
            hasError = true
        }

        // Senha
        if (password.isBlank()) {
            passwordError = "Informe a senha."
            hasError = true
        } else if (password.length < MIN_PASSWORD_LENGTH) {
            passwordError = "A senha deve ter pelo menos $MIN_PASSWORD_LENGTH caracteres."
            hasError = true
        }

        if (hasError) {
            _uiState.update { currentState ->
                currentState.copy(
                    nameError = nameError,
                    emailError = emailError,
                    phoneError = phoneError,
                    passwordError = passwordError
                )
            }
            return
        }

        val phoneOrNull = if (phone.isBlank()) null else phone

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = authRepository.register(
                name = name,
                email = email,
                password = password,
                phone = phoneOrNull
            )

            result
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRegistered = true,
                            registeredUser = user,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRegistered = false,
                            registeredUser = null,
                            errorMessage = throwable.message
                                ?: "Erro ao realizar cadastro. Tente novamente."
                        )
                    }
                }
        }
    }
}

/**
 * Estado da tela de Cadastro.
 *
 * Ideal para ligar com TextInputLayouts exibindo erros
 * e um ProgressBar/estado de carregamento no botão "Cadastrar".
 */
data class RegisterUiState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",

    val nameError: String? = null,
    val emailError: String? = null,
    val phoneError: String? = null,
    val passwordError: String? = null,

    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val registeredUser: User? = null,
    val errorMessage: String? = null
)