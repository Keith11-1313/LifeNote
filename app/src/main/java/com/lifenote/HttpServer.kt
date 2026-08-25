package com.lifenote

import android.content.res.AssetManager
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Hand-rolled HTTP/1.1 listener on port 8420 (doc 05).
 * Five routes only; everything else is a 404. Token auth on every
 * API route except loopback /api/config and the debug UI at /.
 */
class HttpServer(
    private val port: Int,
    private val store: JournalStore,
    private val settings: Settings,
    private val assets: AssetManager
) {
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        serverSocket = ServerSocket(port)
        acceptThread = Thread {
            while (running) {
                try {
                    val client = serverSocket!!.accept()
                    Thread { handle(client) }.apply { isDaemon = true }.start()
                } catch (_: Exception) {
                    if (running) continue else break
                }
            }
        }.apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptThread = null
    }

    // ---------- request handling ----------

    private class Request(
        val method: String,
        val path: String,
        val token: String?,
        val isLoopback: Boolean,
        val body: String
    )

    private fun handle(socket: Socket) {
        try {
            socket.use { s ->
                val req = readRequest(s) ?: return
                val (status, contentType, body) = route(req)
                val bytes = body.toByteArray(Charsets.UTF_8)
                val head = buildString {
                    append("HTTP/1.1 $status\r\n")
                    append("Content-Type: $contentType\r\n")
                    append("Content-Length: ${bytes.size}\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Access-Control-Allow-Methods: GET, PUT, POST, OPTIONS\r\n")
                    append("Access-Control-Allow-Headers: X-LifeNote-Token, Content-Type\r\n")
                    append("Connection: close\r\n\r\n")
                }
                s.getOutputStream().write(head.toByteArray(Charsets.UTF_8))
                s.getOutputStream().write(bytes)
                s.getOutputStream().flush()
            }
        } catch (_: Exception) {
            // malformed client or aborted connection — drop it
        }
    }

    private fun readRequest(socket: Socket): Request? {
        val input = BufferedInputStream(socket.getInputStream())
        val headBytes = ArrayList<Byte>(1024)
        while (true) {
            val b = input.read()
            if (b == -1) return null
            headBytes.add(b.toByte())
            val n = headBytes.size
            if (n >= 4 &&
                headBytes[n - 4] == '\r'.code.toByte() &&
                headBytes[n - 3] == '\n'.code.toByte() &&
                headBytes[n - 2] == '\r'.code.toByte() &&
                headBytes[n - 1] == '\n'.code.toByte()
            ) break
            if (n > 16384) return null
        }
        val head = String(headBytes.toByteArray(), Charsets.UTF_8)
        val lines = head.split("\r\n")
        val line = lines.firstOrNull()?.split(" ") ?: return null
        if (line.size < 2) return null
        val method = line[0]
        val path = line[1].substringBefore('?')

        var contentLength = 0
        var token: String? = null
        for (h in lines.drop(1)) {
            val i = h.indexOf(':')
            if (i <= 0) continue
            val name = h.substring(0, i).trim()
            val value = h.substring(i + 1).trim()
            if (name.equals("Content-Length", true)) contentLength = value.toIntOrNull() ?: 0
            if (name.equals("X-LifeNote-Token", true)) token = value
        }
        if (contentLength !in 0..2_000_000) return null

        val body = ByteArray(contentLength)
        var read = 0
        while (read < contentLength) {
            val r = input.read(body, read, contentLength - read)
            if (r == -1) break
            read += r
        }

        val addr = socket.remoteSocketAddress as? InetSocketAddress
        val loopback = addr?.address?.isLoopbackAddress ?: false
        return Request(method, path, token, loopback, String(body, 0, read, Charsets.UTF_8))
    }

    // ---------- routing ----------

    private fun route(req: Request): Triple<Int, String, String> {
        if (req.method == "OPTIONS") {
            return Triple(204, "text/plain", "")
        }

        if (req.method == "GET" && req.path == "/") {
            val html = assets.open("index.html").bufferedReader().use { it.readText() }
            return Triple(200, "text/html; charset=utf-8", html)
        }

        if (req.method == "GET" && req.path == "/api/config") {
            // loopback only — hands the local UI its own token (never a peer)
            if (!req.isLoopback) return Triple(403, "text/plain", "forbidden")
            return json(200, "{\"token\":\"${jEsc(settings.token)}\",\"device\":\"${jEsc(settings.deviceName)}\"}")
        }

        if (!req.path.startsWith("/api/")) {
            return Triple(404, "text/plain", "not found")
        }
        if (req.token != settings.token) {
            return Triple(403, "text/plain", "forbidden")
        }

        return when {
            req.method == "POST" && req.path == "/api/ping" ->
                json(200, "{\"name\":\"${jEsc(settings.deviceName)}\",\"entries\":${store.index().count { !it.deleted }}}")

            req.method == "GET" && req.path == "/api/index" -> {
                val items = store.index().joinToString(",") { m ->
                    "{\"id\":\"${jEsc(m.id)}\",\"created\":\"${jEsc(m.created)}\"," +
                    "\"updated\":\"${jEsc(m.updated)}\",\"device\":\"${jEsc(m.device)}\"," +
                    "\"title\":\"${jEsc(m.title)}\",\"deleted\":${m.deleted}}"
                }
                json(200, "[$items]")
            }

            req.method == "GET" && req.path.startsWith("/api/entries/") -> {
                val id = req.path.removePrefix("/api/entries/")
                val raw = store.read(id) ?: return Triple(404, "text/plain", "missing")
                Triple(200, "text/plain; charset=utf-8", raw)
            }

            req.method == "PUT" && req.path.startsWith("/api/entries/") -> {
                val id = req.path.removePrefix("/api/entries/")
                when (val r = store.write(req.body, id)) {
                    is JournalStore.WriteResult.Ok -> json(200, "{\"saved\":true}")
                    is JournalStore.WriteResult.Conflict ->
                        json(409, "{\"conflict\":true,\"updated\":\"${jEsc(r.existingUpdated)}\"}")
                    is JournalStore.WriteResult.Invalid ->
                        json(400, "{\"error\":\"${jEsc(r.reason)}\"}")
                }
            }

            else -> Triple(404, "text/plain", "not found")
        }
    }

    private fun json(status: Int, body: String) =
        Triple(status, "application/json; charset=utf-8", body)

    private fun jEsc(s: String): String = buildString {
        for (ch in s) when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
        }
    }
}
