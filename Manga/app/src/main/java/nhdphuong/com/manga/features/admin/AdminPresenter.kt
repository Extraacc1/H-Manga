package nhdphuong.com.manga.features.admin

import com.google.gson.JsonArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nhdphuong.com.manga.Constants
import nhdphuong.com.manga.Logger
import nhdphuong.com.manga.SharedPreferencesManager
import nhdphuong.com.manga.data.entity.book.RemoteBook
import nhdphuong.com.manga.data.entity.book.tags.Tag
import nhdphuong.com.manga.data.repository.BookRepository
import nhdphuong.com.manga.scope.corountine.IO
import nhdphuong.com.manga.supports.IFileUtils
import nhdphuong.com.manga.supports.SupportUtils
import java.util.TreeMap
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

class AdminPresenter @Inject constructor(
        private val mView: AdminContract.View,
        private val mBookRepository: BookRepository,
        private val fileUtils: IFileUtils,
        private val mSharedPreferencesManager: SharedPreferencesManager,
        @IO private val io: CoroutineScope
) : AdminContract.Presenter {
    companion object {
        private const val TAG = "AdminPresenter"
    }

    init {
        mView.setPresenter(this)
    }

    private val mArtists = TreeMap<Long, Tag>()
    private val mCharacters = TreeMap<Long, Tag>()
    private val mCategories = TreeMap<Long, Tag>()
    private val mLanguages = TreeMap<Long, Tag>()
    private val mParodies = TreeMap<Long, Tag>()
    private val mGroups = TreeMap<Long, Tag>()
    private val mTags = TreeMap<Long, Tag>()
    private val mUnknownTypes = TreeMap<Long, Tag>()

    private var mCurrentPage = 1L
    private var mNumberOfPage = -1L

    override fun start() {
        clearData()
        io.launch {
            mNumberOfPage = mBookRepository.getBookByPage(mCurrentPage)?.numOfPages ?: 0L
            Logger.d(TAG, "Number Of pages=$mNumberOfPage")
        }
    }

    override fun stop() {

    }

    override fun startDownloading() {
        if (fileUtils.isStoragePermissionAccepted()) {
            downloadPage(mCurrentPage)
        } else {
            mView.showRequestStoragePermission()
        }
    }

    override fun toggleCensored(censored: Boolean) {
        mSharedPreferencesManager.isCensored = censored
        mView.restartApp()
    }

    private fun downloadPage(page: Long) {
        if (mNumberOfPage < 0) {
            return
        }

        if (page <= mNumberOfPage) {
            io.launch {
                val remoteBook = mBookRepository.getBookByPage(page)
                if (remoteBook != null) {
                    handleResponse(remoteBook)
                } else {
                    handleError()
                }
            }
        } else {
            Logger.d(TAG, "Downloading completed")

            saveTagsFiles(mArtists, Constants.ARTIST)
            saveTagsFiles(mCharacters, Constants.CHARACTER)
            saveTagsFiles(mCategories, Constants.CATEGORY)
            saveTagsFiles(mLanguages, Constants.LANGUAGE)
            saveTagsFiles(mParodies, Constants.PARODY)
            saveTagsFiles(mGroups, Constants.GROUP)
            saveTagsFiles(mTags, Constants.TAG)
            saveTagsFiles(mUnknownTypes, "unknown")
            saveChangeSummaryData()
            saveChangeLogsFile()
        }
    }

    private fun handleError() {
        Logger.d(TAG, "Downloading tags failed")
        downloadPage(++mCurrentPage)
    }

    private fun handleResponse(remoteBook: RemoteBook) {
        Logger.d(TAG, "Downloading tags response: $remoteBook")
        if (mNumberOfPage <= 0) {
            mNumberOfPage = remoteBook.numOfPages
            mView.showNumberOfPages(mNumberOfPage)
            mSharedPreferencesManager.run {
                mView.updateDownloadingStatistics(
                        mCurrentPage,
                        lastArtistsCount,
                        lastCharactersCount,
                        lastCategoriesCount,
                        lastLanguagesCount,
                        lastParodiesCount,
                        lastGroupsCount,
                        lastTagsCount,
                        lastUnknownTypesCount
                )
            }
        } else {
            for (book in remoteBook.bookList) {
                for (tag in book.tags) {
                    addTag(tag)
                }
            }
            mView.updateDownloadingStatistics(
                    mCurrentPage,
                    mArtists.size,
                    mCharacters.size,
                    mCategories.size,
                    mLanguages.size,
                    mParodies.size,
                    mGroups.size,
                    mTags.size,
                    mUnknownTypes.size
            )
        }
        downloadPage(++mCurrentPage)
    }

    private fun addTag(tag: Tag) {
        when (tag.type.toLowerCase(Locale.US)) {
            Constants.ARTIST -> {
                Logger.d(TAG, "Artist - id: ${tag.tagId}," +
                        " name: ${tag.name}, type: ${tag.type}, url: ${tag.url}")
                mArtists[tag.tagId] = tag
            }
            Constants.CHARACTER -> {
                Logger.d(TAG, "Character - id: ${tag.tagId}," +
                        " name: ${tag.name}, type: ${tag.type}, url: ${tag.url}")
                mCharacters[tag.tagId] = tag
            }
            Constants.CATEGORY -> {
                Logger.d(TAG, "Category - id: ${tag.tagId}," +
                        " name: ${tag.name}, type: ${tag.type}, url: ${tag.url}")
                mCategories[tag.tagId] = tag
            }
            Constants.LANGUAGE -> {
                Logger.d(TAG, "Language - id: ${tag.tagId}," +
                        " name: ${tag.name}, type: ${tag.type}, url: ${tag.url}")
                mLanguages[tag.tagId] = tag
            }
            Constants.PARODY -> {
                Logger.d(TAG, "Parody - id: ${tag.tagId}," +
                        " name: ${tag.name}, type: ${tag.type}, url: ${tag.url}")
                mParodies[tag.tagId] = tag
            }
            Constants.GROUP -> {
                Logger.d(TAG, "Group - id: ${tag.tagId}," +
                        " name: ${tag.name}, type: ${tag.type}, url: ${tag.url}")
                mGroups[tag.tagId] = tag
            }
            Constants.TAG -> {
                Logger.d(TAG, "Tag - id: ${tag.tagId}," +
                        " name: ${tag.name}, type: ${tag.type}, url: ${tag.url}")
                mTags[tag.tagId] = tag
            }
            else -> {
                Logger.d(TAG, "Unknown tag - id: ${tag.tagId}," +
                        " name: ${tag.name}, type: ${tag.type}, url: ${tag.url}")
                mUnknownTypes[tag.tagId] = tag
            }
        }
    }

    private fun saveTagsFiles(tagsMap: Map<Long, Tag>, tagName: String) {
        io.launch {
            val jsonArray = JsonArray()
            for (entry in tagsMap.entries) {
                jsonArray.add(entry.value.jsonValue)
            }
            val saveResult = SupportUtils.saveStringFile(jsonArray.toString(), tagName, fileUtils.getTagDirectory())
            Logger.d(TAG, "$tagName list saving result=$saveResult")
        }
    }

    private fun saveChangeSummaryData() {
        io.launch {
            val saveResult = SupportUtils.saveStringFile(
                    System.currentTimeMillis().toString(),
                    "CurrentVersion",
                    fileUtils.getTagDirectory()
            )
            Logger.d(TAG, "Current id saving result=$saveResult")
        }
    }

    private fun saveChangeLogsFile() {
        io.launch {
            val stringBuffer = StringBuffer("")
            val calendar = Calendar.getInstance(Locale.US)
            val total = mArtists.size +
                    mCategories.size +
                    mCharacters.size +
                    mLanguages.size +
                    mParodies.size +
                    mGroups.size +
                    mTags.size +
                    mUnknownTypes.size

            stringBuffer.append("===========================NHentai Data===========================\n")
            stringBuffer.append("Created date: ${calendar.get(Calendar.DAY_OF_MONTH)}/${calendar.get(Calendar.MONTH) + 1}/${calendar.get(Calendar.YEAR)}\n")
            stringBuffer.append("Total: $total items\n")
            stringBuffer.append("Categories: ${mCategories.size} items, number of old items: ${mSharedPreferencesManager.lastCategoriesCount}\n")
            stringBuffer.append("Characters: ${mCharacters.size} items, number of old items: ${mSharedPreferencesManager.lastCharactersCount}\n")
            stringBuffer.append("Languages: ${mLanguages.size} items, number of old items: ${mSharedPreferencesManager.lastLanguagesCount}\n")
            stringBuffer.append("Parodies: ${mParodies.size} items, number of old items: ${mSharedPreferencesManager.lastParodiesCount}\n")
            stringBuffer.append("Artists: ${mArtists.size} items, number of old items: ${mSharedPreferencesManager.lastArtistsCount}\n")
            stringBuffer.append("Groups: ${mGroups.size} items, number of old items: ${mSharedPreferencesManager.lastGroupsCount}\n")
            stringBuffer.append("mTags: ${mTags.size} items, number of old items: ${mSharedPreferencesManager.lastTagsCount}\n")
            stringBuffer.append("UnknownTypes: ${mUnknownTypes.size} items, number of old items: ${mSharedPreferencesManager.lastUnknownTypesCount}\n")
            stringBuffer.append("==================================================================")
            val saveResult = SupportUtils.saveStringFile(
                    stringBuffer.toString(),
                    "ChangeLogs",
                    fileUtils.getTagDirectory()
            )
            Logger.d(TAG, "NHentai.txt list saving result=$saveResult")
            mSharedPreferencesManager.lastCategoriesCount = mCategories.size
            mSharedPreferencesManager.lastCharactersCount = mCharacters.size
            mSharedPreferencesManager.lastLanguagesCount = mLanguages.size
            mSharedPreferencesManager.lastParodiesCount = mParodies.size
            mSharedPreferencesManager.lastArtistsCount = mArtists.size
            mSharedPreferencesManager.lastGroupsCount = mGroups.size
            mSharedPreferencesManager.lastTagsCount = mTags.size
            mSharedPreferencesManager.lastUnknownTypesCount = mUnknownTypes.size
        }
    }

    private fun clearData() {
        mCurrentPage = 1
        mNumberOfPage = -1L
        mArtists.clear()
        mCharacters.clear()
        mCategories.clear()
        mLanguages.clear()
        mParodies.clear()
        mGroups.clear()
        mTags.clear()
        mUnknownTypes.clear()
    }
}
