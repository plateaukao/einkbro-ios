package info.plateaukao.einkbro.util

/**
 * LAN sharing seam, wire-compatible with Android EinkBro's ShareUtil: links are
 * raw UDP multicast datagrams of the URL string to 239.10.10.100:54545; app
 * data is a one-shot TCP server announced over the same group as
 * "einkbro-backup:<ip>:<port>". Callbacks arrive on the main thread.
 *
 * iOS requires the com.apple.developer.networking.multicast entitlement (plus
 * the local-network privacy prompt) at runtime; failures surface via onError.
 */
expect object LanShare {
    /** Multicast [message] [times] times, one second apart. */
    fun startBroadcast(message: String, times: Int, onError: (String) -> Unit)

    /** Join the group and deliver each distinct datagram (duplicates within 5s dropped). */
    fun startReceiving(onError: (String) -> Unit, onMessage: (String) -> Unit)

    /** Serve [bytes] once over TCP and announce the endpoint via multicast. */
    fun serveBytes(bytes: ByteArray, onError: (String) -> Unit)

    /** Wait for an "einkbro-backup:" announcement, download the payload, deliver it. */
    fun receiveBytes(
        onError: (String) -> Unit,
        onConnected: () -> Unit,
        onReceived: (ByteArray) -> Unit,
    )

    /** Stop all broadcasting/receiving/serving and close every socket. */
    fun stop()
}
