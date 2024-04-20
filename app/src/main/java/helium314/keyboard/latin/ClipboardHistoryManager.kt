// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import kotlin.collections.ArrayList

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var clipboardManager: ClipboardManager
    private var onHistoryChangeListener: OnHistoryChangeListener? = null
    private var isRetentionCheckScheduled = false
    private var clipboardHistoryEnabled = true
    private var maxClipRetentionTime = DEFAULT_RETENTION_TIME_MIN * ONE_MINUTE_MILLIS
    private val retentionCheckHandler = Handler(Looper.getMainLooper())
    private val retentionCheckRunnable: Runnable = object : Runnable {
        override fun run() {
            checkClipRetentionElapsed()
            retentionCheckHandler.postDelayed(this, maxClipRetentionTime)
        }
    }

    fun onCreate() {
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardHistoryEnabled = latinIME.mSettings.current.mClipboardHistoryEnabled
        maxClipRetentionTime = latinIME.mSettings.current.mClipboardHistoryRetentionTime * ONE_MINUTE_MILLIS
        clipboardManager.addPrimaryClipChangedListener(this)
        if (historyEntries.isEmpty())
            loadPinnedClips()
        if (latinIME.mSettings.current?.mClipboardHistoryEnabled == true)
            fetchPrimaryClip()
        scheduleRetentionCheck()
        DeviceProtectedUtils.getSharedPreferences(latinIME).registerOnSharedPreferenceChangeListener(this)
    }

    fun onPinnedClipsAvailable(pinnedClips: List<ClipboardHistoryEntry>) {
        historyEntries.addAll(pinnedClips)
        sortHistoryEntries()
        if (onHistoryChangeListener != null) {
            pinnedClips.forEach {
                onHistoryChangeListener?.onClipboardHistoryEntryAdded(historyEntries.indexOf(it))
            }
        }
    }

    fun onDestroy() {
        clipboardManager.removePrimaryClipChangedListener(this)
        cancelRetentionCheck()
    }

    override fun onPrimaryClipChanged() {
        // Make sure we read clipboard content only if history settings is set
        if (clipboardHistoryEnabled) {
            fetchPrimaryClip()
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Settings.PREF_ENABLE_CLIPBOARD_HISTORY -> {
                clipboardHistoryEnabled = Settings.readClipboardHistoryEnabled(sharedPreferences)
                if (clipboardHistoryEnabled) {
                    scheduleRetentionCheck()
                } else {
                    cancelRetentionCheck()
                    clearHistory()
                }
            }
            Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME -> {
                val newRetentionTimeMinutes =
                    sharedPreferences?.getInt(key, DEFAULT_RETENTION_TIME_MIN) ?: return
                if (newRetentionTimeMinutes == 0) {
                    cancelRetentionCheck()
                }
                else if (maxClipRetentionTime == 0L) { // retention limit has been enabled
                    scheduleRetentionCheck(newRetentionTimeMinutes * ONE_MINUTE_MILLIS)
                }
                maxClipRetentionTime = newRetentionTimeMinutes * ONE_MINUTE_MILLIS
            }
        }
    }

    private fun fetchPrimaryClip() {
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount == 0 || clipData.description?.hasMimeType("text/*") == false) return
        clipData.getItemAt(0)?.let { clipItem ->
            val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData) ?: System.currentTimeMillis()
            val content = clipItem.coerceToText(latinIME)
            if (TextUtils.isEmpty(content)) return

            val duplicateEntryIndex = historyEntries.indexOfFirst { it.content.toString() == content.toString() }
            if (duplicateEntryIndex != -1) {
                val existingEntry = historyEntries[duplicateEntryIndex]
                if (existingEntry.timeStamp == timeStamp) return // nothing to change (may occur frequently starting with API 30)
                // older entry with the same text already exists, update the timestamp and re-sort the list
                existingEntry.timeStamp = timeStamp
                historyEntries.removeAt(duplicateEntryIndex)
                historyEntries.add(0, existingEntry)
                sortHistoryEntries()
                val newIndex = historyEntries.indexOf(existingEntry)
                onHistoryChangeListener?.onClipboardHistoryEntryMoved(duplicateEntryIndex, newIndex)
                return
            }
            if (historyEntries.any { it.content.toString() == content.toString() }) return

            val entry = ClipboardHistoryEntry(timeStamp, content)
            historyEntries.add(entry)
            sortHistoryEntries()
            val at = historyEntries.indexOf(entry)
            onHistoryChangeListener?.onClipboardHistoryEntryAdded(at)
        }
    }

    fun toggleClipPinned(ts: Long) {
        val from = historyEntries.indexOfFirst { it.timeStamp == ts }
        val historyEntry = historyEntries[from].apply {
            timeStamp = System.currentTimeMillis()
            isPinned = !isPinned
        }
        sortHistoryEntries()
        val to = historyEntries.indexOf(historyEntry)
        onHistoryChangeListener?.onClipboardHistoryEntryMoved(from, to)
        savePinnedClips()
    }

    fun clearHistory() {
        ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        val pos = historyEntries.indexOfFirst { !it.isPinned }
        val count = historyEntries.count { !it.isPinned }
        historyEntries.removeAll { !it.isPinned }
        if (onHistoryChangeListener != null) {
            onHistoryChangeListener?.onClipboardHistoryEntriesRemoved(pos, count)
        }
    }

    fun canRemove(index: Int) = historyEntries.getOrNull(index)?.isPinned != true

    fun removeEntry(index: Int) {
        if (canRemove(index))
            historyEntries.removeAt(index)
    }

    private fun sortHistoryEntries() {
        historyEntries.sort()
    }

    private fun checkClipRetentionElapsed() {
        val now = System.currentTimeMillis()
        if (latinIME.mSettings.current?.mClearPrimaryClipboard == true) {
            ClipboardManagerCompat.clearPrimaryClip(clipboardManager)
        }
        historyEntries.removeAll { !it.isPinned && (now - it.timeStamp) > maxClipRetentionTime }
    }

    fun scheduleRetentionCheck(timeMillis: Long = maxClipRetentionTime) {
        if (isRetentionCheckScheduled || !clipboardHistoryEnabled || timeMillis <= 0) return
        retentionCheckHandler.postDelayed(retentionCheckRunnable, timeMillis)
        isRetentionCheckScheduled = true
    }

    fun cancelRetentionCheck() {
        if (!isRetentionCheckScheduled) return
        retentionCheckHandler.removeCallbacksAndMessages(null)
        isRetentionCheckScheduled = false
    }

    fun getHistorySize() = historyEntries.size

    fun getHistoryEntry(position: Int) = historyEntries[position]

    fun getHistoryEntryContent(timeStamp: Long) = historyEntries.first { it.timeStamp == timeStamp }

    fun setHistoryChangeListener(l: OnHistoryChangeListener?) {
        onHistoryChangeListener = l
    }

    fun retrieveClipboardContent(): CharSequence {
        val clipData = clipboardManager.primaryClip ?: return ""
        if (clipData.itemCount == 0) return ""
        return clipData.getItemAt(0)?.coerceToText(latinIME) ?: ""
    }

    // pinned clips are stored in default shared preferences, not in device protected preferences!
    private fun loadPinnedClips() {
        val pinnedClipString = Settings.readPinnedClipString(latinIME)
        if (pinnedClipString.isEmpty()) return
        val pinnedClips: List<ClipboardHistoryEntry> = Json.decodeFromString(pinnedClipString)
        latinIME.mHandler.postUpdateClipboardPinnedClips(pinnedClips)
    }

    private fun savePinnedClips() {
        val pinnedClips = Json.encodeToString(historyEntries.filter { it.isPinned })
        Settings.writePinnedClipString(latinIME, pinnedClips)
    }

    interface OnHistoryChangeListener {
        fun onClipboardHistoryEntryAdded(at: Int)
        fun onClipboardHistoryEntriesRemoved(pos: Int, count: Int)
        fun onClipboardHistoryEntryMoved(from: Int, to: Int)
    }

    companion object {
        // store pinned clips in companion object so they survive a keyboard switch (which destroys the current instance)
        private val historyEntries: MutableList<ClipboardHistoryEntry> = ArrayList()
        private const val ONE_MINUTE_MILLIS = 60 * 1000L
        private const val DEFAULT_RETENTION_TIME_MIN = 10
    }
}
