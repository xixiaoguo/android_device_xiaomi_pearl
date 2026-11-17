/*
 * SPDX-FileCopyrightText: 2019 The Android Open Source Project
 * SPDX-FileCopyrightText: 2023-2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/* Provide preference summary for injected items. */
class SummaryProvider : ContentProvider() {

    override fun call(method: String, uri: String?, extras: Bundle?): Bundle =
        throw IllegalArgumentException("Unknown method: $method")

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = throw UnsupportedOperationException()

    override fun getType(uri: Uri): String? = throw UnsupportedOperationException()

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException()
}
