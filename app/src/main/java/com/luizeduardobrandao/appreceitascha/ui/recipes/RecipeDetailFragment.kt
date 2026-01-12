package com.luizeduardobrandao.appreceitascha.ui.recipes

import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
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

@AndroidEntryPoint
class RecipeDetailFragment : Fragment(R.layout.fragment_recipe_detail) {

    private var _binding: FragmentRecipeDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecipeDetailViewModel by viewModels()
    private val args: RecipeDetailFragmentArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRecipeDetailBinding.bind(view)

        // Prepara animações de entrada
        prepareEntranceAnimations()

        // Carrega a receita uma única vez ao criar a view
        viewModel.loadRecipe(args.recipeId)

        setupObservers()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSessionState()
        viewModel.syncFavoriteState()
    }

    /**
     * Retorna a View raiz da Activity para ancorar o Snackbar.
     * Isso corrige o problema de posição, evitando o comportamento flutuante
     * padrão do CoordinatorLayout e garantindo que fique idêntico ao RecipeListFragment.
     */
    private fun getSnackbarAnchorView(): View {
        return requireActivity().findViewById(android.R.id.content) ?: requireView()
    }

    private fun prepareEntranceAnimations() {
        binding.textShortDescription.alpha = 0f
        binding.textShortDescription.translationY = 50f

        binding.cardBenefits.alpha = 0f
        binding.cardBenefits.translationY = 100f

        binding.cardIngredients.alpha = 0f
        binding.cardIngredients.translationY = 100f

        binding.cardPrep.alpha = 0f
        binding.cardPrep.translationY = 100f

        binding.cardNotes.alpha = 0f
        binding.cardNotes.translationY = 100f

        binding.fabFavorite.scaleX = 0f
        binding.fabFavorite.scaleY = 0f
    }

    private fun runEntranceAnimations() {
        val interpolator = OvershootInterpolator()

        binding.fabFavorite.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(400)
            .setInterpolator(interpolator)
            .setStartDelay(300)
            .start()

        binding.textShortDescription.animate()
            .alpha(1f).translationY(0f)
            .setDuration(500)
            .setStartDelay(100)
            .start()

        binding.cardBenefits.animate()
            .alpha(1f).translationY(0f)
            .setDuration(500)
            .setStartDelay(200)
            .start()

        binding.cardIngredients.animate()
            .alpha(1f).translationY(0f)
            .setDuration(500)
            .setStartDelay(300)
            .start()

        binding.cardPrep.animate()
            .alpha(1f).translationY(0f)
            .setDuration(500)
            .setStartDelay(400)
            .start()

        binding.cardNotes.animate()
            .alpha(1f).translationY(0f)
            .setDuration(500)
            .setStartDelay(500)
            .start()
    }

    private fun setupListeners() {
        binding.fabFavorite.setOnClickListener {
            // Micro-interação de "Pulo"
            binding.fabFavorite.animate()
                .scaleX(1.2f).scaleY(1.2f)
                .setDuration(100)
                .withEndAction {
                    binding.fabFavorite.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()

            viewModel.toggleFavorite()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBarRecipeDetail.isVisible = state.isLoading

                    state.recipe?.let { recipe ->
                        binding.textRecipeTitle.text = recipe.title
                        binding.textRecipeSubtitle.text = recipe.subtitle
                        binding.textShortDescription.text = recipe.shortDescription
                        binding.textRecipeIngredientes.text = recipe.ingredientes
                        binding.textRecipeModoPreparo.text = recipe.modoDePreparo
                        binding.textRecipeBeneficios.text = recipe.beneficios
                        binding.textRecipeObservacoes.text = recipe.observacoes

                        // Executa animação apenas se ainda não foi exibida (alpha 0)
                        if (binding.cardBenefits.alpha == 0f) {
                            runEntranceAnimations()
                        }
                    }

                    updateFavoriteIcon(state.isFavorite)

                    state.errorMessage?.let { errorMsg ->
                        if (errorMsg == RecipeDetailViewModel.ERROR_FAVORITE_REQUIRES_PLAN_OR_LOGIN) {
                            SnackbarFragment.showWarning(
                                getSnackbarAnchorView(),
                                getString(R.string.recipe_favorite_requires_plan_or_login)
                            )
                        } else {
                            SnackbarFragment.showError(
                                getSnackbarAnchorView(),
                                errorMsg
                            )
                        }
                        viewModel.clearError()
                    }

                    state.lastFavoriteAction?.let { action ->
                        val title = state.recipe?.title.orEmpty()
                        val message = when (action) {
                            RecipeFavoriteAction.ADDED ->
                                getString(R.string.recipe_favorite_added_success, title)

                            RecipeFavoriteAction.REMOVED ->
                                getString(R.string.recipe_favorite_removed_success, title)
                        }
                        // Usa o anchorView para sucesso também, mantendo consistência
                        SnackbarFragment.showSuccess(getSnackbarAnchorView(), message)
                        viewModel.clearFavoriteAction()
                    }
                }
            }
        }
    }

    private fun updateFavoriteIcon(isFavorite: Boolean) {
        val context = requireContext()

        if (isFavorite) {
            binding.fabFavorite.setImageResource(R.drawable.ic_favorite_24)
            binding.fabFavorite.setColorFilter(
                ContextCompat.getColor(context, R.color.color_favorite_active)
            )
            binding.fabFavorite.contentDescription = getString(R.string.cd_favorite_remove)
        } else {
            binding.fabFavorite.setImageResource(R.drawable.ic_favorite_border_24)
            binding.fabFavorite.setColorFilter(
                ContextCompat.getColor(context, R.color.color_primary_base)
            )
            binding.fabFavorite.contentDescription = getString(R.string.cd_favorite_add)
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