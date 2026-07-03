// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.domain.model

/**
 * Paid-plan model for Play Store distribution.
 *
 * The app sells DIGITAL app functionality (Glyph effects, premium themes,
 * customization). On Google Play those purchases must be handled by Google Play
 * Billing, so this model deliberately contains Play product IDs instead of
 * Razorpay/Stripe/UPI checkout links.
 */
enum class AppPlan(val rank: Int) {
    FREE(rank = 0),
    BASIC(rank = 1),
    PRO(rank = 2);

    fun includes(required: AppPlan): Boolean = rank >= required.rank
}

enum class ProductBillingType {
    ONE_TIME,
    SUBSCRIPTION,
}

data class MonetizationProduct(
    val productId: String,
    val plan: AppPlan,
    val billingType: ProductBillingType,
    val displayName: String,
    val targetPriceInr: Int,
)

enum class PaidFeature(val requiredPlan: AppPlan) {
    GLYPH_INCOMING_FLASH(AppPlan.BASIC),
    CONTACT_GLYPH_PATTERNS(AppPlan.BASIC),
    RINGTONE_GLYPH_SYNC(AppPlan.BASIC),
    GLYPH_EFFECT_PREVIEW(AppPlan.BASIC),
    PREMIUM_THEMES(AppPlan.PRO),
    CUSTOM_GLYPH_CREATOR(AppPlan.PRO),
    IMPORT_EXPORT_PRESETS(AppPlan.PRO),
    CONTACT_GROUP_GLYPH(AppPlan.PRO),
    CALL_PERSONALITY_PROFILES(AppPlan.PRO),
    CHARGING_GLYPH_MODE(AppPlan.PRO),
}

object MonetizationCatalog {
    const val BASIC_ONE_TIME_ID = "glyph_dialer_basic_49"
    const val PRO_ONE_TIME_ID = "glyph_dialer_pro_99"

    val products: List<MonetizationProduct> = listOf(
        MonetizationProduct(
            productId = BASIC_ONE_TIME_ID,
            plan = AppPlan.BASIC,
            billingType = ProductBillingType.ONE_TIME,
            displayName = "Glyph Dialer Basic",
            targetPriceInr = 49,
        ),
        MonetizationProduct(
            productId = PRO_ONE_TIME_ID,
            plan = AppPlan.PRO,
            billingType = ProductBillingType.ONE_TIME,
            displayName = "Glyph Dialer Pro",
            targetPriceInr = 99,
        ),
    )

    fun canUse(plan: AppPlan, feature: PaidFeature): Boolean =
        plan.includes(feature.requiredPlan)
}
