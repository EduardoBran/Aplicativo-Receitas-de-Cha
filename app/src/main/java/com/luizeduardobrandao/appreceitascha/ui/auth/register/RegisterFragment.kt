package com.luizeduardobrandao.appreceitascha.ui.auth.register

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.InputMethodManager
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
        // ---------- NOME ----------
        binding.etName.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            viewModel.onNameChanged(value)

            if (value.isBlank()) {
                // Campo vazio: limpa erro via validator
                binding.tilName.tag = null
                fieldValidator.validateNameField(binding.tilName, value)
            } else if (binding.tilName.tag != null) {
                // Revalida enquanto digita se já havia erro
                fieldValidator.validateNameField(binding.tilName, value)
            }

            updateRegisterButtonState()
        }
        binding.etName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.etName.text?.toString().orEmpty()
                if (value.isNotBlank()) {
                    val valid = fieldValidator.validateNameField(binding.tilName, value)
                    if (!valid) {
                        val messageFromValidator = binding.tilName.tag as? String
                        SnackbarFragment.showError(
                            binding.root,
                            messageFromValidator ?: getString(R.string.error_name_required)
                        )
                    }
                }
            }
        }

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

            updateRegisterButtonState()
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

            updateRegisterButtonState()
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

        // ---------- CONFIRMAR SENHA ----------
        binding.etConfirmPassword.doAfterTextChanged {
            val confirm = it?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            viewModel.onPasswordChanged(password)

            if (confirm.isBlank()) {
                binding.tilConfirmPassword.tag = null
                fieldValidator.validateConfirmPasswordField(
                    binding.tilConfirmPassword,
                    password,
                    confirm
                )
            } else if (binding.tilConfirmPassword.tag != null) {
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
                val password = binding.etPassword.text?.toString().orEmpty()
                val confirm = binding.etConfirmPassword.text?.toString().orEmpty()

                if (confirm.isNotBlank()) {
                    val valid = fieldValidator.validateConfirmPasswordField(
                        binding.tilConfirmPassword,
                        password,
                        confirm
                    )
                    if (!valid) {
                        val messageFromValidator = binding.tilConfirmPassword.tag as? String
                        SnackbarFragment.showError(
                            binding.root,
                            messageFromValidator
                                ?: getString(R.string.error_confirm_password_required)
                        )
                    }
                }
            }
        }

        // ---------- TELEFONE ----------
        binding.etPhone.addTextChangedListener(object : TextWatcher {

            private var isFormatting = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return

                val current = s?.toString().orEmpty()

                // Mantém apenas dígitos
                val digits = current.filter { it.isDigit() }

                val formatted = when {
                    digits.isEmpty() -> ""
                    digits.length <= 2 -> "(${digits}"
                    else -> "(${digits.substring(0, 2)}) ${digits.substring(2)}"
                }

                isFormatting = true
                s?.replace(0, s.length, formatted)
                isFormatting = false

                // Ajusta posição do cursor para o final do texto formatado
                val cursorPosition = formatted.length
                binding.etPhone.setSelection(
                    cursorPosition.coerceAtMost(binding.etPhone.text?.length ?: 0)
                )

                // Atualiza ViewModel com o TEXTO formatado (a validação depois tira máscara)
                val valueForState = formatted
                viewModel.onPhoneChanged(valueForState)

                // Se já existia erro, revalida enquanto digita
                if (binding.tilPhone.tag != null) {
                    fieldValidator.validatePhoneField(binding.tilPhone, valueForState)
                }

                updateRegisterButtonState()
            }
        })

        binding.etPhone.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.etPhone.text?.toString().orEmpty()
                // Valida APENAS se tiver conteúdo (opcional, mas se tiver tem que ser válido)
                if (value.isNotBlank()) {
                    val valid = fieldValidator.validatePhoneField(binding.tilPhone, value)
                    if (!valid) {
                        val messageFromValidator = binding.tilPhone.tag as? String
                        SnackbarFragment.showError(
                            binding.root,
                            messageFromValidator ?: getString(R.string.error_phone_invalid)
                        )
                    }
                } else {
                    // Campo vazio é válido (opcional), então limpa qualquer erro
                    binding.tilPhone.tag = null
                    fieldValidator.validatePhoneField(binding.tilPhone, value)
                }
            }
        }

        binding.btnRegister.setOnClickListener {
            hideKeyboard()
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