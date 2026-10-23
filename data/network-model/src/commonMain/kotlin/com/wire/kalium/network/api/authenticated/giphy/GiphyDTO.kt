/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.network.api.authenticated.giphy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A page of GIFs from Giphy's search or trending endpoint, as passed through by the backend proxy. */
@Serializable
data class GiphyGifsResponseDTO(
    @SerialName("data")
    val data: List<GiphyGifDTO> = emptyList(),
    @SerialName("pagination")
    val pagination: GiphyPaginationDTO? = null,
)

@Serializable
data class GiphyGifDTO(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String? = null,
    @SerialName("images")
    val images: GiphyImagesDTO = GiphyImagesDTO(),
)

/** The renditions Wire uses. Giphy sends many more; unknown keys are ignored. */
@Serializable
data class GiphyImagesDTO(
    @SerialName("downsized")
    val downsized: GiphyImageDTO? = null,
    @SerialName("fixed_width")
    val fixedWidth: GiphyImageDTO? = null,
    @SerialName("fixed_width_downsampled")
    val fixedWidthDownsampled: GiphyImageDTO? = null,
)

/** Giphy sends dimensions and sizes as strings, for example `"width": "200"`. */
@Serializable
data class GiphyImageDTO(
    @SerialName("url")
    val url: String? = null,
    @SerialName("width")
    val width: String? = null,
    @SerialName("height")
    val height: String? = null,
    @SerialName("size")
    val size: String? = null,
)

@Serializable
data class GiphyPaginationDTO(
    @SerialName("total_count")
    val totalCount: Int? = null,
    @SerialName("count")
    val count: Int = 0,
    @SerialName("offset")
    val offset: Int = 0,
)

/** Search-term suggestions from Giphy's `search/tags` endpoint. */
@Serializable
data class GiphySearchSuggestionsResponseDTO(
    @SerialName("data")
    val data: List<GiphySearchSuggestionDTO> = emptyList(),
)

@Serializable
data class GiphySearchSuggestionDTO(
    @SerialName("name")
    val name: String,
)
