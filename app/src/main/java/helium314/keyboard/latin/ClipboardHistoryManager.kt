// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.text.TextUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import helium314.keyboard.compat.ClipboardManagerCompat
import helium314.keyboard.keyboard.clipboard.ClipboardImageManager
import helium314.keyboard.latin.settings.Settings
import kotlin.collections.ArrayList

class ClipboardHistoryManager(
        private val latinIME: LatinIME
) : ClipboardManager.OnPrimaryClipChangedListener {

    private lateinit var clipboardManager: ClipboardManager
    private var onHistoryChangeListener: OnHistoryChangeListener? = null
    private val ClipboardImageManager = ClipboardImageManager(latinIME)

    fun onCreate() {
        clipboardManager = latinIME.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.addPrimaryClipChangedListener(this)
        if (historyEntries.isEmpty())
            loadPinnedClips()
        if (latinIME.mSettings.current?.mClipboardHistoryEnabled == true)
            fetchPrimaryClip()
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
    }

    override fun onPrimaryClipChanged() {
        // Make sure we read clipboard content only if history settings is set
        if (latinIME.mSettings.current?.mClipboardHistoryEnabled == true) {
            fetchPrimaryClip()
        }
    }

    private fun fetchPrimaryClip() {
        val clipData = clipboardManager.primaryClip ?: return
        if (clipData.itemCount == 0) return
        val isImage = clipData.description?.hasMimeType("image/*") == true
        val isText = !isImage && clipData.description?.hasMimeType("text/*") == true
        if (!isText && !isImage) return
        clipData.getItemAt(0)?.let { clipItem ->
            val timeStamp = ClipboardManagerCompat.getClipTimestamp(clipData)?.also { stamp ->
                if (historyEntries.any { it.timeStamp == stamp }) return // nothing to change (may occur frequently starting with API 30)
            } ?: System.currentTimeMillis()
            val imageUri: Uri?
            val content: CharSequence
            if (isText) {
                imageUri = null
                content = clipItem.coerceToText(latinIME)
                if (TextUtils.isEmpty(content)) return

                val duplicateEntryIndex = historyEntries.indexOfFirst { it.content.toString() == content.toString() }
                if (updateTimestamp(duplicateEntryIndex, timeStamp)) return // older entry with the same text already exists
            } else {
                imageUri = ClipboardImageManager.saveClipboardImage(clipItem.uri, timeStamp) ?: return
                content = clipData.description.label
            }

            val entry = ClipboardHistoryEntry(timeStamp, content, imageUri)
            historyEntries.add(entry)
            sortHistoryEntries()
            val at = historyEntries.indexOf(entry)
            onHistoryChangeListener?.onClipboardHistoryEntryAdded(at)
        }
    }

    // update the timestamp and re-sort the list
    // return true if updated the timestamp, false otherwise
    private fun updateTimestamp(index: Int, timeStamp: Long): Boolean {
        if (index !in historyEntries.indices) return false
        val existingEntry = historyEntries[index]
        existingEntry.timeStamp = timeStamp
        historyEntries.removeAt(index)
        historyEntries.add(0, existingEntry)
        sortHistoryEntries()
        val newIndex = historyEntries.indexOf(existingEntry)
        onHistoryChangeListener?.onClipboardHistoryEntryMoved(index, newIndex)
        return true
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
        ClipboardImageManager.removeOrphanedImages(historyEntries)
    }

    fun canRemove(index: Int) = index in historyEntries.indices && !historyEntries[index].isPinned

    fun removeEntry(index: Int) {
        if (canRemove(index)) {
            val imageUri = historyEntries[index].imageUri
            if (imageUri != null) {
                ClipboardImageManager.deleteClipboardImage(imageUri)
            }
            historyEntries.removeAt(index)
        }
    }

    private fun sortHistoryEntries() {
        historyEntries.sort()
    }

    private fun checkClipRetentionElapsed() {
        val mins = latinIME.mSettings.current.mClipboardHistoryRetentionTime
        if (mins <= 0) return // No retention limit
        val maxClipRetentionTime = mins * 60 * 1000L
        val now = System.currentTimeMillis()
        historyEntries.removeAll { !it.isPinned && (now - it.timeStamp) > maxClipRetentionTime }
        ClipboardImageManager.removeOrphanedImages(historyEntries)
    }

    // We do not want to update history while user is visualizing it, so we check retention only
    // when history is about to be shown
    fun prepareClipboardHistory() = checkClipRetentionElapsed()

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

    fun retrieveClipboardUri(): Uri? {
        val clipData = clipboardManager.primaryClip ?: return null
        if (clipData.itemCount == 0) return null
        return clipData.getItemAt(0)?.uri ?: return null
    }

    fun pasteClipboard() {
        if (clipboardManager.primaryClip?.description?.hasMimeType("text/*") == true) {
            val text = retrieveClipboardContent()
            if (!TextUtils.isEmpty(text))
                latinIME.onTextInput(text.toString())
        } else {
            val uri = retrieveClipboardUri() ?: return
            latinIME.onUriInput(uri)
        }
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
    }
}
