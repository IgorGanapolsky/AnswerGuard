package com.igorganapolsky.answerguard.billing

enum class EntitlementLevel {
    NONE,
    PRO,
    FAMILY,
    BUSINESS,
    ;

    val isPro: Boolean get() = this != NONE
}
