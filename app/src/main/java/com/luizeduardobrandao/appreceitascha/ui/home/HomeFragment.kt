package com.luizeduardobrandao.appreceitascha.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
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

    private val homeViewModel: HomeViewModel by viewModels()
    private lateinit var lottieController: LottieLoadingController

    private var isShowingLoadingOverlay: Boolean = false
    private var loadingStartTime: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        lottieController = LottieLoadingController(
            overlay = binding.homeLoadingOverlay,
            lottieView = binding.lottieHome
        )

        setupListeners()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.refreshSession()
    }

    private fun setupListeners() {
        binding.viewSearchBar.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_searchFragment)
        }

        binding.btnBannerAction.setOnClickListener {
            val state = homeViewModel.uiState.value.sessionState
            val authState = state?.authState ?: AuthState.NAO_LOGADO
            val planState = state?.planState ?: PlanState.SEM_PLANO

            when (authState) {
                AuthState.NAO_LOGADO -> {
                    findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
                }

                AuthState.LOGADO if planState == PlanState.SEM_PLANO -> {
                    findNavController().navigate(R.id.plansFragment)
                }

                else -> {
                    findNavController().navigate(R.id.categoriesFragment)
                }
            }
        }

        // --- ✅ CORREÇÃO 2: Navegação do Ícone de Perfil ---
        binding.ivProfileAvatar.setOnClickListener {
            val state = homeViewModel.uiState.value.sessionState

            if (state?.authState == AuthState.NAO_LOGADO) {
                // Visitante -> Vai para Login
                findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
            } else {
                // Logado (Free ou Premium) -> Vai para Perfil (Modo Edição)
                val args = Bundle().apply {
                    putBoolean("isEditMode", true)
                }
                // Navega para o RegisterFragment com argumentos
                findNavController().navigate(R.id.registerFragment, args)
            }
        }

        // Navegações de categoria (mantidas igual ao seu código anterior)
        binding.btnCatCalm.setOnClickListener { navigateToCategory("calmante", "Chás Calmantes") }
        binding.btnCatEnergy.setOnClickListener {
            navigateToCategory(
                "energizante",
                "Energia e Foco"
            )
        }
        binding.btnCatDetox.setOnClickListener {
            navigateToCategory(
                "digestivo",
                "Detox e Digestão"
            )
        }
        binding.btnCatMore.setOnClickListener { findNavController().navigate(R.id.categoriesFragment) }
    }

    private fun navigateToCategory(id: String, name: String) {
        val action = HomeFragmentDirections.actionHomeFragmentToRecipeListFragment(
            categoryId = id,
            categoryName = name
        )
        findNavController().navigate(action)
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

    private fun handleLoadingState(state: HomeUiState) {
        if (_binding == null) return

        if (state.isLoading) {
            if (!isShowingLoadingOverlay) {
                isShowingLoadingOverlay = true
                loadingStartTime = System.currentTimeMillis()
                lottieController.showLoading()
            }
            return
        }

        if (isShowingLoadingOverlay) {
            val elapsed = System.currentTimeMillis() - loadingStartTime
            if (elapsed < MIN_HOME_LOADING_LOTTIE_MS) {
                val delay = MIN_HOME_LOADING_LOTTIE_MS - elapsed
                binding.root.postDelayed({
                    if (!isAdded || _binding == null) return@postDelayed
                    finishLoadingAndRender(state)
                }, delay)
                return
            }
        }
        finishLoadingAndRender(state)
    }

    private fun finishLoadingAndRender(state: HomeUiState) {
        isShowingLoadingOverlay = false
        lottieController.hide()

        state.errorMessage?.let { msg ->
            SnackbarFragment.showError(binding.root, msg)
        }

        if (state.sessionState != null) {
            updateDashboardUI(
                state.sessionState.authState,
                state.sessionState.planState,
                state.userName // ✅ Agora passamos o nome vindo da ViewModel
            )
        }
    }

    private fun updateDashboardUI(
        authState: AuthState,
        planState: PlanState,
        userName: String?
    ) {
        val context = requireContext()
        val isGuest = authState == AuthState.NAO_LOGADO
        val isPremium = authState == AuthState.LOGADO && planState == PlanState.COM_PLANO

        // --- HEADER ---
        if (isGuest) {
            binding.tvGreetingTitle.text = getString(R.string.home_hello_guest)
            binding.tvGreetingSubtitle.text = getString(R.string.home_subtitle_guest)

            // Ícone Padrão Visitante
            binding.ivProfileAvatar.setImageResource(R.drawable.ic_account_circle_24)
            binding.ivProfileAvatar.setColorFilter(
                ContextCompat.getColor(
                    context,
                    R.color.color_primary_base
                )
            )
            binding.ivProfileAvatar.alpha = 0.6f
        } else {
            // ✅ CORREÇÃO 1: Usa o nome do usuário ou "Membro" como fallback
            val displayName = if (!userName.isNullOrBlank()) userName else "Membro"
            binding.tvGreetingTitle.text = getString(R.string.home_hello_user, displayName)

            // ✅ CORREÇÃO 3: Padronização do Ícone
            // Mantemos SEMPRE o ic_account_circle_24 para consistência
            binding.ivProfileAvatar.setImageResource(R.drawable.ic_account_circle_24)
            binding.ivProfileAvatar.alpha = 1f

            if (isPremium) {
                binding.tvGreetingSubtitle.text = getString(R.string.home_subtitle_premium)
                binding.tvGreetingSubtitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.color_primary_base
                    )
                )

                // Premium: Ícone DOURADO
                binding.ivProfileAvatar.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        R.color.warning_color
                    )
                )
            } else {
                binding.tvGreetingSubtitle.text = getString(R.string.home_subtitle_free)
                binding.tvGreetingSubtitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.text_secondary
                    )
                )

                // Free: Ícone VERDE PADRÃO
                binding.ivProfileAvatar.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        R.color.color_primary_base
                    )
                )
            }
        }

        // --- BANNER HERO (Lógica Mantida) ---
        if (isGuest) {
            binding.cardBanner.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    R.color.color_primary_base
                )
            )
            binding.tvBannerTitle.text = getString(R.string.banner_guest_title)
            binding.tvBannerDesc.text = getString(R.string.banner_guest_desc)
            binding.btnBannerAction.text = getString(R.string.banner_guest_btn)
            binding.btnBannerAction.visibility = View.VISIBLE
            binding.ivBannerIcon.setImageResource(R.drawable.ic_recipe_lock_24)
            binding.ivBannerIcon.imageTintList =
                androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources,
                    R.color.white,
                    null
                )?.withAlpha(128)

        } else if (isPremium) {
            binding.cardBanner.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    R.color.color_primary_dark
                )
            )
            binding.tvBannerTitle.text = getString(R.string.banner_premium_title)
            binding.tvBannerDesc.text = getString(R.string.banner_premium_desc)
            binding.btnBannerAction.text = getString(R.string.banner_premium_btn)
            binding.btnBannerAction.visibility = View.VISIBLE
            binding.ivBannerIcon.setImageResource(R.drawable.ic_whatshot_24)
            binding.ivBannerIcon.imageTintList =
                androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources,
                    R.color.white,
                    null
                )?.withAlpha(128)

        } else {
            binding.cardBanner.setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    R.color.color_primary_base
                )
            )
            binding.tvBannerTitle.text = getString(R.string.banner_free_title)
            binding.tvBannerDesc.text = getString(R.string.banner_free_desc)
            binding.btnBannerAction.text = getString(R.string.banner_free_btn)
            binding.btnBannerAction.visibility = View.VISIBLE
            binding.ivBannerIcon.setImageResource(R.drawable.ic_star_premium_24)
            binding.ivBannerIcon.imageTintList =
                androidx.core.content.res.ResourcesCompat.getColorStateList(
                    resources,
                    R.color.white,
                    null
                )?.withAlpha(128)
        }
    }

    override fun onDestroyView() {
        if (::lottieController.isInitialized) lottieController.clear()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val MIN_HOME_LOADING_LOTTIE_MS: Long = 1000L
    }
}