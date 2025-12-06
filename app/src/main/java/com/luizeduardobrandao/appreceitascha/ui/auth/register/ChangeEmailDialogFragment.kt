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
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.utils.KeyboardUtils
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ChangeEmailDialogFragment : DialogFragment() {

    private var _binding: DialogChangeEmailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChangeEmailViewModel by viewModels()
    private val fieldValidator = FieldValidator()

    // Exibir SnackbarFragment de sucesso após fechar alert dialog
    private var parentView: View? = null

    companion object {
        private const val KEY_PARENT_VIEW = "parent_view"

        fun newInstance(): ChangeEmailDialogFragment {
            return ChangeEmailDialogFragment()
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
        _binding = DialogChangeEmailBinding.inflate(inflater, container, false)
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

            val currentPassword = binding.etCurrentPassword.text?.toString().orEmpty()
            val newEmail = binding.etNewEmail.text?.toString().orEmpty()

            // Valida todos os campos no submit
            val passwordValid =
                fieldValidator.validatePasswordField(binding.tilCurrentPassword, currentPassword)
            val emailValid = fieldValidator.validateEmailField(binding.tilNewEmail, newEmail)

            if (passwordValid && emailValid) {
                viewModel.changeEmail(currentPassword, newEmail)
            } else {
                // Mostra Snackbar apenas no submit se houver erro
                if (!passwordValid) {
                    val messageFromValidator = binding.tilCurrentPassword.tag as? String
                    SnackbarFragment.showError(
                        binding.root,
                        messageFromValidator ?: getString(R.string.error_password_required)
                    )
                } else if (!emailValid) {
                    val messageFromValidator = binding.tilNewEmail.tag as? String
                    SnackbarFragment.showError(
                        binding.root,
                        messageFromValidator ?: getString(R.string.error_email_required)
                    )
                }
            }
        }
    }

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
                            getString(R.string.error_google_user_cannot_change_email)
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
                                getString(R.string.dialog_change_email_success)
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
        parentView = null
    }
}