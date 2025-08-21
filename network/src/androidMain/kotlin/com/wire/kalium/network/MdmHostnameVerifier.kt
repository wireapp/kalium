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
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession

class MdmHostnameVerifier(
    private val allowedHosts: List<String>
) : HostnameVerifier {
    
    private val defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
    
    override fun verify(hostname: String?, session: SSLSession?): Boolean {
        if (hostname == null || session == null) {
            return false
        }
        
        // First check if the hostname passes default verification
        val passesDefaultVerification = defaultVerifier.verify(hostname, session)
        
        if (!passesDefaultVerification) {
            Log.w(TAG, "Hostname $hostname failed default verification")
            return false
        }
        
        // If we have no allowed hosts list, allow all hosts that pass default verification
        if (allowedHosts.isEmpty()) {
            return true
        }
        
        // Check if the hostname is in the allowed hosts list
        val isAllowed = isHostAllowed(hostname)
        
        if (!isAllowed) {
            Log.w(TAG, "Hostname $hostname is not in the allowed hosts list")
        }
        
        return isAllowed
    }
    
    private fun isHostAllowed(hostname: String): Boolean {
        return allowedHosts.any { allowedHost ->
            when {
                // Exact match
                allowedHost == hostname -> true
                
                // Wildcard subdomain match (*.example.com)
                allowedHost.startsWith("*.") -> {
                    val domain = allowedHost.substring(2)
                    hostname.endsWith(domain) && 
                    (hostname == domain || hostname.endsWith(".$domain"))
                }
                
                // Default: no match
                else -> false
            }
        }
    }
    
    companion object {
        private const val TAG = "MdmHostnameVerifier"
        
        fun createDefault(): MdmHostnameVerifier {
            return MdmHostnameVerifier(emptyList())
        }
        
        fun create(allowedHosts: List<String>?): MdmHostnameVerifier {
            return MdmHostnameVerifier(allowedHosts ?: emptyList())
        }
    }
}