package com.nordef.voicememos.animations

import android.annotation.TargetApi
import android.os.Build
import android.os.Handler
import android.view.View
import android.view.animation.Transformation
import android.widget.ListView

import com.nordef.voicememos.R

class RecordingAnimation(internal var list: ListView, internal var convertView: View, expand: Boolean) : MarginAnimation(convertView.findViewById(R.id.ll_expand), expand) {

    internal var partial: Boolean = false
    internal var handler: Handler

    init {

        handler = Handler()
    }

    override fun init() {
        super.init()

        run {
            val paddedTop = list.listPaddingTop
            val paddedBottom = list.height - list.listPaddingTop - list.listPaddingBottom

            partial = false

            partial = partial or (convertView.top < paddedTop)
            partial = partial or (convertView.bottom > paddedBottom)
        }
    }

    override fun calc(i: Float, t: Transformation?) {
        super.calc(i, t)

        val ii = if (expand) i else 1 - i

        // ViewGroup will crash on null pointer without this post pone.
        // seems like some views are removed by RecyvingView when they
        // gone off screen.
        if (Build.VERSION.SDK_INT >= 19) {
            if (!expand && atomicExpander != null && !atomicExpander!!.hasEnded()) {
                // do not showChild;
            } else {
                handler.post { showChild(i) }
            }
        }
    }

    @TargetApi(19)
    internal fun showChild(i: Float) {
        val paddedTop = list.listPaddingTop
        val paddedBottom = list.height - list.listPaddingTop - list.listPaddingBottom

        if (convertView.top < paddedTop) {
            var off = convertView.top - paddedTop
            if (partial)
                off = (off * i).toInt()
            list.scrollListBy(off)
        }

        if (convertView.bottom > paddedBottom) {
            var off = convertView.bottom - paddedBottom
            if (partial)
                off = (off * i).toInt()
            list.scrollListBy(off)
        }
    }

    override fun restore() {
        super.restore()
    }

    override fun end() {
        super.end()
    }

    companion object {

        // if we have two concurrent animations on the same listview
        // the only one 'expand' should have control of showChild function.
        internal var atomicExpander: RecordingAnimation? = null

        fun apply(list: ListView, v: View, expand: Boolean, animate: Boolean) {
            StepAnimation.apply(object : StepAnimation.LateCreator {
                override fun create(): MarginAnimation {
                    val a = RecordingAnimation(list, v, expand)
                    if (expand)
                        atomicExpander = a
                    return a
                }
            }, v, expand, animate)
        }
    }
}
