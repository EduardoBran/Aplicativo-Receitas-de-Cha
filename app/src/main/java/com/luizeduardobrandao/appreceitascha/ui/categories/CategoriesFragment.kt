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
                    // Lista
                    categoriesAdapter.submitList(state.categories)

                    // Loading
                    binding.progressBarCategories.isVisible = state.isLoading

                    // Estado vazio
                    val showEmpty =
                        !state.isLoading &&
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
            }
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
        // Cancela listeners pendentes de teclado/Snackbar ligados a esta view
        _binding?.root?.let { root ->
            SnackbarFragment.cancelPendingSnackbars(root)
        }

        _binding = null
        super.onDestroyView()
    }
}