package com.luizeduardobrandao.appreceitascha.domain.billing

import com.luizeduardobrandao.appreceitascha.domain.auth.PlanConstants

/**
 * IDs de produto usados na Google Play e espelhados em /userPlans.
 * Mantém 1 fonte de verdade via [PlanConstants].
 */
object BillingProductsIds {
    // Assinaturas
    const val PLAN_3_MONTHS  = PlanConstants.PLAN_ID_3M
    const val PLAN_6_MONTHS  = PlanConstants.PLAN_ID_6M
    const val PLAN_12_MONTHS = PlanConstants.PLAN_ID_12M

    // Compra vitalícia (inapp não consumível)
    const val PLAN_LIFETIME = PlanConstants.PLAN_ID_LIFE

    // Listas utilitárias para consultas
    val SUBSCRIPTION_IDS = listOf(
        PLAN_3_MONTHS,
        PLAN_6_MONTHS,
        PLAN_12_MONTHS
    )

    val INAPP_IDS = listOf(
        PLAN_LIFETIME
    )
}
