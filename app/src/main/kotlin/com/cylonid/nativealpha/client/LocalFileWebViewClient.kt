package com.cylonid.nativealpha.client

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

class LocalFileWebViewClient(private val context: Context) : WebViewClient() {

    private val copiedFiles = mutableMapOf<String, File>()
    private var baseHtmlUri: Uri? = null

    // What the page is allowed to read. Established by the first main-frame document:
    // for file:// the directory that document lives in, for content:// its authority.
    private var allowedDir: File? = null
    private var allowedAuthority: String? = null

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val uri = request.url
        val scheme = uri.scheme

        if (scheme == "file") {
            // APK assets/resources (e.g. the built-in error page) are handled by WebView itself.
            val path = uri.path.orEmpty()
            if (path.startsWith("/android_asset/") || path.startsWith("/android_res/")) {
                return super.shouldInterceptRequest(view, request)
            }
        }

        if (scheme == "file" || scheme == "content") {
            // Answer explicitly: returning null would let WebView try to load the URL itself.
            if (!isAccessAllowed(uri, request.isForMainFrame)) {
                return forbiddenResponse()
            }
            return handleLocalFile(uri)
        }

        if (baseHtmlUri != null && (scheme == "http" || scheme == "https")) {
            val relativeResource = tryResolveRelativeResource(uri)
            if (relativeResource != null) {
                return relativeResource
            }
        }

        return super.shouldInterceptRequest(view, request)
    }

    private fun forbiddenResponse() = WebResourceResponse(
        "text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0))
    )

    private fun cacheRoot() = File(context.cacheDir, "local_files")

    private fun canonicalOrNull(path: String?): File? =
        try {
            path?.let { File(it).canonicalFile }
        } catch (e: Exception) {
            null
        }

    /** Both arguments must be canonical. */
    private fun isInside(file: File, dir: File): Boolean =
        file.path.startsWith(dir.path + File.separator)

    private fun isAccessAllowed(uri: Uri, isMainFrame: Boolean): Boolean {
        if (allowedDir == null && allowedAuthority == null) {
            if (!isMainFrame) return false
            if (uri.scheme == "file") {
                allowedDir = canonicalOrNull(uri.path)?.parentFile
            } else {
                allowedAuthority = uri.authority
            }
            return allowedDir != null || allowedAuthority != null
        }

        return when (uri.scheme) {
            "file" -> {
                val file = canonicalOrNull(uri.path) ?: return false
                val dir = allowedDir
                (dir != null && isInside(file, dir)) || isInside(file, cacheRoot().canonicalFile)
            }
            "content" -> allowedAuthority != null && uri.authority == allowedAuthority
            else -> false
        }
    }

    private fun handleLocalFile(uri: Uri): WebResourceResponse? {
        try {
            val inputStream = if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)
            } else {
                FileInputStream(canonicalOrNull(uri.path) ?: return null)
            }

            inputStream ?: return null

            val mimeType = getMimeType(uri)
            val file = copyToAppStorage(uri, inputStream)

            if (mimeType == "text/html") {
                baseHtmlUri = uri
            }

            val fileInputStream = FileInputStream(file)
            return WebResourceResponse(mimeType, "UTF-8", fileInputStream)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun tryResolveRelativeResource(requestUri: Uri): WebResourceResponse? {
        val baseUri = baseHtmlUri ?: return null
        val requestPath = requestUri.path ?: return null

        val resolvedFile = resolveRelativePath(baseUri, requestPath) ?: return null
        return try {
            val mimeType = getMimeType(Uri.fromFile(resolvedFile))
            val copiedFile = copyFileToAppStorage(resolvedFile)
            WebResourceResponse(mimeType, "UTF-8", FileInputStream(copiedFile))
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Resolves [relativePath] against the base HTML's directory, never leaving that directory. */
    private fun resolveRelativePath(baseUri: Uri, relativePath: String): File? {
        if (baseUri.scheme != "file") return null

        val baseDir = canonicalOrNull(baseUri.path)?.parentFile ?: return null
        val targetFile = canonicalOrNull(File(baseDir, relativePath.trimStart('/')).path)
            ?: return null

        return if (isInside(targetFile, baseDir) && targetFile.isFile) targetFile else null
    }

    private fun hashOf(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

    /** One cache folder per source, so identical file names from different places can't collide. */
    private fun cacheFileFor(source: String, name: String): File =
        File(File(cacheRoot(), hashOf(source)), name)

    private fun copyFileToAppStorage(file: File): File {
        val outputFile = cacheFileFor(file.canonicalPath, file.name)
        outputFile.parentFile?.mkdirs()

        // Re-copy when the source changed, so edited resources aren't served stale.
        if (!outputFile.exists() ||
            file.lastModified() > outputFile.lastModified() ||
            file.length() != outputFile.length()
        ) {
            file.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        return outputFile
    }

    private fun isCacheFresh(uri: Uri, cached: File): Boolean {
        // content:// sources can't be checked cheaply; keep the per-session copy.
        if (uri.scheme != "file") return true
        val source = uri.path?.let { File(it) } ?: return true
        return source.lastModified() <= cached.lastModified() && source.length() == cached.length()
    }

    private fun copyToAppStorage(uri: Uri, inputStream: InputStream): File {
        val cachedFile = copiedFiles[uri.toString()]
        if (cachedFile != null && cachedFile.exists() && isCacheFresh(uri, cachedFile)) {
            inputStream.close()
            return cachedFile
        }

        val outputFile = cacheFileFor(uri.toString(), getFileName(uri))
        outputFile.parentFile?.mkdirs()

        FileOutputStream(outputFile).use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }

        copiedFiles[uri.toString()] = outputFile
        return outputFile
    }

    private fun getFileName(uri: Uri): String {
        val path = uri.path ?: uri.lastPathSegment ?: "unknown"
        return path.substringAfterLast('/')
            .ifEmpty { "file_${System.currentTimeMillis()}" }
    }

    private fun getMimeType(uri: Uri): String {
        val path = uri.path ?: uri.toString()
        return when (path.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "eot" -> "application/vnd.ms-fontobject"
            "xml" -> "text/xml"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }

    fun clearCache() {
        copiedFiles.values.forEach { file ->
            file.delete()
        }
        copiedFiles.clear()
        baseHtmlUri = null

        cacheRoot().deleteRecursively()
    }
}
