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
import java.time.Instant
import java.time.temporal.ChronoUnit
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
     * - Se for temporal (3, 6, 12 meses) → calcula expiresAtMillis somando meses à data atual.
     *
     * OBS: esse cálculo de meses pressupõe que você está usando desugaring de Java Time.
     */
    private suspend fun persistPlanForCurrentUser(
        planId: String,
        isLifetime: Boolean,
        durationMonths: Int?
    ): Result<Unit> {
        val currentUser = authRepository.getCurrentUser()
            ?: return Result.failure(
                IllegalStateException("Usuário não autenticado ao salvar plano.")
            )

        val planType = planId.toPlanType()

        // Segurança: se ID não mapeia para um plano conhecido, grava SEM_PLANO.
        if (planType == PlanType.NONE) {
            val userPlan = UserPlan(
                uid = currentUser.uid,
                planType = PlanType.NONE,
                expiresAtMillis = null,
                isLifetime = false
            )
            return authRepository.updateUserPlan(userPlan)
        }

        val expiresAtMillis: Long? = if (isLifetime) {
            null
        } else {
            durationMonths?.let { months ->
                val now = Instant.now()
                now.plus(months.toLong(), ChronoUnit.MONTHS).toEpochMilli()
            }
        }

        val userPlan = UserPlan(
            uid = currentUser.uid,
            planType = planType,
            expiresAtMillis = expiresAtMillis,
            isLifetime = isLifetime
        )

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
                    val persistResult = persistPlanForCurrentUser(
                        planId = meta.planId,
                        isLifetime = meta.isLifetime,
                        durationMonths = meta.months
                    )

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
            BillingProductsIds.PLAN_3_MONTHS -> PlanMeta(
                planId = BillingProductsIds.PLAN_3_MONTHS,
                isSubscription = true,
                months = 3,
                isLifetime = false
            )

            BillingProductsIds.PLAN_6_MONTHS -> PlanMeta(
                planId = BillingProductsIds.PLAN_6_MONTHS,
                isSubscription = true,
                months = 6,
                isLifetime = false
            )

            BillingProductsIds.PLAN_12_MONTHS -> PlanMeta(
                planId = BillingProductsIds.PLAN_12_MONTHS,
                isSubscription = true,
                months = 12,
                isLifetime = false
            )

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
        val subsDetails = billingDataSource.querySubscriptionProductDetails(
            BillingProductsIds.SUBSCRIPTION_IDS
        )
        val inAppDetails = billingDataSource.queryInAppProductDetails(
            BillingProductsIds.INAPP_IDS
        )

        val allDetails = subsDetails + inAppDetails
        cachedProductDetails = allDetails.associateBy { it.productId }

        allDetails.mapNotNull { pd ->
            val meta = mapProductIdToMeta(pd.productId) ?: return@mapNotNull null

            val formattedPrice = extractFormattedPrice(pd)
            BillingPlan(
                planId = meta.planId,
                title = pd.title,
                description = pd.description,
                formattedPrice = formattedPrice,
                isSubscription = meta.isSubscription,
                durationMonths = meta.months,
                isLifetime = meta.isLifetime
            )
        }.sortedBy { plan ->
            // Vitalício (months = null) vai para o final da lista
            plan.durationMonths ?: Int.MAX_VALUE
        }
    }

    /**
     * Extrai o preço formatado do [ProductDetails], seja SUBS ou INAPP.
     */
    private fun extractFormattedPrice(productDetails: ProductDetails): String {
        // INAPP
        productDetails.oneTimePurchaseOfferDetails?.let { oneTime ->
            return oneTime.formattedPrice
        }

        // SUBS
        val subOffer = productDetails.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()

        return subOffer?.formattedPrice ?: ""
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

        val flowParams = buildBillingFlowParams(pd, plan.isSubscription)
            ?: return@withContext BillingLaunchResult.Error(
                message = "Não foi possível montar os parâmetros de compra para ${plan.planId}.",
                responseCode = null
            )

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
        val meta = mapProductIdToMeta(planId) ?: return null

        val list = if (meta.isSubscription) {
            billingDataSource.querySubscriptionProductDetails(listOf(planId))
        } else {
            billingDataSource.queryInAppProductDetails(listOf(planId))
        }

        if (list.isEmpty()) return null

        // Atualiza cache
        cachedProductDetails = cachedProductDetails + list.associateBy { it.productId }

        return list.firstOrNull()
    }

    /**
     * Monta [BillingFlowParams] adequados para SUBS ou INAPP.
     */
    private fun buildBillingFlowParams(
        productDetails: ProductDetails,
        isSubscription: Boolean
    ): BillingFlowParams? {
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)

        if (isSubscription) {
            val offerDetails = productDetails.subscriptionOfferDetails?.firstOrNull()
                ?: return null

            productParamsBuilder.setOfferToken(offerDetails.offerToken)
        }

        val productParams = productParamsBuilder.build()

        return BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
    }

    /**
     * Lê compras ativas (SUBS + INAPP) e emite eventos em [purchaseResults].
     * Útil para restaurar compras em novo dispositivo.
     */
    override suspend fun refreshActivePurchases() {
        val purchases = billingDataSource.queryAllActivePurchases()
        handlePurchasesList(purchases)
    }
}