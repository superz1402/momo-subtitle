package com.momosubtitle.core.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** Storage Access Framework helpers: metadata queries and persistable permissions. */
object SafUtils {

    /** Returns (displayName, sizeBytes) or null when the provider does not expose them. */
    fun queryMeta(context: Context, uri: Uri): Pair<String, Long>? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else "media"
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else -1L
                    name to size
                } else null
            }
        }.getOrNull()
    }

    /** Keeps read access across process restarts so checkpointed runs can resume. */
    fun takePersistableRead(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }
}
