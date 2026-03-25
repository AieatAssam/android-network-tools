package com.example.netswissknife.core.network.traceroute

data class HopGeoLocation(
    val ip: String,
    val country: String,
    val countryCode: String,
    val city: String,
    val lat: Double,
    val lon: Double,
    val isp: String? = null,
    val asn: String? = null
)
