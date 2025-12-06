package com.luizeduardobrandao.appreceitascha.ui.categories

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
import com.luizeduardobrandao.appreceitascha.databinding.FragmentCategoriesBinding
import com.luizeduardobrandao.appreceitascha.domain.recipes.Category
import com.luizeduardobrandao.appreceitascha.ui.categories.adapter.CategoriesAdapter
import com.luizeduardobrandao.appreceitascha.ui.common.animation.LottieLoadingController
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CategoriesFragment : Fragment() {

    private var _binding: FragmentCategoriesBinding? = null
    private val binding: FragmentCategoriesBinding
        get() = _binding!!

    private val viewModel: CategoriesViewModel by viewModels()

    private lateinit var categoriesAdapter: CategoriesAdapter

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
        _binding = FragmentCategoriesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Instancia o controlador de loading com Lottie centralizado
        lottieController = LottieLoadingController(
            overlay = binding.categoriesLoadingOverlay,
            lottieView = binding.lottieCategories
        )

        setupRecyclerView()
        observeUiState()
    }

    private fun setupRecyclerView() {
        categoriesAdapter = CategoriesAdapter { category ->
            onCategoryClicked(category)
        }

        binding.recyclerViewCategories.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = categoriesAdapter
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

    private fun renderUi(state: CategoriesUiState) {
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
        // Garante que o Lottie fique visível por pelo menos MIN_CATEGORIES_LOADING_LOTTIE_MS.
        if (isShowingLoadingOverlay) {
            val elapsed = System.currentTimeMillis() - loadingStartTime
            if (elapsed < MIN_CATEGORIES_LOADING_LOTTIE_MS) {
                // Ainda não atingiu o tempo mínimo: mantém overlay e conteúdo ocultos.
                hideAllContent()

                val delay = MIN_CATEGORIES_LOADING_LOTTIE_MS - elapsed

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
        binding.recyclerViewCategories.isVisible = false
        binding.textCategoriesEmptyState.isVisible = false
    }

    private fun renderNonLoadingUi(state: CategoriesUiState) {
        val binding = _binding ?: return

        // Lista de categorias
        categoriesAdapter.submitList(state.categories)
        binding.recyclerViewCategories.isVisible = state.categories.isNotEmpty()

        // Estado vazio (sem erro)
        val showEmpty =
            state.categories.isEmpty() &&
                    state.errorMessage == null

        binding.textCategoriesEmptyState.isVisible = showEmpty

        // Erro → Snackbar com texto vindo de strings.xml
        if (state.errorMessage != null) {
            SnackbarFragment.showError(
                requireView(),
                getString(R.string.categories_error_generic)
            )
            viewModel.clearError()
        }
    }

    private fun onCategoryClicked(category: Category) {
        val action = CategoriesFragmentDirections
            .actionCategoriesFragmentToRecipeListFragment(
                categoryId = category.id,
                categoryName = category.name
            )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        // Cancela snackbars pendentes ligados a esta view
        _binding?.root?.let { root ->
            SnackbarFragment.cancelPendingSnackbars(root)
        }

        // Evita leaks de listeners do Lottie
        if (::lottieController.isInitialized) {
            lottieController.clear()
        }

        _binding = null
        super.onDestroyView()
    }

    companion object {
        /** Tempo mínimo em ms que o Lottie deve permanecer visível na tela de categorias. */
        private const val MIN_CATEGORIES_LOADING_LOTTIE_MS: Long = 1000L
    }
}