package com.osudroid.ui.verktex

import com.osudroid.ui.verktex.LegacyUnits.vu
import com.reco1l.verktex.data.px
import com.reco1l.verktex.ui.UIComponent
import com.reco1l.verktex.ui.container.UIContainer
import com.reco1l.verktex.ui.container.UILinearContainer
import com.reco1l.verktex.ui.text.UIText

inline fun UIContainer.compoundText(builder: CompoundText.() -> Unit) = CompoundText().apply(builder).also { +it }

/**
 * The AndEngine CompoundText with a trailing icon slot: the text in smallFont, then the icon [spacing]
 * apart. The slot was as high as the text and its content started at its top, so the icon is top
 * aligned with the text.
 */
open class CompoundText : UILinearContainer() {

    /**
     * The text entity.
     */
    val textEntity = UIText().apply {
        fontSize = LegacyUnits.fontSize("smallFont").vu.px
        wrapText = false
    }

    var text by textEntity::text

    /**
     * The trailing icon.
     */
    var trailingIcon: UIComponent? = null
        set(value) {
            if (field != value) {
                field?.removeSelf()
                field = value

                if (value != null) {
                    +value
                }
            }
        }

    init {
        orientation = Orientation.Horizontal
        +textEntity
    }
}
