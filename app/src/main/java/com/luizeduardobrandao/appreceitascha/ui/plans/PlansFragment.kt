package com.luizeduardobrandao.appreceitascha.ui.plans

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.appreceitascha.MainViewModel
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentPlansBinding
import com.luizeduardobrandao.appreceitascha.databinding.ItemBenefitCheckBinding // Se usar o include
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingPlan
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingProductsIds
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlansFragment : Fragment(R.layout.fragment_plans) {

    private var _binding: FragmentPlansBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PlansViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private var planLife: BillingPlan? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentPlansBinding.bind(view)

        setupBenefits()
        setupListeners()
        observeState()
    }

    private fun setupBenefits() {
        // Exemplo populando os includes se tiver criado o arquivo separado,
        // ou defina o texto diretamente no XML
        // Aqui assumindo que você ajustou o XML para ter TextViews com IDs
        // Caso use o include:
        ItemBenefitCheckBinding.bind(binding.benefit1.root).tvBenefit.text =
            getString(R.string.benefit_all_recipes)
        ItemBenefitCheckBinding.bind(binding.benefit2.root).tvBenefit.text =
            getString(R.string.benefit_favorites)
        ItemBenefitCheckBinding.bind(binding.benefit3.root).tvBenefit.text =
            getString(R.string.benefit_updates)
        ItemBenefitCheckBinding.bind(binding.benefit4.root).tvBenefit.text =
            getString(R.string.benefit_one_time)
    }

    private fun setupListeners() {
        binding.btnPlanLifeBuy.setOnClickListener {
            planLife?.let { plan ->
                viewModel.buyPlan(requireActivity(), plan)
            }
        }

        binding.btnPlansRestore.setOnClickListener {
            viewModel.restorePurchases()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressPlans.isVisible = state.isLoading || state.isPurchaseInProgress

                    val canInteract = !state.isLoading && !state.isPurchaseInProgress
                    binding.btnPlanLifeBuy.isEnabled = canInteract
                    binding.btnPlansRestore.isEnabled = canInteract

                    // Atualiza dados do produto
                    val plan =
                        state.plans.firstOrNull { it.planId == BillingProductsIds.PLAN_LIFETIME }
                    planLife = plan
                    binding.tvPrice.text =
                        plan?.formattedPrice ?: getString(R.string.plans_plan_life_price)

                    // Feedback de Erro
                    state.errorMessage?.let { msg ->
                        SnackbarFragment.showError(binding.root, msg)
                        viewModel.clearMessages()
                    }

                    // Feedback de Sucesso
                    state.successMessage?.let { msg ->
                        SnackbarFragment.showSuccess(binding.root, msg)
                        viewModel.clearMessages()
                        mainViewModel.refreshAuthState()
                        // Opcional: Navegar para Home ou ManagePlan
                        findNavController().navigateUp()
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