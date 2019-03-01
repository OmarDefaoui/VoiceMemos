package com.nordef.voicememos.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.nordef.voicememos.R
import com.nordef.voicememos.app.RawSamples
import com.nordef.voicememos.classes.ThemeUtils
import java.util.*

class PitchView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                          defStyleAttr: Int = 0) : ViewGroup(context, attrs,
        defStyleAttr) {

    // 'pitch length' in milliseconds (100ms)
    //
    // in other words how many milliseconds do we need to show whole pitch.
    var pitchTime: Int = 0
        internal set

    internal var data: MutableList<Double> = LinkedList()

    // how many pitches we can fit on screen
    internal var pitchScreenCount: Int = 0
    // how many pitches we should fit in memory
    internal var pitchMemCount: Int = 0
    // pitch delimiter length in px
    internal var pitchDlimiter: Int = 0
    // pitch length in px
    internal var pitchWidth: Int = 0
    // pitch length in pn + pitch delimiter length in px
    internal var pitchSize: Int = 0

    internal lateinit var graph: PitchGraphView
    internal lateinit var current: PitchCurrentView

    internal var time: Long = 0

    // how many samples were cut from begining of 'data' list
    internal var samples: Long = 0

    internal var edit: Runnable? = null
    // index
    internal var editPos = -1
    internal var editFlash = false
    // current playing position in samples
    internal var playPos = -1f
    internal var play: Runnable? = null

    internal var draw: Runnable? = null
    internal var offset = 0f

    internal lateinit var handler: Handler

    val end: Int
        get() {
            var end = data.size - 1

            if (editPos != -1) {
                end = editPos
            }
            if (playPos > 0) {
                end = playPos.toInt()
            }

            return end
        }

    class HandlerUpdate : Runnable {
        internal var start: Long = 0
        internal var updateSpeed: Long = 0
        internal lateinit var handler: Handler
        internal lateinit var run: Runnable

        override fun run() {
            this.run.run()

            val cur = System.currentTimeMillis()

            val diff = cur - start

            start = cur

            var delay = updateSpeed + (updateSpeed - diff)
            if (delay > updateSpeed)
                delay = updateSpeed

            if (delay > 0)
                this.handler.postDelayed(this, delay)
            else
                this.handler.post(this)
        }

        companion object {

            fun start(handler: Handler, run: Runnable, updateSpeed: Long): HandlerUpdate {
                val r = HandlerUpdate()
                r.run = run
                r.start = System.currentTimeMillis()
                r.updateSpeed = updateSpeed
                r.handler = handler
                // post instead of draw.run() so 'start' will measure actual queue time
                handler.postDelayed(r, updateSpeed)
                return r
            }

            fun stop(handler: Handler, run: Runnable) {
                handler.removeCallbacks(run)
            }
        }
    }

    inner class PitchGraphView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
        internal var paint: Paint
        internal var paintRed: Paint
        internal var editPaint: Paint
        internal var playPaint: Paint
        internal var cutColor: Paint

        init {

            paint = Paint()
            paint.color = resources.getColor(R.color.color_red)
            paint.strokeWidth = pitchWidth.toFloat()

            paintRed = Paint()
            paintRed.color = resources.getColor(R.color.color_orange)
            paintRed.strokeWidth = pitchWidth.toFloat()

            cutColor = Paint()
            cutColor.color = resources.getColor(R.color.color_grey)
            cutColor.strokeWidth = pitchWidth.toFloat()

            editPaint = Paint()
            editPaint.color = resources.getColor(R.color.color_yellow)
            editPaint.strokeWidth = pitchWidth.toFloat()

            playPaint = Paint()
            playPaint.color = resources.getColor(R.color.color_red)
            playPaint.strokeWidth = (pitchWidth / 2).toFloat()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)

            val w = View.MeasureSpec.getSize(widthMeasureSpec)

            pitchScreenCount = w / pitchSize + 1

            pitchMemCount = pitchScreenCount + 1
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)

            fit(pitchScreenCount)
        }

        fun calc() {
            if (data.size >= pitchMemCount) {
                val cur = System.currentTimeMillis()

                var tick = (cur - time) / pitchTime.toFloat()

                // force clear queue
                if (data.size > pitchMemCount + 1) {
                    tick = 0f
                    time = cur
                    fit(pitchMemCount)
                }

                if (tick > 1) {
                    if (data.size > pitchMemCount) {
                        tick -= 1f
                        time += pitchTime.toLong()
                    } else if (data.size == pitchMemCount) {
                        tick = 0f
                        time = cur
                    }
                    fit(data.size - 1)
                }

                offset = pitchSize * tick
            }
        }

        public override fun onDraw(canvas: Canvas) {
            val m = Math.min(pitchMemCount, data.size)

            //            if (edit != null) {
            //                float x = editPos * pitchSize + pitchSize / 2f;
            //                canvas.drawRect(x, 0, getWidth(), getHeight(), bg_cut);
            //            }

            for (i in 0 until m) {
                val dB = filterDB(i)

                var left = dB.toFloat()
                var right = dB.toFloat()

                val mid = height / 2f

                val x = -offset + (i * pitchSize).toFloat() + pitchSize / 2f

                var p = paint

                if (getDB(i) < 0) {
                    p = paintRed
                    left = 1f
                    right = 1f
                }

                if (editPos != -1 && i >= editPos)
                    p = cutColor

                // left channel pitch
                canvas.drawLine(x, mid, x, mid - mid * left - 1f, p)
                // right channel pitch
                canvas.drawLine(x, mid, x, mid + mid * right + 1f, p)
            }

            // paint edit mark
            if (editPos != -1 && editFlash) {
                val x = editPos * pitchSize + pitchSize / 2f
                canvas.drawLine(x, 0f, x, height.toFloat(), editPaint)
            }

            // paint play mark
            if (playPos > 0) {
                val x = playPos * pitchSize + pitchSize / 2f
                canvas.drawLine(x, 0f, x, height.toFloat(), playPaint)
            }
        }
    }

    inner class PitchCurrentView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
        internal var paint: Paint
        internal var textPaint: Paint
        internal var text: String
        internal var textBounds: Rect

        internal var dB: Double = 0.toDouble()

        init {

            text = "100 " + getContext().getString(R.string.db)
            textBounds = Rect()

            textPaint = Paint()
            textPaint.color = Color.GRAY
            textPaint.isAntiAlias = true
            textPaint.textSize = 20f

            paint = Paint()
            paint.color = resources.getColor(R.color.color_red)
            paint.strokeWidth = pitchWidth.toFloat()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = View.MeasureSpec.getSize(widthMeasureSpec)
            textPaint.getTextBounds(this.text, 0, this.text.length, textBounds)

            var h = paddingTop
            h += textBounds.height()
            h += ThemeUtils.dp2px(context, 2f)
            h += pitchWidth + paddingBottom

            setMeasuredDimension(w, h)
        }

        fun setText(text: String) {
            this.text = text
            textPaint.getTextBounds(this.text, 0, this.text.length, textBounds)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
        }

        internal fun update(end: Int) {
            dB = getDB(end) / RawSamples.MAXIMUM_DB

            var str = ""

            str = Integer.toString(getDB(end).toInt()) + " " + context.getString(R.string.db)

            setText(str)
        }

        public override fun onDraw(canvas: Canvas) {
            if (data.size > 0) {
                current.update(end)
            }

            var y = (paddingTop + textBounds.height()).toFloat()

            val x = width / 2 - textBounds.width() / 2
            canvas.drawText(text, x.toFloat(), y, textPaint)

            y += ThemeUtils.dp2px(context, 2f).toFloat()

            val left = dB.toFloat()
            val right = dB.toFloat()

            val mid = width / 2f

            y += (pitchWidth / 2).toFloat()

            canvas.drawLine(mid, y, mid - mid * left - 1f, y, paint)
            canvas.drawLine(mid, y, mid + mid * right + 1f, y, paint)
        }
    }

    init {

        create()
    }

    internal fun create() {
        handler = Handler()

        pitchDlimiter = ThemeUtils.dp2px(context, PITCH_DELIMITER)
        pitchWidth = ThemeUtils.dp2px(context, PITCH_WIDTH)
        pitchSize = pitchWidth + pitchDlimiter

        pitchTime = pitchSize * UPDATE_SPEED

        graph = PitchGraphView(context)
        addView(graph)

        //        fft = new FFTChartView(getContext()) {
        //            @Override
        //            public void onDraw(Canvas canvas) {
        //                if (data.size() > 0) {
        //                    short[] buf = dataSamples.get(getEnd());
        //                    double[] d = FFTView.fft(buf, 0, buf.length);
        //                    //double[] d = asDouble(buf, 0, buf.length);
        //                    fft.setBuffer(d);
        //                }
        //
        //                super.onDraw(canvas);
        //            }
        //        };
        //        fft.setPadding(0, ThemeUtils.dp2px(2), 0, 0);
        //        addView(fft);

        current = PitchCurrentView(context)
        current.setPadding(0, ThemeUtils.dp2px(context, 2f), 0, 0)
        addView(current)

        if (isInEditMode) {
            for (i in 0..2999) {
                data.add(-Math.sin(i.toDouble()) * RawSamples.MAXIMUM_DB)
            }
        }

        time = System.currentTimeMillis()
    }

    fun getThemeColor(id: Int): Int {
        return ThemeUtils.getThemeColor(context, id)
    }

    fun getMaxPitchCount(width: Int): Int {
        val pitchScreenCount = width / pitchSize + 1

        return pitchScreenCount + 1
    }

    fun clear(s: Long) {
        data.clear()
        samples = s
        offset = 0f
        edit = null
        draw = null
        play = null
    }

    fun fit(max: Int) {
        if (data.size > max) {
            val cut = data.size - max
            data.subList(0, cut).clear()
            samples += cut.toLong()

            val m = data.size - 1
            // screen rotate may cause play/edit offsets off screen
            if (editPos > m)
                editPos = m
            if (playPos > m)
                playPos = m.toFloat()
        }
    }

    fun add(a: Double) {
        data.add(a)
    }

    fun drawCalc() {
        graph.calc()
        draw()
    }

    fun drawEnd() {
        fit(pitchMemCount)
        offset = 0f
        draw()
    }

    fun getDB(i: Int): Double {
        var db = data[i]

        db = RawSamples.MAXIMUM_DB + db

        return db
    }

    fun filterDB(i: Int): Double {
        var db = getDB(i)

        // do not show below NOISE_DB
        db = db - RawSamples.NOISE_DB

        if (db < 0)
            db = 0.0

        val rest = RawSamples.MAXIMUM_DB - RawSamples.NOISE_DB

        db = db / rest

        return db
    }

    fun draw() {
        graph.invalidate()
        current.invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        val ww = measuredWidth - paddingRight - paddingLeft
        var hh = measuredHeight - paddingTop - paddingBottom

        current.measure(View.MeasureSpec.makeMeasureSpec(ww, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.AT_MOST))

        hh = hh - current.measuredHeight

        graph.measure(View.MeasureSpec.makeMeasureSpec(ww, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(hh, View.MeasureSpec.AT_MOST))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        graph.layout(paddingLeft, paddingTop,
                paddingLeft + graph.measuredWidth, paddingTop + graph.measuredHeight)

        current.layout(paddingLeft, graph.bottom,
                paddingLeft + current.measuredWidth, graph.bottom + current.measuredHeight)
    }

    fun stop() {
        if (edit != null)
            HandlerUpdate.stop(handler, edit!!)
        edit = null

        if (draw != null)
            HandlerUpdate.stop(handler, draw!!)
        draw = null

        if (play != null)
            HandlerUpdate.stop(handler, play!!)
        play = null

        draw()
    }

    fun edit(offset: Float): Long {
        if (offset < 0)
            editPos = -1
        else
            editPos = offset.toInt() / pitchSize

        playPos = -1f

        if (editPos >= pitchScreenCount)
            editPos = pitchScreenCount - 1

        if (editPos >= data.size)
            editPos = data.size - 1

        if (draw != null) {
            HandlerUpdate.stop(handler, draw!!)
            draw = null
        }

        if (play != null) {
            HandlerUpdate.stop(handler, play!!)
            play = null
        }

        draw()

        edit()

        return samples + editPos
    }

    fun edit() {
        if (edit == null) {
            editFlash = true

            edit = HandlerUpdate.start(handler, Runnable {
                draw()
                editFlash = !editFlash
            }, EDIT_UPDATE_SPEED.toLong())
        }
    }

    fun record() {
        if (edit != null)
            HandlerUpdate.stop(handler, edit!!)
        edit = null
        editPos = -1

        if (play != null)
            HandlerUpdate.stop(handler, play!!)
        play = null
        playPos = -1f

        if (draw == null) {
            time = System.currentTimeMillis()

            draw = HandlerUpdate.start(handler, Runnable { drawCalc() }, UPDATE_SPEED.toLong())
        }
    }

    // current paying pos in actual samples
    fun play(pos: Float) {
        if (pos < 0) {
            playPos = -1f
            if (play != null) {
                HandlerUpdate.stop(handler, play!!)
                play = null
            }
            if (edit == null) {
                edit()
            }
            return
        }

        playPos = pos - samples

        editFlash = true

        val max = data.size - 1

        if (playPos > max)
            playPos = max.toFloat()

        if (edit != null)
            HandlerUpdate.stop(handler, edit!!)
        edit = null

        if (draw != null)
            HandlerUpdate.stop(handler, draw!!)
        draw = null

        if (play == null) {
            time = System.currentTimeMillis()
            play = HandlerUpdate.start(handler, Runnable { draw() }, UPDATE_SPEED.toLong())
        }
    }

    companion object {
        val TAG = PitchView::class.java.simpleName

        // pitch delimiter length in dp
        val PITCH_DELIMITER = 1f
        // pitch length in dp
        val PITCH_WIDTH = 2f

        // update pitchview in milliseconds
        val UPDATE_SPEED = 10

        // edit update time
        val EDIT_UPDATE_SPEED = 250
    }
}