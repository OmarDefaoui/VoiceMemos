package com.nordef.voicememos.on_bording_screen

import android.app.NotificationManager
import android.content.Context
import android.content.Context.LAYOUT_INFLATER_SERVICE
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.NonNull
import androidx.viewpager.widget.PagerAdapter
import com.nordef.voicememos.R

class OnBoardingSliderAdapter(
    internal var context: Context, private val slideTitles: Array<String>,
    private val slideDescription: Array<String>,
    internal var params: RelativeLayout.LayoutParams
) : PagerAdapter() {

    private val slideImages = intArrayOf(
        R.drawable.obs_screen1,
        R.drawable.obs_screen2,
        R.drawable.obs_screen3,
        R.drawable.obs_screen4
    )

    override fun getCount(): Int {
        return slideTitles.size
    }

    override fun isViewFromObject(@NonNull view: View, @NonNull o: Any): Boolean {
        return view === o as RelativeLayout
    }

    @NonNull
    override fun instantiateItem(@NonNull container: ViewGroup, position: Int): Any {
        val inflater = context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(
            R.layout.on_boarding_slide_layout_container, container,
            false
        )

        //initialise views
        val iv_picture: ImageView = view.findViewById(R.id.iv_picture)
        val tv_title: TextView = view.findViewById(R.id.tv_title)
        val tv_description: TextView = view.findViewById(R.id.tv_description)
        val btn_submit: Button = view.findViewById(R.id.btn_submit)

        //set size to imageView
        iv_picture.layoutParams = params

        //set data to views
        iv_picture.setImageResource(slideImages[position])
        tv_title.text = slideTitles[position]
        tv_description.text = slideDescription[position]

        //set up btn
        if (position == 2) {
            //btn accept change silent mode
            btn_submit.visibility = View.VISIBLE
            btn_submit.text = context.getString(R.string.accept_permission)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notificationManager.isNotificationPolicyAccessGranted) {
                    btn_submit.text = context.getString(R.string.accepted_permission)
                    //btn_submit.isEnabled = false
                }
            } else {
                btn_submit.text = context.getString(R.string.accepted_permission)
                btn_submit.isEnabled = false
            }


            btn_submit.setOnClickListener {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                context.startActivity(intent)
            }

        } else if (position == 1) {

            //btn accept permissions
            btn_submit.visibility = View.VISIBLE
            btn_submit.text = context.getString(R.string.accept_permission)

            btn_submit.setOnClickListener {
                (context as OnBoardingScreenActivity).checkPermissions()
            }

        } else
            btn_submit.visibility = View.GONE

        //animate components
        val animation = AnimationUtils.loadAnimation(context, R.anim.anim_obs)
        val animationImage = AnimationUtils.loadAnimation(context, R.anim.anim_obs_image)

        tv_title.startAnimation(animation)
        tv_description.startAnimation(animation)
        btn_submit.startAnimation(animation)
        iv_picture.startAnimation(animationImage)

        container.addView(view)

        return view
    }

    override fun destroyItem(@NonNull container: ViewGroup, position: Int, @NonNull `object`: Any) {
        container.removeView(`object` as RelativeLayout)
    }
}
