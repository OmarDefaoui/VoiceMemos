package com.nordef.voicememos

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.nordef.voicememos.animations.MarginBottomAnimation
import com.nordef.voicememos.app.MainApplication
import com.nordef.voicememos.app.RawSamples
import com.nordef.voicememos.app.Sound
import com.nordef.voicememos.app.Storage
import com.nordef.voicememos.encoders.*
import com.nordef.voicememos.fragment.FragmentDisplayRecords
import com.nordef.voicememos.fragment.FragmentDisplayRecords.Companion.recordings
import com.nordef.voicememos.service.RecordingService
import com.nordef.voicememos.ui.PitchView
import java.io.File


class MainActivity : AppCompatActivity() {

    lateinit var view_grey: View
    lateinit var ll_bottom_sheet: LinearLayout

    lateinit var rl_details: RelativeLayout
    lateinit var tv_recording_title: TextView
    lateinit var tv_recording_time: TextView
    lateinit var rl_record: RelativeLayout
    lateinit var rl_recording_item_container: RelativeLayout
    lateinit var tv_recording_state: TextView

    lateinit var rl_cancel: RelativeLayout
    lateinit var rl_pause_resume: RelativeLayout
    lateinit var iv_play: ImageView
    lateinit var rl_save: RelativeLayout
    lateinit var rl_edit_box: View
    lateinit var iv_play_preview: ImageView
    lateinit var iv_cut: ImageView
    lateinit var iv_edit_done: ImageView
    lateinit var recording_pitch: PitchView

    lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>

    var isRecording = false
    var isBottomSheetExpanded = false

    var themeId: Int = -2


    //////////////


    val TAG = MainActivity::class.java.getSimpleName()

    var START_PAUSE = MainActivity::class.java.canonicalName!! + ".START_PAUSE"

    internal var pscl: PhoneStateChangeListener? = PhoneStateChangeListener()
    internal var handle = Handler()
    lateinit var encoder: FileEncoder

    // do we need to start recording immidiatly?
    internal var start = false

    var thread: Thread? = null
    // lock for bufferSize
    internal val bufferSizeLock = Any()
    // dynamic buffer size. big for backgound recording. small for realtime view updates.
    internal var bufferSize: Int = 0
    // variable from settings. how may samples per second.
    internal var sampleRate: Int = 0
    // pitch size in samples. how many samples count need to update view. 4410 for 100ms update.
    internal var samplesUpdate: Int = 0
    // output target file 2016-01-01 01.01.01.wav
    lateinit var targetFile: File
    // how many samples passed for current recording
    internal var samplesTime: Long = 0
    // current cut position in samples from begining of file
    internal var editSample: Long = -1

    // current sample index in edit mode while playing;
    internal var playIndex: Long = 0
    // send ui update every 'playUpdate' samples.
    internal var playUpdate: Int = 0
    // current play sound track
    var play: AudioTrack? = null

    lateinit var storage: Storage
    lateinit var sound: Sound
    internal var receiver: RecordingReceiver? = null


    internal inner class RecordingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == PAUSE_BUTTON) {
                pauseButton()
            }
        }
    }

    internal inner class PhoneStateChangeListener : PhoneStateListener() {
        var wasRinging: Boolean = false
        var pausedByCall: Boolean = false

        override fun onCallStateChanged(s: Int, incomingNumber: String) {
            when (s) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    wasRinging = true
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> {
                    wasRinging = true
                    if (thread != null) {
                        stopRecording(getString(R.string.hold_by_call))
                        pausedByCall = true
                    }
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    if (pausedByCall) {
                        startRecording()
                    }
                    wasRinging = false
                    pausedByCall = false
                }
            }
        }
    }


    ///////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppTheme(getAppTheme())
        setContentView(R.layout.activity_main)

        initViews()
        setClickListener()

        object : Thread() {
            override fun run() {
                runOnUiThread {
                    permitted()
                }
            }
        }.start()

        initRecording()
    }

    private fun initRecording() {
        storage = Storage(this)
        sound = Sound(this)

        edit(false, false)

        try {
            targetFile = storage.getNewFile
        } catch (e: RuntimeException) {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            collapseBS()
            return
        }


        tv_recording_title.text = targetFile.name

        val shared = PreferenceManager.getDefaultSharedPreferences(this)

        if (shared.getBoolean(MainApplication.PREFERENCE_CALL, false)) {
            val tm = this.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.listen(pscl, PhoneStateListener.LISTEN_CALL_STATE)
        }

        sampleRate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, "")!!)

        if (Build.VERSION.SDK_INT < 23 && isEmulator()) {
            // old emulators are not going to record on high sample rate.
            Toast.makeText(
                    this, "Emulator Detected. Reducing Sample Rate to 8000 Hz",
                    Toast.LENGTH_SHORT
            ).show()
            sampleRate = 8000
        }

        samplesUpdate = (recording_pitch.pitchTime * sampleRate / 1000.0).toInt()

        updateBufferSize(false)

        loadSamples()

        rl_cancel.setOnClickListener {
            cancelDialog(Runnable {
                stopRecording()
                storage.delete(storage.tempRecording)
                collapseBS()
            })
        }

        rl_pause_resume.setOnClickListener { pauseButton() }

        rl_save.setOnClickListener {
            rl_save.isEnabled = false
            rl_pause_resume.isEnabled = false
            rl_cancel.isEnabled = false

            Toast.makeText(this, getString(R.string.file_saved), Toast.LENGTH_SHORT).show()
            stopRecording(getString(R.string.encoding))
            encoding(Runnable { collapseBS() })

            object : Thread() {
                override fun run() {
                    runOnUiThread {
                        //notify data added
                        //recordings.scan(FragmentDisplayRecords.storage.storagePath)
                        FragmentDisplayRecords.load()
                    }
                }
            }.start()
        }

        val a = intent.action
        if (a != null && a == START_PAUSE) {
            // pretend we already start it
            start = false
            stopRecording(getString(R.string.pause))
        }

        receiver = RecordingReceiver()
        val filter = IntentFilter()
        filter.addAction(PAUSE_BUTTON)
        registerReceiver(receiver, filter)
    }

    internal fun loadSamples() {
        if (!storage.tempRecording.exists()) {
            updateSamples(0)
            return
        }

        val rs = RawSamples(storage.tempRecording)
        samplesTime = rs.samples

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        val count = recording_pitch.getMaxPitchCount(metrics.widthPixels)

        val buf = ShortArray(count * samplesUpdate)
        var cut = samplesTime - buf.size

        if (cut < 0)
            cut = 0

        rs.open(cut, buf.size)
        val len = rs.read(buf)
        rs.close()

        recording_pitch.clear(cut / samplesUpdate)
        var i = 0
        while (i < len) {
            val dB = RawSamples.getDB(buf, i, samplesUpdate)
            recording_pitch.add(dB)
            i += samplesUpdate
        }
        updateSamples(samplesTime)
    }

    internal fun isEmulator(): Boolean {
        return "goldfish" == Build.HARDWARE
    }

    internal fun pauseButton() {
        if (thread != null) {
            stopRecording(getString(R.string.pause))
        } else {
            editCut()

            startRecording()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")

        if (themeId != getAppTheme()) {
            finish()
            MainActivity.startActivity(this)
            return
        }

        updateBufferSize(false)

        // start once
        if (start) {
            start = false
            if (permitted()) {
                startRecording()
            }
        }

        val recording = thread != null

        //RecordingService.startService(this, targetFile.name, recording)

        if (recording) {
            recording_pitch.record()
        } else {
            if (editSample != -1L)
                edit(true, false)
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        updateBufferSize(true)
        editPlay(false)
        recording_pitch.stop()
    }

    @SuppressLint("ClickableViewAccessibility")
    internal fun stopRecording(status: String) {
        setState(status)
        iv_play.setImageResource(R.drawable.ic_circle_red)

        stopRecording()

        RecordingService.startService(this, targetFile.name, thread != null)

        recording_pitch.setOnTouchListener { v, event ->
            edit(true, true)
            var x = event.x
            if (x < 0)
                x = 0f
            editSample = recording_pitch.edit(x) * samplesUpdate
            true
        }
    }

    internal fun stopRecording() {
        if (thread != null) {
            thread!!.interrupt()
            thread = null
        }
        recording_pitch.stop()
        sound.unsilent();
    }

    internal fun edit(show: Boolean, animate: Boolean) {
        if (show) {
            setState(getString(R.string.edit))
            editPlay(false)


            //show/hide edit box with animation
            MarginBottomAnimation.apply(rl_edit_box, true, animate)

            iv_cut.setOnClickListener { editCut() }

            iv_play_preview.setOnClickListener {
                if (play != null) {
                    editPlay(false)
                } else {
                    editPlay(true)
                }
            }

            iv_edit_done.setOnClickListener { edit(false, true) }
        } else {
            editSample = -1
            setState(getString(R.string.pause))
            editPlay(false)
            recording_pitch.edit(-1F)
            recording_pitch.stop()

            //show/hide edit box with animation
            MarginBottomAnimation.apply(rl_edit_box, false, animate)
        }
    }

    internal fun setState(s: String) {
        val free = storage.getFree(storage.tempRecording)

        val shared = PreferenceManager.getDefaultSharedPreferences(this)

        val rate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, "")!!)
        val m = if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO) 1 else 2
        val c = if (RawSamples.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT) 2 else 1

        val perSec = (c * m * rate).toLong()
        val sec = free / perSec * 1000

        tv_recording_state.text = s + " (" + (application as MainApplication).formatFree(free, sec) + ")"
    }

    internal fun editPlay(show: Boolean) {
        if (show) {
            playIndex = editSample

            playUpdate = PitchView.UPDATE_SPEED * sampleRate / 1000

            val handler = Handler()

            val listener = object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack) {
                    editPlay(false)
                }

                override fun onPeriodicNotification(track: AudioTrack) {
                    if (play != null) {
                        playIndex += playUpdate.toLong()
                        val p = playIndex / samplesUpdate.toFloat()
                        recording_pitch.play(p)
                    }
                }
            }

            val rs = RawSamples(storage.tempRecording)
            val len = (rs.samples - editSample)
            val buf = ShortArray(len.toInt())
            rs.open(editSample, buf.size)
            val r = rs.read(buf)
            play = sound.generateTrack(sampleRate, buf, r)
            play!!.play()
            play!!.positionNotificationPeriod = playUpdate
            play!!.setPlaybackPositionUpdateListener(listener, handler)
        } else {
            if (play != null) {
                play!!.release()
                play = null
            }
            recording_pitch.play(-1F)
            iv_play_preview.setImageResource(R.drawable.ic_play)
        }
    }

    internal fun editCut() {
        if (editSample == -1L)
            return

        val rs = RawSamples(storage.tempRecording)
        rs.trunk(editSample + samplesUpdate)
        rs.close()

        edit(false, true)
        loadSamples()
        recording_pitch.drawCalc()
    }

    internal fun cancelDialog(run: Runnable) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.confirm_cancel)
        builder.setMessage(R.string.are_you_sure_cancel)
        builder.setPositiveButton(R.string.yes) { dialog, which -> run.run() }
        builder.setNegativeButton(R.string.no) { dialog, which -> dialog.cancel() }
        builder.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestory")

        stopRecording()

        if (receiver != null) {
            unregisterReceiver(receiver)
            receiver = null
        }

        RecordingService.stopService(this)

        if (pscl != null) {
            val tm = this.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.listen(pscl, PhoneStateListener.LISTEN_NONE)
            pscl = null
        }

        recordings.close()
    }

    @SuppressLint("ClickableViewAccessibility")
    internal fun startRecording() {
        edit(false, true)
        recording_pitch.setOnTouchListener(null)

        setState(getString(R.string.recording))

        sound.silent();

        iv_play.setImageResource(R.drawable.ic_pause)

        recording_pitch.record()

        if (thread != null) {
            thread!!.interrupt()
        }

        thread = Thread(Runnable {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            val p = android.os.Process.getThreadPriority(android.os.Process.myTid())

            if (p != android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) {
                Log.e(TAG, "Unable to set Thread Priority " + android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            }

            var rs: RawSamples? = null
            var recorder: AudioRecord? = null
            try {
                rs = RawSamples(storage.tempRecording)

                rs.open(samplesTime)

                var min = AudioRecord.getMinBufferSize(sampleRate, RawSamples.CHANNEL_CONFIG,
                        RawSamples.AUDIO_FORMAT)
                if (min <= 0) {
                    sampleRate = 8000
                    min = AudioRecord.getMinBufferSize(sampleRate, RawSamples.CHANNEL_CONFIG,
                            RawSamples.AUDIO_FORMAT)
                    if (min <= 0) {
                        throw RuntimeException("Unable to initialize AudioRecord: Bad audio values")
                    }
                }

                // min = 1 sec
                min = Math.max(
                        sampleRate * if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO) 1 else 2,
                        min
                )

                recorder = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        RawSamples.CHANNEL_CONFIG,
                        RawSamples.AUDIO_FORMAT,
                        min
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    throw RuntimeException("Unable to initialize AudioRecord")
                }

                var start = System.currentTimeMillis()
                recorder.startRecording()

                var samplesTimeCount = 0
                // how many samples we need to update 'samples'. time clock. every 1000ms.
                val samplesTimeUpdate =
                        1000 / 1000 * sampleRate * if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO) 1 else 2

                var buffer: ShortArray? = null

                var stableRefresh = false

                while (!Thread.currentThread().isInterrupted) {
                    synchronized(bufferSizeLock) {
                        if (buffer == null || buffer!!.size != bufferSize)
                            buffer = ShortArray(bufferSize)
                    }

                    val readSize = recorder.read(buffer!!, 0, buffer!!.size)
                    if (readSize <= 0) {
                        break
                    }
                    val end = System.currentTimeMillis()

                    val diff = (end - start) * sampleRate / 1000

                    start = end

                    val s = if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO) readSize else readSize / 2

                    if (stableRefresh || diff >= s) {
                        stableRefresh = true

                        rs.write(buffer!!)

                        var i = 0
                        while (i < readSize) {
                            val dB = RawSamples.getDB(buffer!!, i, samplesUpdate)
                            handle.post { recording_pitch.add(dB) }
                            i += samplesUpdate
                        }

                        samplesTime += s.toLong()
                        samplesTimeCount += s
                        if (samplesTimeCount > samplesTimeUpdate) {
                            val m = samplesTime
                            handle.post { updateSamples(m) }
                            samplesTimeCount -= samplesTimeUpdate
                        }
                    }
                }
            } catch (e: RuntimeException) {
                handle.post {
                    Log.e(TAG, Log.getStackTraceString(e))
                    Toast.makeText(this@MainActivity, getString(R.string.audio_record_error) +
                            " " + e.message, Toast.LENGTH_SHORT).show()
                    collapseBS()
                }
            } finally {
                // redraw view, we may add one last pich which is not been drawen because draw tread already interrupted.
                // to prevent resume recording jump - draw last added pitch here.
                handle.post { recording_pitch.drawEnd() }

                rs?.close()

                recorder?.release()
            }
        }, "RecordingThread")
        thread!!.start()

        RecordingService.startService(this, targetFile.name, thread != null)
    }

    // calculate buffer length dynamically, this way we can reduce thread cycles when activity in background
    // or phone screen is off.
    internal fun updateBufferSize(pause: Boolean) {
        synchronized(bufferSizeLock) {
            val samplesUpdate: Int

            if (pause) {
                // we need make buffer multiply of pitch.getPitchTime() (100 ms).
                // to prevent missing blocks from view otherwise:

                // file may contain not multiply 'samplesUpdate' count of samples. it is about 100ms.
                // we can't show on pitchView sorter then 100ms samples. we can't add partial sample because on
                // resumeRecording we have to apply rest of samplesUpdate or reload all samples again
                // from file. better then confusing user we cut them on next resumeRecording.

                var l: Long = 1000
                l = l / recording_pitch.pitchTime * recording_pitch.pitchTime
                samplesUpdate = (l * sampleRate / 1000.0).toInt()
            } else {
                samplesUpdate = this.samplesUpdate
            }

            bufferSize =
                    if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO) samplesUpdate else samplesUpdate * 2
        }
    }

    internal fun updateSamples(samplesTime: Long) {
        val ms = samplesTime / sampleRate * 1000

        tv_recording_time.text = MainApplication.formatDuration(this, ms)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> if (permitted(permissions)) {
                storage.migrateLocalStorage()
                recordings.scan(storage.storagePath)

            } else {
                Toast.makeText(this, R.string.not_permitted, Toast.LENGTH_SHORT).show()
                permitted()
            }
        }
    }

    val PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    internal fun permitted(ss: Array<String>): Boolean {
        for (s in ss) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    internal fun permitted(): Boolean {
        for (s in PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, 1)
                return false
            }
        }
        return true
    }

    internal fun getInfo(): EncoderInfo {
        val channels = if (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO) 2 else 1
        val bps = if (RawSamples.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT) 16 else 8

        return EncoderInfo(channels, sampleRate, bps)
    }

    internal fun encoding(run: Runnable) {
        val `in` = storage.tempRecording
        val out = targetFile

        val info = getInfo()

        var e: Encoder? = null

        val shared = PreferenceManager.getDefaultSharedPreferences(this)
        val ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "")

        if (ext == "wav") {
            e = FormatWAV(info, out)
        } else if (ext == "m4a") {
            e = FormatM4A(info, out)
        } else if (ext == "3gp") {
            e = Format3GP(info, out)
        }

        encoder = FileEncoder(this, `in`, e!!)

        val d = ProgressDialog(this)
        d.setTitle(getString(R.string.encoding_title))
        d.setMessage(".../" + targetFile.name)
        d.max = 100
        d.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        d.isIndeterminate = false
        d.show()

        encoder.run(Runnable { d.progress = encoder.progress }, Runnable {
            d.cancel()
            storage.delete(`in`)

            val edit = shared.edit()
            edit.putString(MainApplication.PREFERENCE_LAST, out.name)
            edit.commit()

            run.run()
        }, Runnable {
            Toast.makeText(
                    this@MainActivity, encoder.exception.message,
                    Toast.LENGTH_SHORT
            ).show()
            collapseBS()
        })
    }

    fun collapseBS() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        isBottomSheetExpanded = false

        rl_record.visibility = View.VISIBLE
        rl_details.visibility = View.GONE
        rl_recording_item_container.visibility = View.INVISIBLE

        if (receiver != null) {
            unregisterReceiver(receiver)
            receiver = null
        }

        RecordingService.stopService(this)

        if (pscl != null) {
            val tm = this.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.listen(pscl, PhoneStateListener.LISTEN_NONE)
            pscl = null
        }

        START_PAUSE = MainActivity::class.java.canonicalName!! + ".START_PAUSE"
        PAUSE_BUTTON = MainActivity::class.java.canonicalName!! + ".PAUSE_BUTTON"

        pscl = PhoneStateChangeListener()
        handle = Handler()

        // do we need to start recording immidiatly?
        start = false

        thread = null
        // dynamic buffer size. big for backgound recording. small for realtime view updates.
        bufferSize = 0
        // variable from settings. how may samples per second.
        sampleRate = 0
        // pitch size in samples. how many samples count need to update view. 4410 for 100ms update.
        samplesUpdate = 0
        // how many samples passed for current recording
        samplesTime = 0
        // current cut position in samples from begining of file
        editSample - 1

        // current sample index in edit mode while playing;
        playIndex = 0
        // send ui update every 'playUpdate' samples.
        playUpdate = 0
        // current play sound track
        play = null

        receiver = null

        recording_pitch.clear(0)
        //updateSamples(0)
        initRecording()

        rl_save.isEnabled = true
        rl_pause_resume.isEnabled = true
        rl_cancel.isEnabled = true
    }


    ////////


    //////


    ///////

    private fun setClickListener() {
        rl_record.setOnClickListener {

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            isBottomSheetExpanded = true

            rl_record.visibility = View.GONE
            rl_details.visibility = View.VISIBLE
            rl_recording_item_container.visibility = View.VISIBLE
            isRecording = true

            val recording = thread != null

            RecordingService.startService(this, targetFile.name, recording)

            if (recording) {
                recording_pitch.record()
            } else {
                if (editSample != -1L)
                    edit(true, false)
            }

            pauseButton()
        }

    }

    private fun initViews() {
        initBottomSheet()
        view_grey = findViewById(R.id.view_grey)

        rl_details = findViewById(R.id.rl_details)
        tv_recording_title = findViewById(R.id.tv_recording_title)
        tv_recording_time = findViewById(R.id.tv_recording_time)
        rl_record = findViewById(R.id.rl_record)
        rl_recording_item_container = findViewById(R.id.rl_recording_item_container)
        tv_recording_state = findViewById(R.id.tv_recording_state)

        rl_cancel = findViewById(R.id.rl_cancel)
        rl_pause_resume = findViewById(R.id.rl_pause_resume)
        iv_play = findViewById(R.id.iv_play)
        rl_save = findViewById(R.id.rl_save)
        rl_edit_box = findViewById(R.id.rl_edit_box)
        iv_play_preview = findViewById(R.id.iv_play_preview)
        iv_cut = findViewById(R.id.iv_cut)
        iv_edit_done = findViewById(R.id.iv_edit_done)
        recording_pitch = findViewById(R.id.recording_pitch)
    }

    fun initBottomSheet() {
        // get the bottom sheet view
        ll_bottom_sheet = findViewById(R.id.ll_bottom_sheet)

        // init the bottom sheet behavior
        bottomSheetBehavior = BottomSheetBehavior.from(ll_bottom_sheet)

        // set callback for changes
        bottomSheetBehavior.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                //this lines is used to set BS change state only programmatically -only when user click on buttons-
                /*if (newState == BottomSheetBehavior.STATE_DRAGGING) {
                    if (isBottomSheetExpanded)
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    else
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                }*/
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                view_grey.alpha = slideOffset / 4
            }
        })
    }

    fun setAppTheme(id: Int) {
        super.setTheme(id)

        themeId = id
    }

    internal fun getAppTheme(): Int {
        return MainApplication.getTheme(
                this, R.style.AppThemeLight_NoActionBar,
                R.style.AppThemeDark_NoActionBar
        )
    }

    companion object {
        var PAUSE_BUTTON = MainActivity::class.java.canonicalName!! + ".PAUSE_BUTTON"

        fun startActivity(context: Context) {
            val i = Intent(context, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            context.startActivity(i)
        }
    }
}
