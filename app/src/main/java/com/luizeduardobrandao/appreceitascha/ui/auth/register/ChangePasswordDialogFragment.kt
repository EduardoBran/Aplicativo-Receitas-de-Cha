package com.luizeduardobrandao.appreceitascha.ui.auth.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.DialogChangePasswordBinding
import com.luizeduardobrandao.appreceitascha.ui.auth.AuthErrorCode
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.utils.KeyboardUtils
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidationRules
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * DialogFragment responsável pela alteração de senha do usuário.
 *
 * Responsabilidades:
 * - Validação de campos em tempo real
 * - Tradução de [AuthErrorCode] para mensagens localizadas
 * - Exibição de loading e mensagens de erro/sucesso
 * - Bloqueio de alteração para usuários Google
 *
 * O diálogo fecha automaticamente após sucesso ou após 2s se for usuário Google.
 */
@AndroidEntryPoint
class ChangePasswordDialogFragment : DialogFragment() {

    private var _binding: DialogChangePasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChangePasswordViewModel by viewModels()
    private val fieldValidator = FieldValidator()

    /**
     * View pai para exibir Snackbar de sucesso após fechar o diálogo.
     * Deve ser definida antes de show() via setParentView().
     */
    private var parentView: View? = null

    companion object {
        fun newInstance(): ChangePasswordDialogFragment {
            return ChangePasswordDialogFragment()
        }
    }

    /**
     * Define a view pai onde será exibido o Snackbar de sucesso.
     * Deve ser chamado antes de show().
     */
    fun setParentView(view: View) {
        parentView = view
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogChangePasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeState()
    }

    /**
     * Configura listeners para campos de texto e botões.
     * Implementa validação em tempo real apenas após primeira interação.
     */
    private fun setupListeners() {
        // ---------- SENHA ATUAL ----------
        binding.etCurrentPassword.doAfterTextChanged {
            val value = it?.toString().orEmpty()

            if (value.isBlank()) {
                binding.tilCurrentPassword.tag = null
                fieldValidator.validatePasswordField(binding.tilCurrentPassword, value)
            } else if (binding.tilCurrentPassword.tag != null) {
                fieldValidator.validatePasswordField(binding.tilCurrentPassword, value)
            }

            updateConfirmButtonState()
        }

        binding.etCurrentPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.etCurrentPassword.text?.toString().orEmpty()
                if (value.isNotBlank()) {
                    fieldValidator.validatePasswordField(binding.tilCurrentPassword, value)
                }
            }
        }

        // ---------- NOVA SENHA ----------
        binding.etNewPassword.doAfterTextChanged {
            val value = it?.toString().orEmpty()

            if (value.isBlank()) {
                binding.tilNewPassword.tag = null
                fieldValidator.validatePasswordField(binding.tilNewPassword, value)
            } else if (binding.tilNewPassword.tag != null) {
                fieldValidator.validatePasswordField(binding.tilNewPassword, value)
            }

            updateConfirmButtonState()
        }

        binding.etNewPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.etNewPassword.text?.toString().orEmpty()
                if (value.isNotBlank()) {
                    fieldValidator.validatePasswordField(binding.tilNewPassword, value)
                }
            }
        }

        // ---------- CONFIRMAR NOVA SENHA ----------
        binding.etConfirmNewPassword.doAfterTextChanged {
            val confirm = it?.toString().orEmpty()
            val password = binding.etNewPassword.text?.toString().orEmpty()

            if (confirm.isBlank()) {
                binding.tilConfirmNewPassword.tag = null
                fieldValidator.validateConfirmPasswordField(
                    binding.tilConfirmNewPassword,
                    password,
                    confirm
                )
            } else if (binding.tilConfirmNewPassword.tag != null) {
                fieldValidator.validateConfirmPasswordField(
                    binding.tilConfirmNewPassword,
                    password,
                    confirm
                )
            }

            updateConfirmButtonState()
        }

        binding.etConfirmNewPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val password = binding.etNewPassword.text?.toString().orEmpty()
                val confirm = binding.etConfirmNewPassword.text?.toString().orEmpty()

                if (confirm.isNotBlank()) {
                    fieldValidator.validateConfirmPasswordField(
                        binding.tilConfirmNewPassword,
                        password,
                        confirm
                    )
                }
            }
        }

        // ---------- BOTÕES ----------
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            KeyboardUtils.hideKeyboard(this@ChangePasswordDialogFragment)
            handleConfirmClick()
        }
    }

    /**
     * Valida todos os campos e submete a alteração se estiverem válidos.
     */
    private fun handleConfirmClick() {
        val currentPassword = binding.etCurrentPassword.text?.toString().orEmpty()
        val newPassword = binding.etNewPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmNewPassword.text?.toString().orEmpty()

        // Valida todos os campos
        val currentPasswordValid =
            fieldValidator.validatePasswordField(binding.tilCurrentPassword, currentPassword)
        val newPasswordValid =
            fieldValidator.validatePasswordField(binding.tilNewPassword, newPassword)
        val confirmPasswordValid = fieldValidator.validateConfirmPasswordField(
            binding.tilConfirmNewPassword,
            newPassword,
            confirmPassword
        )

        if (currentPasswordValid && newPasswordValid && confirmPasswordValid) {
            viewModel.changePassword(currentPassword, newPassword)
        } else {
            // Exibe primeiro erro encontrado
            showFirstValidationError(
                currentPasswordValid,
                newPasswordValid,
                confirmPasswordValid
            )
        }
    }

    /**
     * Exibe Snackbar com o primeiro erro de validação encontrado.
     */
    private fun showFirstValidationError(
        currentPasswordValid: Boolean,
        newPasswordValid: Boolean,
        confirmPasswordValid: Boolean
    ) {
        when {
            !currentPasswordValid -> {
                val messageFromValidator = binding.tilCurrentPassword.tag as? String
                SnackbarFragment.showError(
                    binding.root,
                    messageFromValidator ?: getString(R.string.error_password_required)
                )
            }

            !newPasswordValid -> {
                val messageFromValidator = binding.tilNewPassword.tag as? String
                SnackbarFragment.showError(
                    binding.root,
                    messageFromValidator ?: getString(R.string.error_password_required)
                )
            }

            !confirmPasswordValid -> {
                val messageFromValidator = binding.tilConfirmNewPassword.tag as? String
                SnackbarFragment.showError(
                    binding.root,
                    messageFromValidator ?: getString(R.string.error_confirm_password_required)
                )
            }
        }
    }

    /**
     * Atualiza estado do botão Confirmar baseado na validação dos campos.
     *
     * @param additionalLoadingFlag Flag adicional para desabilitar durante loading
     */
    private fun updateConfirmButtonState(additionalLoadingFlag: Boolean = false) {
        val currentPassword = binding.etCurrentPassword.text?.toString().orEmpty()
        val newPassword = binding.etNewPassword.text?.toString().orEmpty()
        val confirmPassword = binding.etConfirmNewPassword.text?.toString().orEmpty()

        val currentPasswordValid = FieldValidationRules.validatePassword(currentPassword).isValid
        val newPasswordValid = FieldValidationRules.validatePassword(newPassword).isValid
        val confirmPasswordValid =
            FieldValidationRules.validateConfirmPassword(newPassword, confirmPassword).isValid

        val enabled =
            currentPasswordValid && newPasswordValid && confirmPasswordValid && !additionalLoadingFlag

        binding.btnConfirm.isEnabled = enabled
        binding.btnConfirm.alpha = if (enabled) 1f else 0.6f
    }

    /**
     * Observa mudanças no estado do ViewModel e atualiza a UI.
     *
     * Responsável por:
     * - Bloquear operação para usuários Google
     * - Exibir loading
     * - Traduzir AuthErrorCode para mensagens localizadas
     * - Exibir mensagem de sucesso e fechar diálogo
     */
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    handleGoogleUserRestriction(state)
                    updateLoadingState(state)
                    handleError(state)
                    handleSuccess(state)
                }
            }
        }
    }

    /**
     * Bloqueia operação e fecha diálogo após 2s se for usuário Google.
     */
    private fun handleGoogleUserRestriction(state: ChangePasswordUiState) {
        if (state.isGoogleUser) {
            binding.progressDialog.isVisible = false
            binding.btnConfirm.isEnabled = false
            binding.btnCancel.isEnabled = true

            SnackbarFragment.showError(
                binding.root,
                getString(R.string.erro_google_user_alterar_senha)
            )

            binding.root.postDelayed({
                dismiss()
            }, 2000)
        }
    }

    /**
     * Atualiza estado visual de loading.
     */
    private fun updateLoadingState(state: ChangePasswordUiState) {
        if (state.isGoogleUser) return

        binding.progressDialog.isVisible = state.isLoading
        updateConfirmButtonState(additionalLoadingFlag = state.isLoading)
        binding.btnCancel.isEnabled = !state.isLoading
    }

    /**
     * Traduz AuthErrorCode para mensagem localizada e exibe.
     */
    private fun handleError(state: ChangePasswordUiState) {
        state.errorCode?.let { errorCode ->
            val message = translateErrorCode(errorCode)
            SnackbarFragment.showError(binding.root, message)
            viewModel.clearError()
        }
    }

    /**
     * Traduz código de erro tipado para mensagem localizada.
     */
    private fun translateErrorCode(errorCode: AuthErrorCode): String {
        return when (errorCode) {
            AuthErrorCode.GOOGLE_USER_CANNOT_CHANGE_PASSWORD ->
                getString(R.string.erro_google_user_alterar_senha)

            AuthErrorCode.CHANGE_PASSWORD_SAME_PASSWORD ->
                getString(R.string.erro_senha_igual_atual)

            AuthErrorCode.CHANGE_PASSWORD_INCORRECT_PASSWORD ->
                getString(R.string.erro_senha_incorreta_reauth)

            AuthErrorCode.CHANGE_PASSWORD_USER_MISMATCH ->
                getString(R.string.erro_operacao_indisponivel_conta)

            AuthErrorCode.CHANGE_PASSWORD_WEAK ->
                getString(R.string.erro_senha_fraca_update)

            AuthErrorCode.CHANGE_PASSWORD_REQUIRES_RECENT_LOGIN ->
                getString(R.string.erro_requer_login_recente_senha)

            AuthErrorCode.CHANGE_PASSWORD_GENERIC ->
                getString(R.string.erro_alterar_senha_generico)

            else -> getString(R.string.erro_alterar_senha_generico)
        }
    }

    /**
     * Exibe mensagem de sucesso na view pai e fecha o diálogo.
     */
    private fun handleSuccess(state: ChangePasswordUiState) {
        if (state.isSuccess) {
            parentView?.let { view ->
                SnackbarFragment.showSuccess(
                    view,
                    getString(R.string.dialog_change_password_success)
                )
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        parentView = null
    }
}