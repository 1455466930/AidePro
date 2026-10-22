package com.sfzsgybb.ai.mitm

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLSession

/**
 * 用 SSLEngine 手动驱动的 TLS 终结器。
 *
 * 【为什么不用 SSLSocket】
 * MITM 必须先把客户端发来的 ClientHello 读出来解析 SNI，
 * 这段字节已从 socket 流消费掉，之后必须"还回去"给 TLS 层。
 * SSLSocketFactory.createSocket(Socket, InputStream consumed, boolean)
 * 在 Android（Conscrypt）上语义不可靠，实测握手卡死。
 *
 * SSLEngine 是 JSSE 底层接口：不给它数据它就不动，
 * 我们把已读的 ClientHello 和后续 socket 数据一并喂进去，
 * 完全掌控每个字节。
 *
 * 【缓冲区管理要点】
 * 这是 SSLEngine 最容易写错的地方：
 *  - netIn 在读模式/写模式间显式切换，不用 compact 混用
 *  - BUFFER_UNDERFLOW 表示数据不够，需要继续从 socket 读
 *  - BUFFER_OVERFLOW 表示输出缓冲不够，扩容后重试
 *  - NEED_TASK 必须把所有 delegatedTask 跑完
 */
class TlsTerminator(
    private val socket: Socket,
    private val engine: SSLEngine,
    /** 已从 socket 读走、需先喂给 engine 的字节（ClientHello） */
    private val consumed: ByteArray,
) {
    private val rawIn: InputStream = socket.getInputStream()
    private val rawOut: OutputStream = socket.getOutputStream()

    /**
     * TLS 状态机互斥锁。
     *
     * 【为什么必须有】
     * MITM 转发用了两个线程：一个从客户端读、往服务端写；
     * 另一个从服务端读、往客户端写。两个线程会同时调用
     * 同一个 SSLEngine 的 unwrap / wrap —— SSLEngine **不是线程安全的**。
     * 并发调用会让 TLS 状态机错乱：数据被丢弃、记录解析错位，
     * 表现为「连接建立了但内容损坏」。
     *
     * 症状很有迷惑性：小请求一次收发就结束，几乎不冲突，看起来完全正常；
     * 大响应（图片、视频）传输时间长，必然撞上，然后加载失败。
     *
     * 锁的粒度是「单次 unwrap / wrap」，不跨阻塞读，
     * 所以不会因为对端慢而互相拖死。
     */
    private val engineLock = Any()

    /**
     * 网络层输入缓冲（密文）。始终维护为「读模式」：position..limit 是可读数据。
     *
     * 【为什么固定给 64KB，而不是 engine.session.packetBufferSize】
     * packetBufferSize 只是「一条 TLS 记录」的建议大小（约 16KB）。
     * 但 unwrap 返回 BUFFER_UNDERFLOW 时，意味着现有数据凑不齐一条完整记录 ——
     * 如果缓冲刚好只有 16KB，稍大的记录（TLS 1.3 的 NewSessionTicket、
     * 大响应的首个应用数据记录）永远塞不进去，会一直 UNDERFLOW 直到超时，
     * 表现为「小请求正常、图片/大响应必然失败」。
     * 直接给 4 倍余量，配合下面的动态扩容，基本不会再卡住。
     */
    private var netIn: ByteBuffer = ByteBuffer.allocate(NET_IN_SIZE)

    /** 网络层输出缓冲（密文）。 */
    private var netOut: ByteBuffer = ByteBuffer.allocate(engine.session.packetBufferSize + 1024)

    /** 应用层输入缓冲（明文）。 */
    private var appIn: ByteBuffer = ByteBuffer.allocate(engine.session.applicationBufferSize + 1024)

    /** 已解密但尚未被 read() 取走的明文。 */
    private val appPlain = ByteArrayOutputStream(16 * 1024)

    /** 已读到流末尾。 */
    @Volatile private var eof = false

    @Volatile var lastError: String? = null
        private set

    val isEof: Boolean get() = eof

    val session: SSLSession get() = engine.session

    init {
        netIn.limit(0)
    }

    private fun hasNetData(): Boolean = netIn.hasRemaining()

    /** 把 netIn 切到写模式准备追加数据（保留未读完的部分）。 */
    private fun netInToWrite() {
        if (!netIn.hasRemaining()) {
            netIn.clear()
        } else {
            netIn.compact()
        }
    }

    /** 把 netIn 切回读模式。 */
    private fun netInToRead() {
        netIn.flip()
    }

    /** 从 socket 读更多密文。返回读到的字节数，-1 = 连接关闭。 */
    /**
     * 从 socket 读更多密文。
     * @return >0 读到字节；0 无数据（超时）；-1 流结束
     */
    private fun fillNetIn(): Int {
        if (eof) return -1
        netInToWrite()
        if (!netIn.hasRemaining()) {
            val bigger = ByteBuffer.allocate(netIn.capacity() * 2)
            netIn.flip()
            bigger.put(netIn)
            netIn = bigger
        }
        val n = try {
            rawIn.read(netIn.array(), netIn.position(), netIn.remaining())
        } catch (t: java.net.SocketTimeoutException) {
            0
        }
        if (n > 0) netIn.position(netIn.position() + n)
        if (n < 0) eof = true
        netInToRead()
        return n
    }

    /** 完成 TLS 握手。 */
    fun handshake(): Boolean {
        try {
            Log.d(TAG, "开始握手: 协议=${engine.enabledProtocols.joinToString()} " +
                "ALPN=${engine.sslParameters.applicationProtocols?.joinToString()}")
            engine.beginHandshake()

            if (consumed.isNotEmpty()) {
                if (consumed.size > netIn.capacity()) {
                    netIn = ByteBuffer.allocate(consumed.size + 1024)
                }
                netIn.clear()
                netIn.put(consumed)
                netIn.flip()
            }

            var loops = 0
            var stall = 0

            while (loops++ < MAX_HANDSHAKE_LOOPS) {
                when (engine.handshakeStatus) {
                    SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                        if (!hasNetData()) {
                            val n = fillNetIn()
                            if (n < 0) { lastError = "客户端在握手期间关闭连接"; return false }
                            if (n == 0) { if (++stall > MAX_STALL) { lastError = "握手停滞"; return false }; continue }
                        }
                        stall = 0
                        val before = netIn.remaining()
                        appIn.clear()
                        val res = engine.unwrap(netIn, appIn)
                        Log.d(TAG, "unwrap: 输入剩余=$before 状态=${res.status} " +
                            "握手=${res.handshakeStatus} 消耗=${res.bytesConsumed()} 产出=${res.bytesProduced()}")
                        when (res.status) {
                            SSLEngineResult.Status.OK -> Unit
                            SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                                // 【关键修复】数据不够凑齐一条完整握手记录。
                                //
                                // 旧写法只调 fillNetIn()，n == 0（读超时）时什么都不做
                                // 就进入下一轮 —— 而 stall 计数只在外面那个分支自增，
                                // 这里不自增，于是永远不触发「握手停滞」，
                                // 空转到 MAX_HANDSHAKE_LOOPS 才失败，
                                // 表现为「有的站握手成功、有的站反复超时」。
                                //
                                // 现在：有空间就多读，没空间就扩容，读不到就累计停滞。
                                val hadSpace = netIn.hasRemaining()
                                val n = fillNetIn()
                                if (n < 0) { lastError = "握手期连接关闭"; return false }
                                if (n == 0) {
                                    if (!hadSpace) {
                                        // 缓冲满了仍凑不齐：扩容（fillNetIn 内部已 flip）
                                        val bigger = ByteBuffer.allocate(
                                            maxOf(netIn.capacity() * 2, 64 * 1024)
                                        )
                                        netIn.flip()
                                        bigger.put(netIn)
                                        bigger.flip()
                                        netIn = bigger
                                        Log.d(TAG, "握手期缓冲扩容到 ${bigger.capacity()}")
                                    }
                                    if (++stall > MAX_STALL) {
                                        lastError = "握手停滞（等不到足够的握手数据）"
                                        return false
                                    }
                                } else {
                                    stall = 0
                                }
                            }
                            SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                                appIn = ByteBuffer.allocate(appIn.capacity() * 2)
                            }
                            SSLEngineResult.Status.CLOSED -> { lastError = "engine 已关闭"; return false }
                        }
                    }

                    SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                        netOut.clear()
                        val res = engine.wrap(EMPTY, netOut)
                        when (res.status) {
                            SSLEngineResult.Status.OK -> {
                                netOut.flip()
                                if (netOut.hasRemaining()) {
                                    rawOut.write(netOut.array(), netOut.position(), netOut.remaining())
                                    rawOut.flush()
                                }
                            }
                            SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                                netOut = ByteBuffer.allocate(netOut.capacity() * 2)
                            }
                            else -> Unit
                        }
                    }

                    SSLEngineResult.HandshakeStatus.NEED_TASK -> runTasks()

                    SSLEngineResult.HandshakeStatus.FINISHED,
                    SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> {
                        Log.d(TAG, "TLS 握手完成 proto=" + engine.session.protocol)
                        return true
                    }
                }
            }
            lastError = "握手循环超限（" + MAX_HANDSHAKE_LOOPS + "）"
            return false
        } catch (t: Throwable) {
            lastError = t.javaClass.simpleName + ": " + t.message
            Log.w(TAG, "TLS 握手失败", t)
            return false
        }
    }

    /** 读取解密后的应用数据。返回字节数，-1 = 连接关闭。 */
    /**
     * 读取解密后的应用数据。
     *
     * 【关键】BUFFER_UNDERFLOW 必须去 socket 读更多数据。
     * 早期版本在这里什么都不做就继续循环，导致对着同一份数据反复 unwrap，
     * 转发线程彻底卡死 —— 表现为「目标应用一直转圈、抓不到事务」。
     *
     * @return 读到的字节数；-1 表示连接结束
     */
    fun read(out: ByteArray): Int {
        try {
            var guard = 0
            // 等数据的总时长（毫秒）。超时仍解不出就放弃，避免连接被永久占住。
            var waitedMs = 0
            val maxWaitMs = 30_000
            while (guard++ < MAX_READ_LOOPS) {
                // 1) 有已解密数据，直接返回
                if (appPlain.size() > 0) {
                    val data = appPlain.toByteArray()
                    appPlain.reset()
                    val take = minOf(data.size, out.size)
                    System.arraycopy(data, 0, out, 0, take)
                    if (take < data.size) appPlain.write(data, take, data.size - take)
                    return take
                }

                // 2) 没有待处理密文就去读
                if (!hasNetData()) {
                    val n = fillNetIn()
                    if (n < 0) return -1
                    if (n == 0) {
                        // 无数据可读（超时）：让出 CPU，避免空转烧 CPU
                        try { Thread.sleep(5) } catch (e: InterruptedException) { return -1 }
                        continue
                    }
                }

                // 3) 解密
                appIn.clear()
                // SSLEngine 非线程安全，必须和 write() 的 wrap 互斥
                val res = synchronized(engineLock) { engine.unwrap(netIn, appIn) }
                when (res.status) {
                    SSLEngineResult.Status.OK -> {
                        appIn.flip()
                        if (appIn.hasRemaining()) {
                            val b = ByteArray(appIn.remaining())
                            appIn.get(b)
                            val take = minOf(b.size, out.size)
                            System.arraycopy(b, 0, out, 0, take)
                            if (take < b.size) appPlain.write(b, take, b.size - take)
                            return take
                        }
                        // unwrap 成功但没产出应用数据，继续
                    }
                    SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                        // 【关键修复】网络缓冲里的数据不足以组成完整的 TLS 记录。
                        //
                        // 旧写法只调 fillNetIn()，遇到读超时（返回 0）就 continue，
                        // 一直原地打转到 MAX_READ_LOOPS 然后返回 -1 ——
                        // 连接被直接掐断，大响应（图片、视频分片）必然失败，
                        // 而小请求因为一条完整记录就够，完全不受影响。
                        //
                        // 注意：fillNetIn() 内部已经做了 flip()，这里**不能再 flip**，
                        // 否则 limit/position 被搞乱，数据永远解不出来。
                        val hadSpace = netIn.hasRemaining()
                        val n = fillNetIn()
                        if (n < 0) return -1

                        if (n == 0) {
                            if (!hadSpace) {
                                // 缓冲已满仍解不出：扩容后继续（扩容后仍需 flip 才能读）
                                val bigger = ByteBuffer.allocate(maxOf(netIn.capacity() * 2, 64 * 1024))
                                netIn.flip()
                                bigger.put(netIn)
                                bigger.flip()
                                netIn = bigger
                            } else {
                                // 单纯还没等到数据，让出 CPU 后继续
                                try { Thread.sleep(2) } catch (e: InterruptedException) { return -1 }
                            }
                            // 等待不算失败：退回 guard，改用「累计等待时长」兜底
                            guard--
                            waitedMs += 5
                            if (waitedMs > maxWaitMs) {
                                lastError = "读超时（${maxWaitMs}ms 未凑齐完整 TLS 记录）"
                                return -1
                            }
                        }
                    }
                    SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                        appIn = ByteBuffer.allocate(appIn.capacity() * 2)
                    }
                    SSLEngineResult.Status.CLOSED -> return -1
                }

                runTasks()
                if (engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) flushWrap()
            }
            lastError = "read 循环超限"
            return -1
        } catch (t: Throwable) {
            lastError = t.javaClass.simpleName + ": " + t.message
            return -1
        }
    }

    /**
     * 发送应用数据（加密后写出）。
     *
     * 【曾经有个严重 Bug】
     * 旧实现是「按块推进」：
     * ```
     * while (remaining > 0) {
     *     val src = ByteBuffer.wrap(data, off, chunk)
     *     var guard = 0
     *     while (src.hasRemaining() && guard++ < MAX_WRITE_LOOPS) { ... }
     *     off += chunk          // 不管内层写完没写完，都跳过整块
     *     remaining -= chunk
     * }
     * return true               // 永远返回成功
     * ```
     * 内层循环一旦因为 guard 超限（wrap 返回 NEED_UNWRAP 或缓冲满）退出，
     * **没写完的数据被静默丢弃**，函数还返回 true —— 上层以为发成功了。
     * 表现就是：图片等大响应体传到一半损坏，App 报 Load failed；
     * 而小请求完全正常，很难怀疑到这一层。
     *
     * 现在改成「按实际消费量推进」，并且没写完就返回 false 让上层断开重连，
     * 而不是假装成功继续传下一块。
     */
    fun write(data: ByteArray, offset: Int, length: Int): Boolean {
        if (length <= 0) return true
        return try {
            var off = offset
            val end = offset + length
            while (off < end) {
                val chunk = minOf(end - off, MAX_WRAP_CHUNK)
                val src = ByteBuffer.wrap(data, off, chunk)
                var guard = 0
                var progressed = false

                while (src.hasRemaining() && guard++ < MAX_WRITE_LOOPS) {
                    netOut.clear()
                    // SSLEngine 非线程安全，必须和 read() 的 unwrap 互斥
                    val res = synchronized(engineLock) { engine.wrap(src, netOut) }
                    when (res.status) {
                        SSLEngineResult.Status.OK,
                        SSLEngineResult.Status.CLOSED -> {
                            netOut.flip()
                            if (netOut.hasRemaining()) {
                                rawOut.write(netOut.array(), netOut.position(), netOut.remaining())
                                rawOut.flush()
                            }
                            if (res.bytesConsumed() > 0) progressed = true
                        }
                        SSLEngineResult.Status.BUFFER_OVERFLOW -> {
                            // 网络缓冲不够：扩容后重试，不算进度
                            netOut = ByteBuffer.allocate(netOut.capacity() * 2)
                        }
                        SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                            // 对 wrap 来说不该出现，出现说明状态异常
                            lastError = "wrap BUFFER_UNDERFLOW"
                            return false
                        }
                    }
                    runTasks()
                }

                // 按真实消费量推进，而不是整块跳过
                val consumed = chunk - src.remaining()
                if (consumed > 0) {
                    off += consumed
                } else if (!progressed) {
                    // 一点都没写出去，不能假装成功 —— 让上层断开
                    lastError = "write 无进展（wrap 卡住）"
                    return false
                }
            }
            true
        } catch (t: Throwable) {
            lastError = t.javaClass.simpleName + ": " + t.message
            false
        }
    }

    fun close() {
        runCatching {
            engine.closeOutbound()
            flushWrap()
        }
        runCatching { socket.close() }
    }

    private fun flushWrap() {
        runCatching {
            netOut.clear()
            val res = engine.wrap(EMPTY, netOut)
            if (res.bytesProduced() > 0) {
                netOut.flip()
                rawOut.write(netOut.array(), netOut.position(), netOut.remaining())
                rawOut.flush()
            }
        }
    }

    private fun runTasks() {
        var t = engine.delegatedTask
        while (t != null) {
            runCatching { t.run() }
            t = engine.delegatedTask
        }
    }

    companion object {
        /** 网络输入缓冲大小：4 倍于单条 TLS 记录的建议值 */
        private const val NET_IN_SIZE = 64 * 1024

        private const val TAG = "TlsTerminator"
        private const val MAX_HANDSHAKE_LOOPS = 200
        private const val MAX_READ_LOOPS = 500
        private const val MAX_WRITE_LOOPS = 200
        private const val MAX_STALL = 100
        private const val MAX_WRAP_CHUNK = 16 * 1024
        private val EMPTY: ByteBuffer = ByteBuffer.allocate(0)
    }
}
