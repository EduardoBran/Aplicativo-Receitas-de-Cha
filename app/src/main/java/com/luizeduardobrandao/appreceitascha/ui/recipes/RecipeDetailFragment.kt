package com.luizeduardobrandao.appreceitascha.ui.recipes

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
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentRecipeDetailBinding
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Tela que exibe os detalhes de uma receita específica.
 *
 * - Carrega a receita pelo recipeId recebido via Safe Args.
 * - Mostra todos os textos (resumo, modo de preparo, benefícios, observações).
 * - Ícone de favorito no canto superior direito:
 *      • plus  → não favoritado
 *      • minus → já favoritado
 *   Apenas usuários LOGADO + COM_PLANO devem conseguir favoritar.
 */
@AndroidEntryPoint
class RecipeDetailFragment : Fragment() {

    private var _binding: FragmentRecipeDetailBinding? = null
    private val binding: FragmentRecipeDetailBinding
        get() = _binding!!

    private val viewModel: RecipeDetailViewModel by viewModels()
    private val args: RecipeDetailFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecipeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeUiState()
        setupFavoriteClick()

        // Carrega a receita
        viewModel.loadRecipe(args.recipeId)

        // Atualiza estado de sessão (auth + plano) e sincroniza favorito
        viewModel.refreshSessionState()
        viewModel.syncFavoriteState()
    }

    /**
     * Clique no ícone de favorito:
     * - Quem decide se pode ou não é o ViewModel (regra de negócio + sessão).
     */
    private fun setupFavoriteClick() {
        binding.imageRecipeFavorite.setOnClickListener {
            viewModel.toggleFavorite()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBarRecipeDetail.isVisible = state.isLoading

                    val recipe = state.recipe
                    binding.scrollRecipeContent.isVisible =
                        recipe != null && !state.isLoading

                    if (recipe != null) {
                        binding.textRecipeTitle.text = recipe.title
                        binding.textRecipeSubtitle.text = recipe.subtitle
                        binding.textRecipeShortDescription.text = recipe.shortDescription
                        binding.textRecipeModoPreparo.text = recipe.modoDePreparo
                        binding.textRecipeBeneficios.text = recipe.beneficios
                        binding.textRecipeObservacoes.text = recipe.observacoes

                        updateFavoriteIcon(state.isFavorite)
                    }

                    // Erros / avisos
                    state.errorMessage?.let { error ->
                        when (error) {
                            RecipeDetailViewModel.ERROR_FAVORITE_REQUIRES_PLAN_OR_LOGIN -> {
                                SnackbarFragment.showWarning(
                                    requireView(),
                                    getString(R.string.recipe_favorite_requires_plan_or_login)
                                )
                            }

                            else -> {
                                SnackbarFragment.showError(
                                    requireView(),
                                    getString(R.string.recipe_detail_error_generic)
                                )
                            }
                        }
                        viewModel.clearError()
                    }

                    // ✅ Sucesso ao adicionar ou remover favorito
                    state.lastFavoriteAction?.let { action ->
                        val title = state.recipe?.title.orEmpty()
                        val message = when (action) {
                            RecipeFavoriteAction.ADDED ->
                                getString(R.string.recipe_favorite_added_success, title)
                            RecipeFavoriteAction.REMOVED ->
                                getString(R.string.recipe_favorite_removed_success, title)
                        }

                        SnackbarFragment.showSuccess(requireView(), message)
                        viewModel.clearFavoriteAction()
                    }
                }
            }
        }
    }

    /**
     * Atualiza o ícone do botão de favorito:
     * - plus  → não favoritado
     * - minus → já favoritado
     */
    private fun updateFavoriteIcon(isFavorite: Boolean) {
        val iconRes = if (isFavorite) {
            R.drawable.ic_recipe_favorite_minus_24
        } else {
            R.drawable.ic_recipe_favorite_plus_24
        }

        binding.imageRecipeFavorite.setImageResource(iconRes)
        binding.imageRecipeFavorite.contentDescription = if (isFavorite) {
            getString(R.string.recipe_favorite_remove_content_description)
        } else {
            getString(R.string.recipe_favorite_add_content_description)
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