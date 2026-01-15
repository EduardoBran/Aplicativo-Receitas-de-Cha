package com.luizeduardobrandao.appreceitascha.ui.auth.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.luizeduardobrandao.appreceitascha.databinding.FragmentLoginBinding
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.ui.auth.AuthErrorCode
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.extensions.showEmailVerificationDialog
import com.luizeduardobrandao.appreceitascha.ui.common.utils.KeyboardUtils
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidationRules
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment responsável pela tela de Login.
 *
 * - Observa o [LoginViewModel] via StateFlow.
 * - Vincula campos de texto ao estado.
 * - Exibe erros de validação e mensagens gerais.
 * - Navega para outras telas (RegisterFragment, ResetPasswordFragment, Home).
 * - Implementa login com Google usando Credential Manager API (API moderna oficial).
 */
@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()
    private val fieldValidator = FieldValidator()
    private lateinit var credentialManager: CredentialManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        credentialManager = CredentialManager.create(requireActivity())

        setupListeners()
        observeUiState()
    }

    private fun setupListeners() {
        // ---------- E-MAIL ----------
        binding.etEmail.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            viewModel.onEmailChanged(value)

            if (value.isBlank()) {
                binding.tilEmail.tag = null
                fieldValidator.validateEmailField(binding.tilEmail, value)
            } else if (binding.tilEmail.tag != null) {
                fieldValidator.validateEmailField(binding.tilEmail, value)
            }

            updateLoginButtonState()
        }

        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.etEmail.text?.toString().orEmpty()
                if (value.isNotBlank()) {
                    val valid = fieldValidator.validateEmailField(binding.tilEmail, value)
                    if (!valid) {
                        val messageFromValidator = binding.tilEmail.tag as? String
                        SnackbarFragment.showError(
                            binding.root,
                            messageFromValidator ?: getString(R.string.error_email_required)
                        )
                    }
                }
            }
        }

        // ---------- SENHA ----------
        binding.etPassword.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            viewModel.onPasswordChanged(value)

            if (value.isBlank()) {
                binding.tilPassword.tag = null
                fieldValidator.validatePasswordField(binding.tilPassword, value)
            } else if (binding.tilPassword.tag != null) {
                fieldValidator.validatePasswordField(binding.tilPassword, value)
            }

            updateLoginButtonState()
        }

        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.etPassword.text?.toString().orEmpty()
                if (value.isNotBlank()) {
                    val valid = fieldValidator.validatePasswordField(binding.tilPassword, value)
                    if (!valid) {
                        val messageFromValidator = binding.tilPassword.tag as? String
                        SnackbarFragment.showError(
                            binding.root,
                            messageFromValidator ?: getString(R.string.error_password_required)
                        )
                    }
                }
            }
        }

        // Botão de login
        binding.btnLogin.setOnClickListener {
            KeyboardUtils.hideKeyboard(this@LoginFragment)
            viewModel.submitLogin()
        }

        // Botão Google Sign-In
        binding.btnGoogleSignIn.setOnClickListener {
            KeyboardUtils.hideKeyboard(this@LoginFragment)
            startGoogleSignIn()
        }

        // Link para recuperação de senha
        binding.btnGoToResetPassword.setOnClickListener {
            findNavController().navigate(
                R.id.action_loginFragment_to_resetPasswordFragment
            )
        }

        // Link para cadastro
        binding.btnGoToRegister.setOnClickListener {
            findNavController().navigate(
                R.id.action_loginFragment_to_registerFragment
            )
        }
    }

    private fun updateLoginButtonState(additionalLoadingFlag: Boolean = false) {
        val email = binding.etEmail.text?.toString().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        val emailValid = FieldValidationRules.validateEmail(email).isValid
        val passwordValid = FieldValidationRules.validatePassword(password).isValid

        val enabled = emailValid && passwordValid && !additionalLoadingFlag

        binding.btnLogin.isEnabled = enabled
        binding.btnLogin.alpha = if (enabled) 1f else 0.6f
    }

    private fun startGoogleSignIn() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val webClientId = getString(R.string.default_web_client_id).trim()

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = requireActivity()
                )

                handleSignInResult(result)

            } catch (_: GetCredentialCancellationException) {
                SnackbarFragment.showWarning(
                    binding.root,
                    getString(R.string.google_sign_in_cancelled)
                )

            } catch (_: NoCredentialException) {
                SnackbarFragment.showError(
                    binding.root,
                    getString(R.string.erro_google_nenhuma_conta)
                )

            } catch (e: GetCredentialException) {
                SnackbarFragment.showError(
                    binding.root,
                    getString(R.string.erro_google_falha_provedor)
                )

            } catch (_: Exception) {
                SnackbarFragment.showError(
                    binding.root,
                    getString(R.string.error_google_signin_generic)
                )
            }
        }
    }

    private fun handleSignInResult(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential
                            .createFrom(credential.data)

                        val idToken = googleIdTokenCredential.idToken

                        // Envia o token para o ViewModel
                        viewModel.signInWithGoogle(idToken)

                    } catch (_: Exception) {
                        SnackbarFragment.showError(
                            binding.root,
                            getString(R.string.error_google_signin_generic)
                        )
                    }
                } else {
                    SnackbarFragment.showError(
                        binding.root,
                        getString(R.string.error_google_signin_generic)
                    )
                }
            }

            else -> {
                showSnackbar(getString(R.string.error_google_signin_generic))
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUi(state)
            }
        }
    }

    private fun updateUi(state: LoginUiState) {
        // Atualiza erros nos campos
        binding.tilEmail.error = state.emailError
        binding.tilPassword.error = state.passwordError

        // Atualiza estado do botão de login
        updateLoginButtonState(additionalLoadingFlag = state.isLoading)

        // Desabilita botão Google durante loading
        binding.btnGoogleSignIn.isEnabled = !state.isLoading

        // Mostra/esconde ProgressBar
        binding.progressLogin.visibility = if (state.isLoading) View.VISIBLE else View.GONE

        // Traduz código de erro para mensagem ou Dialog
        state.errorCode?.let { errorCode ->

            if (errorCode == AuthErrorCode.EMAIL_NOT_VERIFIED) {
                // Passamos o clearErrorMessage APENAS na lambda (quando o dialog fechar)
                requireContext().showEmailVerificationDialog(state.userEmail ?: "") {
                    viewModel.clearErrorMessage()
                }

            } else {
                // Para erros normais (Snackbar), mantemos a lógica antiga
                val message = when (errorCode) {
                    AuthErrorCode.INVALID_CREDENTIALS -> getString(R.string.error_login_invalid_credentials)
                    AuthErrorCode.GOOGLE_SIGNIN_FAILED -> getString(R.string.error_google_signin_generic)
                    else -> getString(R.string.error_login_generic)
                }
                SnackbarFragment.showError(binding.root, message)

                // Erros de snackbar podem ser limpos imediatamente
                viewModel.clearErrorMessage()
            }
        }

        // Se login foi bem-sucedido, navega para a Home
        if (state.isLoggedIn && state.loggedUser != null) {
            navigateToHome()
        }
    }

    private fun navigateToHome() {
        findNavController().navigate(
            R.id.action_loginFragment_to_homeFragment
        )
    }

    private fun showSnackbar(message: String) {
        SnackbarFragment.showError(binding.root, message)
    }

    override fun onDestroyView() {
        // Cancela qualquer snackbar pendente antes de destruir a view
        _binding?.let { binding ->
            SnackbarFragment.cancelPendingSnackbars(binding.root)
        }
        super.onDestroyView()
        _binding = null
    }
}