package com.luizeduardobrandao.appreceitascha.ui.home

import android.os.Bundle
import android.view.View
import android.view.ViewStub
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by viewModels()

    private var hasRenderedOnce: Boolean = false
    private var skeletonView: View? = null

    private var isShowingLoadingUi: Boolean = false
    private var loadingUiStartTime: Long = 0L
    private var finishJob: Job? = null

    private fun ensureSkeletonVisible() {
        if (_binding == null) return

        if (skeletonView == null) {
            val stub: ViewStub = binding.homeSkeletonStub
            skeletonView = stub.inflate()
        }

        skeletonView?.apply {
            alpha = 1f
            visibility = View.VISIBLE
        }
    }

    private fun hideSkeletonImmediate() {
        skeletonView?.visibility = View.GONE
    }

    private fun crossfadeSkeletonToContent() {
        val sk = skeletonView ?: run {
            // fallback seguro
            binding.homeContentScroll.visibility = View.VISIBLE
            binding.homeContentScroll.alpha = 1f
            return
        }

        // prepara conteúdo “por trás”
        binding.homeContentScroll.apply {
            alpha = 0f
            visibility = View.VISIBLE
        }

        sk.animate().cancel()
        binding.homeContentScroll.animate().cancel()

        sk.animate()
            .alpha(0f)
            .setDuration(SKELETON_CROSSFADE_MS)
            .withEndAction {
                sk.visibility = View.GONE
                sk.alpha = 1f
            }
            .start()

        binding.homeContentScroll.animate()
            .alpha(1f)
            .setDuration(SKELETON_CROSSFADE_MS)
            .start()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_HAS_RENDERED_ONCE, hasRenderedOnce)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        hasRenderedOnce = savedInstanceState?.getBoolean(KEY_HAS_RENDERED_ONCE, false) ?: false

        // Cold start: mostra skeleton, esconde conteúdo (zero flash)
        if (!hasRenderedOnce) {
            ensureSkeletonVisible()
            binding.homeContentScroll.visibility = View.INVISIBLE
        } else {
            // Se veio de volta (instância preservada), conteúdo pode ficar visível até loading começar
            binding.homeContentScroll.visibility = View.VISIBLE
        }

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
                AuthState.NAO_LOGADO -> findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
                AuthState.LOGADO if planState == PlanState.SEM_PLANO -> findNavController().navigate(
                    R.id.plansFragment
                )

                else -> findNavController().navigate(R.id.categoriesFragment)
            }
        }

        binding.ivProfileAvatar.setOnClickListener {
            val state = homeViewModel.uiState.value.sessionState
            if (state?.authState == AuthState.NAO_LOGADO) {
                findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
            } else {
                val args = Bundle().apply { putBoolean("isEditMode", true) }
                findNavController().navigate(R.id.registerFragment, args)
            }
        }

        binding.btnCatCalm.setOnClickListener {
            navigateToCategory(
                "calmante",
                "Calmantes e Relaxamento"
            )
        }
        binding.btnCatEnergy.setOnClickListener {
            navigateToCategory(
                "energizante",
                "Energia e Foco Total"
            )
        }
        binding.btnCatDetox.setOnClickListener {
            navigateToCategory(
                "emagrecimento",
                "Emagrecimento e Equilíbrio"
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

        // cancela qualquer finalização pendente ao receber novo estado
        finishJob?.cancel()
        finishJob = null

        if (state.isLoading) {
            // Sempre que estiver carregando:
            // - skeleton visível
            // - conteúdo invisível (evita mostrar estado antigo por 1 frame)
            ensureSkeletonVisible()
            binding.homeContentScroll.visibility = View.INVISIBLE

            if (!isShowingLoadingUi) {
                isShowingLoadingUi = true
                loadingUiStartTime = System.currentTimeMillis()
            }
            return
        }

        // Se saiu do loading, respeita um tempo mínimo do skeleton para evitar flicker
        val elapsed =
            if (isShowingLoadingUi) (System.currentTimeMillis() - loadingUiStartTime) else Long.MAX_VALUE
        val remaining = MIN_SKELETON_VISIBLE_MS - elapsed

        if (isShowingLoadingUi && remaining > 0) {
            finishJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(remaining)
                if (!isAdded || _binding == null) return@launch
                finishLoadingAndRender(state)
            }
            return
        }

        finishLoadingAndRender(state)
    }

    private fun finishLoadingAndRender(state: HomeUiState) {
        if (_binding == null) return

        state.errorMessage?.let { msg ->
            SnackbarFragment.showError(binding.root, msg)
        }

        state.sessionState?.let { ss ->
            updateDashboardUI(
                authState = ss.authState,
                planState = ss.planState,
                userName = state.userName
            )
        }

        // Cold start: crossfade skeleton -> conteúdo
        if (!hasRenderedOnce) {
            hasRenderedOnce = true
            isShowingLoadingUi = false
            crossfadeSkeletonToContent()
            return
        }

        // Refresh: some skeleton e mostra conteúdo (tudo já renderizado “por trás”)
        isShowingLoadingUi = false
        hideSkeletonImmediate()
        binding.homeContentScroll.alpha = 1f
        binding.homeContentScroll.visibility = View.VISIBLE
    }

    private fun updateDashboardUI(authState: AuthState, planState: PlanState, userName: String?) {
        val context = requireContext()
        val isGuest = authState == AuthState.NAO_LOGADO
        val isPremium = authState == AuthState.LOGADO && planState == PlanState.COM_PLANO

        if (isGuest) {
            binding.tvGreetingTitle.text = getString(R.string.home_hello_guest)
            binding.tvGreetingSubtitle.text = getString(R.string.home_subtitle_guest)

            binding.ivProfileAvatar.setImageResource(R.drawable.ic_account_circle_24)
            binding.ivProfileAvatar.setColorFilter(
                ContextCompat.getColor(
                    context,
                    R.color.color_primary_base
                )
            )
            binding.ivProfileAvatar.alpha = 0.6f
        } else {
            val displayName = if (!userName.isNullOrBlank()) userName else "Membro"
            binding.tvGreetingTitle.text = getString(R.string.home_hello_user, displayName)

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
                binding.ivProfileAvatar.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        R.color.color_primary_base
                    )
                )
            }
        }

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
        finishJob?.cancel()
        finishJob = null

        // cancela animações para não segurar referência da View
        skeletonView?.animate()?.cancel()
        if (_binding != null) {
            binding.homeContentScroll.animate().cancel()
        }

        skeletonView = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val KEY_HAS_RENDERED_ONCE = "KEY_HAS_RENDERED_ONCE"
        private const val MIN_SKELETON_VISIBLE_MS: Long = 220L
        private const val SKELETON_CROSSFADE_MS: Long = 180L
    }
}