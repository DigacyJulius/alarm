package com.shiftalarm.app.core

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams

/**
 * Minimal Google Play Billing helper for the one-time "remove ads" purchase.
 * Requires the app to be distributed via Google Play with an in-app product
 * id "remove_ads" configured in Play Console. On sideloaded APKs the
 * connection simply reports unavailability — nothing crashes.
 */
object AdsBilling {
    const val PRODUCT_ID = "remove_ads"

    fun purchase(activity: Activity, onResult: (ok: Boolean, message: String) -> Unit) =
        Session(activity, onResult).start()

    private class Session(
        private val activity: Activity,
        private val onResult: (Boolean, String) -> Unit
    ) {
        private var finished = false
        private lateinit var client: BillingClient

        private fun fail(msg: String) {
            if (!finished) { finished = true; onResult(false, msg) }
        }
        private fun done(msg: String) {
            if (!finished) { finished = true; onResult(true, msg) }
        }

        private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
            when {
                result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null ->
                    handle(purchases)
                result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                    fail("已取消購買")
                else -> fail("購買失敗：" + result.debugMessage)
            }
        }

        fun start() {
            client = BillingClient.newBuilder(activity)
                .setListener(purchasesListener)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
                )
                .build()
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        fail(
                            "連唔到 Google Play（code " + result.responseCode +
                                "）。需要 Play Store 版本先可以購買。"
                        )
                        return
                    }
                    queryProduct()
                }

                override fun onBillingServiceDisconnected() {
                    // No retry loop — the user can simply tap the button again.
                }
            })
        }

        private fun queryProduct() {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build()
                    )
                )
                .build()
            client.queryProductDetailsAsync(params) { result, products ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK ||
                    products.isNullOrEmpty()
                ) {
                    fail(
                        "搵唔到「移除廣告」商品，需要先喺 Play Console 設定 in-app 商品「remove_ads」。"
                    )
                    return@queryProductDetailsAsync
                }
                launchFlow(products.first())
            }
        }

        private fun launchFlow(product: ProductDetails) {
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(product)
                            .build()
                    )
                )
                .build()
            activity.runOnUiThread {
                val result = client.launchBillingFlow(activity, flowParams)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    fail("無法啟動購買流程：" + result.debugMessage)
                }
            }
        }

        private fun handle(purchases: List<Purchase>) {
            val bought = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (!bought) return
            for (p in purchases) {
                if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
                if (p.isAcknowledged) {
                    done("多謝支持！廣告已永久移除。")
                } else {
                    val ack = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.purchaseToken)
                        .build()
                    client.acknowledgePurchase(ack) { done("多謝支持！廣告已永久移除。") }
                }
            }
        }
    }
}
