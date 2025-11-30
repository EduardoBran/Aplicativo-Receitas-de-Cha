package com.luizeduardobrandao.appreceitascha.domain.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/**
 * Identificadores dos produtos de Billing na Google Play.
 *
 * ATENÇÃO:
 * - Esses IDs DEVEM ser exatamente os mesmos configurados no Google Play Console.
 * - 3, 6 e 12 meses: assinaturas (SUBS).
 * - Vitalício: produto in-app não consumível (INAPP).
 */

/**
 * Representa um plano que o app exibe na tela de planos.
 *
 * Dados amigáveis para UI:
 * - título / descrição vindos da Play Store;
 * - preço formatado pela própria Play Store (R$ xx,xx);
 * - metadados de negócio (meses, lifetime, se é assinatura etc.).
 */
data class BillingPlan(
    val planId: String,          // Ex.: "plan_3m" (igual ao productId no Play Console)
    val title: String,           // Título vindo de ProductDetails.title
    val description: String,     // Descrição vindo de ProductDetails.description
    val formattedPrice: String,  // Ex.: "R$ 19,90"
    val isSubscription: Boolean, // true para 3/6/12 meses; false para vitalício
    val durationMonths: Int?,    // 3, 6, 12 ou null para vitalício
    val isLifetime: Boolean      // true somente para o plano vitalício
)

/**
 * Resultado de iniciar o fluxo de compra (abrir UI da Google Play).
 */
sealed class BillingLaunchResult {
    /** A tela de compra foi aberta com sucesso. O resultado virá em [BillingPurchaseResult] via Flow. */
    data object LaunchStarted : BillingLaunchResult()

    /**
     * A compra não pôde ser iniciada (erro técnico ou configuração de produto).
     *
     * @param message Mensagem para log / debug, você pode transformar em algo amigável na UI.
     * @param responseCode Código de resposta da BillingLibrary (BillingClient.BillingResponseCode).
     */
    data class Error(
        val message: String,
        val responseCode: Int?
    ) : BillingLaunchResult()
}

/**
 * Resultado de uma compra já tratado pelo backend de Billing:
 *
 * - Se for sucesso:
 *   • A compra já foi ACKNOWLEDGED (confirmada) na Google Play.
 *   • Você recebe dados suficientes para atualizar /userPlans/{uid}.
 *
 * - Se for cancelada/erro:
 *   • Você pode exibir mensagens amigáveis na UI.
 */
sealed class BillingPurchaseResult {

    /**
     * Compra concluída com sucesso E já confirmada (acknowledged).
     *
     * Use:
     * - [planId], [isSubscription], [durationMonths], [isLifetime]
     *   → para atualizar /userPlans/{uid} e o estado de plano no app.
     *
     * - [purchaseToken], [orderId] → para logs, backend, suporte etc.
     */
    data class Success(
        val planId: String,
        val isSubscription: Boolean,
        val durationMonths: Int?,
        val isLifetime: Boolean,
        val purchaseToken: String,
        val orderId: String?
    ) : BillingPurchaseResult()

    /**
     * Compra iniciada mas ainda em estado PENDING (pagamento não confirmado).
     * Ex.: boleto, algum método que demora.
     */
    data object Pending : BillingPurchaseResult()

    /**
     * Usuário cancelou o fluxo de compra na UI da Google Play.
     */
    data object Cancelled : BillingPurchaseResult()

    /**
     * Chamamos query de compras e não havia nada relevante (pode ser ignorado).
     */
    data object Empty : BillingPurchaseResult()

    /**
     * Erro técnico da BillingLibrary ou caso não mapeado.
     */
    data class Error(
        val message: String,
        val responseCode: Int?
    ) : BillingPurchaseResult()
}

/**
 * Repositório de Billing exposto para a camada de UI (ex.: PlansViewModel).
 *
 * Responsabilidades:
 * - Conectar ao Google Play Billing.
 * - Carregar detalhes dos produtos (3m, 6m, 12m, vitalício).
 * - Iniciar o fluxo de compra.
 * - Confirmar (acknowledge) compras bem-sucedidas.
 * - Expor resultados via Flow para a UI poder:
 *      • mostrar mensagens
 *      • atualizar /userPlans/{uid} via Auth/UserPlanRepository
 */
interface BillingRepository {

    /**
     * Fluxo com atualizações de compra:
     * - Success: compra confirmada (acknowledged) e pronta para atualizar plano.
     * - Cancelled: usuário cancelou.
     * - Pending: aguardando confirmação do método de pagamento.
     * - Error: problema técnico.
     * - Empty: nenhuma compra relevante encontrada.
     */
    val purchaseResults: Flow<BillingPurchaseResult>

    /**
     * Carrega todos os planos disponíveis na Play Store (3m, 6m, 12m, vitalício).
     * Retorna uma lista já pronta para exibir na UI.
     *
     * Em caso de erro, lança exceção (que você pode tratar no ViewModel com try/catch).
     */
    suspend fun loadAvailablePlans(): List<BillingPlan>

    /**
     * Inicia o fluxo de compra para um [plan].
     * O resultado detalhado (sucesso, erro, cancelamento) virá depois em [purchaseResults].
     */
    suspend fun launchPurchase(
        activity: Activity,
        plan: BillingPlan
    ): BillingLaunchResult

    /**
     * Garante que compras ativas existentes (ex.: usuário reinstalou app ou trocou de device)
     * serão lidas e emitidas em [purchaseResults] (já com acknowledge se necessário).
     *
     * Chame, por exemplo:
     * - No início da PlansFragment
     * - No startup do app (ex.: em um SessionManager)
     */
    suspend fun refreshActivePurchases()
}