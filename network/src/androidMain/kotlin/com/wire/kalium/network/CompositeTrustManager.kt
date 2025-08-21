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

import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class CompositeTrustManager(
    private val systemTrustManager: X509TrustManager,
    private val customTrustManager: X509TrustManager?
) : X509TrustManager {
    
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        systemTrustManager.checkClientTrusted(chain, authType)
    }
    
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        try {
            // First try the system trust manager
            systemTrustManager.checkServerTrusted(chain, authType)
        } catch (systemException: CertificateException) {
            // If system trust fails and we have a custom trust manager, try it
            if (customTrustManager != null) {
                try {
                    customTrustManager.checkServerTrusted(chain, authType)
                } catch (customException: CertificateException) {
                    // Both failed, throw the original system exception
                    Log.w(TAG, "Both system and custom trust managers failed validation")
                    throw systemException
                }
            } else {
                // No custom trust manager, throw the original exception
                throw systemException
            }
        }
    }
    
    override fun getAcceptedIssuers(): Array<X509Certificate> {
        // Combine accepted issuers from both trust managers
        val systemIssuers = systemTrustManager.acceptedIssuers
        val customIssuers = customTrustManager?.acceptedIssuers ?: emptyArray()
        
        return systemIssuers + customIssuers
    }
    
    companion object {
        private const val TAG = "CompositeTrustManager"
        
        fun create(pemCertificate: String?): CompositeTrustManager {
            // Always use the system trust manager
            val systemTrustManager = getSystemTrustManager()
            
            // Create custom trust manager if PEM certificate is provided
            val customTrustManager = if (!pemCertificate.isNullOrBlank()) {
                try {
                    createCustomTrustManager(pemCertificate)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create custom trust manager from PEM", e)
                    null
                }
            } else {
                null
            }
            
            return CompositeTrustManager(systemTrustManager, customTrustManager)
        }
        
        private fun getSystemTrustManager(): X509TrustManager {
            val trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            )
            trustManagerFactory.init(null as KeyStore?)
            
            val trustManagers = trustManagerFactory.trustManagers
            return trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                ?: throw IllegalStateException("No system X509TrustManager found")
        }
        
        private fun createCustomTrustManager(pemCertificate: String): X509TrustManager {
            // Parse the PEM certificate
            val certificateFactory = CertificateFactory.getInstance("X.509")
            
            // Remove PEM headers/footers and decode
            val cleanedPem = pemCertificate
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replace("\\s".toRegex(), "")
            
            val certificateBytes = Base64.decode(cleanedPem, Base64.DEFAULT)
            val certificate = certificateFactory.generateCertificate(
                ByteArrayInputStream(certificateBytes)
            ) as X509Certificate
            
            // Create a KeyStore containing the custom certificate
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            keyStore.setCertificateEntry("custom_ca", certificate)
            
            // Create a TrustManager that trusts the custom CA
            val trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            )
            trustManagerFactory.init(keyStore)
            
            val trustManagers = trustManagerFactory.trustManagers
            return trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
                ?: throw IllegalStateException("Failed to create custom X509TrustManager")
        }
    }
}