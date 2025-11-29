package com.luizeduardobrandao.appreceitascha.ui.auth.resetpassword

import android.content.Context
import android.os.Bundle
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
import com.luizeduardobrandao.appreceitascha.databinding.FragmentResetPasswordBinding
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
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
        setupToolbar()
        setupListeners()
        observeUiState()
    }

    private fun setupToolbar() {
        // Clique no item de menu "Home" -> voltar para Login limpando a back stack
        binding.toolbarReset.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_go_home_login -> {
                    val navOptions = NavOptions.Builder()
                        .setPopUpTo(R.id.loginFragment, true)
                        .build()
                    findNavController().navigate(R.id.loginFragment, null, navOptions)
                    true
                }
                else -> false
            }
        }

        // Deixa o ícone do item "Home" branco programaticamente
        val homeItem = binding.toolbarReset.menu.findItem(R.id.menu_go_home_login)
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.text_on_primary)

        homeItem?.icon?.let { originalDrawable ->
            val wrapped = DrawableCompat.wrap(originalDrawable).mutate()
            DrawableCompat.setTint(wrapped, whiteColor)
            homeItem.icon = wrapped
        }
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
            hideKeyboard()
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
                        // Para simplificar: erro comum é "e-mail não existe"
                        SnackbarFragment.showError(
                            binding.root,
                            getString(R.string.snackbar_error_email_not_found)
                        )
                        viewModel.clearErrorMessage()
                    }

                    if (state.isSuccess) {
                        SnackbarFragment.showSuccess(
                            binding.root,
                            getString(R.string.snackbar_success_reset_email_sent)
                        )
                    }
                }
            }
        }
    }

    private fun updateSendButtonState(additionalLoadingFlag: Boolean = false) {
        val email = binding.etEmail.text?.toString().orEmpty()
        val emailValid = FieldValidationRules.validateEmail(email).isValid

        val enabled = emailValid && !additionalLoadingFlag
        binding.btnSend.isEnabled = enabled
        binding.btnSend.alpha = if (enabled) 1f else 0.6f
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}