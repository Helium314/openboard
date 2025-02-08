// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isGone
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.prefs
import kotlinx.coroutines.flow.MutableStateFlow

// todo (roughly in order)
//  check dark and light theme (don't have dynamic)
//  any way to get rid of the "old" background on starting settings? probably comes from app theme, can we avoid it?
//  try making old fragment back stuff work better, and try the different themes (with and without top bar, it should only appear for old fragments)
//  calling KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext()) while keyboard is showing shows just full screen background
//   but reload while keyboard is showing would be great (isn't it at least semi-done when changing one-handed mode?)
//  bg image inconsistent about being on toolbar or not (is this new?)
//  performance
//   find a nice way of testing (probably add logs for measuring time and recompositions)
//   consider that stuff in composables can get called quite often on any changes
//    -> use remember for things that are slow, but be careful they don't change from outside the composable
//   not so nice now on S4 mini, try non-debug
//    maybe related to lazy column?
//  PRs adding prefs -> need to finish and merge main before finishing this PR
//   1263 (no response for several weeks now...)
//  really use the restart dialog for debug settings stuff?
//   could do it the old way, and hide debug settings from search

// what should be done, but not in this PR
//  in general: changes to anything outside the new settings (unless necessary), and changes to how screens / fragments work
//  re-organize screens, no need to keep exactly the same arrangement
//  language settings (should change more than just move to compose)
//  user dictionary settings (or maybe leave old state for a while?)
//  color settings (should at least change how colors are stored, and have a color search/filter)
//  allow users to add custom themes instead of only having a single one (maybe also switchable in colors settings)
//  one single place for default values (to be used in composables and settings)
//   does it make sense to put this into PrefDef?
//  make auto_correct_threshold a float directly with the list pref (needs pref upgrade)
//  using context.prefs() outside settings
//  merge PREF_TOOLBAR_CUSTOM_KEY_CODES and PREF_TOOLBAR_CUSTOM_LONGPRESS_CODES into one pref (don't forget settings upgrade)
//  adjust debug settings
//   have them in main screen?
//   allow users to find the individual settings in search even if debug settings are not enabled?
//  replace the setup wizard

//  consider disabled settings & search
//   don't show -> users confused
//   show as disabled -> users confused
//   show (but change will not do anything because another setting needs to be enabled first)
//   -> last is probably best, but people will probably open issues no matter what

// maybe do after the PR
//  bottom dummy text field (though we have the search now anyway, and thus maybe don't need it)
//  search only in current pref screen, except when in main?
//  try getting rid of appcompat stuff (activity, dialogs, ...)
//  rearrange settings screens? now it should be very simple to do (definitely separate PR)
//  actually lenient json parsing is not good in a certain way: we should show an error if a json property is unknown
//  syntax highlighting for json? should show basic json errors
//  does restore prefs not delete dictionaries?
//  don't require to switch keyboard when entering settings

// preliminary results:
// looks ok (ugly M3 switches)
// performance
//  time until app and screens are shown is clearly worse than previously (2-4x)
//  gets much better when opening same screen again
//  material3 is ~25% faster than material2
//  debug is MUCH slower than release
//   much of this is before the app actually starts (before App.onCreate), maybe loading the many compose classes slows down startup
//  -> should be fine on reasonably recent phones (imo even still acceptable on S4 mini)
// apk size increase
//  initially it was 900 kB, and another 300 kB for Material3
//  textField and others add more (not sure what exactly), and now we're already at 2 MB...

class SettingsActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val prefs by lazy { this.prefs() }
    val prefChanged = MutableStateFlow(0) // simple counter, as the only relevant information is that something changed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Settings.getInstance().current == null)
            Settings.init(this)

        settingsContainer = SettingsContainer(this)

        // todo: when removing old settings completely, remove settings_activity.xml and supportFragmentManager stuff
//        val cv = ComposeView(context = this)
//        setContentView(cv)
        setContentView(R.layout.settings_activity)
        supportFragmentManager.addOnBackStackChangedListener {
            updateContainerVisibility()
        }
//        cv.setContent { // todo: when removing old settings
        findViewById<ComposeView>(R.id.navHost).setContent {
            Theme {
                Surface {
                    SettingsNavHost(
                        onClickBack = {
//                            this.finish() // todo: when removing old settings
                            if (supportFragmentManager.findFragmentById(R.id.settingsFragmentContainer) == null)
                                this.finish()
                            else supportFragmentManager.popBackStack()
                        }
                    )
                }
            }
        }
    }

    private fun updateContainerVisibility() { // todo: remove when removing old settings
        findViewById<RelativeLayout>(R.id.settingsFragmentContainer).isGone = supportFragmentManager.findFragmentById(R.id.settingsFragmentContainer) == null
    }

    override fun onStart() {
        super.onStart()
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    companion object {
        // public write so compose previews can show the screens
        lateinit var settingsContainer: SettingsContainer
    }

    override fun onSharedPreferenceChanged(prefereces: SharedPreferences?, key: String?) {
        prefChanged.value++
    }
}

@JvmField
var keyboardNeedsReload = false
