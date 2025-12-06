package com.luizeduardobrandao.appreceitascha.ui.recipes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.appreceitascha.MainViewModel
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentRecipeListBinding
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.recipes.adapter.RecipesAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Tela que exibe todas as receitas de uma categoria específica.
 *
 * - Recebe categoryId e categoryName via Safe Args.
 * - Carrega receitas do Firebase via RecipeListViewModel.
 * - Exibe cards com título + subtítulo.
 * - Ao clicar em uma receita, navega para RecipeDetailFragment,
 *   respeitando o estado de sessão (NAO_LOGADO / SEM_PLANO / COM_PLANO).
 */
@AndroidEntryPoint
class RecipeListFragment : Fragment() {

    private var _binding: FragmentRecipeListBinding? = null
    private val binding: FragmentRecipeListBinding
        get() = _binding!!

    // ViewModel de escopo de Activity, com estado global de sessão (auth + plano)
    private val mainViewModel: MainViewModel by activityViewModels()

    // ViewModel específico da lista de receitas
    private val viewModel: RecipeListViewModel by viewModels()
    private val args: RecipeListFragmentArgs by navArgs()

    private lateinit var recipesAdapter: RecipesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupTitle()
        observeSessionState()
        observeUiState()

        // Dispara o carregamento das receitas da categoria
        viewModel.loadRecipes(args.categoryId)
    }

    private fun setupTitle() {
        // Mostra o nome da categoria passado via Safe Args
        binding.textRecipesTitle.text = args.categoryName
    }

    private fun setupRecyclerView() {
        recipesAdapter = RecipesAdapter(
            canOpenRecipe = { recipe ->
                viewModel.canOpenRecipe(recipe)
            },
            onRecipeClick = { recipe ->
                onRecipeClicked(recipe)
            }
        )

        binding.recyclerViewRecipes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recipesAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * Observa o estado global de sessão (auth + plano) vindo do MainViewModel
     * e repassa para o RecipeListViewModel, que usa isso para decidir o acesso.
     */
    private fun observeSessionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.sessionState.collect { sessionState ->
                    viewModel.updateSessionState(sessionState)
                }
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    recipesAdapter.submitList(state.recipes)

                    binding.progressBarRecipes.isVisible = state.isLoading

                    val showEmpty =
                        !state.isLoading &&
                                state.recipes.isEmpty() &&
                                state.errorMessage == null

                    binding.textRecipesEmptyState.isVisible = showEmpty

                    if (state.errorMessage != null) {
                        SnackbarFragment.showError(
                            requireView(),
                            getString(R.string.recipes_list_error_generic)
                        )
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    /**
     * Clique em uma receita:
     * - Se o usuário puder abrir (regra do ViewModel + isFreePreview/plano), navega para detalhes.
     * - Caso contrário, mostra Snackbar explicando que precisa de login/plano.
     */
    private fun onRecipeClicked(recipe: Recipe) {
        if (viewModel.canOpenRecipe(recipe)) {
            val action = RecipeListFragmentDirections
                .actionRecipeListFragmentToRecipeDetailFragment(
                    recipeId = recipe.id
                )
            findNavController().navigate(action)
        } else {
            SnackbarFragment.showWarning(
                requireView(),
                getString(R.string.recipe_locked_requires_plan_or_login)
            )
        }
    }

    override fun onDestroyView() {
        _binding?.root?.let { root ->
            SnackbarFragment.cancelPendingSnackbars(root)
        }
        _binding = null
        super.onDestroyView()
    }
}