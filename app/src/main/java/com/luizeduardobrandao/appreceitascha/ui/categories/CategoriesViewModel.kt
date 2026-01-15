package com.luizeduardobrandao.appreceitascha.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.recipes.Category
import com.luizeduardobrandao.appreceitascha.domain.recipes.RecipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsável pela tela de categorias.
 *
 * Responsabilidades:
 * - Carrega todas as categorias do Firebase via [RecipeRepository]
 * - Expõe estado observável para a UI
 * - Trata erros usando códigos técnicos (traduzidos no Fragment)
 */
@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    companion object {
        private const val ERROR_LOAD_CATEGORIES = "CATEGORIES_LOAD_FAILED"
    }

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    /**
     * Carrega lista de categorias do Firebase.
     * Em caso de erro, armazena código técnico para tradução na UI.
     */
    fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = recipeRepository.getCategories()

            result
                .onSuccess { categories ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            categories = categories,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { _ ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = ERROR_LOAD_CATEGORIES
                        )
                    }
                }
        }
    }

    /**
     * Limpa mensagem de erro após exibição na UI.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

/**
 * Estado da UI para lista de categorias.
 *
 * @property isLoading Indica carregamento em andamento
 * @property categories Lista de categorias carregadas
 * @property errorMessage Código de erro técnico (ou null)
 */
data class CategoriesUiState(
    val isLoading: Boolean = false,
    val categories: List<Category> = emptyList(),
    val errorMessage: String? = null
)