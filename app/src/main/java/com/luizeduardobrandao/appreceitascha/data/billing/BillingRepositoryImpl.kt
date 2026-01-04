package com.luizeduardobrandao.appreceitascha.data.billing

import android.app.Activity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.google.firebase.functions.FirebaseFunctions
import com.luizeduardobrandao.appreceitascha.domain.auth.AuthRepository
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingLaunchResult
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingPlan
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingProductsIds
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingPurchaseResult
import com.luizeduardobrandao.appreceitascha.domain.billing.BillingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepositoryImpl @Inject constructor(
    private val billingDataSource: BillingDataSource,
    private val authRepository: AuthRepository,
    private val firebaseFunctions: FirebaseFunctions
) : BillingRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _purchaseResults = MutableSharedFlow<BillingPurchaseResult>(extraBufferCapacity = 1)
    override val purchaseResults: SharedFlow<BillingPurchaseResult> = _purchaseResults.asSharedFlow()

    private val productDetailsCache = mutableMapOf<String, ProductDetails>()

    init {
        scope.launch {
            billingDataSource.purchaseUpdates.collectLatest { update ->
                when (val code = update.billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        // OK pode vir com lista vazia em alguns casos.
                        handlePurchasesList(update.purchases)
                    }

                    BillingClient.BillingResponseCode.USER_CANCELED -> {
                        _purchaseResults.tryEmit(BillingPurchaseResult.Cancelled)
                    }

                    else -> {
                        _purchaseResults.tryEmit(
                            BillingPurchaseResult.Error(
                                message = update.billingResult.debugMessage,
                                responseCode = code
                            )
                        )
                    }
                }
            }
        }
    }

    override suspend fun loadAvailablePlans(): List<BillingPlan> {
        // Pelo contrato do domínio: em erro, lança exceção (tratar no ViewModel) :contentReference[oaicite:3]{index=3}
        val details = billingDataSource.queryInAppProductDetails(BillingProductsIds.INAPP_IDS)

        details.forEach { pd ->
            productDetailsCache[pd.productId] = pd
        }

        return details.map { pd ->
            val formattedPrice = pd.oneTimePurchaseOfferDetails?.formattedPrice.orEmpty()

            // Seu app hoje só oferece vitalício (INAPP não-consumível) :contentReference[oaicite:4]{index=4}
            BillingPlan(
                planId = pd.productId,
                title = pd.name,
                description = pd.description,
                formattedPrice = formattedPrice,
                isSubscription = false,
                durationMonths = null,
                isLifetime = true
            )
        }
    }

    override suspend fun launchPurchase(activity: Activity, plan: BillingPlan): BillingLaunchResult {
        if (authRepository.getCurrentUser() == null) {
            return BillingLaunchResult.Error(
                responseCode = -1,
                debugMessage = "Usuário não logado."
            )
        }

        val pd = productDetailsCache[plan.planId]
            ?: return BillingLaunchResult.Error(
                responseCode = -1,
                debugMessage = "Produto não carregado. Reabra a tela."
            )

        val flowParams = buildInAppFlowParams(pd)
        val billingResult = billingDataSource.launchBillingFlow(activity, flowParams)

        return when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> BillingLaunchResult.LaunchStarted
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> BillingLaunchResult.AlreadyOwned
            BillingClient.BillingResponseCode.USER_CANCELED -> BillingLaunchResult.Canceled
            else -> BillingLaunchResult.Error(
                responseCode = billingResult.responseCode,
                debugMessage = billingResult.debugMessage
            )
        }
    }

    override suspend fun refreshActivePurchases() {
        try {
            val purchases = billingDataSource.queryAllActivePurchases()
            handlePurchasesList(purchases)
        } catch (e: Exception) {
            _purchaseResults.tryEmit(
                BillingPurchaseResult.Error(
                    message = e.message ?: "Falha ao restaurar compras.",
                    responseCode = -1
                )
            )
        }
    }

    private fun buildInAppFlowParams(pd: ProductDetails): BillingFlowParams {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(pd)
            .build()

        return BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
    }

    private suspend fun handlePurchasesList(purchases: List<Purchase>) {
        if (purchases.isEmpty()) {
            _purchaseResults.tryEmit(BillingPurchaseResult.Empty)
            return
        }

        for (purchase in purchases) {
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    val purchaseToken = purchase.purchaseToken
                    val orderId = purchase.orderId
                    val productId = purchase.products.firstOrNull() ?: BillingProductsIds.PLAN_LIFETIME

                    // Só processa o produto esperado (vitalício)
                    if (productId != BillingProductsIds.PLAN_LIFETIME) continue

                    // Acknowledge obrigatório para INAPP não-consumível
                    if (!purchase.isAcknowledged) {
                        val ackResult = billingDataSource.acknowledgePurchase(purchaseToken)
                        if (ackResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            _purchaseResults.tryEmit(
                                BillingPurchaseResult.Error(
                                    message = "Falha ao confirmar compra: ${ackResult.debugMessage}",
                                    responseCode = ackResult.responseCode
                                )
                            )
                            continue
                        }
                    }

                    val currentUser = authRepository.getCurrentUser()
                    if (currentUser == null) {
                        _purchaseResults.tryEmit(
                            BillingPurchaseResult.Error(
                                message = "Faça login para validar a compra.",
                                responseCode = -1
                            )
                        )
                        continue
                    }

                    // Validação via Cloud Function (server-side)
                    val syncResult = verifyAndGrantLifetime(
                        packageName = billingDataSource.getPackageName(),
                        productId = productId,
                        purchaseToken = purchaseToken
                    )

                    if (syncResult.isSuccess) {
                        _purchaseResults.tryEmit(
                            BillingPurchaseResult.Success(
                                planId = productId,
                                isSubscription = false,
                                durationMonths = null,
                                isLifetime = true,
                                purchaseToken = purchaseToken,
                                orderId = orderId
                            )
                        )
                    } else {
                        val msg = syncResult.exceptionOrNull()?.message
                            ?: "Falha ao validar compra no servidor."
                        _purchaseResults.tryEmit(
                            BillingPurchaseResult.Error(
                                message = msg,
                                responseCode = -1
                            )
                        )
                    }
                }

                Purchase.PurchaseState.PENDING -> {
                    _purchaseResults.tryEmit(BillingPurchaseResult.Pending)
                }

                else -> {
                    _purchaseResults.tryEmit(BillingPurchaseResult.Cancelled)
                }
            }
        }
    }

    private suspend fun verifyAndGrantLifetime(
        packageName: String,
        productId: String,
        purchaseToken: String
    ): Result<Unit> {
        return try {
            val data = hashMapOf(
                "packageName" to packageName,
                "productId" to productId,
                "purchaseToken" to purchaseToken
            )

            firebaseFunctions
                .getHttpsCallable("verifyAndGrantLifetime")
                .call(data)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}