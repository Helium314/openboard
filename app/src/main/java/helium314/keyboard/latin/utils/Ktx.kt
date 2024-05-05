package helium314.keyboard.latin.utils

import android.content.Context

// generic extension functions

// adapted from Kotlin source: https://github.com/JetBrains/kotlin/blob/7a7d392b3470b38d42f80c896b7270678d0f95c3/libraries/stdlib/common/src/generated/_Collections.kt#L3004
inline fun <T> Iterable<T>.sumOf(selector: (T) -> Float): Float {
    var sum = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}

fun CharSequence.getStringResourceOrName(prefix: String, context: Context): CharSequence {
    val resId = context.resources.getIdentifier(prefix + this, "string", context.packageName)
    return if (resId == 0) this else context.getString(resId)
}

/**
 *  Splits the collection into a pair of lists on the first match of [condition], discarding the element first matching the condition.
 *  If [condition] is not met, all elements are in the first list.
 */
fun <T> Collection<T>.splitAt(condition: (T) -> Boolean): Pair<List<T>, List<T>> {
    var conditionMet = false
    val first = mutableListOf<T>()
    val second = mutableListOf<T>()
    forEach {
        if (conditionMet) {
            second.add(it)
        } else {
            conditionMet = condition(it)
            if (!conditionMet)
                first.add(it)
        }
    }
    return first to second
}
