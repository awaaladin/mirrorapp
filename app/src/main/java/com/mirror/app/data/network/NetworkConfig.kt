package com.mirror.app.data.network

object NetworkConfig {
    const val BASE_URL = "https://mirrorcentral.vercel.app/"

    /** Where "Upgrade to Pro" sends people - payment only ever happens on the web, never
     * in-app (see POST /billing/checkout on mirrorcentral). */
    const val PRICING_URL = BASE_URL + "pricing"
}
