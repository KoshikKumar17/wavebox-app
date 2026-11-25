package com.wavebox.app

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** Lets NewPipeExtractor make its web requests through OkHttp (from the phone's own IP). */
class NpDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: NpRequest): NpResponse {
        val method = request.httpMethod()
        val url = request.url()
        val data = request.dataToSend()
        val body = if (method.equals("POST", true) || method.equals("PUT", true)) (data ?: ByteArray(0)).toRequestBody() else null

        val b = Request.Builder().method(method, body).url(url)
        var hasUa = false
        request.headers().forEach { (name, values) ->
            b.removeHeader(name)
            values.forEach { v -> b.addHeader(name, v) }
            if (name.equals("User-Agent", true)) hasUa = true
        }
        if (!hasUa) b.header("User-Agent", UA)

        client.newCall(b.build()).execute().use { r ->
            if (r.code == 429) throw ReCaptchaException("reCaptcha challenge requested", url)
            val text = r.body?.string()
            return NpResponse(r.code, r.message, r.headers.toMultimap(), text, r.request.url.toString())
        }
    }

    private companion object {
        const val UA = "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"
    }
}
