package com.nordef.voicememos.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import com.nordef.voicememos.R
import com.nordef.voicememos.activity.SettingsActivity
import com.nordef.voicememos.app.Storage
import com.nordef.voicememos.classes.FirstLaunch
import com.nordef.voicememos.classes.Recordings
import com.nordef.voicememos.on_bording_screen.OnBoardingScreenActivity


class FragmentDisplayRecords : Fragment(), AbsListView.OnScrollListener,
        SearchView.OnQueryTextListener {

    internal lateinit var view: View
    internal lateinit var context: Context

    companion object {
        val TAG = FragmentDisplayRecords::class.java.simpleName

        val TYPE_COLLAPSED = 0
        val TYPE_EXPANDED = 1
        val TYPE_DELETED = 2

        var scrollState: Int = 0

        lateinit var storage: Storage
        lateinit var handler: Handler
        lateinit var recordings: Recordings
        lateinit var list: ListView

        // load recordings
        fun load() {
            recordings.scan(storage.storagePath)
        }
    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        view = inflater.inflate(R.layout.fragment_display_records, container, false)
        context = inflater.context
        FirstLaunch(context).checkFirstLaunch()

        initRecordings()

        return view
    }

    fun initRecordings() {
        storage = Storage(context)
        handler = Handler()

        recordings = Recordings(context)

        list = view.findViewById(R.id.list_view)
        list.setOnScrollListener(this)
        initHeader()
        list.adapter = recordings

        storage.migrateLocalStorage()
        load()
    }

    fun initHeader() {
        val myHeader = layoutInflater.inflate(
                R.layout.header_main_activity, list,
                false
        ) as ViewGroup

        val iv_menu_popup: ImageView = myHeader.findViewById(R.id.iv_menu_popup)
        iv_menu_popup.setOnClickListener {
            showPopUpMenu(iv_menu_popup)
        }

        val search_view: SearchView = myHeader.findViewById(R.id.search_view)
        search_view.setOnQueryTextListener(this)

        val tv_share_app: TextView = myHeader.findViewById(R.id.tv_share_app)
        tv_share_app.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            val shareSubText = "${getString(R.string.app_name)} - ${getString(R.string.share_sub_text)}"
            val shareBody = "${getString(R.string.share_body)}\n"
            val shareBodyText = "https://play.google.com/store/apps/details?id=${context.packageName}"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, shareSubText)
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody + shareBodyText)
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_with)))
        }

        list.addHeaderView(myHeader, null, false)
    }

    private fun showPopUpMenu(iv_menu_popup: ImageView) {
        val popup = PopupMenu(context, iv_menu_popup)
        popup.menuInflater.inflate(R.menu.menu_popup, popup.menu)

        popup.setOnMenuItemClickListener(object : PopupMenu.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem?): Boolean {

                when (item!!.itemId) {
                    R.id.menu_settings -> {
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    }
                    R.id.menu_welcome_page -> {
                        val intent = Intent(context, OnBoardingScreenActivity::class.java)
                        intent.putExtra("from_fragment", true)
                        context.startActivity(intent)
                    }
                }
                return true
            }
        })

        popup.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        recordings.close()
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        recordings.filter(query)
        hideKeyboardFrom()
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }

    fun hideKeyboardFrom() {
        val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {

    }

    override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
        FragmentDisplayRecords.scrollState = scrollState
    }
}