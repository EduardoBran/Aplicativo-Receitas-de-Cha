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

                    // Lista
                    favoritesAdapter.submitList(state.recipes)

                    // Loading
                    binding.progressBarFavorites.isVisible = state.isLoading

                    // Calcula o showEmpty conforme a regra
                    val showEmpty =
                        !state.isLoading && state.recipes.isEmpty()

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
            }
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
        _binding = null
        super.onDestroyView()
    }
}