package co.nayan.c3specialist_v2

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import co.nayan.c3specialist_v2.storage.SharedStorage
import co.nayan.c3v2.core.di.preference.PreferenceHelper
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class AccessTokenProvider : ContentProvider() {

    private val pathAuth = "auth_token"

    // MIME type for the access token
    private val contentType = ContentResolver.CURSOR_DIR_BASE_TYPE + "/$pathAuth"

    // Access token column name
    private val columnAccessToken = "access_token"
    private val columnClient = "client"
    private val columnExpiry = "expiry"
    private val columnUid = "uid"

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AccessTokenContentProviderEntryPoint {
        var mPreferenceHelper: PreferenceHelper
    }

    override fun onCreate(): Boolean {
        // Initialize your Content Provider here
        return true
    }

    override fun query(
        uri: Uri, projection: Array<String?>?, selection: String?,
        selectionArgs: Array<String?>?, sortOrder: String?
    ): Cursor? {
        if (context == null) return null

        val appContext = context?.applicationContext
        val accessTokenEntryPoint = EntryPointAccessors.fromApplication(
            appContext!!,
            AccessTokenContentProviderEntryPoint::class.java
        )
        val preferenceHelper = accessTokenEntryPoint.mPreferenceHelper
        return try {
            // Retrieve the access token from your backend
            preferenceHelper.getAuthenticationHeaders()?.let {
                val accessToken = it.access_token
                val client = it.client
                val expiry = it.expiry
                val uid = it.uid

                // Create a MatrixCursor with the access token
                val cursor =
                    MatrixCursor(arrayOf(columnAccessToken, columnClient, columnExpiry, columnUid))
                cursor.addRow(arrayOf(accessToken, client, expiry, uid))
                cursor
            } ?: run { null }
        } catch (e: Exception) {
            e.printStackTrace()
            Firebase.crashlytics.recordException(e)
            null
        }
    }

    override fun getType(uri: Uri): String {
        return contentType
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert operation not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String?>?): Int {
        throw UnsupportedOperationException("Delete operation not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String?>?
    ): Int {
        throw UnsupportedOperationException("Update operation not supported")
    }
}