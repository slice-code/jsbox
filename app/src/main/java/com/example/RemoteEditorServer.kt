package com.example

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class RemoteEditorServer(
    private val context: Context,
    private val projectPath: String,
    private val port: Int = 5009
) {
    private val TAG = "RemoteEditorServer"
    private var serverSocket: ServerSocket? = null
    private var running = false
    private val executor = Executors.newCachedThreadPool()
    private val activeWebSockets = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())

    interface ServerListener {
        fun onClientConnected()
        fun onClientDisconnected()
    }

    var listener: ServerListener? = null

    fun start() {
        if (running) return
        running = true
        executor.execute {
            try {
                serverSocket = ServerSocket(port, 50, java.net.InetAddress.getByName("0.0.0.0"))
                Log.d(TAG, "Server running on port $port")
                while (running) {
                    val clientSocket = serverSocket?.accept() ?: break
                    executor.execute {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        activeWebSockets.forEach { socket ->
            try {
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        activeWebSockets.clear()
        executor.shutdownNow()
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            val headerLines = mutableListOf<String>()
            var line: String? = reader.readLine()
            
            // Read HTTP headers
            while (!line.isNullOrEmpty()) {
                headerLines.add(line)
                line = reader.readLine()
            }

            if (headerLines.isEmpty()) {
                socket.close()
                return
            }

            val requestLine = headerLines[0]
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0]
            val url = parts[1]

            // Check if WebSocket upgrade requested
            val isWebSocket = headerLines.any { it.contains("Upgrade: websocket", ignoreCase = true) }
            if (isWebSocket && url.startsWith("/ws")) {
                val secKeyLine = headerLines.firstOrNull { it.contains("Sec-WebSocket-Key:", ignoreCase = true) }
                if (secKeyLine != null) {
                    val clientKey = secKeyLine.substringAfter(":").trim()
                    val acceptKey = getWebSocketAcceptKey(clientKey)

                    val outputStream = BufferedOutputStream(socket.getOutputStream())
                    val handshake = """
                        HTTP/1.1 101 Switching Protocols
                        Upgrade: websocket
                        Connection: Upgrade
                        Sec-WebSocket-Accept: $acceptKey
                        
                        
                    """.trimIndent()
                    outputStream.write(handshake.toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    activeWebSockets.add(socket)
                    listener?.onClientConnected()
                    Log.d(TAG, "WebSocket connected successfully!")

                    handleWebSocketSession(socket)
                } else {
                    sendHTTPResponse(socket, "400 Bad Request", "text/plain", "Missing Sec-WebSocket-Key")
                }
                return
            }

            // Read potential body for POST requests
            var contentLength = 0
            headerLines.forEach { hdr ->
                if (hdr.contains("Content-Length:", ignoreCase = true)) {
                    contentLength = hdr.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            val bodyBuilder = java.lang.StringBuilder()
            if (contentLength > 0 && method.equals("POST", ignoreCase = true)) {
                val bodyChars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val r = reader.read(bodyChars, read, contentLength - read)
                    if (r == -1) break
                    read += r
                }
                bodyBuilder.append(bodyChars)
            }
            val body = bodyBuilder.toString()

            // Router
            when {
                url == "/" || url == "/index.html" -> {
                    try {
                        val html = context.assets.open("web-ide/index.html").bufferedReader().use { it.readText() }
                        sendHTTPResponse(socket, "200 OK", "text/html; charset=utf-8", html)
                    } catch (e: Exception) {
                        sendHTTPResponse(socket, "500 Internal Error", "text/plain", "Assets error: ${e.message}")
                    }
                }
                url.startsWith("/libs/") -> {
                    try {
                        val filename = url.substringAfter("/libs/")
                        val inputStream = context.assets.open("web-ide/libs/$filename")
                        val content = inputStream.bufferedReader().use { it.readText() }
                        val mime = when {
                            filename.endsWith(".js") -> "application/javascript; charset=utf-8"
                            filename.endsWith(".css") -> "text/css; charset=utf-8"
                            else -> "text/plain; charset=utf-8"
                        }
                        sendHTTPResponse(socket, "200 OK", mime, content)
                    } catch (e: Exception) {
                        sendHTTPResponse(socket, "404 Not Found", "text/plain", "Asset not found: ${e.message}")
                    }
                }
                url == "/api/files" -> {
                    try {
                        val baseDir = File(projectPath)
                        val files = baseDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") } ?: emptyList()
                        val json = "[" + files.map { "{\"name\":\"${it.name}\"}" }.joinToString(",") + "]"
                        sendHTTPResponse(socket, "200 OK", "application/json; charset=utf-8", json)
                    } catch (e: Exception) {
                        sendHTTPResponse(socket, "500 Error", "application/json", "{\"error\":\"${e.message}\"}")
                    }
                }
                url.startsWith("/api/file") -> {
                    val filename = java.net.URLDecoder.decode(url.substringAfter("?path=").substringBefore("&"), "UTF-8")
                    val file = File(projectPath, filename)
                    if (file.exists() && file.isFile) {
                        sendHTTPResponse(socket, "200 OK", "text/plain; charset=utf-8", file.readText())
                    } else {
                        sendHTTPResponse(socket, "404 Not Found", "text/plain", "File not found")
                    }
                }
                url == "/api/save" && method.equals("POST", ignoreCase = true) -> {
                    try {
                        val pathToken = "\"path\":\""
                        val contentToken = "\"content\":\""
                        val pathStart = body.indexOf(pathToken)
                        var pathVal = ""
                        if (pathStart != -1) {
                            pathVal = body.substring(pathStart + pathToken.length).substringBefore("\"")
                        }
                        val contentStart = body.indexOf(contentToken)
                        var contentVal = ""
                        if (contentStart != -1) {
                            contentVal = body.substring(contentStart + contentToken.length)
                            if (contentVal.endsWith("}")) {
                                contentVal = contentVal.substring(0, contentVal.length - 1)
                            }
                            if (contentVal.endsWith("\"")) {
                                contentVal = contentVal.substring(0, contentVal.length - 1)
                            }
                            contentVal = unescapeJsonString(contentVal)
                        }

                        if (pathVal.isNotEmpty()) {
                            val destFile = File(projectPath, pathVal)
                            destFile.writeText(contentVal)
                            sendHTTPResponse(socket, "200 OK", "application/json", "{\"status\":\"saved\"}")
                        } else {
                            sendHTTPResponse(socket, "400 Bad Request", "application/json", "{\"error\":\"missing file path\"}")
                        }
                    } catch (e: Exception) {
                        sendHTTPResponse(socket, "500 Error", "application/json", "{\"error\":\"${e.message}\"}")
                    }
                }
                url == "/api/play" && method.equals("POST", ignoreCase = true) -> {
                    RemotePlayManager.triggerPlay()
                    sendHTTPResponse(socket, "200 OK", "application/json", "{\"status\":\"trigger_play\"}")
                }
                else -> {
                    sendHTTPResponse(socket, "404 Not Found", "text/plain", "Not found")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (!activeWebSockets.contains(socket)) {
                try { socket.close() } catch (ignored: Exception) {}
            }
        }
    }

    private fun handleWebSocketSession(socket: Socket) {
        try {
            val ips = socket.getInputStream()
            while (running) {
                val msg = readWebSocketFrame(ips) ?: break
                if (msg.trim().isNotEmpty()) {
                    if (msg.contains("trigger_play")) {
                        RemotePlayManager.triggerPlay()
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "WebSocket closed: ${e.message}")
        } finally {
            activeWebSockets.remove(socket)
            listener?.onClientDisconnected()
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun readWebSocketFrame(inputStream: InputStream): String? {
        val b0 = inputStream.read()
        if (b0 == -1) return null
        val opcode = b0 and 0x0F
        if (opcode == 0x08) return null // connection close

        val b1 = inputStream.read()
        if (b1 == -1) return null

        val masked = (b1 and 0x80) != 0
        var payloadLength = (b1 and 0x7F).toLong()

        if (payloadLength == 126L) {
            val lenByte0 = inputStream.read()
            val lenByte1 = inputStream.read()
            if (lenByte0 == -1 || lenByte1 == -1) return null
            payloadLength = ((lenByte0 shl 8) or lenByte1).toLong()
        } else if (payloadLength == 127L) {
            var len = 0L
            for (i in 0 until 8) {
                val b = inputStream.read()
                if (b == -1) return null
                len = (len shl 8) or b.toLong()
            }
            payloadLength = len
        }

        val mask = ByteArray(4)
        if (masked) {
            for (i in 0 until 4) {
                val b = inputStream.read()
                if (b == -1) return null
                mask[i] = b.toByte()
            }
        }

        val payload = ByteArray(payloadLength.toInt())
        var totalRead = 0
        while (totalRead < payloadLength) {
            val read = inputStream.read(payload, totalRead, payload.size - totalRead)
            if (read == -1) return null
            totalRead += read
        }

        if (masked) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
        }

        return String(payload, Charsets.UTF_8)
    }

    private fun sendHTTPResponse(socket: Socket, status: String, contentType: String, content: String) {
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        val header = """
            HTTP/1.1 $status
            Content-Type: $contentType
            Content-Length: ${contentBytes.size}
            Connection: close
            Access-Control-Allow-Origin: *
            Access-Control-Allow-Headers: *
            
            
        """.trimIndent()
        try {
            val out = BufferedOutputStream(socket.getOutputStream())
            out.write(header.toByteArray(Charsets.UTF_8))
            out.write(contentBytes)
            out.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun getWebSocketAcceptKey(clientKey: String): String {
        val concat = clientKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(concat.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun unescapeJsonString(input: String): String {
        val sb = java.lang.StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                val next = input[i + 1]
                when (next) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < input.length) {
                            val hex = input.substring(i + 2, i + 6)
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        }
                    }
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
