package com.cylonid.nativealpha.client

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class LocalFileWebViewClient(private val context: Context) : WebViewClient() {

    private val copiedFiles = mutableMapOf<String, File>()
    private var baseHtmlUri: Uri? = null
    private val resourceMapping = mutableMapOf<String, File>()

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val uri = request.url
        val scheme = uri.scheme

        if (scheme == "file" || scheme == "content") {
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

    private fun handleLocalFile(uri: Uri): WebResourceResponse? {
        try {
            val inputStream = if (uri.scheme == "content") {
                context.contentResolver.openInputStream(uri)
            } else {
                FileInputStream(File(uri.path ?: return null))
            }

            inputStream ?: return null

            val mimeType = getMimeType(uri)
            val file = copyToAppStorage(uri, inputStream)

            if (mimeType == "text/html") {
                baseHtmlUri = uri
                scanAndCopyResources(file, uri)
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
        
        val resourcePath = requestUri.path?.substringAfterLast('/') ?: return null
        val cachedFile = resourceMapping[resourcePath]
        
        if (cachedFile != null && cachedFile.exists()) {
            return try {
                val mimeType = getMimeType(Uri.fromFile(cachedFile))
                WebResourceResponse(mimeType, "UTF-8", FileInputStream(cachedFile))
            } catch (e: Exception) {
                null
            }
        }

        val resolvedFile = resolveRelativePath(baseUri, requestUri.path ?: return null)
        if (resolvedFile != null && resolvedFile.exists()) {
            try {
                val mimeType = getMimeType(Uri.fromFile(resolvedFile))
                val copiedFile = copyFileToAppStorage(resolvedFile)
                resourceMapping[resourcePath] = copiedFile
                return WebResourceResponse(mimeType, "UTF-8", FileInputStream(copiedFile))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    private fun resolveRelativePath(baseUri: Uri, relativePath: String): File? {
        if (baseUri.scheme != "file") return null
        
        val basePath = baseUri.path ?: return null
        val baseFile = File(basePath)
        val baseDir = baseFile.parentFile ?: return null
        
        val cleanPath = relativePath.trimStart('/')
        val targetFile = File(baseDir, cleanPath)
        
        return if (targetFile.exists()) targetFile else null
    }

    private fun scanAndCopyResources(htmlFile: File, baseUri: Uri) {
        try {
            val content = htmlFile.readText()
            val baseDir = if (baseUri.scheme == "file") {
                File(baseUri.path ?: return).parentFile ?: return
            } else {
                return
            }

            val patterns = listOf(
                """src=["']([^"']+)["']""".toRegex(),
                """href=["']([^"']+)["']""".toRegex(),
                """url\(["']?([^"')]+)["']?\)""".toRegex()
            )

            patterns.forEach { pattern ->
                pattern.findAll(content).forEach { match ->
                    val resourcePath = match.groupValues[1]
                    if (!resourcePath.startsWith("http://") && 
                        !resourcePath.startsWith("https://") &&
                        !resourcePath.startsWith("data:")) {
                        
                        val resourceFile = File(baseDir, resourcePath)
                        if (resourceFile.exists()) {
                            val fileName = resourceFile.name
                            val copiedFile = copyFileToAppStorage(resourceFile)
                            resourceMapping[fileName] = copiedFile
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun copyFileToAppStorage(file: File): File {
        val outputFile = File(context.cacheDir, "local_files/${file.name}")
        outputFile.parentFile?.mkdirs()
        
        if (!outputFile.exists()) {
            file.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        
        return outputFile
    }

    private fun copyToAppStorage(uri: Uri, inputStream: InputStream): File {
        val cachedFile = copiedFiles[uri.toString()]
        if (cachedFile != null && cachedFile.exists()) {
            inputStream.close()
            return cachedFile
        }

        val fileName = getFileName(uri)
        val outputFile = File(context.cacheDir, "local_files/$fileName")
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
        resourceMapping.clear()
        baseHtmlUri = null

        val localFilesDir = File(context.cacheDir, "local_files")
        localFilesDir.deleteRecursively()
    }
}
