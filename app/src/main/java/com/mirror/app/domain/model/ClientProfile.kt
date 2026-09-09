package com.mirror.app.domain.model

/**
 * Groups saved looks under either the artist's own profile ("self") or a named client,
 * so a makeup artist can keep separate history per person they work on.
 */
data class ClientProfile(
    val id: String,
    val name: String,
    val notes: String = "",
    val savedLookIds: List<String> = emptyList(),
    val tag: ClientTag = ClientTag.SELF
)

enum class ClientTag {
    SELF,
    CLIENT
}
