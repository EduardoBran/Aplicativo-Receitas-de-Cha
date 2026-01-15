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
import com.luizeduardobrandao.appreceitascha.databinding.DialogChangeEmailBinding
import com.luizeduardobrandao.appreceitascha.ui.auth.AuthErrorCode
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.utils.KeyboardUtils
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * DialogFragment responsável pela alteração de e-mail do usuário.
 *
 * Responsabilidades:
 * - Validação de campos em tempo real
 * - Tradução de [AuthErrorCode] para mensagens localizadas
 * - Exibição de loading e mensagens de erro/sucesso
 * - Bloqueio de alteração para usuários Google
 *
 * IMPORTANTE: Após sucesso, o Firebase envia um e-mail de verificação.
 * O e-mail só será atualizado após o usuário clicar no link recebido.
 *
 * O diálogo fecha automaticamente após sucesso ou após 2s se for usuário Google.
 */
@AndroidEntryPoint
class ChangeEmailDialogFragment : DialogFragment() {

    private var _binding: DialogChangeEmailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChangeEmailViewModel by viewModels()
    private val fieldValidator = FieldValidator()

    /**
     * View pai para exibir Snackbar de sucesso após fechar o diálogo.
     * Deve ser definida antes de show() via setParentView().
     */
    private var parentView: View? = null

    companion object {
        fun newInstance(): ChangeEmailDialogFragment {
            return ChangeEmailDialogFragment()
        }
    }

    /**
     * Define a view pai onde será exibido o Snackbar de sucesso.
     * Deve ser chamado antes de show().
     *
     * Exemplo de uso:
     * ```
     * val dialog = ChangeEmailDialogFragment.newInstance()
     * dialog.setParentView(binding.root)
     * dialog.show(parentFragmentManager, "ChangeEmailDialog")
     * ```
     */
    fun setParentView(view: View) {
        parentView = view
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogChangeEmailBinding.inflate(inflater, container, false)
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

        // ---------- NOVO E-MAIL ----------
        binding.etNewEmail.doAfterTextChanged {
            val value = it?.toString().orEmpty()

            if (value.isBlank()) {
                binding.tilNewEmail.tag = null
                fieldValidator.validateEmailField(binding.tilNewEmail, value)
            } else if (binding.tilNewEmail.tag != null) {
                fieldValidator.validateEmailField(binding.tilNewEmail, value)
            }

            updateConfirmButtonState()
        }

        binding.etNewEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val value = binding.etNewEmail.text?.toString().orEmpty()
                if (value.isNotBlank()) {
                    fieldValidator.validateEmailField(binding.tilNewEmail, value)
                }
            }
        }

        // ---------- BOTÕES ----------
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            KeyboardUtils.hideKeyboard(this@ChangeEmailDialogFragment)
            handleConfirmClick()
        }
    }

    /**
     * Valida todos os campos e submete a alteração se estiverem válidos.
     */
    private fun handleConfirmClick() {
        val currentPassword = binding.etCurrentPassword.text?.toString().orEmpty()
        val newEmail = binding.etNewEmail.text?.toString().orEmpty()

        // Valida todos os campos no submit
        val passwordValid =
            fieldValidator.validatePasswordField(binding.tilCurrentPassword, currentPassword)
        val emailValid = fieldValidator.validateEmailField(binding.tilNewEmail, newEmail)

        if (passwordValid && emailValid) {
            viewModel.changeEmail(currentPassword, newEmail)
        } else {
            // Exibe primeiro erro encontrado
            showFirstValidationError(passwordValid, emailValid)
        }
    }

    /**
     * Exibe Snackbar com o primeiro erro de validação encontrado.
     */
    private fun showFirstValidationError(passwordValid: Boolean, emailValid: Boolean) {
        when {
            !passwordValid -> {
                val messageFromValidator = binding.tilCurrentPassword.tag as? String
                SnackbarFragment.showError(
                    binding.root,
                    messageFromValidator ?: getString(R.string.error_password_required)
                )
            }

            !emailValid -> {
                val messageFromValidator = binding.tilNewEmail.tag as? String
                SnackbarFragment.showError(
                    binding.root,
                    messageFromValidator ?: getString(R.string.error_email_required)
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
        val newEmail = binding.etNewEmail.text?.toString().orEmpty()

        val passwordValid = currentPassword.isNotBlank() && currentPassword.length >= 6
        val emailValid =
            newEmail.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()

        val enabled = passwordValid && emailValid && !additionalLoadingFlag

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
    private fun handleGoogleUserRestriction(state: ChangeEmailUiState) {
        if (state.isGoogleUser) {
            binding.progressDialog.isVisible = false
            binding.btnConfirm.isEnabled = false
            binding.btnCancel.isEnabled = true

            SnackbarFragment.showError(
                binding.root,
                getString(R.string.erro_google_user_alterar_email)
            )

            binding.root.postDelayed({
                dismiss()
            }, 2000)
        }
    }

    /**
     * Atualiza estado visual de loading.
     */
    private fun updateLoadingState(state: ChangeEmailUiState) {
        if (state.isGoogleUser) return

        binding.progressDialog.isVisible = state.isLoading
        updateConfirmButtonState(additionalLoadingFlag = state.isLoading)
        binding.btnCancel.isEnabled = !state.isLoading
    }

    /**
     * Traduz AuthErrorCode para mensagem localizada e exibe.
     */
    private fun handleError(state: ChangeEmailUiState) {
        state.errorCode?.let { errorCode ->
            val message = translateErrorCode(errorCode)
            SnackbarFragment.showError(binding.root, message)
            viewModel.clearError()
        }
    }

    /**
     * Traduz código de erro tipado para mensagem localizada.
     *
     * Centraliza todas as traduções de AuthErrorCode para strings.xml,
     * garantindo consistência e facilitando internacionalização.
     */
    private fun translateErrorCode(errorCode: AuthErrorCode): String {
        return when (errorCode) {
            AuthErrorCode.GOOGLE_USER_CANNOT_CHANGE_EMAIL ->
                getString(R.string.erro_google_user_alterar_email)

            AuthErrorCode.CHANGE_EMAIL_INCORRECT_PASSWORD ->
                getString(R.string.erro_senha_incorreta_reauth)

            AuthErrorCode.CHANGE_EMAIL_USER_MISMATCH ->
                getString(R.string.erro_operacao_indisponivel_conta)

            AuthErrorCode.CHANGE_EMAIL_ALREADY_IN_USE ->
                getString(R.string.erro_email_ja_em_uso)

            AuthErrorCode.CHANGE_EMAIL_INVALID ->
                getString(R.string.erro_email_invalido_update)

            AuthErrorCode.CHANGE_EMAIL_REQUIRES_RECENT_LOGIN ->
                getString(R.string.erro_requer_login_recente_email)

            AuthErrorCode.CHANGE_EMAIL_GENERIC ->
                getString(R.string.erro_alterar_email_generico)

            else -> getString(R.string.erro_alterar_email_generico)
        }
    }

    /**
     * Exibe mensagem de sucesso na view pai e fecha o diálogo.
     *
     * IMPORTANTE: Informa ao usuário que um e-mail de verificação foi enviado.
     */
    private fun handleSuccess(state: ChangeEmailUiState) {
        if (state.isSuccess) {
            parentView?.let { view ->
                SnackbarFragment.showSuccess(
                    view,
                    getString(R.string.dialog_change_email_success)
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