package com.luizeduardobrandao.appreceitascha.ui.auth.register

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanType
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

        if (isEditMode) {
            viewModel.initEditMode()
        }

        setupListeners()
        observeUiState()
    }

    private fun setupListeners() {
        // ---------- CAMPOS DE TEXTO ----------
        // Lógica mantida identica, apenas referenciando os campos que ainda existem no binding
        binding.etName.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            viewModel.onNameChanged(value)
            if (value.isBlank()) {
                binding.tilName.tag = null
                fieldValidator.validateNameField(binding.tilName, value)
            } else if (binding.tilName.tag != null) {
                fieldValidator.validateNameField(binding.tilName, value)
            }
            updateRegisterButtonState()
        }

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

        binding.etConfirmPassword.doAfterTextChanged {
            val confirm = it?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            viewModel.onPasswordChanged(password) // Atualiza estado senha tb
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

        binding.etPhone.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                val current = s?.toString().orEmpty()
                val digits = current.filter { it.isDigit() }
                val formatted = when {
                    digits.isEmpty() -> ""
                    digits.length <= 2 -> "(${digits}"
                    else -> "(${digits.take(2)}) ${digits.substring(2)}"
                }
                isFormatting = true
                s?.replace(0, s.length, formatted)
                isFormatting = false
                val cursorPosition = formatted.length
                binding.etPhone.setSelection(
                    cursorPosition.coerceAtMost(
                        binding.etPhone.text?.length ?: 0
                    )
                )

                viewModel.onPhoneChanged(formatted)
                if (binding.tilPhone.tag != null) {
                    fieldValidator.validatePhoneField(binding.tilPhone, formatted)
                }
                updateRegisterButtonState()
            }
        })

        // ---------- BOTÕES ----------
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

        // Ação do Card de Assinatura
        binding.btnSubAction.setOnClickListener {
            // Se for Free -> Comprar. Se Premium -> Manage.
            val currentPlan = getCurrentPlanType()
            if (currentPlan == PlanType.PLAN_LIFE) {
                findNavController().navigate(R.id.managePlanFragment)
            } else {
                findNavController().navigate(R.id.plansFragment)
            }
        }
    }

    // Helper simples para obter plano localmente, já que AuthRepository tem cache ou MainViewModel poderia ser usado.
    // Para simplificar e não injetar MainViewModel aqui, usaremos uma verificação básica se o usuário tem a role no futuro,
    // mas por agora vamos assumir verificação pelo estado da UI renderizada.
    // Na verdade, vamos consultar o Repository suspend function se possível, ou confiar no argumento do RegisterViewModel se tivesse.
    // Como RegisterViewModel não expõe Plano, faremos uma checagem rápida no renderUi.
    private var currentPlanCached: PlanType = PlanType.NONE
    private fun getCurrentPlanType(): PlanType = currentPlanCached

    private fun performLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_logout_title)
            .setMessage(R.string.dialog_logout_message)
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                authRepository.logout()
                requireActivity().invalidateOptionsMenu()
                val navOptions = NavOptions.Builder().setPopUpTo(R.id.homeFragment, true).build()
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
        if (state.isEditMode) {
            // ===== MODO EDIÇÃO =====
            // Reorganização Visual
            binding.ivAppLogo.isVisible = false
            binding.tvTitle.text = getString(R.string.register_edit_mode_title)
            binding.tvSubtitle.isVisible = false
            binding.tvSectionData.isVisible = true
            binding.btnLogout.isVisible = true
            binding.layoutBackToLogin.isVisible = false

            binding.btnRegister.text = getString(R.string.register_update_button_text)

            // 1. Configurar Card de Assinatura
            setupSubscriptionCard()

            val currentUser = authRepository.getCurrentUser()
            val isGoogleUser = currentUser?.provider == "google.com"

            // 2. Configurar visibilidade de campos baseada no provider
            if (isGoogleUser) {
                // Esconde todo container de senha
                binding.containerPasswordFields.isVisible = false
                // Mostra aviso Google
                binding.containerGoogleManaged.isVisible = true

                // Email read-only visualmente limpo
                binding.tilEmail.isEnabled = false
                binding.etEmail.isEnabled = false
                binding.btnChangeEmail.isVisible = false
            } else {
                binding.containerPasswordFields.isVisible = true
                binding.containerGoogleManaged.isVisible = false

                binding.tilEmail.isEnabled = false
                binding.etEmail.isEnabled = false
                binding.btnChangeEmail.isVisible = true
                binding.btnChangePassword.isVisible = true
            }

            // Preenchimento de dados
            if (binding.etName.text.toString() != state.name) binding.etName.setText(state.name)
            if (binding.etEmail.text.toString() != state.email) binding.etEmail.setText(state.email)
            if (binding.etPhone.text.toString() != state.phone) binding.etPhone.setText(state.phone)

        } else {
            // ===== MODO CADASTRO (INICIAL) =====
            binding.ivAppLogo.isVisible = true
            binding.sectionSubscription.isVisible = false // Esconde card assinatura
            binding.tvSectionData.isVisible = false

            binding.tvTitle.text = getString(R.string.register_title)
            binding.tvSubtitle.isVisible = true
            binding.tvSubtitle.text = getString(R.string.register_subtitle)
            binding.btnRegister.text = getString(R.string.register_button_text)
            binding.btnLogout.isVisible = false
            binding.layoutBackToLogin.isVisible = true

            // Campos visíveis e habilitados
            binding.containerPasswordFields.isVisible = true
            binding.containerGoogleManaged.isVisible = false

            binding.tilEmail.isEnabled = true
            binding.etEmail.isEnabled = true
            binding.btnChangeEmail.isVisible = false
            binding.btnChangePassword.isVisible = false

            binding.tilConfirmPassword.isVisible = true
        }

        binding.progressRegister.isVisible = state.isLoading
        updateRegisterButtonState(additionalLoadingFlag = state.isLoading)

        // Tratamento de erros e sucesso (mantido do original)
        state.errorCode?.let { errorCode ->
            val message = when (errorCode) {
                AuthErrorCode.EMAIL_ALREADY_IN_USE -> getString(R.string.error_register_email_in_use)
                AuthErrorCode.REGISTER_FAILED -> getString(R.string.error_register_generic)
                AuthErrorCode.EMAIL_VERIFICATION_FAILED -> getString(R.string.error_register_verification_failed)
                AuthErrorCode.UPDATE_PROFILE_FAILED -> getString(R.string.error_update_profile_failed)
                AuthErrorCode.USER_NOT_IDENTIFIED -> getString(R.string.error_user_not_identified)
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
            binding.root.postDelayed({ findNavController().navigateUp() }, 1500)
        }

        if (state.isRegistered && state.registeredUser != null && !hasNavigatedToHome) {
            hasNavigatedToHome = true
            if (state.verificationEmailSent) {
                SnackbarFragment.showSuccess(
                    binding.root,
                    getString(R.string.email_verification_sent, state.registeredUser.email)
                )
            }
            binding.root.postDelayed({ findNavController().navigateUp() }, 2500)
        }
    }

    private fun setupSubscriptionCard() {
        binding.sectionSubscription.isVisible = true

        // Verifica plano via repository de forma síncrona/rápida já que estamos na UI thread e precisamos decidir layout
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = authRepository.getCurrentUser()?.uid ?: return@launch
            val planResult = authRepository.getUserPlan(uid)
            val userPlan = planResult.getOrNull()

            // Lógica simples: se tem userPlan e type == LIFETIME -> Premium
            val isPremium = userPlan?.planType == PlanType.PLAN_LIFE
            currentPlanCached = if (isPremium) PlanType.PLAN_LIFE else PlanType.NONE

            val context = requireContext()
            if (isPremium) {
                // Estilo Premium
                binding.cardSubscriptionStatus.setCardBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        R.color.card_premium_bg
                    )
                )
                binding.ivSubIcon.setImageResource(R.drawable.ic_whatshot_24) // Usando ícone existente similar a workspace_premium
                binding.ivSubIcon.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(context, R.color.premium_gold))

                binding.tvSubTitle.text = getString(R.string.register_member_premium)
                binding.tvSubTitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.color_primary_dark
                    )
                )

                binding.btnSubAction.text = getString(R.string.register_btn_manage)
            } else {
                // Estilo Free
                binding.cardSubscriptionStatus.setCardBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        R.color.card_free_bg
                    )
                )
                binding.ivSubIcon.setImageResource(R.drawable.ic_recipe_lock_open_24) // Usando cadeado aberto
                binding.ivSubIcon.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        R.color.color_primary_base
                    )
                )

                binding.tvSubTitle.text = getString(R.string.register_member_free)
                binding.tvSubTitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.text_primary
                    )
                )

                binding.btnSubAction.text = getString(R.string.register_btn_upgrade)
            }
        }
    }

    private fun updateRegisterButtonState(additionalLoadingFlag: Boolean = false) {
        val currentState = viewModel.uiState.value
        val enabled = if (currentState.isEditMode) {
            val nameValid =
                FieldValidationRules.validateName(binding.etName.text?.toString().orEmpty()).isValid
            nameValid // Simplificado para edit mode, ja que email/senha nao mudam aqui
        } else {
            val name = binding.etName.text?.toString().orEmpty()
            val email = binding.etEmail.text?.toString().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            val confirm = binding.etConfirmPassword.text?.toString().orEmpty()

            val nameValid = FieldValidationRules.validateName(name).isValid
            val emailValid = FieldValidationRules.validateEmail(email).isValid
            val passwordValid = FieldValidationRules.validatePassword(password).isValid
            val confirmValid =
                FieldValidationRules.validateConfirmPassword(password, confirm).isValid

            nameValid && emailValid && passwordValid && confirmValid
        }

        val finalEnabled = enabled && !additionalLoadingFlag
        binding.btnRegister.isEnabled = finalEnabled
        binding.btnRegister.alpha = if (finalEnabled) 1f else 0.6f
    }

    override fun onDestroyView() {
        _binding?.let { binding -> SnackbarFragment.cancelPendingSnackbars(binding.root) }
        super.onDestroyView()
        _binding = null
    }
}