package com.mehuljain.airshare.printer

import com.mehuljain.airshare.http.HttpHandler
import com.mehuljain.airshare.http.HttpRequest
import com.mehuljain.airshare.http.HttpResponse
import com.mehuljain.airshare.http.WebPortalHtml
import com.mehuljain.airshare.ipp.IppCodec
import com.mehuljain.airshare.ipp.IppMessage
import java.io.InputStream
import java.util.logging.Level
import java.util.logging.Logger

/**
 * HTTP adapter for [IppPrinterHandler] and local Web Share / Video Transfer portal.
 */
class IppHttpHandler(
    private val handler: IppPrinterHandler,
    private val config: () -> PrinterConfig,
    private val jobs: JobStore,
    private val iconPng: () -> ByteArray?,
) : HttpHandler {
    override fun handle(request: HttpRequest): HttpResponse {
        val path = request.path.substringBefore('?')
        val method = request.method.uppercase()

        if (method == "OPTIONS") {
            return HttpResponse(200, ByteArray(0), "text/plain", CORS_HEADERS)
        }

        return when (method) {
            "POST" -> when (path) {
                "/upload" -> handleUpload(request)
                "/api/verify" -> handleVerifyPin(request)
                else -> handlePost(request)
            }
            "GET" -> when (path) {
                "/" -> {
                    val host = request.host ?: "localhost:${config().port}"
                    HttpResponse.text(200, WebPortalHtml.render(config(), jobs.list(), host), "text/html; charset=utf-8")
                }
                "/icon.png" -> iconPng()?.let { HttpResponse(200, it, "image/png") }
                    ?: HttpResponse.text(404, "Not found")
                "/api/info" -> {
                    val cfg = config()
                    HttpResponse.text(200, """{"name":"${esc(cfg.name)}","pinRequired":true,"version":"0.5.0"}""", "application/json; charset=utf-8", CORS_HEADERS)
                }
                "/airshare.shortcut" -> handleShortcut(request)
                else -> HttpResponse.text(404, "Not found", headers = CORS_HEADERS)
            }
            else -> HttpResponse(405, "Method not allowed".toByteArray(), "text/plain; charset=utf-8", mapOf("Allow" to "GET, POST, OPTIONS"))
        }
    }

    private fun handlePost(request: HttpRequest): HttpResponse {
        val type = request.contentType?.substringBefore(';')?.trim()?.lowercase()
        if (type != "application/ipp") return HttpResponse.text(415, "Unsupported media type: expected application/ipp")
        val message: IppMessage = try {
            IppCodec.decode(request.body)
        } catch (e: Exception) {
            log.log(Level.INFO, "Malformed IPP request", e)
            return HttpResponse.text(400, "Bad IPP request: ${e.message}")
        }
        val host = request.host?.trim()?.takeIf { it.isNotEmpty() } ?: "localhost:${config().port}"
        val printerUri = "ipp://$host${PrinterConfig.RESOURCE_PATH}"
        val userAgent = request.headers["user-agent"]
        val contentLength = request.headers["content-length"]?.toLongOrNull() ?: 0L
        val response = handler.handle(message, request.body, printerUri, contentLength, userAgent)
        return HttpResponse(200, IppCodec.encode(response), "application/ipp")
    }

    private fun handleVerifyPin(request: HttpRequest): HttpResponse {
        val bodyStr = request.body.readBytes().toString(Charsets.UTF_8)
        val enteredPin = Regex("\"pin\"\\s*:\\s*\"([^\"]+)\"").find(bodyStr)?.groupValues?.get(1)
            ?: request.headers["x-airshare-pin"]
            ?: parseQueryParams(request.path)["pin"]
            ?: ""
        val valid = config().webSharePin.isEmpty() || enteredPin == config().webSharePin
        val status = if (valid) 200 else 401
        return HttpResponse.text(status, """{"valid":$valid}""", "application/json; charset=utf-8", CORS_HEADERS)
    }

    private fun handleUpload(request: HttpRequest): HttpResponse {
        val cfg = config()
        if (!cfg.webShareEnabled) {
            return HttpResponse.text(403, """{"error":"Web transfer disabled"}""", "application/json; charset=utf-8", CORS_HEADERS)
        }
        val queryParams = parseQueryParams(request.path)
        val pin = request.headers["x-airshare-pin"] ?: queryParams["pin"] ?: ""
        if (cfg.webSharePin.isNotEmpty() && pin != cfg.webSharePin) {
            return HttpResponse.text(401, """{"error":"Invalid PIN"}""", "application/json; charset=utf-8", CORS_HEADERS)
        }

        val rawFileName = request.headers["x-file-name"]
            ?: queryParams["filename"]
            ?: "upload_${System.currentTimeMillis()}"
        val fileName = try {
            java.net.URLDecoder.decode(rawFileName, "UTF-8")
        } catch (_: Exception) {
            rawFileName
        }.substringAfterLast('/').substringAfterLast('\\')

        val contentType = request.contentType ?: "application/octet-stream"
        val mime = inferMimeType(fileName, contentType)
        val senderName = request.headers["x-sender-name"]?.let {
            try { java.net.URLDecoder.decode(it, "UTF-8") } catch (_: Exception) { it }
        } ?: "iPhone"
        val contentLength = request.headers["content-length"]?.toLongOrNull() ?: 0L
        val userAgent = request.headers["user-agent"]

        val job = jobs.create(fileName, senderName, mime)
        val approved = if (!cfg.autoAccept) {
            com.mehuljain.airshare.transfer.TransferCoordinator.awaitApproval(
                jobId = job.id,
                senderName = senderName,
                jobName = fileName,
                format = mime,
                totalBytes = contentLength,
                userAgent = userAgent,
            )
        } else {
            true
        }

        if (!approved) {
            jobs.setState(job.id, JobState.CANCELED)
            drain(request.body)
            return HttpResponse.text(403, """{"error":"Transfer declined by recipient"}""", "application/json; charset=utf-8", CORS_HEADERS)
        }

        com.mehuljain.airshare.transfer.TransferCoordinator.startReceiving(
            jobId = job.id,
            senderName = senderName,
            jobName = fileName,
            format = mime,
            totalBytes = contentLength,
            userAgent = userAgent,
        )

        val progressStream = ProgressInputStream(request.body, contentLength) { read, total ->
            com.mehuljain.airshare.transfer.TransferCoordinator.updateProgress(job.id, read, total)
        }

        return try {
            jobs.writeDocument(job.id, mime, progressStream)
            com.mehuljain.airshare.transfer.TransferCoordinator.finishTransfer(job.id, success = true, message = "Saved to Gallery")
            HttpResponse.text(200, """{"success":true,"message":"Saved to Gallery","id":${job.id}}""", "application/json; charset=utf-8", CORS_HEADERS)
        } catch (e: Exception) {
            com.mehuljain.airshare.transfer.TransferCoordinator.finishTransfer(job.id, success = false, message = e.message ?: "Failed")
            HttpResponse.text(500, """{"error":"${e.message}"}""", "application/json; charset=utf-8", CORS_HEADERS)
        }
    }

    private fun handleShortcut(request: HttpRequest): HttpResponse {
        val host = request.host ?: "localhost:${config().port}"
        val pin = config().webSharePin
        val html = """
            |<!DOCTYPE html>
            |<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            |<title>AirShare iOS Shortcut</title>
            |<style>
            |body { font-family: -apple-system, sans-serif; background: #000; color: #fff; padding: 24px; text-align: center; }
            |.box { background: #1c1c1e; border-radius: 20px; padding: 24px; max-width: 440px; margin: 20px auto; }
            |h2 { font-size: 20px; margin-bottom: 12px; }
            |p { color: #8e8e93; font-size: 14px; line-height: 1.5; margin-bottom: 20px; }
            |ol { text-align: left; margin: 16px 20px; color: #e5e5ea; line-height: 1.8; font-size: 14px; }
            |.code { background: rgba(255,255,255,0.1); padding: 4px 8px; border-radius: 6px; font-family: monospace; }
            |.btn { display: inline-block; background: #0A84FF; color: #fff; text-decoration: none; padding: 14px 28px; border-radius: 12px; font-weight: 600; }
            |</style></head><body>
            |<div class="box">
            |<h2>Add to iOS Share Sheet</h2>
            |<p>To send videos and photos directly from your Apple Photos app:</p>
            |<ol>
            |<li>Bookmark this page or add it to your <b>Home Screen</b> from the Safari Share menu.</li>
            |<li>Or create a 2-step Apple Shortcut:
            |<br>&bull; Set shortcut input: <b>Images, Media, Files</b>
            |<br>&bull; Add action: <b>Get contents of URL</b>
            |<br>&bull; URL: <span class="code">http://$host/upload?pin=$pin</span>
            |<br>&bull; Method: <b>POST</b>, Request Body: <b>Shortcut Input</b>
            |</li>
            |</ol>
            |<a href="/" class="btn">Return to Web Portal</a>
            |</div></body></html>
        """.trimMargin()
        return HttpResponse.text(200, html, "text/html; charset=utf-8", CORS_HEADERS)
    }

    private fun inferMimeType(filename: String, rawContentType: String): String {
        val dot = filename.lastIndexOf('.')
        if (dot < 0) return rawContentType
        val ext = filename.substring(dot + 1).lowercase()
        return when (ext) {
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "m4v" -> "video/x-m4v"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            else -> rawContentType
        }
    }

    private fun parseQueryParams(path: String): Map<String, String> {
        val q = path.substringAfter('?', "")
        if (q.isEmpty()) return emptyMap()
        return q.split('&').mapNotNull { param ->
            val parts = param.split('=', limit = 2)
            if (parts.size == 2) {
                try {
                    parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
                } catch (_: Exception) {
                    parts[0] to parts[1]
                }
            } else null
        }.toMap()
    }

    private fun drain(input: InputStream) {
        val buf = ByteArray(8192)
        while (input.read(buf) >= 0) { /* discard */ }
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '<' -> append("&lt;"); '>' -> append("&gt;"); '&' -> append("&amp;")
            '"' -> append("&quot;"); '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    private companion object {
        val log: Logger = Logger.getLogger(IppHttpHandler::class.java.name)
        val CORS_HEADERS = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Access-Control-Allow-Headers" to "Content-Type, X-AirShare-PIN, X-File-Name, X-Sender-Name, Authorization",
        )
    }
}
