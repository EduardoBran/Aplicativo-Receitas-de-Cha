package com.luizeduardobrandao.appreceitascha.data.billing

import android.app.Activity
import android.app.Application
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Camada de acesso CRU ao Google Play Billing.
 *
 * Responsável por:
 *  - Manter e conectar o [BillingClient].
 *  - Expor:
 *      • consulta de produtos (SUBS / INAPP)
 *      • consulta de compras ativas
 *      • launchBillingFlow
 *      • acknowledgePurchase
 *      • fluxo bruto de atualizações de compra.
 */
class BillingDataSource(
    application: Application
) : PurchasesUpdatedListener {

    /**
     * Evento bruto vindo do callback [onPurchasesUpdated].
     */
    data class RawPurchaseUpdate(
        val billingResult: BillingResult,
        val purchases: List<Purchase>
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val billingClient: BillingClient = BillingClient
        .newBuilder(application)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                // Habilita suporte a compras pendentes para produtos únicos (INAPP).
                // Se quiser incluir assinaturas pendentes, use o método equivalente
                // (quando disponível na sua versão da lib).
                .enableOneTimeProducts()
                .build()
        )
        .build()

    // Fluxo interno de atualizações de compra da BillingLibrary.
    private val _purchaseUpdates = MutableSharedFlow<RawPurchaseUpdate>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val purchaseUpdates: SharedFlow<RawPurchaseUpdate> = _purchaseUpdates.asSharedFlow()

    @Volatile
    private var isConnected: Boolean = false

    /**
     * Garante que o BillingClient está conectado antes de qualquer operação.
     */
    private suspend fun ensureConnected() {
        if (isConnected && billingClient.isReady) return

        suspendCancellableCoroutine<Unit> { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingResponseCode.OK) {
                        isConnected = true
                        if (cont.isActive) cont.resume(Unit)
                    } else {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IllegalStateException(
                                    "Billing setup failed: ${billingResult.debugMessage}"
                                )
                            )
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    isConnected = false
                    // Na próxima chamada a ensureConnected() vamos reconectar.
                }
            })
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        scope.launch {
            _purchaseUpdates.emit(
                RawPurchaseUpdate(
                    billingResult = billingResult,
                    purchases = purchases.orEmpty()
                )
            )
        }
    }

    /**
     * Busca detalhes de produtos de ASSINATURA (SUBS) na Play Store.
     */
    suspend fun querySubscriptionProductDetails(
        productIds: List<String>
    ): List<ProductDetails> {
        if (productIds.isEmpty()) return emptyList()

        ensureConnected()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(ProductType.SUBS)
                        .build()
                }
            )
            .build()

        // Função suspensa da billing-ktx
        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingResponseCode.OK) {
            return result.productDetailsList.orEmpty()
        } else {
            throw IllegalStateException(
                "Erro ao buscar produtos SUBS: ${result.billingResult.debugMessage}"
            )
        }
    }

    /**
     * Busca detalhes de produtos INAPP (compra única) na Play Store.
     */
    suspend fun queryInAppProductDetails(
        productIds: List<String>
    ): List<ProductDetails> {
        if (productIds.isEmpty()) return emptyList()

        ensureConnected()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                productIds.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(ProductType.INAPP)
                        .build()
                }
            )
            .build()

        val result = billingClient.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingResponseCode.OK) {
            return result.productDetailsList.orEmpty()
        } else {
            throw IllegalStateException(
                "Erro ao buscar produtos INAPP: ${result.billingResult.debugMessage}"
            )
        }
    }

    /**
     * Inicia o fluxo de compra (UI da Google Play).
     * O resultado final virá depois via [onPurchasesUpdated].
     */
    suspend fun launchBillingFlow(
        activity: Activity,
        params: BillingFlowParams
    ): BillingResult {
        ensureConnected()
        return billingClient.launchBillingFlow(activity, params)
    }

    /**
     * Confirma a compra (ACKNOWLEDGE) na Google Play.
     * Obrigatório para assinaturas e INAPP não consumíveis.
     */
    suspend fun acknowledgePurchase(purchaseToken: String): BillingResult {
        ensureConnected()

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.acknowledgePurchase(params) { result ->
                if (cont.isActive) {
                    cont.resume(result)
                }
            }
        }
    }

    /**
     * Busca todas as compras ativas (SUBS + INAPP).
     * Útil para restaurar acesso em novo dispositivo ou após reinstalação.
     */
    suspend fun queryAllActivePurchases(): List<Purchase> {
        ensureConnected()

        val subs = suspendCancellableCoroutine<List<Purchase>> { cont ->
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(ProductType.SUBS)
                    .build()
            ) { result, purchases ->
                if (result.responseCode == BillingResponseCode.OK) {
                    cont.resume(purchases)
                } else {
                    cont.resumeWithException(
                        IllegalStateException(
                            "Erro ao buscar compras SUBS: ${result.debugMessage}"
                        )
                    )
                }
            }
        }

        val inapps = suspendCancellableCoroutine<List<Purchase>> { cont ->
            billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(ProductType.INAPP)
                    .build()
            ) { result, purchases ->
                if (result.responseCode == BillingResponseCode.OK) {
                    cont.resume(purchases)
                } else {
                    cont.resumeWithException(
                        IllegalStateException(
                            "Erro ao buscar compras INAPP: ${result.debugMessage}"
                        )
                    )
                }
            }
        }

        return subs + inapps
    }
}