package com.luizeduardobrandao.appreceitascha.ui.auth.resetpassword

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
import com.luizeduardobrandao.appreceitascha.databinding.FragmentResetPasswordBinding
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.validation.FieldValidator
import com.luizeduardobrandao.appreceitascha.ui.common.view.RequiredIconManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ResetPasswordFragment : Fragment() {

    private var _binding: FragmentResetPasswordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ResetPasswordViewModel by viewModels()

    private val fieldValidator = FieldValidator()
    private lateinit var requiredIconManager: RequiredIconManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResetPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        requiredIconManager = RequiredIconManager(requireContext())
        setupToolbar()
        setupRequiredIcons()
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

    private fun setupRequiredIcons() {
        requiredIconManager.setRequiredIconVisible(binding.etEmail, true)
    }

    private fun setupListeners() {
        binding.etEmail.doAfterTextChanged {
            val value = it?.toString().orEmpty()
            viewModel.onEmailChanged(value)
            if (binding.tilEmail.isErrorEnabled) {
                fieldValidator.validateEmailField(binding.tilEmail, value)
            }
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

        binding.btnSend.setOnClickListener {
            viewModel.submitResetPassword()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressReset.isVisible = state.isLoading

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
