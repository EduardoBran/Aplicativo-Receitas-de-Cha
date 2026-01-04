package com.luizeduardobrandao.appreceitascha.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentHomeBinding
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.common.animation.LottieLoadingController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // ViewModel local para dados específicos da Home
    private val homeViewModel: HomeViewModel by viewModels()

    // Controller para animação de loading com Lottie (Lógica recuperada)
    private lateinit var lottieController: LottieLoadingController

    // Controle de tempo mínimo da animação de loading (Lógica recuperada)
    private var isShowingLoadingOverlay: Boolean = false
    private var loadingStartTime: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        // Instancia o controlador de loading com Lottie centralizado
        lottieController = LottieLoadingController(
            overlay = binding.homeLoadingOverlay,
            lottieView = binding.lottieHome
        )

        setupListeners()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        // Garante que os dados estejam frescos ao voltar
        homeViewModel.refreshSession() // Ajustado para refreshData() conforme o padrão do HomeViewModel
    }

    private fun setupListeners() {
        // --- CARTÃO 1: VISITANTE (Guest) ---
        binding.btnGuestLogin.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
        }
        binding.btnGuestSeeRecipes.setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }

        // --- CARTÃO 2: LOGADO / GRATUITO (Free) ---
        binding.btnFreeBuyPlan.setOnClickListener {
            findNavController().navigate(R.id.plansFragment)
        }
        binding.btnFreeSeeRecipes.setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }

        // --- CARTÃO 3: PREMIUM (Lifetime) ---
        binding.btnPremiumSeeRecipes.setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }
        binding.btnPremiumPlanDetails.setOnClickListener {
            findNavController().navigate(R.id.managePlanFragment)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                homeViewModel.uiState.collect { state ->
                    handleLoadingState(state)
                }
            }
        }
    }

    /**
     * Gerencia a lógica de tempo mínimo do Lottie para evitar "flickering" na tela.
     */
    private fun handleLoadingState(state: HomeUiState) {
        // Se a view já foi destruída, não faz nada
        if (_binding == null) return

        if (state.isLoading) {
            // Início de um novo ciclo de loading
            if (!isShowingLoadingOverlay) {
                isShowingLoadingOverlay = true
                loadingStartTime = System.currentTimeMillis()
                lottieController.showLoading()
            }
            // Esconde tudo enquanto carrega
            hideAllCards()
            return
        }

        // Aqui, state.isLoading == false.
        // Verifica se o tempo mínimo foi atingido.
        if (isShowingLoadingOverlay) {
            val elapsed = System.currentTimeMillis() - loadingStartTime
            if (elapsed < MIN_HOME_LOADING_LOTTIE_MS) {
                // Ainda não atingiu o tempo mínimo: mantém overlay e cards ocultos e agenda o fim.
                hideAllCards()

                val delay = MIN_HOME_LOADING_LOTTIE_MS - elapsed

                binding.root.postDelayed({
                    if (!isAdded) return@postDelayed
                    if (_binding == null) return@postDelayed
                    // Verifica se ainda devemos estar mostrando (o estado pode ter mudado novamente)
                    if (!isShowingLoadingOverlay) return@postDelayed

                    finishLoadingAndRender(state)
                }, delay)
                return
            }
        }

        // Se não estava carregando ou já passou do tempo, renderiza direto.
        finishLoadingAndRender(state)
    }

    /**
     * Finaliza a animação e exibe os dados reais.
     */
    private fun finishLoadingAndRender(state: HomeUiState) {
        isShowingLoadingOverlay = false
        lottieController.hide()

        // 1. Tratamento de Erro
        state.errorMessage?.let { msg ->
            SnackbarFragment.showError(binding.root, msg)
        }

        // 2. Controle de Visibilidade dos Cards
        if (state.sessionState != null) {
            updateCardsVisibility(
                state.sessionState.authState,
                state.sessionState.planState
            )
        }
    }

    private fun hideAllCards() {
        binding.groupGuest.isVisible = false
        binding.groupFree.isVisible = false
        binding.groupPremium.isVisible = false
    }

    private fun updateCardsVisibility(authState: AuthState, planState: PlanState) {
        // Lógica de decisão
        val isGuest = authState == AuthState.NAO_LOGADO
        val isFree = authState == AuthState.LOGADO && planState == PlanState.SEM_PLANO
        val isPremium = authState == AuthState.LOGADO && planState == PlanState.COM_PLANO

        // Aplicação na UI
        binding.groupGuest.isVisible = isGuest
        binding.groupFree.isVisible = isFree
        binding.groupPremium.isVisible = isPremium
    }

    override fun onDestroyView() {
        if (::lottieController.isInitialized) {
            lottieController.clear()
        }
        _binding = null
        super.onDestroyView()
    }

    companion object {
        /** Tempo mínimo em ms que o Lottie deve permanecer visível na Home. */
        private const val MIN_HOME_LOADING_LOTTIE_MS: Long = 1000L
    }
}