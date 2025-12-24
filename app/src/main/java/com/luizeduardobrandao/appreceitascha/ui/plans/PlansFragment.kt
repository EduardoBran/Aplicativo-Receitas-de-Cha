package com.luizeduardobrandao.appreceitascha.ui.plans

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.luizeduardobrandao.appreceitascha.databinding.FragmentPlansBinding
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingProductsIds
import com.luizeduardobrandao.appreceitascha.MainViewModel
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlansFragment : Fragment() {

    private var _binding: FragmentPlansBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlansViewModel by viewModels()

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlansBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeUiState()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    renderUi(uiState)
                }
            }
        }
    }

    private fun renderUi(uiState: PlansViewModel.PlansUiState) {
        // Loading geral (consulta de planos ou compra em andamento)
        binding.progressPlans.isVisible =
            uiState.isLoading || uiState.isPurchaseInProgress

        // Encontrar plano pelo ID
        val planLife = uiState.plans.firstOrNull { it.planId == BillingProductsIds.PLAN_LIFETIME }

        // VITALÍCIO
        binding.tvPlanLifeName.text =
            getString(com.luizeduardobrandao.appreceitascha.R.string.plans_plan_life_name)
        binding.tvPlanLifePrice.text = planLife?.formattedPrice?.takeIf { it.isNotBlank() }
            ?: getString(com.luizeduardobrandao.appreceitascha.R.string.plans_plan_life_price)
        binding.btnPlanLifeBuy.isEnabled = planLife != null && !uiState.isPurchaseInProgress
        binding.btnPlanLifeBuy.setOnClickListener {
            planLife?.let {
                viewModel.onBuyPlanClicked(it, requireActivity())
            }
        }

        // Mensagens
        uiState.errorMessage?.let {
            SnackbarFragment.showError(binding.root, it)
            viewModel.clearMessages()
        }

        uiState.successMessage?.let {
            SnackbarFragment.showSuccess(binding.root, it)

            // ✅ garante liberação imediata sem fechar o app
            mainViewModel.refreshAuthState()

            viewModel.clearMessages()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}