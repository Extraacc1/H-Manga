package nhdphuong.com.manga.features.home

import `in`.srain.cube.views.ptr.PtrDefaultHandler
import `in`.srain.cube.views.ptr.PtrFrameLayout
import `in`.srain.cube.views.ptr.PtrHandler
import `in`.srain.cube.views.ptr.PtrUIHandler
import `in`.srain.cube.views.ptr.indicator.PtrIndicator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.StaggeredGridLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.android.synthetic.main.fragment_book_list.btnFirst
import kotlinx.android.synthetic.main.fragment_book_list.btnLast
import kotlinx.android.synthetic.main.fragment_book_list.clNavigation
import kotlinx.android.synthetic.main.fragment_book_list.clNothing
import kotlinx.android.synthetic.main.fragment_book_list.clReload
import kotlinx.android.synthetic.main.fragment_book_list.mtv_search_result
import kotlinx.android.synthetic.main.fragment_book_list.nsvMainList
import kotlinx.android.synthetic.main.fragment_book_list.refreshHeader
import kotlinx.android.synthetic.main.fragment_book_list.rvMainList
import kotlinx.android.synthetic.main.fragment_book_list.rvPagination
import kotlinx.android.synthetic.main.fragment_book_list.srlPullToReload
import kotlinx.android.synthetic.main.layout_refresh_header.view.ivRefresh
import kotlinx.android.synthetic.main.layout_refresh_header.view.mtvLastUpdate
import kotlinx.android.synthetic.main.layout_refresh_header.view.mtvRefresh
import kotlinx.android.synthetic.main.layout_refresh_header.view.pbRefresh
import nhdphuong.com.manga.Constants
import nhdphuong.com.manga.Logger
import nhdphuong.com.manga.NHentaiApp
import nhdphuong.com.manga.R
import nhdphuong.com.manga.views.adapters.BookAdapter
import nhdphuong.com.manga.data.entity.book.Book
import nhdphuong.com.manga.features.preview.BookPreviewActivity
import nhdphuong.com.manga.views.DialogHelper
import nhdphuong.com.manga.views.adapters.PaginationAdapter

/*
 * Created by nhdphuong on 3/16/18.
 */
class HomeFragment : Fragment(), HomeContract.View, PtrUIHandler {

    private lateinit var mHomeListAdapter: BookAdapter
    private lateinit var mHomePaginationAdapter: PaginationAdapter
    private lateinit var mHomePresenter: HomeContract.Presenter
    private lateinit var mLoadingDialog: Dialog

    private val mSearchResultTitle: String = NHentaiApp.instance.getString(R.string.search_result)

    private lateinit var mUpdateDotsHandler: Handler

    override fun setPresenter(presenter: HomeContract.Presenter) {
        mHomePresenter = presenter
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        Logger.d(TAG, "onCreateView")
        return inflater.inflate(R.layout.fragment_book_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Logger.d(TAG, "onViewCreated")

        nsvMainList.overScrollMode = View.OVER_SCROLL_NEVER
        btnFirst.setOnClickListener {
            mHomePresenter.jumToFirstPage()
            mHomePaginationAdapter.selectFirstPage()
            jumpTo(0)
        }
        btnLast.setOnClickListener {
            mHomePresenter.jumToLastPage()
            mHomePaginationAdapter.selectLastPage()
            jumpTo(mHomePaginationAdapter.itemCount - 1)
        }
        mLoadingDialog = DialogHelper.showLoadingDialog(activity!!)
        srlPullToReload.addPtrUIHandler(this)
        srlPullToReload.setPtrHandler(object : PtrHandler {
            override fun onRefreshBegin(frame: PtrFrameLayout?) {
                mHomePresenter.reloadCurrentPage {
                    frame?.postDelayed({
                        srlPullToReload.refreshComplete()
                    }, REFRESH_COMPLETE_DURATION)
                }
            }

            override fun checkCanDoRefresh(
                    frame: PtrFrameLayout?,
                    content: View?,
                    header: View?
            ): Boolean {
                return PtrDefaultHandler.checkContentCanBePulledDown(frame, content, header)
            }
        })
        clReload.setOnClickListener {
            mHomePresenter.reloadCurrentPage {

            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Logger.d(TAG, "onActivityCreated")
        mHomePresenter.start()
        toggleSearchResult("")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "onDestroy")
        mHomePresenter.stop()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        Logger.d(TAG, "onConfigurationChanged")
        val isLandscape = newConfig?.orientation == Configuration.ORIENTATION_LANDSCAPE
        val mainListLayoutManager = object : StaggeredGridLayoutManager(
                if (isLandscape) LANDSCAPE_GRID_COLUMNS else GRID_COLUMNS,
                VERTICAL
        ) {
            override fun isAutoMeasureEnabled(): Boolean {
                return true
            }
        }
        mainListLayoutManager.gapStrategy = StaggeredGridLayoutManager.HORIZONTAL
        rvMainList.layoutManager = mainListLayoutManager
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) {
            return
        }
        if (requestCode == Constants.BOOK_PREVIEW_RESULT &&
                data?.action == Constants.RECENT_DATA_UPDATED_ACTION) {
            mHomePresenter.reloadRecentBooks()
        }
    }

    @SuppressLint("PrivateResource")
    override fun setUpHomeBookList(homeBookList: List<Book>) {
        val homeFragment = this
        mHomeListAdapter = BookAdapter(
                homeBookList,
                BookAdapter.HOME_PREVIEW_BOOK,
                object : BookAdapter.OnBookClick {
                    override fun onItemClick(item: Book) {
                        BookPreviewActivity.start(homeFragment, item)
                    }
                })
        val mainList: RecyclerView = rvMainList
        val isLandscape = resources.getBoolean(R.bool.is_landscape)
        val mainListLayoutManager = object : StaggeredGridLayoutManager(
                if (isLandscape) LANDSCAPE_GRID_COLUMNS else GRID_COLUMNS,
                VERTICAL
        ) {
            override fun isAutoMeasureEnabled(): Boolean {
                return true
            }
        }
        mainListLayoutManager.gapStrategy = StaggeredGridLayoutManager.HORIZONTAL
        mainList.layoutManager = mainListLayoutManager
        mainList.adapter = mHomeListAdapter
    }

    override fun refreshHomeBookList() {
        mHomeListAdapter.notifyDataSetChanged()
        mHomePresenter.saveLastBookListRefreshTime()
        rvMainList.post {
            rvMainList.smoothScrollBy(0, 0)
        }
        mHomePresenter.reloadRecentBooks()
    }

    override fun refreshHomePagination(pageCount: Long) {
        val mainPagination = rvPagination
        if (pageCount == 0L) {
            btnFirst.visibility = View.GONE
            btnLast.visibility = View.GONE
            mainPagination.visibility = View.GONE
            return
        }
        mHomePaginationAdapter = PaginationAdapter(context!!, pageCount.toInt())
        mHomePaginationAdapter.onPageSelectCallback =
                object : PaginationAdapter.OnPageSelectCallback {
                    override fun onPageSelected(page: Int) {
                        Logger.d(TAG, "Page $page is selected")
                        mHomePresenter.jumpToPage(page.toLong())
                    }
                }
        mainPagination.visibility = View.VISIBLE
        mainPagination.layoutManager = LinearLayoutManager(
                activity,
                LinearLayoutManager.HORIZONTAL,
                false
        )
        mainPagination.adapter = mHomePaginationAdapter
        mainPagination.viewTreeObserver.addOnGlobalLayoutListener {
            if (mHomePaginationAdapter.maxVisible >= pageCount - 1) {
                btnFirst.visibility = View.GONE
                btnLast.visibility = View.GONE
            } else {
                btnFirst.visibility = View.VISIBLE
                btnLast.visibility = View.VISIBLE
            }
        }
    }

    override fun showLastBookListRefreshTime(lastRefreshTimeStamp: String) {
        val lastRefresh = String.format(getString(R.string.last_update), lastRefreshTimeStamp)
        refreshHeader.mtvLastUpdate.text = lastRefresh
    }

    override fun showNothingView(isEmpty: Boolean) {
        clNothing.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    override fun showRefreshingDialog() {
        DialogHelper.showBookListRefreshingDialog(activity!!) {

        }
    }

    override fun showFavoriteBooks(favoriteList: List<Int>) {
        mHomeListAdapter.setFavoriteList(favoriteList)
    }

    override fun showRecentBooks(recentList: List<Int>) {
        mHomeListAdapter.setRecentList(recentList)
    }

    override fun changeSearchResult(data: String) {
        toggleSearchResult(data)
    }

    override fun showRandomBook(randomBook: Book) {
        BookPreviewActivity.start(this, randomBook)
    }

    override fun startUpdateTagsService() {
        NHentaiApp.instance.startUpdateTagsService()
    }

    fun changeSearchInputted(data: String) {
        mHomePresenter.updateSearchData(data)
    }

    fun randomizeBook() {
        mHomePresenter.pickBookRandomly()
    }

    override fun showLoading() {
        if (isAdded) {
            mLoadingDialog.show()
            clNavigation.visibility = View.GONE
        }
    }

    override fun hideLoading() {
        if (isAdded) {
            mLoadingDialog.dismiss()
            clNavigation.visibility = View.VISIBLE
        }
    }

    override fun isActive(): Boolean = isAdded

    override fun onUIRefreshComplete(frame: PtrFrameLayout?) {
        Logger.d(TAG, "onUIRefreshComplete")
        endUpdateDotsTask()
        refreshHeader.mtvRefresh.text = getString(R.string.updated)
        mHomePresenter.saveLastBookListRefreshTime()
        mHomePresenter.reloadLastBookListRefreshTime()
        refreshHeader.ivRefresh.rotation = 0F
        refreshHeader.ivRefresh.visibility = View.VISIBLE
        refreshHeader.pbRefresh.visibility = View.GONE
        mUpdateDotsHandler.removeCallbacksAndMessages(null)
    }

    override fun onUIPositionChange(
            frame: PtrFrameLayout?,
            isUnderTouch: Boolean,
            status: Byte,
            ptrIndicator: PtrIndicator?
    ) {
        Logger.d(TAG, "onUIPositionChange isUnderTouch: $isUnderTouch, status: $status, " +
                "over keep header: ${ptrIndicator?.isOverOffsetToKeepHeaderWhileLoading}, " +
                "over refresh: ${ptrIndicator?.isOverOffsetToRefresh}")
        if (ptrIndicator?.isOverOffsetToKeepHeaderWhileLoading == true) {
            refreshHeader.mtvRefresh.text = getString(R.string.release_to_refresh)
            refreshHeader.ivRefresh.rotation = REFRESH_HEADER_ANGEL
        }
    }

    override fun onUIRefreshBegin(frame: PtrFrameLayout?) {
        Logger.d(TAG, "onUIRefreshBegin")
        refreshHeader.ivRefresh.visibility = View.GONE
        refreshHeader.pbRefresh.visibility = View.VISIBLE
        refreshHeader.mtvRefresh.text = String.format(getString(R.string.updating), "")
        runUpdateDotsTask()

    }

    override fun onUIRefreshPrepare(frame: PtrFrameLayout?) {
        Logger.d(TAG, "onUIRefreshPrepare")
        mHomePresenter.reloadLastBookListRefreshTime()
    }

    override fun onUIReset(frame: PtrFrameLayout?) {
        Logger.d(TAG, "onUIReset")
        refreshHeader.mtvRefresh.text = getString(R.string.pull_down)
    }

    private fun jumpTo(pageNumber: Int) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            rvPagination.scrollToPosition(pageNumber)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun runUpdateDotsTask() {
        mUpdateDotsHandler = Handler()
        var currentPos = 0
        val updateDotsTask = {
            val dotsArray = resources.getStringArray(R.array.dots)
            val loadingString = getString(R.string.updating)
            Logger.d(TAG, "Current pos: $currentPos")
            refreshHeader.mtvRefresh.text =
                    String.format(loadingString, dotsArray[currentPos])
            if (currentPos < dotsArray.size - 1) currentPos++ else currentPos = 0
        }
        val runnable = object : Runnable {
            override fun run() {
                updateDotsTask()
                mUpdateDotsHandler.postDelayed(this, DOTS_UPDATE_INTERVAL)
            }
        }
        runnable.run()
    }

    private fun endUpdateDotsTask() {
        if (this::mUpdateDotsHandler.isInitialized) {
            mUpdateDotsHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun toggleSearchResult(data: String) {
        mtv_search_result.run {
            text = String.format(mSearchResultTitle, data)
            visibility = if (data.isBlank()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        private const val TAG = "HomeFragment"
        private const val GRID_COLUMNS = 2
        private const val LANDSCAPE_GRID_COLUMNS = 3
        private const val DOTS_UPDATE_INTERVAL = 500L
        private const val REFRESH_COMPLETE_DURATION = 800L
        private const val REFRESH_HEADER_ANGEL = 180F
    }
}
