package com.simplemobiletools.musicplayer.adapters

import android.graphics.PorterDuff
import android.support.v7.view.ActionMode
import android.support.v7.widget.RecyclerView
import android.view.*
import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback
import com.bignerdranch.android.multiselector.MultiSelector
import com.bignerdranch.android.multiselector.SwappingHolder
import com.simplemobiletools.commons.extensions.beInvisibleIf
import com.simplemobiletools.commons.extensions.deleteFiles
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.RemovePlaylistDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.helpers.DBHelper
import com.simplemobiletools.musicplayer.interfaces.RefreshItemsListener
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.item_playlist.view.*
import java.io.File
import java.util.*

class PlaylistsAdapter(val activity: SimpleActivity, val mItems: List<Playlist>, val listener: RefreshItemsListener?, val itemClick: (Playlist) -> Unit) :
        RecyclerView.Adapter<PlaylistsAdapter.ViewHolder>() {
    val multiSelector = MultiSelector()
    val views = ArrayList<View>()

    companion object {
        var actMode: ActionMode? = null
        val markedItems = HashSet<Int>()
        var textColor = 0
        var itemCnt = 0

        fun toggleItemSelection(itemView: View, select: Boolean, pos: Int = -1) {
            itemView.playlist_frame.isSelected = select
            if (pos == -1)
                return

            if (select)
                markedItems.add(pos)
            else
                markedItems.remove(pos)
        }

        fun updateTitle(cnt: Int) {
            actMode?.title = "$cnt / $itemCnt"
            actMode?.invalidate()
        }
    }

    init {
        textColor = activity.config.textColor
        itemCnt = mItems.size
    }

    private val multiSelectorMode = object : ModalMultiSelectorCallback(multiSelector) {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.cab_delete -> askConfirmDelete()
                R.id.cab_rename -> showRenameDialog()
                else -> return false
            }
            return true
        }

        override fun onCreateActionMode(actionMode: ActionMode?, menu: Menu?): Boolean {
            super.onCreateActionMode(actionMode, menu)
            actMode = actionMode
            activity.menuInflater.inflate(R.menu.cab_playlists, menu)
            return true
        }

        override fun onPrepareActionMode(actionMode: ActionMode?, menu: Menu): Boolean {
            menu.findItem(R.id.cab_rename).isVisible = multiSelector.selectedPositions.size <= 1
            return true
        }

        override fun onDestroyActionMode(actionMode: ActionMode?) {
            super.onDestroyActionMode(actionMode)
            views.forEach { toggleItemSelection(it, false) }
            markedItems.clear()
        }
    }

    private fun askConfirmDelete() {
        RemovePlaylistDialog(activity) {
            actMode?.finish()
            val selections = multiSelector.selectedPositions
            val ids = selections.map { mItems[it].id } as ArrayList<Int>
            if (it) {
                deletePlaylistSongs(ids) {
                    removePlaylists(ids)
                }
            } else {
                removePlaylists(ids)
            }
        }
    }

    private fun deletePlaylistSongs(ids: ArrayList<Int>, callback: () -> Unit) {
        var cnt = ids.size
        ids.map { activity.dbHelper.getPlaylistSongPaths(it).map(::File) as ArrayList<File> }
                .forEach {
                    activity.deleteFiles(it) {
                        if (--cnt <= 0) {
                            callback()
                        }
                    }
                }
    }

    private fun removePlaylists(ids: ArrayList<Int>) {
        activity.dbHelper.removePlaylists(ids)
        if (ids.contains(DBHelper.ALL_SONGS_ID)) {
            activity.toast(R.string.this_playlist_cannot_be_deleted)
        }
        listener?.refreshItems()
    }

    private fun showRenameDialog() {
        val selections = multiSelector.selectedPositions
        NewPlaylistDialog(activity, mItems[selections[0]]) {
            actMode?.finish()
            listener?.refreshItems()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent?.context).inflate(R.layout.item_playlist, parent, false)
        return ViewHolder(view, activity, multiSelectorMode, multiSelector, itemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        views.add(holder.bindView(mItems[position]))
    }

    override fun getItemCount() = mItems.size

    class ViewHolder(view: View, val activity: SimpleActivity, val multiSelectorCallback: ModalMultiSelectorCallback, val multiSelector: MultiSelector,
                     val itemClick: (Playlist) -> (Unit)) : SwappingHolder(view, MultiSelector()) {
        fun bindView(playlist: Playlist): View {

            itemView.apply {
                playlist_title.text = playlist.title
                toggleItemSelection(this, markedItems.contains(layoutPosition), layoutPosition)

                playlist_title.setTextColor(textColor)
                playlist_icon.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
                playlist_icon.beInvisibleIf(playlist.id != context.config.currentPlaylist)

                setOnClickListener { viewClicked(playlist) }
                setOnLongClickListener { viewLongClicked(); true }
            }

            return itemView
        }

        private fun viewClicked(playlist: Playlist) {
            if (multiSelector.isSelectable) {
                val isSelected = multiSelector.selectedPositions.contains(layoutPosition)
                multiSelector.setSelected(this, !isSelected)
                toggleItemSelection(itemView, !isSelected, layoutPosition)

                val selectedCnt = multiSelector.selectedPositions.size
                if (selectedCnt == 0) {
                    actMode?.finish()
                } else {
                    updateTitle(selectedCnt)
                }
                actMode?.invalidate()
            } else {
                itemClick(playlist)
            }
        }

        private fun viewLongClicked() {
            if (!multiSelector.isSelectable) {
                activity.startSupportActionMode(multiSelectorCallback)
                multiSelector.setSelected(this@ViewHolder, true)
                updateTitle(multiSelector.selectedPositions.size)
                toggleItemSelection(itemView, true, layoutPosition)
            }
        }
    }
}
