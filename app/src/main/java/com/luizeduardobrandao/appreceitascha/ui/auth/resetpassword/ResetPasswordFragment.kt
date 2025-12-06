package com.luizeduardobrandao.appreceitascha.ui.auth.resetpassword

import android.os.Bundle
import android.view.*
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentResetPasswordBinding
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.utils.KeyboardUtils
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidationRules
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResetPasswordViewModel by viewModels()

    private val fieldValidator = FieldValidator()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupListeners()
        observeUiState()
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

            updateSendButtonState()
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

        binding.btnSend.setOnClickListener {
            // Fecha o teclado antes de enviar
            KeyboardUtils.hideKeyboard(this@ResetPasswordFragment)
            viewModel.submitResetPassword()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressReset.isVisible = state.isLoading
                    updateSendButtonState(additionalLoadingFlag = state.isLoading)

                    if (state.errorMessage != null) {
                        SnackbarFragment.showError(
                            binding.root,
                            getString(R.string.snackbar_error_email_not_found)
                        )
                        viewModel.clearErrorMessage()
                    }

                    if (state.isSuccess) {
                        SnackbarFragment.showWarning(
                            binding.root,
                            getString(R.string.snackbar_success_reset_email_sent)
                        )

                        binding.root.postDelayed({
                            navigateToLogin()
                        }, 2500)
                    }
                }
            }
        }
    }

    private fun navigateToLogin() {
        findNavController().navigateUp()
    }

    private fun updateSendButtonState(additionalLoadingFlag: Boolean = false) {
        val email = binding.etEmail.text?.toString().orEmpty()
        val emailValid = FieldValidationRules.validateEmail(email).isValid

        val enabled = emailValid && !additionalLoadingFlag
        binding.btnSend.isEnabled = enabled
        binding.btnSend.alpha = if (enabled) 1f else 0.6f
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}