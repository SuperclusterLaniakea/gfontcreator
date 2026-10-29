package com.gfontcreator.app.font

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import java.io.File

/**
 * 字体产物写入「公共存储空间」：/storage/emulated/0/Documents/GFontCreator/
 *
 * - API 29+（Q，scoped storage）：通过 MediaStore.Files + RELATIVE_PATH 写入，
 *   无需运行时权限，文件出现在文件管理器的 Documents/GFontCreator 目录；返回 content:// URI 字符串。
 * - API 26–28：若已授予 WRITE_EXTERNAL_STORAGE 则写公共目录（返回绝对文件路径）；
 *   否则回退到应用私有目录 files/fonts（返回绝对文件路径），保证生成不因缺权限而失败。
 *
 * 本对象同时封装展示（文件名 / 大小）与分享（content URI 直分享 / 文件路径经 FileProvider），
 * 让 UI 层无需区分两种产物位置。
 */
object PublicFontStore {

    const val DIR_NAME = "GFontCreator"

    private const val MIME_TTF = "font/ttf"
    private const val MIME_GFONT = "application/octet-stream"

    fun mimeFor(filename: String): String =
        if (filename.endsWith(".ttf")) MIME_TTF else MIME_GFONT

    /** 将字体字节写入公共存储。
     * @return 位置标识：API 29+ 为 content URI 字符串；26–28 为绝对文件路径。 */
    fun write(context: Context, filename: String, bytes: ByteArray): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, filename, bytes)
        } else {
            val dir = if (hasWritePermission(context)) publicDir() else privateDir(context)
            File(dir, filename).apply { writeBytes(bytes) }.absolutePath
        }
    }

    private fun hasWritePermission(context: Context): Boolean =
        context.checkSelfPermission("android.permission.WRITE_EXTERNAL_STORAGE") ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun publicDir(): File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        DIR_NAME,
    ).apply { mkdirs() }

    private fun privateDir(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(context: Context, filename: String, bytes: ByteArray): String {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/" + DIR_NAME,
            )
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(filename))
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: throw IllegalStateException("MediaStore insert failed for $filename")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IllegalStateException("openOutputStream failed for $uri")
        return uri.toString()
    }

    /** 判断产物位置是否为 content URI（API 29+ 产物） */
    fun isUri(location: String): Boolean = location.startsWith("content://")

    /** 展示用文件名 */
    fun displayName(context: Context, location: String): String {
        if (!isUri(location)) return File(location).name
        return runCatching {
            context.contentResolver.query(
                Uri.parse(location),
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null,
            )?.use { c ->
                val i = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i) else null
            }
        }.getOrNull() ?: File(location).name
    }

    /** 展示用文件大小（KB） */
    fun displaySizeKb(context: Context, location: String): Long {
        if (!isUri(location)) return File(location).length() / 1024
        return runCatching {
            context.contentResolver.query(
                Uri.parse(location),
                arrayOf(MediaStore.MediaColumns.SIZE), null, null, null,
            )?.use { c ->
                val i = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (i >= 0 && c.moveToFirst()) c.getLong(i) / 1024 else 0L
            }
        }.getOrNull() ?: 0L
    }

    /** 删除产物：content URI 走 MediaStore，文件路径走 File */
    fun delete(context: Context, location: String) {
        if (location.isBlank()) return
        runCatching {
            if (isUri(location)) context.contentResolver.delete(Uri.parse(location), null, null)
            else File(location).delete()
        }
    }

    /** 构造分享 Intent：content URI 直接分享；文件路径经 FileProvider */
    fun shareIntent(context: Context, location: String): Intent {
        val uri: Uri = if (isUri(location)) {
            Uri.parse(location)
        } else {
            FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", File(location),
            )
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(displayName(context, location))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
