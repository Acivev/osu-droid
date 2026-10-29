package com.osudroid

import ru.nsu.ccfit.zuev.osuplus.BuildConfig

object BuildSettings {

    /**
     * Whether to use textures or not.
     */
    const val NO_TEXTURES_MODE = false

    /**
     * Whether to show entity boundaries or not.
     */
    const val SHOW_ENTITY_BOUNDARIES = false

    /**
     * Whether to use fake multiplayer mode or not.
     */
    val MOCK_MULTIPLAYER = BuildConfig.DEBUG

    /**
     * Whether score submission to the server is disabled. Debug builds never submit, so test plays
     * on logged-in accounts cannot reach the production database.
     */
    val NO_SCORE_SUBMISSION = BuildConfig.DEBUG
}


