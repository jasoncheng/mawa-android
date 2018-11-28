package cool.mixi.dica.fragment

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cool.mixi.dica.R
import cool.mixi.dica.activity.MainActivity
import cool.mixi.dica.adapter.StatusesAdapter
import cool.mixi.dica.bean.Status
import cool.mixi.dica.util.*
import kotlinx.android.synthetic.main.fg_timeline.*
import retrofit2.Call

abstract class TimelineFragment: Fragment(), IStatusDataSource {

    var stl: StatusTimeline? = null
    private var isInitLoad = false
    private var ifVisible = false

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        ifVisible = isVisibleToUser
        ifVisible.let {
            if(ifVisible && !isInitLoad) {
                stl?.loadNewest(this)
                isInitLoad = true
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        isInitLoad = false
//        PrefUtil.setTimelineSinceId(this.javaClass.simpleName, 0)
        return inflater.inflate(R.layout.fg_timeline, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        stl = StatusTimeline(context!!, statuses_list, srl, this).init()
        (stl!!.table.adapter as StatusesAdapter).isFavoritesFragment =
                this.javaClass.simpleName == TimelineFavoritesFragment::class.java.simpleName

        srl.setOnRefreshListener {
            reloadNotification()
            stl?.resetQuery()
            stl?.loadNewest(this)
        }

        if(ifVisible && !isInitLoad){
            stl?.loadNewest(this)
            isInitLoad = true
        }
    }

    fun reloadNotification(){
        (activity as MainActivity).getNotifications()
    }

    private fun saveSinceId(){
        stl?.sinceId?.let {
            PrefUtil.setTimelineSinceId(this.javaClass.simpleName, it)
        }
    }
    override fun loaded(data: List<Status>) {
        stl?.clear()
        stl?.addAll(data)
        try {
            statuses_list.adapter.notifyItemRangeChanged(0, data.size)
            (statuses_list.adapter as StatusesAdapter).initLoaded = true
           // SinceId & find position and scroll to
            val lastSinceId = PrefUtil.getTimelineSinceId(this.javaClass.simpleName)
            dLog("lastSinceId $lastSinceId, ${stl?.sinceId} ${stl?.maxId}- ${this.javaClass.simpleName}")
            val currentSinceId = stl?.sinceId ?: 0
            if(currentSinceId == 0 ||  currentSinceId <= lastSinceId){
                return
            }

            var pos = data.size - 1
            var ifItemFound = false
            data.forEachIndexed { index, status ->
                if(status.id == lastSinceId){
                    pos = index
                    ifItemFound = true
                    dLog("item found at $pos")
                }
            }

            saveSinceId()

            // show how many new messages
            if(pos > 0){
                val msg = getString(R.string.new_status_since).format("$pos")
                (activity as MainActivity).showSnackBar(msg)
            }

            // When status delete, sinceId will lose it's meaning, should ignore
            if(!ifItemFound){ return }

            if(pos > 0){
                dLog("=========> last sinceId is $lastSinceId, scroll to position $pos")
                (statuses_list.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
            }
        }catch(e: Exception){
            eLog("${e.message}")
        }
    }

    abstract override fun sourceOld(): Call<List<Status>>?
    abstract override fun sourceNew(): Call<List<Status>>?
}