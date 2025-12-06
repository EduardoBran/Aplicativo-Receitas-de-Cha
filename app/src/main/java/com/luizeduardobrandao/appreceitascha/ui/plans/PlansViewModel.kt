package com.luizeduardobrandao.appreceitascha.ui.plans

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingLaunchResult
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingPlan
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingPurchaseResult
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val billingRepository: BillingRepository
) : ViewModel() {

    data class PlansUiState(
        val isLoading: Boolean = true,
        val plans: List<BillingPlan> = emptyList(),
        val isPurchaseInProgress: Boolean = false,
        val errorMessage: String? = null,
        val successMessage: String? = null
    )

    private val _uiState = MutableStateFlow(PlansUiState())
    val uiState: StateFlow<PlansUiState> = _uiState.asStateFlow()

    init {
        observePurchaseResults()
        // Executa as operações de Billing em SEQUÊNCIA dentro de UMA coroutine
        // para evitar o erro "Client is already in the process of connecting to billing service".
        viewModelScope.launch {
            loadPlans()
            refreshExistingPurchases()
        }
    }

    /**
     * Observa o fluxo de resultados de compra do BillingRepository
     * e atualiza o estado de UI para exibir mensagens adequadas.
     */
    private fun observePurchaseResults() {
        viewModelScope.launch {
            billingRepository.purchaseResults.collect { result ->
                when (result) {
                    is BillingPurchaseResult.Success -> {
                        // Neste ponto o plano JÁ foi salvo em /userPlans/{uid}
                        // pelo BillingRepositoryImpl.persistPlanForCurrentUser()
                        _uiState.value = _uiState.value.copy(
                            isPurchaseInProgress = false,
                            successMessage = "Plano ativado com sucesso! Aproveite as receitas completas.",
                            errorMessage = null
                        )
                    }

                    BillingPurchaseResult.Pending -> {
                        _uiState.value = _uiState.value.copy(
                            isPurchaseInProgress = false,
                            successMessage = "Pagamento pendente de confirmação. Assim que o pagamento for aprovado, seu plano será liberado.",
                            errorMessage = null
                        )
                    }

                    BillingPurchaseResult.Cancelled -> {
                        _uiState.value = _uiState.value.copy(
                            isPurchaseInProgress = false,
                            errorMessage = "Compra cancelada. Você pode tentar novamente quando quiser.",
                            successMessage = null
                        )
                    }

                    BillingPurchaseResult.Empty -> {
                        // Nada relevante: apenas garantimos que não estamos mais em loading de compra
                        _uiState.value = _uiState.value.copy(
                            isPurchaseInProgress = false
                        )
                    }

                    is BillingPurchaseResult.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isPurchaseInProgress = false,
                            errorMessage = "Erro ao processar a compra. Tente novamente mais tarde.",
                            successMessage = null
                        )
                    }
                }
            }
        }
    }

    /**
     * Carrega os planos disponíveis na Play Store.
     * Agora é 'suspend' e é chamada em sequência dentro de uma única coroutine.
     */
    private suspend fun loadPlans() {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null,
            successMessage = null
        )
        try {
            val plans = billingRepository.loadAvailablePlans()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                plans = plans
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Não foi possível carregar os planos. Tente novamente mais tarde."
            )
        }
    }

    /**
     * Restaura compras ativas (caso o usuário já tenha plano em outro device ou reinstalação).
     * Também é 'suspend' para ser chamada em sequência após loadPlans().
     */
    private suspend fun refreshExistingPurchases() {
        try {
            billingRepository.refreshActivePurchases()
        } catch (_: Exception) {
            // Se der erro aqui, apenas ignoramos — a UI continua funcionando.
        }
    }

    /**
     * Dispara o fluxo de compra para o plano selecionado.
     */
    fun onBuyPlanClicked(plan: BillingPlan, activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isPurchaseInProgress = true,
                errorMessage = null,
                successMessage = null
            )

            val launchResult = billingRepository.launchPurchase(activity, plan)

            if (launchResult is BillingLaunchResult.Error) {
                _uiState.value = _uiState.value.copy(
                    isPurchaseInProgress = false,
                    errorMessage = "Não foi possível iniciar a compra: ${launchResult.message}"
                )
            }
            // LaunchStarted: mantemos o loading até receber o resultado em purchaseResults.
        }
    }

    /**
     * Chamado pela Fragment após exibir Snackbar, para não repetir mensagens.
     */
    fun clearMessages() {
        val current = _uiState.value
        _uiState.value = current.copy(
            errorMessage = null,
            successMessage = null
        )
    }
}