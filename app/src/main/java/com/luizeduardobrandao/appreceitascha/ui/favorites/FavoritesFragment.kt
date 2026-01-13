package com.luizeduardobrandao.appreceitascha.ui.favorites

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentFavoritesBinding
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthState
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanState
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.ui.common.animation.LottieLoadingController
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.favorites.adapter.FavoritesAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FavoritesFragment : Fragment(R.layout.fragment_favorites) {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FavoritesViewModel by viewModels()
    private lateinit var adapter: FavoritesAdapter
    private lateinit var lottieController: LottieLoadingController

    // Controle de tempo mínimo
    private var isShowingLoadingOverlay: Boolean = false
    private var loadingStartTime: Long = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentFavoritesBinding.bind(view)

        lottieController = LottieLoadingController(
            overlay = binding.favoritesLoadingOverlay,
            lottieView = binding.lottieFavorites
        )

        setupRecyclerView()
        observeUiState()
    }

    override fun onResume() {
        super.onResume()
        // Força o recarregamento da lista sempre que a tela se torna visível novamente.
        viewModel.loadFavorites()
    }

    private fun setupRecyclerView() {
        // Agora passamos também a lógica 'canOpenRecipe'
        adapter = FavoritesAdapter(
            canOpenRecipe = { recipe -> canAccessRecipe(recipe) },
            onOpenRecipe = { recipe -> openRecipeDetails(recipe) }
        )
        binding.recyclerViewFavorites.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewFavorites.adapter = adapter
    }

    /**
     * Verifica visualmente se o usuário tem permissão para esta receita
     */
    private fun canAccessRecipe(recipe: Recipe): Boolean {
        val sessionState = viewModel.uiState.value.sessionState
        return when {
            sessionState.authState == AuthState.LOGADO &&
                    sessionState.planState == PlanState.COM_PLANO -> true

            else -> recipe.isFreePreview
        }
    }

    private fun openRecipeDetails(recipe: Recipe) {
        if (canAccessRecipe(recipe)) {
            val action = FavoritesFragmentDirections
                .actionFavoritesFragmentToRecipeDetailFragment(
                    recipeId = recipe.id
                )
            findNavController().navigate(action)
        } else {
            // Navega para o BottomSheet de bloqueio
            findNavController().navigate(R.id.action_favoritesFragment_to_lockedRecipeBottomSheet)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    handleLoadingState(uiState)
                }
            }
        }
    }

    private fun handleLoadingState(state: FavoritesUiState) {
        if (_binding == null) return

        if (state.isLoading) {
            if (!isShowingLoadingOverlay) {
                isShowingLoadingOverlay = true
                loadingStartTime = System.currentTimeMillis()
                lottieController.showLoading()
            }
            binding.recyclerViewFavorites.isVisible = false
            return
        }

        if (isShowingLoadingOverlay) {
            val elapsed = System.currentTimeMillis() - loadingStartTime
            if (elapsed < MIN_FAVORITES_LOADING_LOTTIE_MS) {
                val delay = MIN_FAVORITES_LOADING_LOTTIE_MS - elapsed
                binding.root.postDelayed({
                    if (!isAdded) return@postDelayed
                    if (_binding == null) return@postDelayed
                    if (!isShowingLoadingOverlay) return@postDelayed
                    finishLoadingAndRender(state)
                }, delay)
                return
            }
        }

        finishLoadingAndRender(state)
    }

    private fun finishLoadingAndRender(state: FavoritesUiState) {
        isShowingLoadingOverlay = false
        lottieController.hide()

        val isEmpty = state.favoriteListItems.isEmpty()

        binding.recyclerViewFavorites.isVisible = !isEmpty
        binding.textFavoritesEmpty.isVisible = isEmpty

        adapter.submitList(state.favoriteListItems)

        state.errorMessage?.let {
            SnackbarFragment.showError(binding.root, it)
            viewModel.clearError()
        }
    }

    override fun onDestroyView() {
        if (::lottieController.isInitialized) {
            lottieController.clear()
        }
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val MIN_FAVORITES_LOADING_LOTTIE_MS: Long = 1000L
    }
}