package ovh.devlabstudy.devlabnetworktoolkit.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserAccessState(
    val hasFullSubscription: Boolean = false,
    val unlockedModules: Set<String> = emptySet()
) {
    val isAdFree: Boolean
        get() = hasFullSubscription

    fun hasAccessToFeature(featureId: String): Boolean {
        return hasFullSubscription || unlockedModules.contains(featureId)
    }
}

class BillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"

        const val SUB_MONTHLY = "sub_monthly"
        const val SUB_YEARLY = "sub_yearly"
        const val LIFETIME_PRO = "lifetime_pro"
        const val PRODUCT_LIFETIME = LIFETIME_PRO

        const val MOD_LAN_SCANNER = "sub_module_lan_scanner"
        const val MOD_PORT_SCANNER = "sub_module_port_scanner"
        const val MOD_PING_MONITOR = "sub_module_ping_monitor"
        const val MOD_DNS_LOOKUP = "sub_module_dns_lookup"

        val ALL_SUB_IDS = listOf(SUB_MONTHLY, SUB_YEARLY, MOD_LAN_SCANNER, MOD_PORT_SCANNER, MOD_PING_MONITOR, MOD_DNS_LOOKUP)
        val ALL_INAPP_IDS = listOf(LIFETIME_PRO)
    }

    private val _userAccessState = MutableStateFlow(UserAccessState())
    val userAccessState: StateFlow<UserAccessState> = _userAccessState.asStateFlow()

    val isProPurchased = _userAccessState.map { it.hasFullSubscription }

    private val _productPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    val productPrices: StateFlow<Map<String, String>> = _productPrices.asStateFlow()

    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    fun startConnection(onConnected: (() -> Unit)? = null) {
        if (billingClient.isReady) {
            queryPurchases()
            fetchProductDetails()
            onConnected?.invoke()
            return
        }

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing Client połączony pomyślnie.")
                    queryPurchases()
                    fetchProductDetails()
                    onConnected?.invoke()
                } else {
                    Log.e(TAG, "Błąd łączenia z Billing: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Rozłączono z Billing Service. Próba ponownego połączenia przy następnej operacji.")
            }
        })
    }

    /**
     * Pobiera aktualne sformatowane ceny bezpośrednio z Google Play Console.
     */
    fun fetchProductDetails() {
        if (!billingClient.isReady) return

        coroutineScope.launch(Dispatchers.IO) {
            val pricesMap = mutableMapOf<String, String>()

            // 1. Pobieranie cen subskrypcji (SUBS)
            val subProducts = ALL_SUB_IDS.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            }

            if (subProducts.isNotEmpty()) {
                val subParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(subProducts)
                    .build()

                billingClient.queryProductDetailsAsync(subParams) { result, productDetailsList ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        for (details in productDetailsList) {
                            val price = details.subscriptionOfferDetails
                                ?.firstOrNull()
                                ?.pricingPhases
                                ?.pricingPhaseList
                                ?.firstOrNull()
                                ?.formattedPrice

                            if (price != null) {
                                pricesMap[details.productId] = price
                            }
                        }
                        _productPrices.update { it + pricesMap }
                    }
                }
            }

            // 2. Pobieranie cen produktów jednorazowych (INAPP)
            val inAppProducts = ALL_INAPP_IDS.map { productId ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }

            if (inAppProducts.isNotEmpty()) {
                val inAppParams = QueryProductDetailsParams.newBuilder()
                    .setProductList(inAppProducts)
                    .build()

                billingClient.queryProductDetailsAsync(inAppParams) { result, productDetailsList ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        for (details in productDetailsList) {
                            val price = details.oneTimePurchaseOfferDetails?.formattedPrice
                            if (price != null) {
                                pricesMap[details.productId] = price
                            }
                        }
                        _productPrices.update { it + pricesMap }
                    }
                }
            }
        }
    }

    /**
     * Sprawdzanie aktywnej subskrypcji lub zakupu wieczystego.
     */
    fun queryPurchases() {
        if (!billingClient.isReady) {
            startConnection { queryPurchases() }
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            var hasFullSub = false
            val modules = mutableSetOf<String>()

            // 1. Sprawdzenie subskrypcji
            val subParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            billingClient.queryPurchasesAsync(subParams) { resultSub, purchasesSub ->
                if (resultSub.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchasesSub.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
                        if (purchase.products.contains(SUB_MONTHLY) || purchase.products.contains(SUB_YEARLY)) {
                            hasFullSub = true
                        }
                        if (purchase.products.contains(MOD_LAN_SCANNER)) modules.add(MOD_LAN_SCANNER)
                        if (purchase.products.contains(MOD_PORT_SCANNER)) modules.add(MOD_PORT_SCANNER)
                        if (purchase.products.contains(MOD_PING_MONITOR)) modules.add(MOD_PING_MONITOR)
                        if (purchase.products.contains(MOD_DNS_LOOKUP)) modules.add(MOD_DNS_LOOKUP)

                        if (!purchase.isAcknowledged) handlePurchase(purchase)
                    }
                }

                // 2. Sprawdzenie zakupów jednorazowych (INAPP)
                val inAppParams = QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

                billingClient.queryPurchasesAsync(inAppParams) { resultInApp, purchasesInApp ->
                    if (resultInApp.responseCode == BillingClient.BillingResponseCode.OK) {
                        purchasesInApp.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { purchase ->
                            if (purchase.products.contains(LIFETIME_PRO)) {
                                hasFullSub = true
                            }

                            if (!purchase.isAcknowledged) handlePurchase(purchase)
                        }
                    }

                    _userAccessState.update {
                        UserAccessState(
                            hasFullSubscription = hasFullSub,
                            unlockedModules = modules
                        )
                    }
                }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity, productId: String) {
        if (!billingClient.isReady) {
            startConnection { launchPurchaseFlow(activity, productId) }
            return
        }

        val productType = if (productId == LIFETIME_PRO || productId == PRODUCT_LIFETIME) {
            BillingClient.ProductType.INAPP
        } else {
            BillingClient.ProductType.SUBS
        }

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(productType)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && productDetailsList.isNotEmpty()) {
                val productDetails = productDetailsList[0]

                val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)

                if (productType == BillingClient.ProductType.SUBS) {
                    val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
                    if (offerToken != null) {
                        productDetailsParamsBuilder.setOfferToken(offerToken)
                    }
                }

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(listOf(productDetailsParamsBuilder.build()))
                    .build()

                billingClient.launchBillingFlow(activity, flowParams)
            } else {
                Log.e(TAG, "Nie znaleziono szczegółów produktu dla: $productId (kod: ${billingResult.responseCode})")
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    handlePurchase(purchase)
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            coroutineScope.launch(Dispatchers.IO) {
                billingClient.acknowledgePurchase(acknowledgePurchaseParams) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        queryPurchases()
                    }
                }
            }
        } else {
            queryPurchases()
        }
    }
}