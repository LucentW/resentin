package pm.antani.resentin.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PendingUpload(val bytes: ByteArray, val fileName: String, val mimeType: String)
/** Reads a picked/shared `content://` (or `file://`) [uri] fully into memory. Fine for
 * this app's purposes — the server's own per-file caps top out at 50MiB (video) — but a
 * genuinely large file would want streaming instead of `readBytes()`. Returns null if
 * the URI can't be opened at all (revoked grant, deleted file, ...). */
suspend fun readUploadFile(context: Context, uri: Uri): PendingUpload? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@withContext null
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    val fileName = queryDisplayName(context, uri) ?: "upload"
    PendingUpload(bytes, fileName, mimeType)
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    if (uri.scheme != "content") return uri.lastPathSegment
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }.getOrNull()
}

/** Metadata for the #1883 pre-upload confirm — name/size/type WITHOUT the byte
 * read, so a declined pick never pays it. The bytes are read only on confirm
 * (see [readUploadFile]). `sizeBytes` is -1 when the provider doesn't report
 * one. Returns null only if the URI can't be queried at all. */
data class PendingUploadMeta(val uri: Uri, val fileName: String, val sizeBytes: Long, val mimeType: String)

suspend fun readUploadMeta(context: Context, uri: Uri): PendingUploadMeta? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri) ?: "application/octet-stream"
    if (uri.scheme != "content") {
        return@withContext PendingUploadMeta(uri, uri.lastPathSegment ?: "upload", -1L, mimeType)
    }
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            PendingUploadMeta(
                uri = uri,
                fileName = if (nameIndex >= 0) cursor.getString(nameIndex) ?: "upload" else "upload",
                sizeBytes = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L,
                mimeType = mimeType,
            )
        }
    }.getOrNull()
}
