package com.luizeduardobrandao.appreceitascha.ui.recipes

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentRecipeDetailBinding
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.abs

@AndroidEntryPoint
class RecipeDetailFragment : Fragment(R.layout.fragment_recipe_detail) {

    private var _binding: FragmentRecipeDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecipeDetailViewModel by viewModels()
    private val args: RecipeDetailFragmentArgs by navArgs()

    private var originalActivityPaddingTop: Int = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRecipeDetailBinding.bind(view)

        setupEdgeToEdgeFix()
        setupToolbar()

        // Evita rodar animação e esconder views ao rotacionar a tela
        if (savedInstanceState == null) {
            prepareEntranceAnimations()
        }

        viewModel.loadRecipe(args.recipeId)

        setupObservers()
        setupListeners()
    }

    private fun setupEdgeToEdgeFix() {
        val activityRoot = requireActivity().findViewById<View>(R.id.main)
        originalActivityPaddingTop = activityRoot.paddingTop
        activityRoot.updatePadding(top = 0)

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top
            }
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutExpandedContent) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val actionBarSize = android.util.TypedValue().let { tv ->
                if (requireContext().theme.resolveAttribute(
                        android.R.attr.actionBarSize,
                        tv,
                        true
                    )
                ) {
                    android.util.TypedValue.complexToDimensionPixelSize(
                        tv.data,
                        resources.displayMetrics
                    )
                } else 0
            }
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = systemBars.top + actionBarSize
            }
            insets
        }
    }

    override fun onDestroyView() {
        val activityRoot = requireActivity().findViewById<View>(R.id.main)
        activityRoot?.updatePadding(top = originalActivityPaddingTop)
        _binding?.root?.let { root ->
            SnackbarFragment.cancelPendingSnackbars(root)
        }
        _binding = null
        super.onDestroyView()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val scrollRange = appBarLayout.totalScrollRange
            val percentage = abs(verticalOffset).toFloat() / scrollRange.toFloat()
            val isCollapsed = percentage > 0.9f

            if (isCollapsed) {
                binding.toolbar.title = binding.textRecipeTitle.text
                binding.layoutExpandedContent.alpha = 0f
            } else {
                binding.toolbar.title = ""
                binding.layoutExpandedContent.alpha = 1f - percentage
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshSessionState()
        viewModel.syncFavoriteState()
    }

    private fun getSnackbarAnchorView(): View {
        // Tenta encontrar o BottomNavigationView na Activity para ancorar o Snackbar acima dele
        return requireActivity().findViewById(R.id.bottomNav) ?: binding.root
    }

    private fun prepareEntranceAnimations() {
        // Define estado inicial (invisível e deslocado) APENAS na primeira carga
        binding.appBarLayout.alpha = 0f
        binding.appBarLayout.translationY = -100f

        val offset = 100f
        binding.cardIntro.alpha = 0f
        binding.cardIntro.translationY = offset
        binding.cardBenefits.alpha = 0f
        binding.cardBenefits.translationY = offset
        binding.cardIngredients.alpha = 0f
        binding.cardIngredients.translationY = offset
        binding.cardPrep.alpha = 0f
        binding.cardPrep.translationY = offset
        binding.cardNotes.alpha = 0f
        binding.cardNotes.translationY = offset

        binding.fabFavorite.scaleX = 0f
        binding.fabFavorite.scaleY = 0f
    }

    private fun runEntranceAnimations() {
        val interpolator = OvershootInterpolator(1f)
        val duration = 600L
        var currentDelay = 0L

        binding.appBarLayout.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setInterpolator(interpolator)
            .setStartDelay(currentDelay)
            .start()

        currentDelay += 100

        val cards = listOf(
            binding.cardIntro,
            binding.cardBenefits,
            binding.cardIngredients,
            binding.cardPrep,
            binding.cardNotes
        )

        for (card in cards) {
            card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .setStartDelay(currentDelay)
                .start()
            currentDelay += 100
        }

        binding.fabFavorite.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(400)
            .setInterpolator(interpolator)
            .setStartDelay(currentDelay + 100)
            .start()
    }

    private fun setupListeners() {
        binding.fabFavorite.setOnClickListener {
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
                    val isLoading = state.isLoading || state.recipe == null
                    binding.progressBarRecipeDetail.isVisible = isLoading

                    // Só esconde as views se estiver carregando E se elas já não estiverem visíveis (ex: rotação)
                    if (isLoading) {
                        // Se for rotação, as views podem já estar lá, mas state.recipe pode ser nulo por microssegundos
                        // Vamos garantir invisibilidade apenas se necessário para evitar flicker
                        if (binding.appBarLayout.isVisible) {
                            // Mantém visível se já estava (caso de refresh ou rotação rápida)
                        } else {
                            binding.appBarLayout.visibility = View.INVISIBLE
                            binding.scrollRecipeContent.visibility = View.INVISIBLE
                            binding.fabFavorite.visibility = View.INVISIBLE
                        }
                    }

                    state.recipe?.let { recipe ->
                        binding.textRecipeTitle.text = recipe.title
                        binding.textRecipeSubtitle.text = recipe.subtitle
                        binding.textShortDescription.text = recipe.shortDescription
                        binding.textRecipeIngredientes.text = recipe.ingredientes
                        binding.textRecipeModoPreparo.text = recipe.modoDePreparo
                        binding.textRecipeBeneficios.text = recipe.beneficios
                        binding.textRecipeObservacoes.text = recipe.observacoes

                        // Atualiza o ícone da receita de acordo com a Categoria
                        updateHeroIcon(recipe.categoryId)

                        // Verificação de nulo segura para evitar o Crash ao rotacionar
                        if (!binding.toolbar.title.isNullOrEmpty()) {
                            binding.toolbar.title = recipe.title
                        }

                        binding.textRecipeTitle.post {
                            if (_binding == null) return@post

                            val lineCount = binding.textRecipeTitle.lineCount
                            val params = binding.viewHeroHeader.layoutParams
                            val heightOriginal = dpToPx(156f)
                            val heightExpandida = dpToPx(172f)
                            val targetHeight =
                                if (lineCount > 1) heightExpandida else heightOriginal

                            if (params.height != targetHeight) {
                                params.height = targetHeight
                                binding.viewHeroHeader.layoutParams = params
                            }

                            if (!state.isLoading) {
                                binding.appBarLayout.visibility = View.VISIBLE
                                binding.scrollRecipeContent.visibility = View.VISIBLE
                                binding.fabFavorite.visibility = View.VISIBLE

                                // Só roda animação se os elementos estiverem invisíveis/transparentes (primeira carga)
                                if (binding.cardBenefits.alpha == 0f) {
                                    runEntranceAnimations()
                                }
                            }
                        }
                    }

                    updateFavoriteIcon(state.isFavorite)

                    state.errorMessage?.let { errorMsg ->
                        if (errorMsg == RecipeDetailViewModel.ERROR_FAVORITE_REQUIRES_PLAN_OR_LOGIN) {

                            // Limpa erro e navega passando isFavoriteAction = true
                            viewModel.clearError()

                            val action = RecipeDetailFragmentDirections
                                .actionRecipeDetailFragmentToLockedRecipeBottomSheet(
                                    isFavoriteAction = true
                                )
                            findNavController().navigate(action)

                        } else {
                            SnackbarFragment.showError(getSnackbarAnchorView(), errorMsg)
                            viewModel.clearError()
                        }
                    }

                    state.lastFavoriteAction?.let { action ->
                        val title = state.recipe?.title.orEmpty()
                        val message = if (action == RecipeFavoriteAction.ADDED)
                            getString(R.string.recipe_favorite_added_success, title)
                        else
                            getString(R.string.recipe_favorite_removed_success, title)

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
                ContextCompat.getColor(
                    context,
                    R.color.color_favorite_active
                )
            )
            binding.fabFavorite.contentDescription = getString(R.string.cd_favorite_remove)
        } else {
            binding.fabFavorite.setImageResource(R.drawable.ic_favorite_border_24)
            binding.fabFavorite.setColorFilter(
                ContextCompat.getColor(
                    context,
                    R.color.color_primary_base
                )
            )
            binding.fabFavorite.contentDescription = getString(R.string.cd_favorite_add)
        }
    }

    /**
     * Define o ícone do Hero (cabeçalho) baseado na categoria da receita.
     */
    private fun updateHeroIcon(categoryId: String) {
        val iconRes = when (categoryId) {
            "digestivo" -> R.drawable.ic_spa_24
            "imunidade" -> R.drawable.ic_shield_with_heart_24
            "beleza" -> R.drawable.ic_face_24
            "medicinal" -> R.drawable.ic_medical_services_24
            "afrodisiaco" -> R.drawable.ic_fire_24
            "emagrecimento" -> R.drawable.ic_monitor_weight_24
            "calmante" -> R.drawable.ic_bedtime_stars_24
            "energizante" -> R.drawable.ic_rocket_launch_24
            else -> R.drawable.ic_tea_48
        }
        binding.imageHeroIcon.setImageResource(iconRes)
        // Garante que o ícone fique visível e com a transparência correta
        binding.imageHeroIcon.alpha = 0.2f
    }

    private fun dpToPx(dp: Float): Int {
        return android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP,
            dp,
            resources.displayMetrics
        ).toInt()
    }
}