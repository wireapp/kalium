package com.wire.kalium.logic.feature.wireuser.search

import com.wire.kalium.logic.data.wireuser.model.WireUser

//TODO: this model could be extended later on if we want to include more info into the search result
data class WireUserSearchResult(val wireUsers: List<WireUser>)
