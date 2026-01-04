package com.luizeduardobrandao.appreceitascha.ui.plans

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luizeduardobrandao.appreceitascha.R
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingLaunchResult
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingPlan
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingPurchaseResult
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlansUiState(
    val isLoading: Boolean = false,
    val plans: List<BillingPlan> = emptyList(),
    val isPurchaseInProgress: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class PlansViewModel @Inject constructor(
    private val billingRepository: BillingRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlansUiState())
    val uiState: StateFlow<PlansUiState> = _uiState.asStateFlow()

    // Flag para saber se a busca por compras foi solicitada manualmente pelo usuário
    private var isManualRestore = false

    init {
        observePurchaseResults()

        viewModelScope.launch {
            loadPlans()
            // Verificação AUTOMÁTICA ao abrir a tela.
            // isManualRestore continua false, então se não achar nada, fica em silêncio.
            billingRepository.refreshActivePurchases()
        }
    }

    private suspend fun loadPlans() {
        _uiState.update { it.copy(isLoading = true) }
        try {
            val plans = billingRepository.loadAvailablePlans()
            _uiState.update { it.copy(isLoading = false, plans = plans) }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = context.getString(R.string.msg_purchase_error, e.message)
                )
            }
        }
    }

    fun buyPlan(activity: Activity, plan: BillingPlan) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPurchaseInProgress = true, errorMessage = null) }

            when (val result = billingRepository.launchPurchase(activity, plan)) {
                is BillingLaunchResult.LaunchStarted -> {
                    // Aguarda o fluxo de purchaseResults
                }

                is BillingLaunchResult.AlreadyOwned -> {
                    _uiState.update {
                        it.copy(
                            isPurchaseInProgress = false,
                            successMessage = context.getString(R.string.msg_restore_owned)
                        )
                    }
                }

                is BillingLaunchResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isPurchaseInProgress = false,
                            errorMessage = context.getString(
                                R.string.msg_purchase_error,
                                result.debugMessage
                            )
                        )
                    }
                }

                else -> {
                    _uiState.update { it.copy(isPurchaseInProgress = false) }
                }
            }
        }
    }

    fun restorePurchases() {
        // AQUI o usuário clicou explicitamente
        isManualRestore = true

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            billingRepository.refreshActivePurchases()
        }
    }

    private fun observePurchaseResults() {
        viewModelScope.launch {
            billingRepository.purchaseResults.collect { result ->
                when (result) {
                    is BillingPurchaseResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isPurchaseInProgress = false,
                                isLoading = false,
                                successMessage = context.getString(R.string.msg_purchase_success)
                            )
                        }
                        // Reseta a flag após sucesso
                        isManualRestore = false
                    }

                    BillingPurchaseResult.Pending -> {
                        _uiState.update {
                            it.copy(
                                isPurchaseInProgress = false,
                                isLoading = false,
                                errorMessage = context.getString(R.string.msg_purchase_pending)
                            )
                        }
                    }

                    BillingPurchaseResult.Cancelled -> {
                        _uiState.update {
                            it.copy(
                                isPurchaseInProgress = false,
                                isLoading = false,
                                errorMessage = context.getString(R.string.msg_purchase_cancelled)
                            )
                        }
                        isManualRestore = false
                    }

                    is BillingPurchaseResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isPurchaseInProgress = false,
                                isLoading = false,
                                errorMessage = context.getString(
                                    R.string.msg_purchase_error,
                                    result.message
                                )
                            )
                        }
                        isManualRestore = false
                    }

                    BillingPurchaseResult.Empty -> {
                        // A LÓGICA MÁGICA ESTÁ AQUI:
                        if (isManualRestore) {
                            // Se foi manual e veio vazio -> Avisa o usuário
                            _uiState.update {
                                it.copy(
                                    isPurchaseInProgress = false,
                                    isLoading = false,
                                    errorMessage = context.getString(R.string.msg_restore_empty)
                                )
                            }
                        } else {
                            // Se foi automático (init) e veio vazio -> Apenas para o loading, sem erro
                            _uiState.update {
                                it.copy(
                                    isPurchaseInProgress = false,
                                    isLoading = false
                                    // errorMessage = null (Mantém nulo para não mostrar snackbar)
                                )
                            }
                        }
                        // Reseta a flag
                        isManualRestore = false
                    }
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}