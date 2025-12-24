package com.luizeduardobrandao.appreceitascha.ui.auth.register

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
 * ViewModel responsável pela tela de Cadastro.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onNameChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                name = value,
                nameError = null,
                errorCode = null
            )
        }
    }

    fun onEmailChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                email = value,
                emailError = null,
                errorCode = null
            )
        }
    }

    fun onPhoneChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                phone = value,
                phoneError = null,
                errorCode = null
            )
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { current ->
            current.copy(
                password = value,
                passwordError = null,
                errorCode = null
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { current ->
            current.copy(errorCode = null)
        }
    }

    /**
     * Inicializa o modo de edição carregando os dados do usuário atual
     */
    fun initEditMode() {
        val currentUser = authRepository.getCurrentUser() ?: return

        // 1) Preenche rápido com o que vier do Auth (nome/email)
        _uiState.update { current ->
            current.copy(
                isEditMode = true,
                currentUserId = currentUser.uid,
                name = currentUser.name ?: "",
                email = currentUser.email,
                phone = "" // será preenchido pelo DB abaixo
            )
        }

        // 2) Busca perfil real do Realtime DB (/users/{uid}) para pegar telefone
        viewModelScope.launch {
            authRepository.getUserProfile(currentUser.uid)
                .onSuccess { profile ->
                    if (profile != null) {
                        _uiState.update { current ->
                            current.copy(
                                name = profile.name ?: current.name,
                                // mantém email da sessão, mas poderia usar profile.email se quiser
                                phone = formatPhoneForDisplay(profile.phone)
                            )
                        }
                    }
                }
            // se falhar, ignora: não quebra a tela, só não preenche o phone
        }
    }

    private fun formatPhoneForDisplay(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.isEmpty() -> ""
            digits.length <= 2 -> "(${digits}"
            else -> "(${digits.take(2)}) ${digits.substring(2)}"
        }
    }

    /**
     * Atualiza apenas nome e telefone no modo edição
     */
    fun submitUpdate() {
        val current = _uiState.value
        val name = current.name.trim()
        val phone = current.phone.trim()

        val digitsOnlyPhone = phone.filter { it.isDigit() }
        val phoneOrNull = digitsOnlyPhone.ifBlank { null }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorCode = null) }

            val userId = current.currentUserId
            if (userId == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorCode = AuthErrorCode.USER_NOT_IDENTIFIED
                    )
                }
                return@launch
            }

            val result = authRepository.updateUserProfile(
                uid = userId,
                name = name,
                phone = phoneOrNull
            )

            result
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isUpdateSuccessful = true,
                            errorCode = null
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorCode = AuthErrorCode.UPDATE_PROFILE_FAILED
                        )
                    }
                }
        }
    }

    /**
     * Tenta realizar o cadastro com os dados atuais do formulário.
     */
    fun submitRegister() {
        val current = _uiState.value
        val name = current.name.trim()
        val email = current.email.trim()
        val phone = current.phone.trim()
        val password = current.password

        val digitsOnlyPhone = phone.filter { it.isDigit() }
        val phoneOrNull = digitsOnlyPhone.ifBlank { null }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorCode = null) }

            val result = authRepository.register(
                name = name,
                email = email,
                password = password,
                phone = phoneOrNull
            )

            result
                .onSuccess { user ->
                    // Enviar e-mail de verificação
                    val verificationResult = authRepository.sendEmailVerification()

                    if (verificationResult.isSuccess) {
                        // Fazer logout para forçar verificação
                        authRepository.logout()

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRegistered = true,
                                registeredUser = user,
                                errorCode = null,
                                verificationEmailSent = true
                            )
                        }
                    } else {
                        // Erro ao enviar verificação, mas cadastro foi feito
                        authRepository.logout()

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isRegistered = true,
                                registeredUser = user,
                                errorCode = AuthErrorCode.EMAIL_VERIFICATION_FAILED,
                                verificationEmailSent = false
                            )
                        }
                    }
                }
                .onFailure { throwable ->
                    // Detecta se é erro de e-mail já cadastrado
                    val errorCode = if (throwable.message?.contains(
                            "already in use",
                            ignoreCase = true
                        ) == true
                    ) {
                        AuthErrorCode.EMAIL_ALREADY_IN_USE
                    } else {
                        AuthErrorCode.REGISTER_FAILED
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRegistered = false,
                            registeredUser = null,
                            errorCode = errorCode,
                            verificationEmailSent = false
                        )
                    }
                }
        }
    }
}

/**
 * Estado da tela de Cadastro.
 */
data class RegisterUiState(
    val isEditMode: Boolean = false,
    val currentUserId: String? = null,

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
    val isUpdateSuccessful: Boolean = false,
    val registeredUser: User? = null,
    val errorCode: AuthErrorCode? = null,
    val verificationEmailSent: Boolean = false
)