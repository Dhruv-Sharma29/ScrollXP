package com.scrollxp.app

import com.scrollxp.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class SparkFeaturesTest {
    @Test fun signedReceiptsRejectTamperingWrongKeysAndMalformedSignatures() {
        val generator = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val pair = generator.generateKeyPair()
        val payload = "{\"productId\":\"scrollxp_pro\",\"purchaseState\":0}"
        val signature = Signature.getInstance("SHA1withRSA").run { initSign(pair.private); update(payload.toByteArray()); sign() }
        val key = Base64.getEncoder().encodeToString(pair.public.encoded)
        val signed = Base64.getEncoder().encodeToString(signature)
        assertTrue(PlayReceiptVerifier.validKey(key))
        assertFalse(PlayReceiptVerifier.validKey(""))
        assertFalse(PlayReceiptVerifier.validKey("not-a-play-key"))
        assertTrue(PlayReceiptVerifier.verify(payload,signed,key))
        assertFalse(PlayReceiptVerifier.verify(payload+" ",signed,key))
        assertFalse(PlayReceiptVerifier.verify(payload,signed,Base64.getEncoder().encodeToString(generator.generateKeyPair().public.encoded)))
        assertFalse(PlayReceiptVerifier.verify(payload,"invalid",key))
        assertFalse(PlayReceiptVerifier.verify(payload,signed,""))
    }
    @Test fun proRequiresMatchingPurchasedVerifiedAcknowledgedAndUnsuspendedReceipt() {
        val valid = ProReceipt("scrollxp_pro",true,false,true,true)
        assertTrue(ProAccessPolicy.active(listOf(valid)))
        listOf(valid.copy(product="another"),valid.copy(purchased=false),valid.copy(suspended=true),
            valid.copy(verified=false),valid.copy(acknowledged=false)).forEach { assertFalse(ProAccessPolicy.active(listOf(it))) }
        assertFalse(ProAccessPolicy.active(emptyList()))
    }
    @Test fun friendScoresExcludePartialPriorFutureAndOverlappingDaysAndAreCapped() {
        val day=86400000L
        val days = listOf(BalanceDay(0,day,true),BalanceDay(day,2*day,true),BalanceDay(day+1,2*day+1,true),
            BalanceDay(2*day,3*day,false),BalanceDay(3*day,4*day,true),BalanceDay(4*day,5*day,true))
        assertEquals(2,FriendScorePolicy.score(days,0,day,7*day,4*day))
        assertEquals(0,FriendScorePolicy.score(days,0,6*day,7*day,7*day))
        val many=(0..10).map { BalanceDay(it*day,(it+1)*day,true) }
        assertEquals(7,FriendScorePolicy.score(many,0,0,12*day,12*day))
    }
    @Test fun inviteCodesRejectPathsAndAcceptPastedFormatting() {
        assertEquals("A123456789ABCDEF0123",FriendScorePolicy.normalizeCode(" a123-4567-89ab-cdef-0123 "))
        assertTrue(FriendScorePolicy.validCode("A123456789ABCDEF0123"))
        assertFalse(FriendScorePolicy.validCode("../../users/alice"))
        assertFalse(FriendScorePolicy.validCode("A123"))
    }
}
