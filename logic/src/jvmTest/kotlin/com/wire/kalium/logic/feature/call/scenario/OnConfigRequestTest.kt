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
package com.wire.kalium.logic.feature.call.scenario

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

import kotlinx.serialization.json.Json
import com.wire.kalium.util.serialization.toJsonElement

class OnConfigRequestTest {
    val sftToInject = "https://rust-sft.stars.wire.link"
    @Test
    fun givenAConfig_whenThereIsAnSft_shouldBeOverriten() = runTest {
        val configWithSft = "{\"ice_servers\":[{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-1.coturn.calling-staging-v01.zinfra.io:3478?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-2.coturn.calling-staging-v01.zinfra.io:3478?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-2.coturn.calling-staging-v01.zinfra.io:3478?transport=udp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-1.coturn.calling-staging-v01.zinfra.io:3478?transport=udp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turns:coturn-1.coturn.calling-staging-v01.zinfra.io:5349?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turns:coturn-0.coturn.calling-staging-v01.zinfra.io:5349?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turns:coturn-2.coturn.calling-staging-v01.zinfra.io:5349?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-0.coturn.calling-staging-v01.zinfra.io:3478?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-0.coturn.calling-staging-v01.zinfra.io:3478?transport=udp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"}],\"is_federating\":true,\"sft_servers\":[{\"urls\":[\"https://sft.calling-staging-v01.zinfra.io\"]}],\"sft_servers_all\":[{\"urls\":[\"https://sft.calling-staging-v01.zinfra.io/sfts/sftd-0\"]},{\"urls\":[\"https://sft.calling-staging-v01.zinfra.io/sfts/sftd-1\"]}],\"ttl\":3600}"
        val expectedConfig = "{\"ice_servers\":[{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-1.coturn.calling-staging-v01.zinfra.io:3478?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-2.coturn.calling-staging-v01.zinfra.io:3478?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-2.coturn.calling-staging-v01.zinfra.io:3478?transport=udp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-1.coturn.calling-staging-v01.zinfra.io:3478?transport=udp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turns:coturn-1.coturn.calling-staging-v01.zinfra.io:5349?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turns:coturn-0.coturn.calling-staging-v01.zinfra.io:5349?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turns:coturn-2.coturn.calling-staging-v01.zinfra.io:5349?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-0.coturn.calling-staging-v01.zinfra.io:3478?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-0.coturn.calling-staging-v01.zinfra.io:3478?transport=udp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"}],\"is_federating\":true,\"sft_servers\":[{\"urls\":[\"https://rust-sft.stars.wire.link\"]}],\"sft_servers_all\":[{\"urls\":[\"https://rust-sft.stars.wire.link\"]}],\"ttl\":3600}"
       
        val modifiedConfig = overwriteSft(Json.parseToJsonElement(configWithSft), sftToInject)
        assertEquals(expectedConfig, modifiedConfig.toString())
    }

    @Test
    fun givenAConfig_whenThereIsNoSft_shouldBeUnmodified() = runTest {
        val configWithoutSft = "{\"ice_servers\":[{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-1.coturn.calling-staging-v01.zinfra.io:3478?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-2.coturn.calling-staging-v01.zinfra.io:3478?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-2.coturn.calling-staging-v01.zinfra.io:3478?transport=udp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-1.coturn.calling-staging-v01.zinfra.io:3478?transport=udp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turns:coturn-1.coturn.calling-staging-v01.zinfra.io:5349?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turns:coturn-0.coturn.calling-staging-v01.zinfra.io:5349?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turns:coturn-2.coturn.calling-staging-v01.zinfra.io:5349?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-0.coturn.calling-staging-v01.zinfra.io:3478?transport=tcp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"},{\"credential\":\"xyz\",\"urls\":[\"turn:coturn-0.coturn.calling-staging-v01.zinfra.io:3478?transport=udp\"],\"username\":\"d=123.v=1.k=0.t=s.r=xyz\"}],\"is_federating\":true,\"some_servers\":[{\"urls\":[\"https://sft.calling-staging-v01.zinfra.io\"]}],\"some_servers_all\":[{\"urls\":[\"https://sft.calling-staging-v01.zinfra.io/sfts/sftd-0\"]},{\"urls\":[\"https://sft.calling-staging-v01.zinfra.io/sfts/sftd-1\"]}],\"ttl\":3600}"
        
        val modifiedConfig = overwriteSft(Json.parseToJsonElement(configWithoutSft), sftToInject)
        assertEquals(configWithoutSft, modifiedConfig.toString())
    }
}