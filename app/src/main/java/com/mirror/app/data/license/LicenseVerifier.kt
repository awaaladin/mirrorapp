package com.mirror.app.data.license

import com.mirror.app.domain.model.ProPlan
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Serializable
private data class LicenseClaims(
    @SerialName("sub") val userId: String,
    val plan: String,
    @SerialName("did") val deviceId: String,
    @SerialName("exp") val expiresAtSeconds: Long
)

/**
 * Checks a license from mirrorcentral's app/core/license.py with no network: its signature, that
 * it's for this account and device, and that it hasn't lapsed. Anything else is [ProPlan.FREE] -
 * the free app keeps working either way, so a bad license never locks anyone out of it.
 */
class LicenseVerifier(publicKeyBase64: String = PUBLIC_KEY) {
    private val publicKey: PublicKey = KeyFactory.getInstance("EC")
        .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)))
    private val json = Json { ignoreUnknownKeys = true }

    fun planFrom(
        license: String,
        userId: String,
        deviceId: String,
        nowSeconds: Long = System.currentTimeMillis() / 1000
    ): ProPlan {
        val parts = license.split('.')
        if (parts.size != 2) return ProPlan.FREE
        return try {
            val decoder = Base64.getUrlDecoder()
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(publicKey)
            signature.update(parts[0].toByteArray())
            if (!signature.verify(decoder.decode(parts[1]))) return ProPlan.FREE

            val claims = json.decodeFromString<LicenseClaims>(String(decoder.decode(parts[0])))
            val valid = claims.userId == userId && claims.deviceId == deviceId && claims.expiresAtSeconds > nowSeconds
            if (valid) ProPlan.fromWire(claims.plan) else ProPlan.FREE
        } catch (e: GeneralSecurityException) {
            ProPlan.FREE
        } catch (e: IllegalArgumentException) {
            ProPlan.FREE // Malformed base64 or JSON.
        }
    }

    private companion object {
        const val PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEDdRWMU4ArwdI7Y7a38rhUk/YM94TxI0CgHYISxB92RLUtMHSqIo1Y8wI/9XJ6Tm1RHmQDCCWnO6s8vJmQk7VBg=="
    }
}
