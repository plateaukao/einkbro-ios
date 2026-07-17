package info.plateaukao.einkbro.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.posix.AF_INET
import platform.posix.IPPROTO_IP
import platform.posix.IP_ADD_MEMBERSHIP
import platform.posix.SOCK_DGRAM
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.SO_REUSEPORT
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.connect
import platform.posix.errno
import platform.posix.getsockname
import platform.posix.ip_mreq
import platform.posix.listen
import platform.posix.recv
import platform.posix.recvfrom
import platform.posix.send
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.strerror

private const val MULTICAST_IP = "239.10.10.100"
private const val MULTICAST_PORT = 54545
private const val BACKUP_PREFIX = "einkbro-backup:"
private const val BROADCAST_INTERVAL_MS = 1000L

@OptIn(ExperimentalForeignApi::class)
actual object LanShare {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var jobs = mutableListOf<Job>()
    private var openFds = mutableListOf<Int>()

    private fun htons(port: Int): UShort =
        (((port and 0xff) shl 8) or ((port ushr 8) and 0xff)).toUShort()

    /** "a.b.c.d" to an in_addr_t (network byte order stored little-endian). */
    private fun ipv4ToAddr(ip: String): UInt {
        val parts = ip.split(".").mapNotNull { it.toUIntOrNull() }
        if (parts.size != 4 || parts.any { it > 255u }) return 0u
        return (parts[3] shl 24) or (parts[2] shl 16) or (parts[1] shl 8) or parts[0]
    }

    private fun formatIpv4(addr: UInt): String =
        "${addr and 0xffu}.${(addr shr 8) and 0xffu}.${(addr shr 16) and 0xffu}.${(addr shr 24) and 0xffu}"

    private fun nowMs(): Long = (NSDate().timeIntervalSinceReferenceDate * 1000).toLong()

    private fun errnoText(what: String): String =
        "$what failed: ${strerror(errno)?.toKString() ?: "errno $errno"}" +
            if (errno == 1 || errno == 13 || errno == 65)
                " (multicast entitlement may be missing)" else ""

    private fun track(fd: Int): Int {
        if (fd >= 0) openFds.add(fd)
        return fd
    }

    /** UDP socket bound to the multicast port with the group joined; -1 on failure. */
    private fun openMulticastSocket(onError: (String) -> Unit): Int = memScoped {
        val fd = socket(AF_INET, SOCK_DGRAM, 0)
        if (fd < 0) {
            onError(errnoText("socket"))
            return -1
        }
        val one = alloc<IntVar>().apply { value = 1 }
        setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<IntVar>().convert())
        setsockopt(fd, SOL_SOCKET, SO_REUSEPORT, one.ptr, sizeOf<IntVar>().convert())

        val addr = alloc<sockaddr_in>().apply {
            sin_len = sizeOf<sockaddr_in>().toUByte()
            sin_family = AF_INET.convert()
            sin_port = htons(MULTICAST_PORT)
            sin_addr.s_addr = 0u // INADDR_ANY
        }
        if (bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
            onError(errnoText("bind"))
            close(fd)
            return -1
        }
        val mreq = alloc<ip_mreq>().apply {
            imr_multiaddr.s_addr = ipv4ToAddr(MULTICAST_IP)
            imr_interface.s_addr = 0u
        }
        if (setsockopt(fd, IPPROTO_IP, IP_ADD_MEMBERSHIP, mreq.ptr, sizeOf<ip_mreq>().convert()) < 0) {
            onError(errnoText("join multicast group"))
            close(fd)
            return -1
        }
        track(fd)
    }

    private fun sendDatagram(fd: Int, message: String): Boolean = memScoped {
        val dest = alloc<sockaddr_in>().apply {
            sin_len = sizeOf<sockaddr_in>().toUByte()
            sin_family = AF_INET.convert()
            sin_port = htons(MULTICAST_PORT)
            sin_addr.s_addr = ipv4ToAddr(MULTICAST_IP)
        }
        val bytes = message.encodeToByteArray()
        val sent = bytes.usePinned { pinned ->
            sendto(
                fd, pinned.addressOf(0), bytes.size.convert(), 0,
                dest.ptr.reinterpret(), sizeOf<sockaddr_in>().convert(),
            )
        }
        sent.toInt() == bytes.size
    }

    /** Blocking receive; null on socket close/error. */
    private fun receiveDatagram(fd: Int): String? {
        val buffer = ByteArray(4096)
        val received = buffer.usePinned { pinned ->
            recvfrom(fd, pinned.addressOf(0), buffer.size.convert(), 0, null, null)
        }.toInt()
        return if (received > 0) buffer.decodeToString(0, received) else null
    }

    private fun toMain(block: () -> Unit) {
        scope.launch { withContext(Dispatchers.Main) { block() } }
    }

    actual fun startBroadcast(message: String, times: Int, onError: (String) -> Unit) {
        stop()
        jobs.add(scope.launch {
            var reportedError: String? = null
            val fd = openMulticastSocket { reportedError = it }
            if (fd < 0) {
                reportedError?.let { err -> toMain { onError(err) } }
                return@launch
            }
            repeat(times) {
                if (!sendDatagram(fd, message)) {
                    toMain { onError(errnoText("send")) }
                    return@launch
                }
                delay(BROADCAST_INTERVAL_MS)
            }
        })
    }

    actual fun startReceiving(onError: (String) -> Unit, onMessage: (String) -> Unit) {
        stop()
        jobs.add(scope.launch {
            var reportedError: String? = null
            val fd = openMulticastSocket { reportedError = it }
            if (fd < 0) {
                reportedError?.let { err -> toMain { onError(err) } }
                return@launch
            }
            var lastMessage = ""
            var lastAtMs = 0L
            while (true) {
                val message = receiveDatagram(fd) ?: return@launch
                val now = nowMs()
                if (message == lastMessage && now - lastAtMs < 5_000L) continue
                lastMessage = message
                lastAtMs = now
                toMain { onMessage(message) }
            }
        })
    }

    actual fun serveBytes(bytes: ByteArray, onError: (String) -> Unit) {
        stop()
        jobs.add(scope.launch {
            memScoped {
                val serverFd = track(socket(AF_INET, SOCK_STREAM, 0))
                if (serverFd < 0) {
                    toMain { onError(errnoText("socket")) }
                    return@launch
                }
                val addr = alloc<sockaddr_in>().apply {
                    sin_len = sizeOf<sockaddr_in>().toUByte()
                    sin_family = AF_INET.convert()
                    sin_port = 0u // ephemeral
                    sin_addr.s_addr = 0u
                }
                if (bind(serverFd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0 ||
                    listen(serverFd, 1) < 0
                ) {
                    toMain { onError(errnoText("listen")) }
                    return@launch
                }
                val bound = alloc<sockaddr_in>()
                val len = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
                getsockname(serverFd, bound.ptr.reinterpret(), len.ptr)
                val portValue = bound.sin_port.toInt()
                val port = ((portValue and 0xff) shl 8) or ((portValue ushr 8) and 0xff)
                val localIp = localIpAddress() ?: run {
                    toMain { onError("No local network address") }
                    return@launch
                }

                // Announce the endpoint like Android does, then serve the first client.
                val announceJob = scope.launch {
                    var announceError: String? = null
                    val mFd = openMulticastSocket { announceError = it }
                    if (mFd < 0) {
                        announceError?.let { err -> toMain { onError(err) } }
                        return@launch
                    }
                    repeat(30) {
                        if (!sendDatagram(mFd, "$BACKUP_PREFIX$localIp:$port")) {
                            toMain { onError(errnoText("send")) }
                            return@launch
                        }
                        delay(BROADCAST_INTERVAL_MS)
                    }
                }
                jobs.add(announceJob)

                val clientFd = accept(serverFd, null, null)
                announceJob.cancel()
                if (clientFd < 0) return@launch // stop() closed us
                track(clientFd)
                var offset = 0
                bytes.usePinned { pinned ->
                    while (offset < bytes.size) {
                        val written = send(
                            clientFd, pinned.addressOf(offset), (bytes.size - offset).convert(), 0,
                        ).toInt()
                        if (written <= 0) {
                            toMain { onError(errnoText("send")) }
                            return@usePinned
                        }
                        offset += written
                    }
                }
                close(clientFd)
                openFds.remove(clientFd)
            }
        })
    }

    actual fun receiveBytes(
        onError: (String) -> Unit,
        onConnected: () -> Unit,
        onReceived: (ByteArray) -> Unit,
    ) {
        stop()
        jobs.add(scope.launch {
            var reportedError: String? = null
            val fd = openMulticastSocket { reportedError = it }
            if (fd < 0) {
                reportedError?.let { err -> toMain { onError(err) } }
                return@launch
            }
            while (true) {
                val message = receiveDatagram(fd) ?: return@launch
                if (!message.startsWith(BACKUP_PREFIX)) continue
                val parts = message.removePrefix(BACKUP_PREFIX).split(":")
                if (parts.size != 2) continue
                val ip = parts[0]
                val port = parts[1].toIntOrNull() ?: continue

                val data = memScoped {
                    val clientFd = track(socket(AF_INET, SOCK_STREAM, 0))
                    if (clientFd < 0) return@memScoped null
                    val dest = alloc<sockaddr_in>().apply {
                        sin_len = sizeOf<sockaddr_in>().toUByte()
                        sin_family = AF_INET.convert()
                        sin_port = htons(port)
                        sin_addr.s_addr = ipv4ToAddr(ip)
                    }
                    if (connect(clientFd, dest.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
                        toMain { onError(errnoText("connect")) }
                        close(clientFd)
                        openFds.remove(clientFd)
                        return@memScoped null
                    }
                    toMain { onConnected() }
                    val chunks = mutableListOf<ByteArray>()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = buffer.usePinned { pinned ->
                            recv(clientFd, pinned.addressOf(0), buffer.size.convert(), 0)
                        }.toInt()
                        if (n <= 0) break
                        chunks.add(buffer.copyOf(n))
                    }
                    close(clientFd)
                    openFds.remove(clientFd)
                    val total = ByteArray(chunks.sumOf { it.size })
                    var pos = 0
                    chunks.forEach { it.copyInto(total, pos); pos += it.size }
                    total
                } ?: continue

                toMain { onReceived(data) }
                break
            }
        })
    }

    actual fun stop() {
        jobs.forEach { it.cancel() }
        jobs = mutableListOf()
        // Closing the fds unblocks any recvfrom/accept still parked on them.
        openFds.forEach { close(it) }
        openFds = mutableListOf()
    }

    /**
     * Local IPv4 of the default route interface: UDP "connect" (no packet is
     * sent) then read the chosen source address back with getsockname.
     * getifaddrs isn't exposed to Kotlin/Native on iOS, so this is the
     * dependency-free way to learn our own address.
     */
    private fun localIpAddress(): String? {
        memScoped {
            val fd = socket(AF_INET, SOCK_DGRAM, 0)
            if (fd < 0) return null
            try {
                val dest = alloc<sockaddr_in>().apply {
                    sin_len = sizeOf<sockaddr_in>().toUByte()
                    sin_family = AF_INET.convert()
                    sin_port = htons(53)
                    sin_addr.s_addr = ipv4ToAddr("8.8.8.8")
                }
                if (connect(fd, dest.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
                    return null
                }
                val local = alloc<sockaddr_in>()
                val len = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
                if (getsockname(fd, local.ptr.reinterpret(), len.ptr) < 0) return null
                val addr = local.sin_addr.s_addr
                return if (addr == 0u) null else formatIpv4(addr)
            } finally {
                close(fd)
            }
        }
    }
}
