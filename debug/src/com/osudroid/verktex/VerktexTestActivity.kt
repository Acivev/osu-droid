package com.osudroid.verktex

import com.reco1l.verktex.BuiltIn
import com.reco1l.verktex.Verktex
import com.reco1l.verktex.android.AndroidGameHost
import com.reco1l.verktex.fonts.Fonts

class VerktexTestActivity : AndroidGameHost() {

    override fun onEngineInitialized() {
        BuiltIn.Fonts.Default = Fonts.load("nunito", "fonts/nunito-medium.ttf", isInternal = true)!!
        Fonts.load("torus", "fonts/torus-bold.otf", isInternal = true)
        BuiltIn.Fonts.FontAwesomeIconRegular = Fonts.load("fa-regular", "fonts/fa-regular.otf", isInternal = true)!!
        BuiltIn.Fonts.FontAwesomeIconSolid = Fonts.load("fa-solid", "fonts/fa-solid.otf", isInternal = true)!!
        BuiltIn.Fonts.FontAwesomeIconBrands = Fonts.load("fa-brands", "fonts/fa-brands.otf", isInternal = true)!!
    }

    override fun onEngineStarted() {
        Verktex.pushScene(VerktexTestScene())
    }
}
