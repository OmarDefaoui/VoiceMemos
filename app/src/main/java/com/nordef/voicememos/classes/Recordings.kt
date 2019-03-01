package com.nordef.voicememos.classes

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.StrictMode
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.nordef.voicememos.R
import com.nordef.voicememos.animations.RecordingAnimation
import com.nordef.voicememos.animations.RemoveItemAnimation
import com.nordef.voicememos.app.MainApplication
import com.nordef.voicememos.app.Storage
import com.nordef.voicememos.fragment.FragmentDisplayRecords
import com.nordef.voicememos.fragment.FragmentDisplayRecords.Companion.TAG
import com.nordef.voicememos.fragment.FragmentDisplayRecords.Companion.TYPE_COLLAPSED
import com.nordef.voicememos.fragment.FragmentDisplayRecords.Companion.TYPE_DELETED
import com.nordef.voicememos.fragment.FragmentDisplayRecords.Companion.TYPE_EXPANDED
import com.nordef.voicememos.fragment.FragmentDisplayRecords.Companion.handler
import com.nordef.voicememos.fragment.FragmentDisplayRecords.Companion.list
import com.nordef.voicememos.fragment.FragmentDisplayRecords.Companion.scrollState
import com.nordef.voicememos.fragment.FragmentDisplayRecords.Companion.storage
import com.nordef.voicememos.ui.OpenFileDialog
import com.nordef.voicememos.ui.PopupShareActionProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class Recordings(context: Context) : ArrayAdapter<File>(context, 0) {

    internal var player: MediaPlayer? = null
    internal var updatePlayer: Runnable? = null
    internal var selected = -1

    internal var durations: MutableMap<File, Int> = TreeMap()

    lateinit var shareProvider: PopupShareActionProvider

    var listFilesName: ArrayList<String> = ArrayList()

    fun scan(dir: File) {
        setNotifyOnChange(false)
        clear()
        durations.clear()

        val ff = storage.scan(dir)
        listFilesName.clear()

        for (f in ff) {
            if (f.isFile) {
                val mp = MediaPlayer.create(context, Uri.fromFile(f))
                if (mp != null) {
                    val d = mp.duration
                    mp.release()
                    durations[f] = d
                    add(f)
                    listFilesName.add(f.name)
                } else {
                    Log.e(TAG, f.toString())
                }
            }
        }

        //sort(SortFiles())
        notifyDataSetChanged()
    }

    fun close() {
        if (player != null) {
            player!!.release()
            player = null
        }
        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer)
            updatePlayer = null
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        val inflater = LayoutInflater.from(context)

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.row_records, parent, false)
            convertView!!.tag = -1
        }

        val view = convertView
        val base = convertView.findViewById<LinearLayout>(R.id.recording_base)

        if (convertView.tag as Int == TYPE_DELETED) {
            RemoveItemAnimation.restore(base)
            convertView.tag = -1
        }

        val f = getItem(position)

        val title = convertView.findViewById<View>(R.id.tv_name) as TextView
        title.text = f!!.name

        val s = SimpleDateFormat("${context.getString(R.string.date_format)} HH:mm:ss")
        val time = convertView.findViewById(R.id.tv_date) as TextView
        time.text = s.format(Date(f.lastModified()))

        val dur = convertView.findViewById(R.id.tv_duration) as TextView
        dur.text = MainApplication.formatDuration(context, durations[f]!!.toLong())

        val size = convertView.findViewById(R.id.tv_size) as TextView
        size.text = MainApplication.formatSize(context, f.length())

        val playerBase = convertView.findViewById<LinearLayout>(R.id.ll_expand)
        playerBase.setOnClickListener { }

        val delete = Runnable {
            val builder = AlertDialog.Builder(context)
            builder.setTitle(R.string.delete_recording)
            builder.setMessage("...\\" + f.name + "\n\n" + context.getString(R.string.are_you_sure))
            builder.setPositiveButton(R.string.yes, DialogInterface.OnClickListener { dialog, which ->
                playerStop()
                dialog.cancel()
                RemoveItemAnimation.apply(list, base) {
                    f.delete()
                    view.tag = TYPE_DELETED
                    select(-1)
                    load()
                }
            })
            builder.setNegativeButton(R.string.no, DialogInterface.OnClickListener
            { dialog, which -> dialog.cancel() })
            builder.show()
        }

        val rename = Runnable {
            val e = OpenFileDialog.EditTextDialog(context)
            e.setTitle(context.getString(R.string.rename_recording))
            e.text = Storage.getNameNoExt(f)
            e.setPositiveButton { dialog, which ->
                val ext = Storage.getExt(f)
                val s = String.format("%s.%s", e.text, ext)
                val ff = File(f.parent, s)
                f.renameTo(ff)
                load()
                notifyDataSetChanged()
            }
            e.show()
        }

        if (selected == position) {
            RecordingAnimation.apply(
                    list,
                    convertView,
                    true,
                    scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE && convertView.tag as Int == TYPE_COLLAPSED
            )
            convertView.tag = TYPE_EXPANDED

            updatePlayerText(convertView, f)

            val play = convertView.findViewById<ImageView>(R.id.iv_play)
            play.setOnClickListener {
                if (player == null) {
                    playerPlay(playerBase, f)
                } else if (player!!.isPlaying) {
                    playerPause(playerBase, f)
                } else {
                    playerPlay(playerBase, f)
                }
            }

            val edit = convertView.findViewById<ImageView>(R.id.iv_edit)
            edit.setOnClickListener { rename.run() }

            val share = convertView.findViewById<ImageView>(R.id.iv_share)
            share.setOnClickListener {
                shareProvider = PopupShareActionProvider(context, share)

                val builder = StrictMode.VmPolicy.Builder()
                StrictMode.setVmPolicy(builder.build())

                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "audio/*"
                intent.putExtra(Intent.EXTRA_EMAIL, "")
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f))
                intent.putExtra(Intent.EXTRA_SUBJECT, f.name)
                intent.putExtra(
                        Intent.EXTRA_TEXT, context.getString(
                        R.string.shared_via,
                        context.getString(R.string.app_name)
                )
                )

                shareProvider.setShareIntent(intent)

                shareProvider.show()
            }

            val trash = convertView.findViewById<ImageView>(R.id.iv_delete)
            trash.setOnClickListener { delete.run() }

            convertView.setOnClickListener { select(-1) }
        } else {
            RecordingAnimation.apply(
                    list, convertView, false,
                    scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE &&
                            convertView.tag as Int == TYPE_EXPANDED
            )
            convertView.tag = TYPE_COLLAPSED

            convertView.setOnClickListener { select(position) }
        }

        convertView.setOnLongClickListener { v ->
            val popup = PopupMenu(context, v)
            val inflater = popup.menuInflater
            inflater.inflate(R.menu.menu_context, popup.menu)

            popup.setOnMenuItemClickListener(PopupMenu.OnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_delete) {
                    delete.run()
                    return@OnMenuItemClickListener true
                }
                if (item.itemId == R.id.action_rename) {
                    rename.run()
                    return@OnMenuItemClickListener true
                }
                false
            })
            popup.show()
            true
        }

        return convertView
    }

    internal fun playerPlay(v: View?, f: File?) {
        if (player == null)
            player = MediaPlayer.create(context, Uri.fromFile(f))
        if (player == null) {
            Toast.makeText(context, R.string.file_not_found, Toast.LENGTH_SHORT).show()
            return
        }
        player!!.start()

        updatePlayerRun(v, f)
    }

    internal fun playerPause(v: View, f: File?) {
        if (player != null) {
            player!!.pause()
        }
        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer)
            updatePlayer = null
        }
        updatePlayerText(v, f)
    }

    internal fun playerStop() {
        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer)
            updatePlayer = null
        }
        if (player != null) {
            player!!.stop()
            player!!.release()
            player = null
        }
    }

    internal fun updatePlayerRun(v: View?, f: File?) {
        val playing = updatePlayerText(v, f)

        if (updatePlayer != null) {
            handler.removeCallbacks(updatePlayer)
            updatePlayer = null
        }

        if (!playing) {
            return
        }

        updatePlayer = Runnable { updatePlayerRun(v, f) }
        handler.postDelayed(updatePlayer, 200)
    }

    internal fun updatePlayerText(v: View?, f: File?): Boolean {
        val i = v!!.findViewById<ImageView>(R.id.iv_play)

        val playing = player != null && player!!.isPlaying

        i.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)

        val start = v.findViewById<View>(R.id.tv_record_start) as TextView
        val bar = v.findViewById<View>(R.id.seek_bar) as SeekBar
        val end = v.findViewById<View>(R.id.tv_record_end) as TextView

        var c = 0
        var d = durations[f]!!

        if (player != null) {
            c = player!!.currentPosition
            d = player!!.duration
        }

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser)
                    return

                if (player == null)
                    playerPlay(v, f)

                if (player != null) {
                    player!!.seekTo(progress)
                    if (!player!!.isPlaying)
                        playerPlay(v, f)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        start.text = MainApplication.formatDuration(context, c.toLong())
        bar.max = d
        bar.keyProgressIncrement = 1
        bar.progress = c
        end.text = "-" + MainApplication.formatDuration(context, (d - c).toLong())

        return playing
    }

    fun select(pos: Int) {
        selected = pos
        notifyDataSetChanged()
        playerStop()
    }

    // load recordings
    fun load() {
        FragmentDisplayRecords.recordings.scan(storage.storagePath)
    }

    fun filter(searchText: String?) {
        if (listFilesName.size == 0) {
            Toast.makeText(context, context.getString(R.string.no_result), Toast.LENGTH_SHORT).show()
            return
        }
        var searchText = searchText
        searchText = searchText!!.toLowerCase(Locale.getDefault())
        for (name in listFilesName) {
            if (name.toLowerCase(Locale.getDefault()).contains(searchText)) {
                list.smoothScrollToPosition(listFilesName.indexOf(name) + 1)
                return
            }
        }
        //name don't found
        Toast.makeText(context, context.getString(R.string.no_result), Toast.LENGTH_SHORT).show()
    }
}