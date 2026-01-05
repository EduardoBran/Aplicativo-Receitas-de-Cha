package com.luizeduardobrandao.appreceitascha.ui.search

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.databinding.FragmentSearchBinding
import com.luizeduardobrandao.appreceitascha.domain.recipes.Recipe
import com.luizeduardobrandao.appreceitascha.ui.common.SnackbarFragment
import com.luizeduardobrandao.appreceitascha.ui.search.adapter.SearchAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment(R.layout.fragment_search) {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var searchAdapter: SearchAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSearchBinding.bind(view)

        setupAdapter()
        setupListeners()
        observeState()

        // Focar no campo de busca e abrir teclado automaticamente
        binding.etSearch.requestFocus()
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun setupAdapter() {
        searchAdapter = SearchAdapter(
            canOpenRecipe = { recipe -> viewModel.canOpenRecipe(recipe) },
            onRecipeClick = { recipe -> onRecipeClicked(recipe) }
        )
        binding.recyclerViewResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
    }

    private fun setupListeners() {
        // Botão Voltar (Toolbar)
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Botão Voltar (Empty State)
        binding.btnBackEmpty.setOnClickListener {
            findNavController().navigateUp()
        }

        // Ação do Teclado (Search)
        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = v.text.toString()
                viewModel.performSearch(query)
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: SearchUiState) {
        // Validação (< 3 chars)
        if (state.validationError != null) {
            SnackbarFragment.showWarning(binding.root, getString(R.string.search_error_min_chars))
            viewModel.clearValidationError()
        }

        // Erro genérico
        if (state.errorMessage != null) {
            SnackbarFragment.showError(binding.root, getString(R.string.search_error_generic))
        }

        // Loading
        binding.layoutLoading.isVisible = state.isLoading

        // Empty State
        binding.layoutEmptyState.isVisible = state.isEmpty && !state.isLoading

        // Resultados
        binding.recyclerViewResults.isVisible = !state.isEmpty && !state.isLoading
        // Força o scroll para o topo após a atualização da lista
        searchAdapter.submitList(state.results) {
            binding.recyclerViewResults.scrollToPosition(0)
        }
    }

    private fun onRecipeClicked(recipe: Recipe) {
        if (viewModel.canOpenRecipe(recipe)) {
            val action =
                SearchFragmentDirections.actionSearchFragmentToRecipeDetailFragment(recipe.id)
            findNavController().navigate(action)
        } else {
            SnackbarFragment.showWarning(
                binding.root,
                getString(R.string.recipe_locked_requires_plan_or_login)
            )
        }
    }

    private fun hideKeyboard() {
        val view = activity?.currentFocus ?: return
        val imm =
            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}