package helium314.keyboard.settings.preferences

import android.content.SharedPreferences
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.dialogs.SliderDialog
import kotlin.math.roundToInt

@Composable
/** Slider preference for Int or Float (weird casting stuff, but should be fine) */
fun <T: Number> SliderPreference(
    name: String,
    modifier: Modifier = Modifier,
    pref: String,
    description: @Composable (T) -> String,
    default: T,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Int? = null,
    onValueChanged: (Float) -> Unit = { },
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val initialValue = if (default is Int || default is Float)
        getPrefOfType(prefs, pref, default)
    else throw IllegalArgumentException("only float and int are supported")

    var showDialog by remember { mutableStateOf(false) }
    Preference(
        name = name,
        onClick = { showDialog = true },
        modifier = modifier,
        description = description(initialValue)
    )
    if (showDialog)
        SliderDialog(
            onDismissRequest = { showDialog = false },
            onDone = {
                if (default is Int) prefs.edit().putInt(pref, it.toInt()).apply()
                else prefs.edit().putFloat(pref, it).apply()
            },
            initialValue = initialValue.toFloat(),
            range = range,
            positionString = {
                @Suppress("UNCHECKED_CAST")
                description((if (default is Int) it.roundToInt() else it) as T)
            },
            onValueChanged = onValueChanged,
            showDefault = true,
            onDefault = { prefs.edit().remove(pref).apply() },
            intermediateSteps = stepSize?.let {
                // this is not nice, but slider wants it like this...
                ((range.endInclusive - range.start) / it - 1).toInt()
            }
        )
}

@Composable
// just in here so we can keep getPrefOfType private... rename file?
fun <T: Any> ListPreference(
    def: PrefDef,
    items: List<Pair<String, T>>,
    default: T,
    onChanged: (T) -> Unit = { }
) {
    var showDialog by remember { mutableStateOf(false) }
    val prefs = LocalContext.current.prefs()
    val selected = items.firstOrNull { it.second == getPrefOfType(prefs, def.key, default) }
    Preference(
        name = def.title,
        description = selected?.first,
        onClick = { showDialog = true }
    )
    if (showDialog) {
        ListPickerDialog(
            onDismissRequest = { showDialog = false },
            items = items,
            onItemSelected = {
                if (it == selected) return@ListPickerDialog
                putPrefOfType(prefs, def.key, it.second)
                onChanged(it.second)
            },
            selectedItem = selected,
            title = { Text(def.title) },
            getItemName = { it.first }
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T: Any> getPrefOfType(prefs: SharedPreferences, key: String, default: T): T =
    when (default) {
        is String -> prefs.getString(key, default)
        is Int -> prefs.getInt(key, default)
        is Long -> prefs.getLong(key, default)
        is Float -> prefs.getFloat(key, default)
        is Boolean -> prefs.getBoolean(key, default)
        else -> throw IllegalArgumentException("unknown type ${default.javaClass}")
    } as T

private fun <T: Any> putPrefOfType(prefs: SharedPreferences, key: String, value: T) =
    prefs.edit {
        when (value) {
            is String -> putString(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Boolean -> putBoolean(key, value)
            else -> throw IllegalArgumentException("unknown type ${value.javaClass}")
        }
    }
