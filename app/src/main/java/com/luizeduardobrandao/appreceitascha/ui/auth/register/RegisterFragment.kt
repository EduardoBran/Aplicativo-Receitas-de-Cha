package com.luizeduardobrandao.appreceitascha.ui.auth.register

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentRegisterBinding
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.ui.auth.AuthErrorCode
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.utils.KeyboardUtils
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidationRules
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RegisterFragment : Fragment() {

    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RegisterViewModel by viewModels()

    @Inject
    lateinit var authRepository: AuthRepository

    private val args: RegisterFragmentArgs by navArgs()
    private val isEditMode: Boolean
        get() = args.isEditMode

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
        super.onViewCreated(view, savedInstanceState)

        // Inicializa modo de edição se necessário
        if (isEditMode) {
            viewModel.initEditMode()
        }

        setupListeners()
        observeUiState()
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
            KeyboardUtils.hideKeyboard(this@RegisterFragment)
            if (isEditMode) {
                viewModel.submitUpdate()
            } else {
                viewModel.submitRegister()
            }
        }

        binding.btnChangeEmail.setOnClickListener {
            val dialog = ChangeEmailDialogFragment.newInstance()
            dialog.setParentView(binding.root)
            dialog.show(childFragmentManager, "ChangeEmailDialog")
        }

        binding.btnChangePassword.setOnClickListener {
            val dialog = ChangePasswordDialogFragment.newInstance()
            dialog.setParentView(binding.root)
            dialog.show(childFragmentManager, "ChangePasswordDialog")
        }

        binding.btnLogout.setOnClickListener {
            performLogout()
        }

        binding.btnGoToLogin.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun performLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_logout_title)
            .setMessage(R.string.dialog_logout_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                authRepository.logout()
                requireActivity().invalidateOptionsMenu()

                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.homeFragment, true)
                    .build()

                findNavController().navigate(R.id.homeFragment, null, navOptions)
            }
            .setNegativeButton(R.string.dialog_no, null)
            .show()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: RegisterUiState) {
        // Configura UI baseado no modo
        if (state.isEditMode) {
            // ===== MODO EDIÇÃO =====
            binding.tvTitle.text = getString(R.string.register_edit_mode_title)
            binding.tvSubtitle.isVisible = false
            binding.btnRegister.text = getString(R.string.register_update_button_text)
            binding.btnLogout.isVisible = true
            binding.layoutBackToLogin.isVisible = false

            val currentUser = authRepository.getCurrentUser()
            val isGoogleUser = currentUser?.provider == "google.com"

            if (isGoogleUser) {
                binding.btnChangeEmail.isVisible = false
                binding.btnChangePassword.isVisible = false

                binding.tilEmail.isEnabled = false
                binding.etEmail.isEnabled = false
                binding.etEmail.isFocusable = false
                binding.etEmail.isFocusableInTouchMode = false
                binding.etEmail.isClickable = false
                binding.tilEmail.alpha = 0.6f

                binding.tilPassword.isEnabled = false
                binding.etPassword.isEnabled = false
                binding.etPassword.isFocusable = false
                binding.etPassword.isFocusableInTouchMode = false
                binding.etPassword.isClickable = false
                binding.tilPassword.alpha = 0.6f
            } else {
                binding.btnChangeEmail.isVisible = true
                binding.btnChangePassword.isVisible = true

                binding.tilEmail.isEnabled = false
                binding.etEmail.isEnabled = false
                binding.etEmail.isFocusable = false
                binding.etEmail.isFocusableInTouchMode = false
                binding.etEmail.isClickable = false
                binding.tilEmail.alpha = 0.6f

                binding.tilPassword.isEnabled = false
                binding.etPassword.isEnabled = false
                binding.etPassword.isFocusable = false
                binding.etPassword.isFocusableInTouchMode = false
                binding.etPassword.isClickable = false
                binding.tilPassword.alpha = 0.6f
            }

            binding.tilConfirmPassword.isVisible = false

            if (binding.etName.text.toString() != state.name) {
                binding.etName.setText(state.name)
            }
            if (binding.etEmail.text.toString() != state.email) {
                binding.etEmail.setText(state.email)
            }
            if (binding.etPhone.text.toString() != state.phone) {
                binding.etPhone.setText(state.phone)
            }
            binding.etPassword.setText("")
            binding.etConfirmPassword.setText("")
        } else {
            // ===== MODO CADASTRO =====
            binding.tvTitle.text = getString(R.string.register_title)
            binding.tvSubtitle.isVisible = true
            binding.tvSubtitle.text = getString(R.string.register_subtitle)
            binding.btnRegister.text = getString(R.string.register_button_text)
            binding.btnLogout.isVisible = false
            binding.layoutBackToLogin.isVisible = true

            binding.tilEmail.isEnabled = true
            binding.etEmail.isEnabled = true
            binding.etEmail.isFocusable = true
            binding.etEmail.isFocusableInTouchMode = true
            binding.etEmail.isClickable = true
            binding.tilEmail.alpha = 1f

            binding.tilPassword.isEnabled = true
            binding.etPassword.isEnabled = true
            binding.etPassword.isFocusable = true
            binding.etPassword.isFocusableInTouchMode = true
            binding.etPassword.isClickable = true
            binding.tilPassword.alpha = 1f

            binding.tilConfirmPassword.isVisible = true
            binding.tilConfirmPassword.isEnabled = true
            binding.etConfirmPassword.isEnabled = true
            binding.etConfirmPassword.isFocusable = true
            binding.etConfirmPassword.isFocusableInTouchMode = true
            binding.etConfirmPassword.isClickable = true
            binding.tilConfirmPassword.alpha = 1f

            binding.btnChangeEmail.isVisible = false
            binding.btnChangePassword.isVisible = false
        }

        binding.progressRegister.isVisible = state.isLoading
        updateRegisterButtonState(additionalLoadingFlag = state.isLoading)

        // ✅ Traduz código de erro para mensagem
        state.errorCode?.let { errorCode ->
            val message = when (errorCode) {
                AuthErrorCode.EMAIL_ALREADY_IN_USE ->
                    getString(R.string.error_register_email_in_use)

                AuthErrorCode.REGISTER_FAILED ->
                    getString(R.string.error_register_generic)

                AuthErrorCode.EMAIL_VERIFICATION_FAILED ->
                    getString(R.string.error_register_verification_failed)

                AuthErrorCode.UPDATE_PROFILE_FAILED ->
                    getString(R.string.error_update_profile_failed)

                AuthErrorCode.USER_NOT_IDENTIFIED ->
                    getString(R.string.error_user_not_identified)

                else -> getString(R.string.error_register_generic)
            }
            SnackbarFragment.showError(binding.root, message)
            viewModel.clearErrorMessage()
        }

        if (state.isUpdateSuccessful && !hasNavigatedToHome) {
            hasNavigatedToHome = true
            SnackbarFragment.showSuccess(
                binding.root,
                getString(R.string.snackbar_success_profile_updated)
            )
            binding.root.postDelayed({
                findNavController().navigateUp()
            }, 1500)
        }

        if (state.isRegistered && state.registeredUser != null && !hasNavigatedToHome) {
            hasNavigatedToHome = true
            if (state.verificationEmailSent) {
                SnackbarFragment.showSuccess(
                    binding.root,
                    getString(R.string.email_verification_sent, state.registeredUser.email)
                )
            }
            binding.root.postDelayed({
                findNavController().navigateUp()
            }, 2500)
        }
    }

    private fun updateRegisterButtonState(additionalLoadingFlag: Boolean = false) {
        val currentState = viewModel.uiState.value

        if (currentState.isEditMode) {
            // Modo edição: valida apenas nome e telefone
            val name = binding.etName.text?.toString().orEmpty()
            val phone = binding.etPhone.text?.toString().orEmpty()

            val nameValid = FieldValidationRules.validateName(name).isValid
            val phoneValid = FieldValidationRules.validatePhone(phone).isValid

            val enabled = nameValid && phoneValid && !additionalLoadingFlag

            binding.btnRegister.isEnabled = enabled
            binding.btnRegister.alpha = if (enabled) 1f else 0.6f
        } else {
            // Modo cadastro: valida todos os campos
            val name = binding.etName.text?.toString().orEmpty()
            val email = binding.etEmail.text?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            val confirm = binding.etConfirmPassword.text?.toString().orEmpty()
            val phone = binding.etPhone.text?.toString().orEmpty()

            val nameValid = FieldValidationRules.validateName(name).isValid
            val emailValid = FieldValidationRules.validateEmail(email).isValid
            val passwordValid = FieldValidationRules.validatePassword(password).isValid
            val confirmValid =
                FieldValidationRules.validateConfirmPassword(password, confirm).isValid
            val phoneValid = FieldValidationRules.validatePhone(phone).isValid

            val enabled =
                nameValid && emailValid && passwordValid && confirmValid && phoneValid && !additionalLoadingFlag

            binding.btnRegister.isEnabled = enabled
            binding.btnRegister.alpha = if (enabled) 1f else 0.6f
        }
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