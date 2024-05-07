/*
 * Copyright (C) 2020 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.keyboard.internal.keyboard_parser.floris

import kotlinx.serialization.Serializable
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.latin.utils.addCollections

// only the constructor and name remain from FlorisBoard
// we don't care about the difference between main and relevant (at least for now)
@Serializable
open class PopupSet<T : AbstractKeyData>(
    open val main: T? = null,
    open val relevant: Collection<T>? = null
) {
    // get labels of all popup keys
    open fun getPopupKeyLabels(params: KeyboardParams): Collection<String>? {
        if (main == null && relevant == null) return null
        val popupKeys = mutableListOf<String>()
        main?.getPopupLabel(params)?.let { popupKeys.add(it) }
        relevant?.let { popupKeys.addAll(it.map { it.getPopupLabel(params) }) }
        if (popupKeys.isEmpty()) return null
        return popupKeys
    }

    var numberIndex: Int? = null
    var symbol: String? = null // maybe list of keys?

    fun <U : AbstractKeyData> merge(other: PopupSet<U>?): PopupSet<out AbstractKeyData> {
        if (other == null || other == EmptyPopups) return this
        if (this == EmptyPopups) return other
        if (this is SimplePopups && other is SimplePopups)
            return SimplePopups(addCollections(popupKeys, other.popupKeys))
        val newMain = if (main == null) other.main else main
        val newRelevant = addCollections(relevant, other.relevant)
        if (main != null && other.main != null)
            return PopupSet(newMain, addCollections(listOf(other.main!!), newRelevant))
        return PopupSet(newMain, newRelevant)
    }
}

class SimplePopups(val popupKeys: Collection<String>?) :  PopupSet<AbstractKeyData>() {
    override fun getPopupKeyLabels(params: KeyboardParams) = popupKeys
}

object EmptyPopups: PopupSet<AbstractKeyData>() {
    override fun getPopupKeyLabels(params: KeyboardParams): Collection<String>? {
        return null
    }
}
