package com.luizeduardobrandao.appreceitascha.ui.auth.login

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentLoginBinding
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidationRules
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.NavOptions

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModels()

    @Inject
    lateinit var authRepository: AuthRepository

    private var hasNavigatedToHome = false
    private val fieldValidator = FieldValidator()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupListeners()
        observeUiState()

        // Se o usuário já estiver logado (fechou e abriu o app), ir direto para Home
        val currentUser = authRepository.getCurrentUser()
        if (currentUser != null && !hasNavigatedToHome) {
            navigateToHomeAndClearBackStack(currentUser.name ?: "")
        }
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

        binding.btnLogin.setOnClickListener {
            hideKeyboard()
            viewModel.submitLogin()
        }

        binding.btnGoToRegister.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }

        binding.btnGoToResetPassword.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_resetPasswordFragment)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressLogin.isVisible = state.isLoading

                    // Atualiza botão com base na validade + loading
                    updateLoginButtonState(additionalLoadingFlag = state.isLoading)

                    if (state.errorMessage != null) {
                        SnackbarFragment.showError(
                            binding.root,
                            getString(R.string.snackbar_error_invalid_login)
                        )
                        viewModel.clearErrorMessage()
                    }

                    if (state.isLoggedIn && state.loggedUser != null && !hasNavigatedToHome) {
                        navigateToHomeAndClearBackStack(state.loggedUser.name ?: "")
                    }
                }
            }
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

    private fun navigateToHomeAndClearBackStack(userName: String) {
        hasNavigatedToHome = true

        val directions =
            LoginFragmentDirections.actionLoginFragmentToHomeFragment(userName = userName)

        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.loginFragment, true)
            .build()

        findNavController().navigate(directions, navOptions)
    }

    private fun hideKeyboard() {
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}