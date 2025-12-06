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
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.utils.KeyboardUtils
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidationRules
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChangePasswordDialogFragment : DialogFragment() {

    private var _binding: DialogChangePasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChangePasswordViewModel by viewModels()
    private val fieldValidator = FieldValidator()

    // Exibir SnackbarFragment de sucesso após fechar alert dialog
    private var parentView: View? = null

    companion object {
        fun newInstance(): ChangePasswordDialogFragment {
            return ChangePasswordDialogFragment()
        }
    }

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

            val currentPassword = binding.etCurrentPassword.text?.toString().orEmpty()
            val newPassword = binding.etNewPassword.text?.toString().orEmpty()
            val confirmPassword = binding.etConfirmNewPassword.text?.toString().orEmpty()

            // Valida todos os campos no submit
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
                // Mostra Snackbar apenas no submit se houver erro
                if (!currentPasswordValid) {
                    val messageFromValidator = binding.tilCurrentPassword.tag as? String
                    SnackbarFragment.showError(
                        binding.root,
                        messageFromValidator ?: getString(R.string.error_password_required)
                    )
                } else if (!newPasswordValid) {
                    val messageFromValidator = binding.tilNewPassword.tag as? String
                    SnackbarFragment.showError(
                        binding.root,
                        messageFromValidator ?: getString(R.string.error_password_required)
                    )
                } else if (!confirmPasswordValid) {
                    val messageFromValidator = binding.tilConfirmNewPassword.tag as? String
                    SnackbarFragment.showError(
                        binding.root,
                        messageFromValidator ?: getString(R.string.error_confirm_password_required)
                    )
                }
            }
        }
    }

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

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.isGoogleUser) {
                        binding.progressDialog.isVisible = false
                        binding.btnConfirm.isEnabled = false
                        binding.btnCancel.isEnabled = true

                        SnackbarFragment.showError(
                            binding.root,
                            getString(R.string.error_google_user_cannot_change_password)
                        )

                        binding.root.postDelayed({
                            dismiss()
                        }, 2000)
                        return@collect
                    }

                    binding.progressDialog.isVisible = state.isLoading
                    updateConfirmButtonState(additionalLoadingFlag = state.isLoading)
                    binding.btnCancel.isEnabled = !state.isLoading

                    if (state.errorMessage != null) {
                        SnackbarFragment.showError(binding.root, state.errorMessage)
                        viewModel.clearError()
                    }

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
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}