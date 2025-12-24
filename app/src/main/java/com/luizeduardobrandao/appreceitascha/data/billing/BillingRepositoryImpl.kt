package com.luizeduardobrandao.appreceitascha.data.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.auth.PlanType
import com.luizeduardobrandao.appreceitascha.domain.auth.UserPlan
import com.luizeduardobrandao.appreceitascha.domain.auth.toPlanType
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingLaunchResult
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingPlan
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingProductsIds
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingPurchaseResult
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implementação concreta de [BillingRepository].
 *
 * Responsável por:
 *  - Orquestrar [BillingDataSource].
 *  - Mapear ProductDetails → [BillingPlan].
 *  - Tratar compras:
 *      • acknowledge automático em PURCHASED.
 *      • atualização de /userPlans/{uid} via [AuthRepository].
 *      • emissão de [BillingPurchaseResult] pronto para ViewModels.
 */
@Singleton
class BillingRepositoryImpl @Inject constructor(
    private val billingDataSource: BillingDataSource,
    private val authRepository: AuthRepository
) : BillingRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Cache em memória: productId -> ProductDetails
    private var cachedProductDetails: Map<String, ProductDetails> = emptyMap()

    private val _purchaseResults = MutableSharedFlow<BillingPurchaseResult>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val purchaseResults: SharedFlow<BillingPurchaseResult> =
        _purchaseResults.asSharedFlow()

    init {
        // Converte eventos crus do BillingDataSource em resultados de domínio.
        scope.launch {
            billingDataSource.purchaseUpdates.collectLatest { raw ->
                handleRawPurchaseUpdate(
                    responseCode = raw.billingResult.responseCode,
                    debugMessage = raw.billingResult.debugMessage,
                    purchases = raw.purchases
                )
            }
        }
    }

    /**
     * Persiste o plano comprado em /userPlans/{uid} usando o [AuthRepository].
     *
     * - Se planId → NONE ou desconhecido, cai em SEM_PLANO.
     * - Se for vitalício → expiresAtMillis = null, isLifetime = true.
     */
    private suspend fun persistPlanForCurrentUser(planId: String): Result<Unit> {
        val currentUser = authRepository.getCurrentUser()
            ?: return Result.failure(IllegalStateException("Usuário não autenticado ao salvar plano."))

        val planType = planId.toPlanType()

        val userPlan = if (planType == PlanType.PLAN_LIFE) {
            UserPlan(
                uid = currentUser.uid,
                planType = PlanType.PLAN_LIFE,
                expiresAtMillis = null,
                isLifetime = true
            )
        } else {
            UserPlan(
                uid = currentUser.uid,
                planType = PlanType.NONE,
                expiresAtMillis = null,
                isLifetime = false
            )
        }

        return authRepository.updateUserPlan(userPlan)
    }

    /**
     * Converte o resultado bruto da BillingLibrary em [BillingPurchaseResult].
     */
    private suspend fun handleRawPurchaseUpdate(
        responseCode: Int,
        debugMessage: String,
        purchases: List<Purchase>
    ) {
        when (responseCode) {
            BillingResponseCode.OK -> handlePurchasesList(purchases)
            BillingResponseCode.USER_CANCELED -> {
                _purchaseResults.emit(BillingPurchaseResult.Cancelled)
            }
            else -> {
                _purchaseResults.emit(
                    BillingPurchaseResult.Error(
                        message = debugMessage,
                        responseCode = responseCode
                    )
                )
            }
        }
    }

    /**
     * Trata compras vindas de:
     *  - onPurchasesUpdated (código OK).
     *  - queryAllActivePurchases() (restauração).
     */
    private suspend fun handlePurchasesList(purchases: List<Purchase>) {
        if (purchases.isEmpty()) {
            _purchaseResults.emit(BillingPurchaseResult.Empty)
            return
        }

        for (purchase in purchases) {
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    // Confirma compra se ainda não foi confirmada
                    if (!purchase.isAcknowledged) {
                        val ackResult =
                            billingDataSource.acknowledgePurchase(purchase.purchaseToken)
                        if (ackResult.responseCode != BillingResponseCode.OK) {
                            _purchaseResults.emit(
                                BillingPurchaseResult.Error(
                                    message = "Falha ao confirmar compra: ${ackResult.debugMessage}",
                                    responseCode = ackResult.responseCode
                                )
                            )
                            continue
                        }
                    }

                    val productId = purchase.products.firstOrNull()
                    val meta = productId?.let { mapProductIdToMeta(it) }

                    if (productId == null || meta == null) {
                        _purchaseResults.emit(
                            BillingPurchaseResult.Error(
                                message = "Produto desconhecido na compra.",
                                responseCode = null
                            )
                        )
                        continue
                    }

                    // Atualiza /userPlans/{uid}
                    val persistResult = persistPlanForCurrentUser(planId = meta.planId)

                    if (persistResult.isFailure) {
                        _purchaseResults.emit(
                            BillingPurchaseResult.Error(
                                message = "Compra confirmada, mas falhou ao salvar plano do usuário.",
                                responseCode = null
                            )
                        )
                        continue
                    }

                    // Tudo certo: plano salvo e compra confirmada.
                    _purchaseResults.emit(
                        BillingPurchaseResult.Success(
                            planId = meta.planId,
                            isSubscription = meta.isSubscription,
                            durationMonths = meta.months,
                            isLifetime = meta.isLifetime,
                            purchaseToken = purchase.purchaseToken,
                            orderId = purchase.orderId
                        )
                    )
                }

                Purchase.PurchaseState.PENDING -> {
                    _purchaseResults.emit(BillingPurchaseResult.Pending)
                }

                Purchase.PurchaseState.UNSPECIFIED_STATE -> {
                    // Ignorado.
                }
            }
        }
    }

    /**
     * Metadados internos para mapear productId → regra de negócio.
     */
    private data class PlanMeta(
        val planId: String,
        val isSubscription: Boolean,
        val months: Int?,
        val isLifetime: Boolean
    )

    private fun mapProductIdToMeta(productId: String): PlanMeta? {
        return when (productId) {
            BillingProductsIds.PLAN_LIFETIME -> PlanMeta(
                planId = BillingProductsIds.PLAN_LIFETIME,
                isSubscription = false,
                months = null,
                isLifetime = true
            )
            else -> null
        }
    }

    /**
     * Carrega todos os planos configurados na Play Store e
     * converte para [BillingPlan] com dados amigáveis.
     */
    override suspend fun loadAvailablePlans(): List<BillingPlan> = withContext(Dispatchers.IO) {
        val inAppDetails = billingDataSource.queryInAppProductDetails(BillingProductsIds.INAPP_IDS)

        cachedProductDetails = inAppDetails.associateBy { it.productId }

        inAppDetails.mapNotNull { pd ->
            val meta = mapProductIdToMeta(pd.productId) ?: return@mapNotNull null
            BillingPlan(
                planId = meta.planId,
                title = pd.title,
                description = pd.description,
                formattedPrice = extractFormattedPrice(pd),
                isSubscription = false,
                durationMonths = null,
                isLifetime = true
            )
        }
    }

    /**
     * Extrai o preço formatado do [ProductDetails], seja SUBS ou INAPP.
     */
    private fun extractFormattedPrice(productDetails: ProductDetails): String {
        return productDetails.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
    }

    /**
     * Inicia o fluxo de compra.
     *
     * A resposta detalhada virá depois em [purchaseResults].
     */
    override suspend fun launchPurchase(
        activity: Activity,
        plan: BillingPlan
    ): BillingLaunchResult = withContext(Dispatchers.IO) {
        val cached = cachedProductDetails[plan.planId]
        val pd = cached ?: reloadSingleProductDetails(plan.planId)

        if (pd == null) {
            return@withContext BillingLaunchResult.Error(
                message = "Plano não encontrado na Google Play para o id: ${plan.planId}",
                responseCode = null
            )
        }

        val flowParams = buildBillingFlowParams(pd)

        val result = billingDataSource.launchBillingFlow(activity, flowParams)

        if (result.responseCode == BillingResponseCode.OK) {
            BillingLaunchResult.LaunchStarted
        } else {
            BillingLaunchResult.Error(
                message = "Falha ao iniciar compra: ${result.debugMessage}",
                responseCode = result.responseCode
            )
        }
    }

    /**
     * Recarrega detalhes de um único produto se o cache estiver vazio para ele.
     */
    private suspend fun reloadSingleProductDetails(planId: String): ProductDetails? {
        val list = billingDataSource.queryInAppProductDetails(listOf(planId))
        val pd = list.firstOrNull()
        if (pd != null) cachedProductDetails = cachedProductDetails + (pd.productId to pd)
        return pd
    }

    /**
     * Monta [BillingFlowParams] adequados para SUBS ou INAPP.
     */
    private fun buildBillingFlowParams(productDetails: ProductDetails): BillingFlowParams {
        val params = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .build()

        return BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(params))
            .build()
    }

    /**
     * Lê compras ativas (INAPP) e emite eventos em [purchaseResults].
     * Útil para restaurar compras em novo dispositivo.
     */
    override suspend fun refreshActivePurchases() {
        val purchases = billingDataSource.queryAllActivePurchases()
        handlePurchasesList(purchases)
    }
}