package nhdphuong.com.manga.data.repository

import nhdphuong.com.manga.data.BookDataSource
import nhdphuong.com.manga.data.entity.RecentBook
import nhdphuong.com.manga.data.entity.book.Book
import nhdphuong.com.manga.data.entity.book.RecommendBook
import nhdphuong.com.manga.scope.Remote
import nhdphuong.com.manga.data.entity.book.RemoteBook
import nhdphuong.com.manga.scope.Local
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

/*
 * Created by nhdphuong on 3/24/18.
 */
@Singleton
class BookRepository @Inject constructor(
        @Remote private val mBookRemoteDataSource: BookDataSource.Remote,
        @Local private val mBookLocalDataSource: BookDataSource.Local
) : BookDataSource.Remote, BookDataSource.Local {
    override suspend fun getBookByPage(page: Long): RemoteBook? {
        return mBookRemoteDataSource.getBookByPage(page)
    }

    override suspend fun getBookByPage(searchContent: String, page: Long): RemoteBook? {
        return mBookRemoteDataSource.getBookByPage(searchContent, page)
    }

    override suspend fun getRecommendBook(bookId: String): RecommendBook? {
        return mBookRemoteDataSource.getRecommendBook(bookId)
    }

    override suspend fun getBookDetails(bookId: String): Book? {
        return mBookRemoteDataSource.getBookDetails(bookId)
    }

    override suspend fun saveFavoriteBook(bookId: String, isFavorite: Boolean) {
        mBookLocalDataSource.saveFavoriteBook(bookId, isFavorite)
    }

    override suspend fun saveRecentBook(bookId: String) {
        mBookLocalDataSource.saveRecentBook(bookId)
    }

    override suspend fun getFavoriteBook(limit: Int, offset: Int): LinkedList<RecentBook> {
        return mBookLocalDataSource.getFavoriteBook(limit, offset)
    }

    override suspend fun getRecentBooks(limit: Int, offset: Int): LinkedList<RecentBook> {
        return mBookLocalDataSource.getRecentBooks(limit, offset)
    }

    override suspend fun isFavoriteBook(bookId: String): Boolean {
        return mBookLocalDataSource.isFavoriteBook(bookId)
    }

    override suspend fun isRecentBook(bookId: String): Boolean {
        return mBookLocalDataSource.isRecentBook(bookId)
    }

    override suspend fun getRecentCount(): Int = mBookLocalDataSource.getRecentCount()

    override suspend fun getFavoriteCount(): Int = mBookLocalDataSource.getFavoriteCount()
}
