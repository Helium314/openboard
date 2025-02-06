// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SliderPreference
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.keyboardNeedsReload

@Composable
fun GestureTypingScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val gestureFloatingPreviewEnabled = prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true)
    val items = listOf(Settings.PREF_GESTURE_INPUT) +
            if (prefs.getBoolean(Settings.PREF_GESTURE_INPUT, true))
                listOfNotNull(
                    Settings.PREF_GESTURE_PREVIEW_TRAIL,
                    Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT,
                    if (gestureFloatingPreviewEnabled)
                        Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC else null,
                    Settings.PREF_GESTURE_SPACE_AWARE,
                    Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN,
                    if (prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, true) || gestureFloatingPreviewEnabled)
                        Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION else null
                )
            else emptyList()
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_gesture),
        prefs = items
    )
}

fun createGestureTypingPrefs(context: Context) = listOf(
    PrefDef(context, Settings.PREF_GESTURE_INPUT, R.string.gesture_input, R.string.gesture_input_summary) {
        SwitchPreference(
            def = it,
            default = true
        )
    },
    PrefDef(context, Settings.PREF_GESTURE_PREVIEW_TRAIL, R.string.gesture_preview_trail) {
        SwitchPreference(
            def = it,
            default = true
        )
    },
    PrefDef(context, Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, R.string.gesture_floating_preview_static, R.string.gesture_floating_preview_static_summary) {
        SwitchPreference(
            def = it,
            default = true
        )
    },
    PrefDef(context, Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC, R.string.gesture_floating_preview_text, R.string.gesture_floating_preview_dynamic_summary) {
        val ctx = LocalContext.current
        SwitchPreference(
            def = it,
            default = true
        ) {
            // is this complexity and 2 pref keys for one setting really needed?
            // default value is based on system reduced motion
            val default = Settings.readGestureDynamicPreviewDefault(ctx)
            val followingSystem = it == default
            // allow the default to be overridden
            ctx.prefs().edit().putBoolean(Settings.PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM, followingSystem).apply()
            keyboardNeedsReload = true
        }
    },
    PrefDef(context, Settings.PREF_GESTURE_SPACE_AWARE, R.string.gesture_space_aware, R.string.gesture_space_aware_summary) {
        SwitchPreference(
            def = it,
            default = false
        )
    },
    PrefDef(context, Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN, R.string.gesture_fast_typing_cooldown) {
        SliderPreference(
            name = it.title,
            pref = it.key,
            default = 500,
            range = 0f..500f,
            description = {
                if (it <= 0) stringResource(R.string.gesture_fast_typing_cooldown_instant)
                else stringResource(R.string.abbreviation_unit_milliseconds, it.toString())
            }
        )
    },
    PrefDef(context, Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION, R.string.gesture_trail_fadeout_duration) {
        // todo: there is some weird stuff going on
        //  for some uses there is an additional 100 ms delay
        //  see config_gesture_trail_fadeout_start_delay
        //  -> check whether this should be changes, or at least made less complicated
        SliderPreference(
            name = it.title,
            pref = it.key,
            default = 800,
            range = 100f..1900f,
            description = { stringResource(R.string.abbreviation_unit_milliseconds, (it + 100).toString()) },
            // todo: 50 ms steps?
        ) { keyboardNeedsReload = true }
    },
)

@Preview
@Composable
private fun Preview() {
    SettingsActivity2.allPrefs = AllPrefs(LocalContext.current)
    Theme(true) {
        Surface {
            GestureTypingScreen { }
        }
    }
}
