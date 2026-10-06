package com.scrollxp.app.online

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.*
import com.scrollxp.app.BuildConfig
import com.scrollxp.app.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

data class BillingState(val configured: Boolean = PlayReceiptVerifier.validKey(BuildConfig.PLAY_LICENSE_KEY),
    val busy: Boolean = false, val active: Boolean = false, val purchaseBlocked: Boolean = false,
    val checkoutOpen: Boolean = false, val price: String? = null,
    val message: String? = null, val theme: String? = null)

/** Play-account purchases unlock local features only. No purchase data goes to Firestore. */
class PlayBillingModel(app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("pro_preferences", android.content.Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(BillingState(theme = preferences.getString("theme", null)?.takeIf { it in ProAccessPolicy.THEMES }))
    val state = mutable.asStateFlow()
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "theme" || key == null) mutable.update { it.copy(theme = preferences.getString("theme", null)?.takeIf { value -> value in ProAccessPolicy.THEMES }) }
    }
    init { preferences.registerOnSharedPreferenceChangeListener(preferenceListener) }
    private val lock = Mutex()
    private var details: ProductDetails? = null
    private var offer: String? = null
    private val client = BillingClient.newBuilder(app).setListener { result, _ ->
        mutable.update { it.copy(checkoutOpen = false) }
        // A payment callback must queue a fresh check even if the resume query is still running.
        if (result.responseCode == BillingClient.BillingResponseCode.OK || result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) viewModelScope.launch { updatePurchases() }
        else mutable.update { it.copy(message = if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) "Purchase cancelled. Your free island is unchanged." else "Purchase could not be completed. Refresh purchases before trying again.") }
    }.enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()

    fun theme(value: String?) {
        if (value != null && (!state.value.active || value !in ProAccessPolicy.THEMES)) return
        preferences.edit().putString("theme", value).apply()
        mutable.update { it.copy(theme = value) }
    }
    fun refresh() { if (!mutable.value.busy) viewModelScope.launch { updatePurchases() } }
    private suspend fun connect(): BillingResult {
        if (client.isReady) return BillingResult.newBuilder().setResponseCode(BillingClient.BillingResponseCode.OK).build()
        return suspendCancellableCoroutine { continuation -> client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) { if (continuation.isActive) continuation.resume(result) }
            override fun onBillingServiceDisconnected() { }
        }) }
    }
    private suspend fun updatePurchases(): Boolean = lock.withLock {
        if (!mutable.value.configured) {
            mutable.update { it.copy(message = "Subscriptions are waiting for Play Store setup. Free features are available.") }
            return@withLock false
        }
        mutable.update { it.copy(busy = true, purchaseBlocked = true, checkoutOpen = false, message = null, price = null) }
        details = null; offer = null
        var purchasesChecked = false
        try {
            withTimeout(20_000) {
                check(connect().responseCode == BillingClient.BillingResponseCode.OK) { "Open Google Play and check your connection, then refresh purchases." }
                val (result, purchases) = suspendCancellableCoroutine<Pair<BillingResult,List<Purchase>>> { c ->
                    client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS)
                        .includeSuspendedSubscriptions(true).build()) { r, p -> if (c.isActive) c.resume(r to p) }
                }
                check(result.responseCode == BillingClient.BillingResponseCode.OK) { "Could not check subscriptions. Refresh purchases when connected to Google Play." }
                val receipts = purchases.filter { ProAccessPolicy.PRODUCT in it.products }.map { p ->
                    val verified = PlayReceiptVerifier.verify(p.originalJson, p.signature, BuildConfig.PLAY_LICENSE_KEY)
                    var acknowledged = p.isAcknowledged
                    if (p.purchaseState == Purchase.PurchaseState.PURCHASED && !p.isSuspended && verified && !acknowledged) {
                        val ack = suspendCancellableCoroutine<BillingResult> { c ->
                            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) { r -> if (c.isActive) c.resume(r) }
                        }
                        acknowledged = ack.responseCode == BillingClient.BillingResponseCode.OK
                    }
                    ProReceipt(ProAccessPolicy.PRODUCT, p.purchaseState == Purchase.PurchaseState.PURCHASED, p.isSuspended, verified, acknowledged)
                }
                val active = ProAccessPolicy.active(receipts)
                purchasesChecked = true
                val pending = purchases.any { ProAccessPolicy.PRODUCT in it.products && it.purchaseState == Purchase.PurchaseState.PENDING }
                mutable.update { it.copy(active = active, purchaseBlocked = purchases.any { p -> ProAccessPolicy.PRODUCT in p.products }, message = when {
                    active -> "Pro is active for this Google Play account."
                    pending -> "Payment is pending in Google Play. Pro unlocks after payment and acknowledgement."
                    receipts.any { it.suspended } -> "Your subscription is paused or on hold. Manage it in Google Play, then refresh purchases."
                    receipts.any { it.purchased && !it.suspended && (!it.verified || !it.acknowledged) } -> "Purchase verification or acknowledgement is incomplete. Refresh purchases; Pro has not unlocked."
                    else -> "No active Pro subscription found. Free features remain available."
                }) }
                val (productResult, products) = suspendCancellableCoroutine<Pair<BillingResult,QueryProductDetailsResult>> { c ->
                    val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(ProAccessPolicy.PRODUCT).setProductType(BillingClient.ProductType.SUBS).build())).build()
                    client.queryProductDetailsAsync(params) { r, p -> if (c.isActive) c.resume(r to p) }
                }
                if (productResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val product = products.productDetailsList.firstOrNull { it.productId == ProAccessPolicy.PRODUCT }
                    val base = product?.subscriptionOfferDetails?.firstOrNull { it.basePlanId == ProAccessPolicy.BASE_PLAN && it.offerId == null }
                    val phase = base?.pricingPhases?.pricingPhaseList?.singleOrNull()?.takeIf {
                        it.billingPeriod == "P1M" && it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
                    }
                    if (phase != null) { details = product; offer = base.offerToken; mutable.update { it.copy(price = phase.formattedPrice) } }
                }
                true
            }
        } catch (_: TimeoutCancellationException) {
            mutable.update { it.copy(active = purchasesChecked && it.active, message = "Google Play timed out. Reconnect and refresh purchases.") }; false
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            mutable.update { it.copy(active = purchasesChecked && it.active, message = error.message?.take(160) ?: "Google Play is unavailable. Free features still work.") }; false
        } finally { mutable.update { it.copy(busy = false) } }
    }
    fun buy(activity: Activity) {
        if (state.value.busy || state.value.checkoutOpen || state.value.purchaseBlocked || !state.value.configured) return
        viewModelScope.launch {
            if (!updatePurchases() || state.value.active || state.value.purchaseBlocked || activity.isFinishing || activity.isDestroyed) return@launch
            val product = details; val token = offer
            if (product == null || token == null) { mutable.update { it.copy(message = "The monthly Pro plan is not available for this Play account yet.") }; return@launch }
            val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product).setOfferToken(token).build())).build()
            val result = client.launchBillingFlow(activity, params)
            mutable.update { it.copy(checkoutOpen = result.responseCode == BillingClient.BillingResponseCode.OK) }
            if (result.responseCode != BillingClient.BillingResponseCode.OK) mutable.update { it.copy(message = "Google Play could not open checkout. Refresh purchases and try again.") }
        }
    }
    override fun onCleared() { preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener); client.endConnection(); super.onCleared() }
}
