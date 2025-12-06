package com.luizeduardobrandao.appreceitascha.ui.plans

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.luizeduardobrandao.appreceitascha.databinding.FragmentPlansBinding
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingProductsIds
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlansFragment : Fragment() {

    private var _binding: FragmentPlansBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlansViewModel by viewModels()

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

        // Encontrar cada plano pelo ID
        val plan3m = uiState.plans.firstOrNull { it.planId == BillingProductsIds.PLAN_3_MONTHS }
        val plan6m = uiState.plans.firstOrNull { it.planId == BillingProductsIds.PLAN_6_MONTHS }
        val plan12m = uiState.plans.firstOrNull { it.planId == BillingProductsIds.PLAN_12_MONTHS }
        val planLife = uiState.plans.firstOrNull { it.planId == BillingProductsIds.PLAN_LIFETIME }

        // 3 MESES
        binding.tvPlan3mName.text =
            getString(com.luizeduardobrandao.appreceitascha.R.string.plans_plan_3m_name)
        binding.tvPlan3mPrice.text = plan3m?.formattedPrice?.takeIf { it.isNotBlank() }
            ?: getString(com.luizeduardobrandao.appreceitascha.R.string.plans_plan_3m_price)
        binding.btnPlan3mBuy.isEnabled = plan3m != null && !uiState.isPurchaseInProgress
        binding.btnPlan3mBuy.setOnClickListener {
            plan3m?.let {
                viewModel.onBuyPlanClicked(it, requireActivity())
            }
        }

        // 6 MESES
        binding.tvPlan6mName.text =
            getString(com.luizeduardobrandao.appreceitascha.R.string.plans_plan_6m_name)
        binding.tvPlan6mPrice.text = plan6m?.formattedPrice?.takeIf { it.isNotBlank() }
            ?: getString(com.luizeduardobrandao.appreceitascha.R.string.plans_plan_6m_price)
        binding.btnPlan6mBuy.isEnabled = plan6m != null && !uiState.isPurchaseInProgress
        binding.btnPlan6mBuy.setOnClickListener {
            plan6m?.let {
                viewModel.onBuyPlanClicked(it, requireActivity())
            }
        }

        // 12 MESES
        binding.tvPlan12mName.text =
            getString(com.luizeduardobrandao.appreceitascha.R.string.plans_plan_12m_name)
        binding.tvPlan12mPrice.text = plan12m?.formattedPrice?.takeIf { it.isNotBlank() }
            ?: getString(com.luizeduardobrandao.appreceitascha.R.string.plans_plan_12m_price)
        binding.btnPlan12mBuy.isEnabled = plan12m != null && !uiState.isPurchaseInProgress
        binding.btnPlan12mBuy.setOnClickListener {
            plan12m?.let {
                viewModel.onBuyPlanClicked(it, requireActivity())
            }
        }

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
            viewModel.clearMessages()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}