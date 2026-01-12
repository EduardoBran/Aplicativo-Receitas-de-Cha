package com.luizeduardobrandao.appreceitascha.ui.home

import android.content.res.ColorStateList
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
import coil.load
import coil.transform.CircleCropTransformation
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

        // Listener do Subtítulo Clicável (Atalho)
        binding.tvGreetingSubtitle.setOnClickListener {
            val state = homeViewModel.uiState.value.sessionState
            val authState = state?.authState ?: AuthState.NAO_LOGADO
            val planState = state?.planState ?: PlanState.SEM_PLANO

            when {
                authState == AuthState.NAO_LOGADO -> {
                    findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
                }

                planState == PlanState.COM_PLANO -> {
                    findNavController().navigate(R.id.managePlanFragment)
                }

                else -> {
                    findNavController().navigate(R.id.plansFragment)
                }
            }
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
                userName = state.userName,
                avatarUrl = state.userAvatarUrl
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

    private fun updateDashboardUI(
        authState: AuthState,
        planState: PlanState,
        userName: String?,
        avatarUrl: String?
    ) {
        val context = requireContext()
        val isGuest = authState == AuthState.NAO_LOGADO
        val isPremium = authState == AuthState.LOGADO && planState == PlanState.COM_PLANO
        val hasPhoto = !avatarUrl.isNullOrBlank() // ✅ Verifica se tem foto real

        applyAvatar(avatarUrl, isGuest, isPremium)

        // Configuração de Textos
        if (isGuest) {
            binding.tvGreetingTitle.text = getString(R.string.home_hello_guest)
            binding.tvGreetingSubtitle.text = getString(R.string.home_subtitle_guest)
            binding.tvGreetingSubtitle.setTextColor(
                ContextCompat.getColor(
                    context,
                    R.color.text_secondary
                )
            )
        } else {
            val displayName = if (!userName.isNullOrBlank()) userName else "Membro"
            binding.tvGreetingTitle.text = getString(R.string.home_hello_user, displayName)

            if (isPremium) {
                binding.tvGreetingSubtitle.text = getString(R.string.home_subtitle_premium)
                binding.tvGreetingSubtitle.setTextColor(
                    ContextCompat.getColor(
                        context,
                        R.color.color_primary_base
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
            }
        }

        // ✅ LÓGICA DO STROKE (BORDA):
        // Só exibe borda colorida se NÃO for visitante E se TIVER foto carregada.
        // Caso contrário (Visitante ou Ícone padrão), a borda fica transparente.
        val strokeColorRes = when {
            isGuest -> android.R.color.transparent
            !hasPhoto -> android.R.color.transparent // Sem foto = Sem borda
            isPremium -> R.color.premium_gold         // Com foto + Premium = Dourado
            else -> R.color.color_primary_base        // Com foto + Free = Verde
        }
        binding.cardAvatarContainer.strokeColor = ContextCompat.getColor(context, strokeColorRes)


        // Configuração do Banner (Mantida igual)
        if (isGuest) {
            binding.tvBannerTitle.text = getString(R.string.banner_guest_title)
            binding.tvBannerDesc.text = getString(R.string.banner_guest_desc)
            binding.btnBannerAction.text = getString(R.string.banner_guest_btn)
            binding.btnBannerAction.visibility = View.VISIBLE
            binding.ivBannerIcon.setImageResource(R.drawable.ic_recipe_lock_24)
        } else if (isPremium) {
            binding.tvBannerTitle.text = getString(R.string.banner_premium_title)
            binding.tvBannerDesc.text = getString(R.string.banner_premium_desc)
            binding.btnBannerAction.text = getString(R.string.banner_premium_btn)
            binding.btnBannerAction.visibility = View.VISIBLE
            binding.ivBannerIcon.setImageResource(R.drawable.ic_whatshot_24)
        } else {
            binding.tvBannerTitle.text = getString(R.string.banner_free_title)
            binding.tvBannerDesc.text = getString(R.string.banner_free_desc)
            binding.btnBannerAction.text = getString(R.string.banner_free_btn)
            binding.btnBannerAction.visibility = View.VISIBLE
            binding.ivBannerIcon.setImageResource(R.drawable.ic_star_premium_24)
        }

        // Ajuste de tint do ícone do banner
        binding.ivBannerIcon.imageTintList =
            androidx.core.content.res.ResourcesCompat.getColorStateList(
                resources,
                R.color.white,
                null
            )?.withAlpha(128)
    }

    // Carregar imagem em autenticação via Google
    private fun applyAvatar(avatarUrl: String?, isGuest: Boolean, isPremium: Boolean) {
        val context = requireContext()
        val hasGoogleAvatar = !avatarUrl.isNullOrBlank()

        // REGRA 1: Se tiver foto (Google) -> Mostra foto SEM tint
        if (hasGoogleAvatar) {
            binding.ivProfileAvatar.imageTintList = null // Remove qualquer cor anterior
            binding.ivProfileAvatar.alpha = 1f
            binding.ivProfileAvatar.load(avatarUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                error(R.drawable.ic_account_circle_24)
                placeholder(R.drawable.ic_account_circle_24)
            }
            return
        }

        // Se chegou aqui, não tem foto URL (Guest ou Email/Senha)
        // Garante que o ícone padrão esteja setado
        binding.ivProfileAvatar.setImageResource(R.drawable.ic_account_circle_24)

        // REGRA 2: Visitante -> Mantém como está (alpha 0.6 + verde base)
        if (isGuest) {
            binding.ivProfileAvatar.alpha = 0.6f
            val color = ContextCompat.getColor(context, R.color.color_primary_base)
            binding.ivProfileAvatar.imageTintList = ColorStateList.valueOf(color)
            return
        }

        // Usuário Logado sem foto (Login Email)
        binding.ivProfileAvatar.alpha = 1f

        // REGRA 3 (Premium) -> Laranja
        // REGRA 4 (Não Premium) -> Verde Base
        val colorRes = if (isPremium) R.color.warning_color else R.color.color_primary_base
        val color = ContextCompat.getColor(context, colorRes)
        binding.ivProfileAvatar.imageTintList = ColorStateList.valueOf(color)
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