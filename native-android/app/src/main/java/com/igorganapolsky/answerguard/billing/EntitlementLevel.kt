package com.igorganapolsky.answerguard.billing

enum class EntitlementLevel {
    NONE,
    PRO,
    FAMILY,
    ;

    val isPro: Boolean get() = this != NONE
}
