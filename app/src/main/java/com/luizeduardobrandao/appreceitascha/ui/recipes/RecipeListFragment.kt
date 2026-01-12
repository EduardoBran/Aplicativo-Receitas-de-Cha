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
import com.luizeduardobrandao.appreceitascha.ui.common.animation.LottieLoadingController
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
        _binding = FragmentRecipeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Instancia o controlador de loading com Lottie centralizado
        lottieController = LottieLoadingController(
            overlay = binding.recipeListLoadingOverlay,
            lottieView = binding.lottieRecipeList
        )

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
                    renderUi(state)
                }
            }
        }
    }

    private fun renderUi(state: RecipeListUiState) {
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
        // Garante que o Lottie fique visível por pelo menos MIN_RECIPE_LOADING_LOTTIE_MS.
        if (isShowingLoadingOverlay) {
            val elapsed = System.currentTimeMillis() - loadingStartTime
            if (elapsed < MIN_RECIPE_LOADING_LOTTIE_MS) {
                // Ainda não atingiu o tempo mínimo: mantém overlay e conteúdo ocultos.
                hideAllContent()

                val delay = MIN_RECIPE_LOADING_LOTTIE_MS - elapsed

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
        binding.recyclerViewRecipes.isVisible = false
    }

    private fun renderNonLoadingUi(state: RecipeListUiState) {
        val binding = _binding ?: return

        // Lista de receitas
        recipesAdapter.submitList(state.recipes)
        binding.recyclerViewRecipes.isVisible = state.recipes.isNotEmpty()

        // Erro → Snackbar com texto vindo de strings.xml
        if (state.errorMessage != null) {
            SnackbarFragment.showError(
                requireView(),
                getString(R.string.recipes_list_error_generic)
            )
            viewModel.clearError()
        }
    }


    /**
     * Clique em uma receita:
     * - Se o usuário puder abrir (regra do ViewModel + isFreePreview/plano), navega para detalhes.
     * - Caso contrário, exibe BottomSheet para navegação.
     */
    private fun onRecipeClicked(recipe: Recipe) {
        if (viewModel.canOpenRecipe(recipe)) {
            val action = RecipeListFragmentDirections
                .actionRecipeListFragmentToRecipeDetailFragment(
                    recipeId = recipe.id
                )
            findNavController().navigate(action)
        } else {
            findNavController().navigate(R.id.action_recipeListFragment_to_lockedRecipeBottomSheet)
        }
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
        /** Tempo mínimo em ms que o Lottie deve permanecer visível na lista de receitas. */
        private const val MIN_RECIPE_LOADING_LOTTIE_MS: Long = 1000L
    }
}