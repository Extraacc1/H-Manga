package nhdphuong.com.manga.features.preview

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.annotation.RequiresApi
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_book_preview.clArtists
import kotlinx.android.synthetic.main.fragment_book_preview.clCategories
import kotlinx.android.synthetic.main.fragment_book_preview.clCharacters
import kotlinx.android.synthetic.main.fragment_book_preview.clDownloadProgress
import kotlinx.android.synthetic.main.fragment_book_preview.clGroups
import kotlinx.android.synthetic.main.fragment_book_preview.clLanguages
import kotlinx.android.synthetic.main.fragment_book_preview.clParodies
import kotlinx.android.synthetic.main.fragment_book_preview.clTags
import kotlinx.android.synthetic.main.fragment_book_preview.hsvPreviewThumbNail
import kotlinx.android.synthetic.main.fragment_book_preview.hsvRecommendList
import kotlinx.android.synthetic.main.fragment_book_preview.ivBookCover
import kotlinx.android.synthetic.main.fragment_book_preview.mtvDownload
import kotlinx.android.synthetic.main.fragment_book_preview.mtvDownloaded
import kotlinx.android.synthetic.main.fragment_book_preview.mtvFavorite
import kotlinx.android.synthetic.main.fragment_book_preview.mtvNotFavorite
import kotlinx.android.synthetic.main.fragment_book_preview.mtvRecommendBook
import kotlinx.android.synthetic.main.fragment_book_preview.pbDownloading
import kotlinx.android.synthetic.main.fragment_book_preview.rvPreviewList
import kotlinx.android.synthetic.main.fragment_book_preview.rvRecommendList
import kotlinx.android.synthetic.main.fragment_book_preview.svBookCover
import kotlinx.android.synthetic.main.fragment_book_preview.svPreview
import kotlinx.android.synthetic.main.fragment_book_preview.tvArtistsLabel
import kotlinx.android.synthetic.main.fragment_book_preview.tvCategoriesLabel
import kotlinx.android.synthetic.main.fragment_book_preview.tvCharactersLabel
import kotlinx.android.synthetic.main.fragment_book_preview.tvGroupsLabel
import kotlinx.android.synthetic.main.fragment_book_preview.tvLanguagesLabel
import kotlinx.android.synthetic.main.fragment_book_preview.tvPageCount
import kotlinx.android.synthetic.main.fragment_book_preview.tvParodiesLabel
import kotlinx.android.synthetic.main.fragment_book_preview.tvTagsLabel
import kotlinx.android.synthetic.main.fragment_book_preview.tvTitle_1
import kotlinx.android.synthetic.main.fragment_book_preview.tvTitle_2
import kotlinx.android.synthetic.main.fragment_book_preview.tvUpdatedAt
import nhdphuong.com.manga.Constants
import nhdphuong.com.manga.Logger
import nhdphuong.com.manga.NHentaiApp
import nhdphuong.com.manga.R
import nhdphuong.com.manga.data.entity.book.Book
import nhdphuong.com.manga.data.entity.book.tags.Tag
import nhdphuong.com.manga.features.reader.ReaderActivity
import nhdphuong.com.manga.supports.ImageUtils
import nhdphuong.com.manga.views.DialogHelper
import nhdphuong.com.manga.views.InformationCardAdapter
import nhdphuong.com.manga.views.MyGridLayoutManager
import nhdphuong.com.manga.views.adapters.BookAdapter
import nhdphuong.com.manga.views.adapters.PreviewAdapter

/*
 * Created by nhdphuong on 4/14/18.
 */
class BookPreviewFragment :
        Fragment(),
        BookPreviewContract.View,
        InformationCardAdapter.TagSelectedListener {
    companion object {
        private const val TAG = "BookPreviewFragment"
        private const val NUM_OF_ROWS = 2
        private const val REQUEST_STORAGE_PERMISSION = 3142
    }

    private lateinit var mPresenter: BookPreviewContract.Presenter
    private lateinit var mPreviewAdapter: PreviewAdapter
    private lateinit var mRecommendBookAdapter: BookAdapter
    private lateinit var mAnimatorSet: AnimatorSet
    private var isDownloadRequested = false

    @Volatile
    private var isPresenterStarted: Boolean = false

    private lateinit var mPreviewLayoutManager: MyGridLayoutManager

    override fun setPresenter(presenter: BookPreviewContract.Presenter) {
        mPresenter = presenter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.d(TAG, "onCreate")
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        Logger.d(TAG, "onCreateView")
        return inflater.inflate(R.layout.fragment_book_preview, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Logger.d(TAG, "onViewCreated")
        super.onViewCreated(view, savedInstanceState)
        svBookCover.let { svBookCover ->
            val scrollDownAnimator =
                    ObjectAnimator.ofInt(svBookCover, "scrollY", 1000)
            scrollDownAnimator.startDelay = 100
            scrollDownAnimator.duration = 6500
            val scrollUpAnimator =
                    ObjectAnimator.ofInt(svBookCover, "scrollY", -1000)
            scrollUpAnimator.startDelay = 100
            scrollUpAnimator.duration = 6500
            scrollDownAnimator.addListener(getAnimationListener(scrollUpAnimator))
            scrollUpAnimator.addListener(getAnimationListener(scrollDownAnimator))

            mAnimatorSet = AnimatorSet()
            mAnimatorSet.playTogether(scrollDownAnimator)
            svBookCover.setOnTouchListener { _, _ ->
                true
            }
        }

        mtvDownload.setOnClickListener {
            isDownloadRequested = true
            mPresenter.downloadBook()
        }

        val changeFavoriteListener = View.OnClickListener { mPresenter.changeBookFavorite() }
        mtvFavorite.setOnClickListener(changeFavoriteListener)
        mtvNotFavorite.setOnClickListener(changeFavoriteListener)

        // Gingerbread
        hsvPreviewThumbNail.overScrollMode = View.OVER_SCROLL_NEVER
        hsvRecommendList.overScrollMode = View.OVER_SCROLL_NEVER
        svPreview.overScrollMode = View.OVER_SCROLL_NEVER
        svBookCover.overScrollMode = View.OVER_SCROLL_NEVER

        view.viewTreeObserver.addOnGlobalLayoutListener {
            if (!isPresenterStarted) {
                isPresenterStarted = true
                mPresenter.loadInfoLists()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Logger.d(TAG, "onActivityCreated")
        super.onActivityCreated(savedInstanceState)
        mPresenter.start()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            val permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                showRequestStoragePermission()
            } else {
                if (isDownloadRequested) {
                    mPresenter.downloadBook()
                }
            }
            val result = if (permissionGranted) "granted" else "denied"
            Logger.d(TAG, "Storage permission is $result")
        }
    }

    override fun onStop() {
        super.onStop()
        mPresenter.stop()
        isPresenterStarted = false
    }

    override fun showBookCoverImage(coverUrl: String) {
        if (!NHentaiApp.instance.isCensored) {
            ImageUtils.loadImage(
                    coverUrl,
                    R.drawable.ic_404_not_found,
                    ivBookCover,
                    onLoadSuccess = {
                        mPresenter.saveCurrentAvailableCoverUrl(coverUrl)
                        mAnimatorSet.start()
                    },
                    onLoadFailed = {
                        mPresenter.reloadCoverImage()
                    })
        } else {
            ivBookCover.setImageResource(R.drawable.ic_nothing_here_grey)
            mPresenter.saveCurrentAvailableCoverUrl(coverUrl)
            mAnimatorSet.start()
        }
    }

    override fun show1stTitle(firstTitle: String) {
        if (!TextUtils.isEmpty(firstTitle)) {
            tvTitle_1.visibility = View.VISIBLE
            tvTitle_1.text = firstTitle
        } else {
            tvTitle_1.visibility = View.GONE
        }
    }

    override fun show2ndTitle(secondTitle: String) {
        if (!TextUtils.isEmpty(secondTitle)) {
            tvTitle_2.visibility = View.VISIBLE
            tvTitle_2.text = secondTitle
        } else {
            tvTitle_2.visibility = View.GONE
        }
    }

    override fun showTagList(tagList: List<Tag>) {
        tvTagsLabel.visibility = View.VISIBLE
        clTags.visibility = View.VISIBLE
        loadInfoList(clTags, tagList)
    }

    override fun showArtistList(artistList: List<Tag>) {
        tvArtistsLabel.visibility = View.VISIBLE
        clArtists.visibility = View.VISIBLE
        loadInfoList(clArtists, artistList)
    }

    override fun showLanguageList(languageList: List<Tag>) {
        tvLanguagesLabel.visibility = View.VISIBLE
        clLanguages.visibility = View.VISIBLE
        loadInfoList(clLanguages, languageList)
    }

    override fun showCategoryList(categoryList: List<Tag>) {
        tvCategoriesLabel.visibility = View.VISIBLE
        clCategories.visibility = View.VISIBLE
        loadInfoList(clCategories, categoryList)
    }

    override fun showCharacterList(characterList: List<Tag>) {
        tvCharactersLabel.visibility = View.VISIBLE
        clCharacters.visibility = View.VISIBLE
        loadInfoList(clCharacters, characterList)
    }

    override fun showGroupList(groupList: List<Tag>) {
        tvGroupsLabel.visibility = View.VISIBLE
        clGroups.visibility = View.VISIBLE
        loadInfoList(clGroups, groupList)
    }

    override fun showParodyList(parodyList: List<Tag>) {
        tvParodiesLabel.visibility = View.VISIBLE
        clParodies.visibility = View.VISIBLE
        loadInfoList(clParodies, parodyList)
    }

    override fun hideTagList() {
        tvTagsLabel.visibility = View.GONE
        clTags.visibility = View.GONE
    }

    override fun hideArtistList() {
        tvArtistsLabel.visibility = View.GONE
        clArtists.visibility = View.GONE
    }

    override fun hideLanguageList() {
        tvLanguagesLabel.visibility = View.GONE
        clLanguages.visibility = View.GONE
    }

    override fun hideCategoryList() {
        tvCategoriesLabel.visibility = View.GONE
        clCategories.visibility = View.GONE
    }

    override fun hideCharacterList() {
        tvCharactersLabel.visibility = View.GONE
        clCharacters.visibility = View.GONE
    }

    override fun hideGroupList() {
        tvGroupsLabel.visibility = View.GONE
        clGroups.visibility = View.GONE
    }

    override fun hideParodyList() {
        tvParodiesLabel.visibility = View.GONE
        clParodies.visibility = View.GONE
    }

    override fun showPageCount(pageCount: Int) {
        tvPageCount.text = getString(R.string.page_count, pageCount.toString())
    }

    override fun showUploadedTime(uploadedTime: String) {
        tvUpdatedAt.text = getString(R.string.uploaded, uploadedTime)
    }

    override fun showBookThumbnailList(thumbnailList: List<String>) {
        var spanCount = thumbnailList.size / NUM_OF_ROWS
        if (thumbnailList.size % NUM_OF_ROWS != 0) {
            spanCount++
        }

        Logger.d(TAG, "thumbnails: ${thumbnailList.size}," +
                " number of rows: $NUM_OF_ROWS, spanCount: $spanCount")
        mPreviewLayoutManager = object : MyGridLayoutManager(context!!, spanCount) {
            override fun isAutoMeasureEnabled(): Boolean {
                return true
            }
        }
        rvPreviewList.run {
            layoutManager = mPreviewLayoutManager
            mPreviewAdapter = PreviewAdapter(
                    NUM_OF_ROWS,
                    thumbnailList,
                    object : PreviewAdapter.ThumbnailClickCallback {
                        override fun onThumbnailClicked(page: Int) {
                            mPresenter.startReadingFrom(page)
                        }
                    })
            adapter = mPreviewAdapter
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hsvPreviewThumbNail.setOnScrollChangeListener { _, _, _, _, _ ->
                if (!hsvPreviewThumbNail.canScrollHorizontally(1)) {
                    Logger.d(TAG, "End of list, load more thumbnails")
                    mPresenter.loadMoreThumbnails()
                }
            }
        }
    }

    override fun updateBookThumbnailList() {
        var spanCount = mPreviewAdapter.itemCount / NUM_OF_ROWS
        if (mPreviewAdapter.itemCount % NUM_OF_ROWS != 0) {
            spanCount++
        }
        mPreviewLayoutManager.spanCount = spanCount
        mPreviewAdapter.notifyDataSetChanged()
    }

    override fun showRecommendBook(bookList: List<Book>) {
        Logger.d(TAG, "recommended books, spanCount: ${bookList.size}")
        mtvRecommendBook.visibility = View.VISIBLE
        val gridLayoutManager = object : MyGridLayoutManager(context!!, bookList.size) {
            override fun isAutoMeasureEnabled(): Boolean {
                return true
            }
        }

        rvRecommendList.layoutManager = gridLayoutManager
        mRecommendBookAdapter = BookAdapter(
                bookList,
                BookAdapter.RECOMMEND_BOOK,
                object : BookAdapter.OnBookClick {
                    override fun onItemClick(item: Book) {
                        BookPreviewActivity.restart(item)
                    }
                })
        rvRecommendList.adapter = mRecommendBookAdapter
    }

    override fun showNoRecommendBook() {
        mtvRecommendBook.visibility = View.GONE
    }

    override fun showRequestStoragePermission() {
        DialogHelper.showStoragePermissionDialog(activity!!, onOk = {
            requestStoragePermission()
        }, onDismiss = {
            Toast.makeText(
                    context,
                    getString(R.string.toast_storage_permission_require),
                    Toast.LENGTH_SHORT
            ).show()
            isDownloadRequested = false
        })
    }

    override fun initDownloading(total: Int) {
        clDownloadProgress.visibility = View.VISIBLE
        pbDownloading.max = total
        mtvDownloaded.text = String.format(getString(R.string.preview_download_progress), 0, total)
    }

    override fun updateDownloadProgress(progress: Int, total: Int) {
        clDownloadProgress.visibility = View.VISIBLE
        pbDownloading.max = total
        pbDownloading.progressDrawable = getProgressDrawableId(progress, total)
        pbDownloading.progress = progress
        mtvDownloaded.text = String.format(getString(R.string.preview_download_progress), progress, total)
    }

    override fun finishDownloading() {
        mtvDownloaded.text = getString(R.string.done)
        val handler = Handler()
        handler.postDelayed({
            pbDownloading.progressDrawable =
                    getProgressDrawableId(0, pbDownloading.max)
            pbDownloading.max = 0
            clDownloadProgress.visibility = View.GONE
            mtvDownloaded.text = getString(R.string.preview_download_progress)
        }, 2000)
    }

    override fun finishDownloading(downloadFailedCount: Int, total: Int) {
        mtvDownloaded.text =
                String.format(getString(R.string.fail_to_download), downloadFailedCount)
        val handler = Handler()
        handler.postDelayed({
            pbDownloading.progressDrawable =
                    getProgressDrawableId(0, pbDownloading.max)
            pbDownloading.max = 0
            clDownloadProgress.visibility = View.GONE
            mtvDownloaded.text = getString(R.string.preview_download_progress)
        }, 2000)
    }

    override fun showBookBeingDownloaded(bookId: String) {
        DialogHelper.showBookDownloadingDialog(activity!!, bookId, onOk = {
            mPresenter.restartBookPreview(bookId)
        }, onDismiss = {

        })
    }

    override fun showThisBookBeingDownloaded() {
        DialogHelper.showThisBookDownloadingDialog(activity!!, onOk = {

        })
    }

    override fun showFavoriteBookSaved(isFavorite: Boolean) {
        if (isFavorite) {
            mtvNotFavorite.visibility = View.INVISIBLE
            mtvFavorite.visibility = View.VISIBLE
        } else {
            mtvNotFavorite.visibility = View.VISIBLE
            mtvFavorite.visibility = View.INVISIBLE
        }
    }

    override fun showFavoriteBooks(favoriteList: List<Int>) {
        mRecommendBookAdapter.setFavoriteList(favoriteList)
    }

    override fun showRecentBooks(recentList: List<Int>) {
        mRecommendBookAdapter.setRecentList(recentList)
    }

    override fun showOpenFolderView() {
        activity?.run {
            DialogHelper.showDownloadingFinishedDialog(this, onOk = {
                val viewGalleryIntent = Intent(Intent.ACTION_VIEW)
                viewGalleryIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                viewGalleryIntent.type = "image/*"
                startActivity(Intent.createChooser(viewGalleryIntent, getString(R.string.open_with)))
            }, onDismiss = {

            })
        }
    }

    override fun startReadingFromPage(page: Int, book: Book) {
        context?.run {
            ReaderActivity.start(this, page, book)
        }
    }

    override fun showLoading() {

    }

    override fun hideLoading() {

    }

    override fun isActive() = isAdded

    override fun onTagSelected(tag: Tag) {
        activity?.run {
            val intent = intent
            intent.action = Constants.TAG_SELECTED_ACTION
            intent.putExtra(Constants.SELECTED_TAG, tag.name)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }
    }

    private fun loadInfoList(layout: ViewGroup, infoList: List<Tag>) {
        val infoCardLayout = InformationCardAdapter(infoList)
        infoCardLayout.loadInfoList(layout)
        infoCardLayout.setTagSelectedListener(this)
    }

    private fun getAnimationListener(
            callOnEndingObject: ObjectAnimator
    ): Animator.AnimatorListener {
        return object : Animator.AnimatorListener {
            override fun onAnimationEnd(p0: Animator?) {
                callOnEndingObject.start()
            }

            override fun onAnimationCancel(p0: Animator?) {

            }

            override fun onAnimationRepeat(p0: Animator?) {

            }

            override fun onAnimationStart(p0: Animator?) {

            }
        }
    }

    private fun requestStoragePermission() {
        val storagePermission = arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        requestPermissions(storagePermission, REQUEST_STORAGE_PERMISSION)
    }

    private fun getProgressDrawableId(progress: Int, max: Int): Drawable {
        val percentage = (progress * 1f) / (max * 1f)
        return ActivityCompat.getDrawable(context!!, when {
            percentage >= Constants.DOWNLOAD_GREEN_LEVEL -> R.drawable.bg_download_green
            percentage >= Constants.DOWNLOAD_YELLOW_LEVEL -> R.drawable.bg_download_yellow
            else -> R.drawable.bg_download_red
        })!!
    }
}
