package com.luizeduardobrandao.appreceitascha.domain.billing

import com.luizeduardobrandao.appreceitascha.domain.auth.PlanConstants

/**
 * IDs de produto usados na Google Play e espelhados em /userPlans.
 * Mantém 1 fonte de verdade via [PlanConstants].
 */
object BillingProductsIds {
    const val PLAN_LIFETIME = PlanConstants.PLAN_ID_LIFE
    val INAPP_IDS = listOf(PLAN_LIFETIME)
}