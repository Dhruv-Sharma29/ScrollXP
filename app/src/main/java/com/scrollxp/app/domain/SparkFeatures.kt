package com.scrollxp.app.domain

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Local receipt checks are not server verification or a cloud entitlement. */
object PlayReceiptVerifier {
    fun validKey(publicKey: String): Boolean = runCatching {
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))).algorithm == "RSA"
    }.getOrDefault(false)
    fun verify(data: String, signature: String, publicKey: String): Boolean = runCatching {
        val decoder = Base64.getDecoder()
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(decoder.decode(publicKey)))
        Signature.getInstance("SHA1withRSA").run {
            initVerify(key); update(data.toByteArray(Charsets.UTF_8)); verify(decoder.decode(signature))
        }
    }.getOrDefault(false)
}

data class ProReceipt(val product: String, val purchased: Boolean, val suspended: Boolean,
    val verified: Boolean, val acknowledged: Boolean)
object ProAccessPolicy {
    const val PRODUCT = "scrollxp_pro"
    const val BASE_PLAN = "monthly"
    val THEMES = listOf("Ocean", "Rose", "Lavender")
    fun active(receipts: List<ProReceipt>) = receipts.any {
        it.product == PRODUCT && it.purchased && !it.suspended && it.verified && it.acknowledged
    }
}

data class BalanceDay(val start: Long, val end: Long, val complete: Boolean)
object FriendScorePolicy {
    const val WEEK = 7 * 24 * 60 * 60 * 1000L
    fun normalizeCode(input: String): String = input.trim().replace("-", "").uppercase(java.util.Locale.ROOT)
    fun validCode(code: String) = code.matches(Regex("[A-F0-9]{20}"))
    fun score(days: List<BalanceDay>, createdAt: Long, joinedAt: Long, endsAt: Long, now: Long): Int {
        var cursor = maxOf(createdAt, joinedAt)
        var result = 0
        days.filter { it.complete && it.start >= cursor && it.end <= endsAt && it.end <= now && it.end > it.start }
            .sortedBy { it.end }.forEach { day ->
                if (day.start >= cursor) { result++; cursor = day.end }
            }
        return result.coerceAtMost(7)
    }
}
