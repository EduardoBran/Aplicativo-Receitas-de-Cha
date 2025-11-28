package com.luizeduardobrandao.appreceitascha.ui.auth.register

import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentRegisterBinding
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidationRules
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels()

    private val fieldValidator = FieldValidator()

    private var hasNavigatedToHome = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupToolbar()
        setupListeners()
        observeUiState()
    }

    private fun setupToolbar() {
        // Clique no item de menu (Home -> voltar para login limpando back stack)
        binding.toolbarRegister.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_go_home_login -> {
                    navigateBackToLoginClearBackStack()
                    true
                }

                else -> false
            }
        }

        // Deixa o ícone do menu "Home" branco programaticamente
        val homeItem = binding.toolbarRegister.menu.findItem(R.id.menu_go_home_login)
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.text_on_primary)

        homeItem?.icon?.let { originalDrawable ->
            val wrapped = DrawableCompat.wrap(originalDrawable).mutate()
            DrawableCompat.setTint(wrapped, whiteColor)
            homeItem.icon = wrapped
        }
    }

    private fun setupListeners() {
        binding.etName.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            viewModel.onNameChanged(value)
            if (binding.tilName.isErrorEnabled) {
                fieldValidator.validateNameField(binding.tilName, value)
            }
            updateRegisterButtonState()
        }
        binding.etName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val valid = fieldValidator.validateNameField(
                    binding.tilName,
                    binding.etName.text?.toString().orEmpty()
                )
                if (!valid) {
                    SnackbarFragment.showError(
                        binding.root,
                        binding.tilName.error?.toString() ?: "Nome inválido."
                    )
                }
            }
        }

        binding.etEmail.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            viewModel.onEmailChanged(value)
            if (binding.tilEmail.isErrorEnabled) {
                fieldValidator.validateEmailField(binding.tilEmail, value)
            }
            updateRegisterButtonState()
        }
        binding.etEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val valid = fieldValidator.validateEmailField(
                    binding.tilEmail,
                    binding.etEmail.text?.toString().orEmpty()
                )
                if (!valid) {
                    SnackbarFragment.showError(
                        binding.root,
                        binding.tilEmail.error?.toString() ?: "E-mail inválido."
                    )
                }
            }
        }

        binding.etPassword.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            viewModel.onPasswordChanged(value)
            if (binding.tilPassword.isErrorEnabled) {
                fieldValidator.validatePasswordField(binding.tilPassword, value)
            }
            updateRegisterButtonState()
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val valid = fieldValidator.validatePasswordField(
                    binding.tilPassword,
                    binding.etPassword.text?.toString().orEmpty()
                )
                if (!valid) {
                    SnackbarFragment.showError(
                        binding.root,
                        binding.tilPassword.error?.toString() ?: "Senha inválida."
                    )
                }
            }
        }

        binding.etConfirmPassword.doAfterTextChanged {
            val confirm = it?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            viewModel.onPasswordChanged(password) // já está sendo chamado acima
            if (binding.tilConfirmPassword.isErrorEnabled) {
                fieldValidator.validateConfirmPasswordField(
                    binding.tilConfirmPassword,
                    password,
                    confirm
                )
            }
            updateRegisterButtonState()
        }
        binding.etConfirmPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val valid = fieldValidator.validateConfirmPasswordField(
                    binding.tilConfirmPassword,
                    binding.etPassword.text?.toString().orEmpty(),
                    binding.etConfirmPassword.text?.toString().orEmpty()
                )
                if (!valid) {
                    SnackbarFragment.showError(
                        binding.root,
                        binding.tilConfirmPassword.error?.toString() ?: "Confirmação inválida."
                    )
                }
            }
        }

        binding.etPhone.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            viewModel.onPhoneChanged(value)
            if (binding.tilPhone.isErrorEnabled) {
                fieldValidator.validatePhoneField(binding.tilPhone, value)
            }
            updateRegisterButtonState()
        }
        binding.etPhone.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val valid = fieldValidator.validatePhoneField(
                    binding.tilPhone,
                    binding.etPhone.text?.toString().orEmpty()
                )
                if (!valid) {
                    SnackbarFragment.showError(
                        binding.root,
                        binding.tilPhone.error?.toString() ?: "Telefone inválido."
                    )
                }
            }
        }

        binding.btnRegister.setOnClickListener {
            viewModel.submitRegister()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressRegister.isVisible = state.isLoading
                    updateRegisterButtonState(additionalLoadingFlag = state.isLoading)

                    if (state.errorMessage != null) {
                        // Para simplificar, qualquer erro no cadastro será exibido como
                        // "e-mail já cadastrado" (caso real comum). Pode refinar depois.
                        SnackbarFragment.showError(
                            binding.root,
                            getString(R.string.snackbar_error_email_exists)
                        )
                        viewModel.clearErrorMessage()
                    }

                    if (state.isRegistered && state.registeredUser != null && !hasNavigatedToHome) {
                        navigateToHomeAndClearBackStack(state.registeredUser.name ?: "")
                    }
                }
            }
        }
    }

    private fun updateRegisterButtonState(additionalLoadingFlag: Boolean = false) {
        val name = binding.etName.text?.toString().orEmpty()
        val email = binding.etEmail.text?.toString().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val confirm = binding.etConfirmPassword.text?.toString().orEmpty()
        val phone = binding.etPhone.text?.toString().orEmpty()

        val nameValid = FieldValidationRules.validateName(name).isValid
        val emailValid = FieldValidationRules.validateEmail(email).isValid
        val passwordValid = FieldValidationRules.validatePassword(password).isValid
        val confirmValid = FieldValidationRules.validateConfirmPassword(password, confirm).isValid
        val phoneValid = FieldValidationRules.validatePhone(phone).isValid

        val enabled =
            nameValid && emailValid && passwordValid && confirmValid && phoneValid && !additionalLoadingFlag

        binding.btnRegister.isEnabled = enabled
        binding.btnRegister.alpha = if (enabled) 1f else 0.6f
    }

    private fun navigateToHomeAndClearBackStack(userName: String) {
        hasNavigatedToHome = true

        val directions =
            RegisterFragmentDirections.actionRegisterFragmentToHomeFragment(userName = userName)

        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.loginFragment, true)
            .build()

        findNavController().navigate(directions, navOptions)
    }

    private fun navigateBackToLoginClearBackStack() {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.loginFragment, true)
            .build()
        findNavController().navigate(R.id.loginFragment, null, navOptions)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}