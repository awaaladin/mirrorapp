package com.mirror.app.domain.model

/** The signed-in user, independent of which auth provider backs it (the custom mirror-backend server today). */
data class AuthUser(
    val uid: String,
    val email: String?,
    val accountType: AccountType = AccountType.CONSUMER
)

/** Mirrors mirrorcentral's `AccountType` enum ("consumer" / "business"). Business accounts are
 *  the Pro/MUA tier that unlocks Client Mode and multi-client management. */
enum class AccountType {
    CONSUMER,
    BUSINESS;

    companion object {
        fun fromWire(value: String?): AccountType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CONSUMER
    }
}
