package com.wire.kalium.models.backend.json

import com.wire.kalium.models.backend.AssetKey


object AssetKeyJson {
    val validCompleteImage = ValidJsonProvider(
        AssetKey(
            AssetKey.Type.IMAGE,
            "3-1-4facb8d0-1370-4114-b6e8-d92ab997f0c2",
            AssetKey.Size.COMPLETE
        )
    ) {
        """
        |{
        |  "size": "complete",
        |  "key": "3-1-4facb8d0-1370-4114-b6e8-d92ab997f0c2",
        |  "type": "image"
        |}
        """.trimMargin()
    }
    val validPreviewImage = ValidJsonProvider(
        AssetKey(
            AssetKey.Type.IMAGE,
            "120938-120938-0981290",
            AssetKey.Size.PREVIEW
        )
    ) {
        """
        |{
        |  "size": "preview",
        |  "key": "120938-120938-0981290",
        |  "type": "image"
        |}
        """.trimMargin()
    }
    val missingSize = FaultyJsonProvider(
        """
        |{
        |  "key": "3-1-4facb8d0-1370-4114-b6e8-d92ab997f0c2",
        |  "type": "image"
        |}
        """.trimMargin()
    )
    val missingKey = FaultyJsonProvider(
        """
        |{
        |  "size": "preview",
        |  "type": "image"
        |}
        """.trimMargin()
    )
    val missingType = FaultyJsonProvider(
        """
        |{
        |  "size": "preview",
        |  "key": "3-1-4facb8d0-1370-4114-b6e8-d92ab997f0c2"
        |}
        """.trimMargin()
    )
}
