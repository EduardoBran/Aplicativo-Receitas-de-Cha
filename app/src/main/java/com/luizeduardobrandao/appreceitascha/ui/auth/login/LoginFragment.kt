package com.luizeduardobrandao.appreceitascha.ui.auth.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        binding.etEmail.doAfterTextChanged {
            viewModel.onEmailChanged(it?.toString().orEmpty())
            updateLoginButtonState()
        }

        binding.etPassword.doAfterTextChanged {
            viewModel.onPasswordChanged(it?.toString().orEmpty())
            updateLoginButtonState()
        }

        binding.btnLogin.setOnClickListener {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}