package com.luizeduardobrandao.appreceitascha.ui.favorites

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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentFavoritesBinding
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.ui.common.animation.LottieLoadingController
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.favorites.adapter.FavoritesAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Tela que exibe as receitas favoritas do usuário.
 *
 * - Somente LOGADO + COM_PLANO pode ter lista.
 * - Cada card mostra título + subtítulo e ícone de ir para detalhes.
 */
@AndroidEntryPoint
class FavoritesFragment : Fragment() {

    private var _binding: FragmentFavoritesBinding? = null
    private val binding: FragmentFavoritesBinding
        get() = _binding!!

    private val viewModel: FavoritesViewModel by viewModels()

    private lateinit var favoritesAdapter: FavoritesAdapter

    // Controller para animação de loading com Lottie
    private lateinit var lottieController: LottieLoadingController

    // Controle de tempo mínimo da animação de loading
    private var isShowingLoadingOverlay: Boolean = false
    private var loadingStartTime: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFavoritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Instancia o controlador de loading com Lottie centralizado
        lottieController = LottieLoadingController(
            overlay = binding.favoritesLoadingOverlay,
            lottieView = binding.lottieFavorites
        )

        setupRecyclerView()
        observeUiState()

        // Carrega favoritos ao abrir a tela
        viewModel.loadFavorites()
    }

    private fun setupRecyclerView() {
        favoritesAdapter = FavoritesAdapter(
            onOpenRecipe = { recipe ->
                openRecipeDetails(recipe)
            }
        )

        binding.recyclerViewFavorites.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = favoritesAdapter
            setHasFixedSize(true)
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: FavoritesUiState) {
        // Se a view já foi destruída, não tenta renderizar nada
        if (_binding == null) return

        if (state.isLoading) {
            // Início de um novo ciclo de loading
            if (!isShowingLoadingOverlay) {
                isShowingLoadingOverlay = true
                loadingStartTime = System.currentTimeMillis()
                lottieController.showLoading()
            }

            // Enquanto estiver carregando, esconde lista e estado vazio
            hideAllContent()
            return
        }

        // Aqui, state.isLoading == false.
        // Garante que o Lottie fique visível por pelo menos MIN_FAVORITES_LOADING_LOTTIE_MS.
        if (isShowingLoadingOverlay) {
            val elapsed = System.currentTimeMillis() - loadingStartTime
            if (elapsed < MIN_FAVORITES_LOADING_LOTTIE_MS) {
                // Ainda não atingiu o tempo mínimo: mantém overlay e conteúdo ocultos.
                hideAllContent()

                val delay = MIN_FAVORITES_LOADING_LOTTIE_MS - elapsed

                binding.root.postDelayed({
                    if (!isAdded) return@postDelayed
                    if (_binding == null) return@postDelayed
                    if (!isShowingLoadingOverlay) return@postDelayed

                    isShowingLoadingOverlay = false
                    lottieController.hide()
                    renderNonLoadingUi(state)
                }, delay)

                return
            } else {
                // Já passou do tempo mínimo: pode esconder overlay imediatamente.
                isShowingLoadingOverlay = false
                lottieController.hide()
            }
        }

        // Não está mais carregando e o overlay já foi tratado: renderiza normalmente.
        renderNonLoadingUi(state)
    }

    private fun hideAllContent() {
        val binding = _binding ?: return
        binding.recyclerViewFavorites.isVisible = false
        binding.imageFavoritesEmptyIcon.isVisible = false
        binding.textFavoritesEmptyState.isVisible = false
    }

    private fun renderNonLoadingUi(state: FavoritesUiState) {
        val binding = _binding ?: return

        // Lista
        favoritesAdapter.submitList(state.recipes)

        // Calcula o showEmpty conforme a regra
        val showEmpty = state.recipes.isEmpty()

        binding.recyclerViewFavorites.isVisible = state.recipes.isNotEmpty()

        // Ícone + texto acompanham o mesmo estado vazio
        binding.imageFavoritesEmptyIcon.isVisible = showEmpty
        binding.textFavoritesEmptyState.isVisible = showEmpty

        // Snackbar de remoção com sucesso
        state.lastRemovedRecipeTitle?.let { title ->
            val message = getString(
                R.string.recipe_favorite_removed_success,
                title
            )
            SnackbarFragment.showSuccess(requireView(), message)
            viewModel.clearLastRemovedRecipeTitle()
        }

        // Erros / avisos reais (se houver)
        state.errorMessage?.let {
            SnackbarFragment.showError(
                requireView(),
                getString(R.string.favorites_error_generic)
            )
            viewModel.clearError()
        }
    }

    /**
     * Abre a tela de detalhes da receita favoritada.
     */
    private fun openRecipeDetails(recipe: Recipe) {
        val action = FavoritesFragmentDirections
            .actionFavoritesFragmentToRecipeDetailFragment(
                recipeId = recipe.id
            )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        _binding?.root?.let { root ->
            SnackbarFragment.cancelPendingSnackbars(root)
        }

        if (::lottieController.isInitialized) {
            lottieController.clear()
        }

        _binding = null
        super.onDestroyView()
    }

    companion object {
        /** Tempo mínimo em ms que o Lottie deve permanecer visível na tela de favoritos. */
        private const val MIN_FAVORITES_LOADING_LOTTIE_MS: Long = 1000L
    }
}