package com.wire.kalium.logic.feature.user.search

import com.wire.kalium.logic.data.user.WireUser

//TODO: this model could be extended later on if we want to include more info into the search result
data class WireUserSearchResult(val wireUsers: List<WireUser>)
