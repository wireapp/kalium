/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network

import android.util.Log
import com.wire.kalium.network.session.MdmTrustConfig
import okhttp3.OkHttpClient
import java.security.SecureRandom
import javax.net.ssl.SSLContext

class MdmSecureClientBuilder(
    private val mdmTrustConfig: MdmTrustConfig
) {

    val logger = kaliumLogger.withTextTag(TAG)
    
    fun buildSecureClient(baseBuilder: OkHttpClient.Builder): OkHttpClient.Builder {
        if (!mdmTrustConfig.isValid()) {
            logger.d("MDM trust configuration is not valid, using default client")
            return baseBuilder
        }
        
        return try {
            // Create composite trust manager
            val compositeTrustManager = CompositeTrustManager.create(mdmTrustConfig.rootCAPem)
            
            // Create SSL context with the composite trust manager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(compositeTrustManager), SecureRandom())
            
            // Configure the builder
            baseBuilder.apply {
                sslSocketFactory(sslContext.socketFactory, compositeTrustManager)
                
                // Apply hostname verifier if we have allowed hosts
                if (mdmTrustConfig.allowedHosts.isNotEmpty()) {
                    hostnameVerifier(MdmHostnameVerifier.create(mdmTrustConfig.allowedHosts))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure MDM secure client", e)
            baseBuilder
        }
    }
    
    companion object {
        private const val TAG = "MdmSecureClientBuilder"
    }
}
