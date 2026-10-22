package com.sfzsgybb.ai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {

    // ---------- 会话 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: CaptureSessionEntity)

    @Query("SELECT * FROM capture_session ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<CaptureSessionEntity>>

    @Query("SELECT * FROM capture_session ORDER BY startedAt DESC LIMIT :limit")
    suspend fun sessions(limit: Int): List<CaptureSessionEntity>

    @Query("SELECT * FROM capture_session WHERE sessionId = :id")
    suspend fun session(id: String): CaptureSessionEntity?

    @Query("SELECT * FROM capture_session WHERE state = 'RUNNING' LIMIT 1")
    suspend fun runningSession(): CaptureSessionEntity?

    /**
     * 全部 RUNNING 会话。
     *
     * 【为什么需要这个】
     * 之前只有 runningSession()（LIMIT 1），而库里可能同时有多个
     * RUNNING 会话（Hook 一个、代理一个，甚至残留的僵尸会话）。
     * 单条查询永远返回同一条，导致：
     *  - 该复用的会话没匹配上 → 每条流量都新建一个会话
     *  - 抓包历史里堆出几十个只装了一两条记录的空壳会话
     *
     * 现在返回全部，由调用方按 (包名 + source) 精确匹配。
     */
    @Query("SELECT * FROM capture_session WHERE state = 'RUNNING'")
    suspend fun runningSessions(): List<CaptureSessionEntity>

    @Query("DELETE FROM capture_session WHERE sessionId IN (:ids)")
    suspend fun deleteSessions(ids: List<String>)

    @Query("UPDATE capture_session SET recordCount = :count WHERE sessionId = :id")
    suspend fun updateRecordCount(id: String, count: Int)

    // ---------- HTTP ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHttp(record: HttpRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHttpAll(records: List<HttpRecordEntity>): List<Long>

    /**
     * 增量游标分页。cursor 形如 "v2:<time>:<h|t>:<id>"，
     * 与 MCP network_list 的 nextAfterId 格式保持一致。
     *
     * 【D1 修复】用轻量投影（不含 requestBody/responseBody），
     * 否则一条大响应体就把 CursorWindow 打爆，整个会话查询全废。
     */
    @Query(
        """
        SELECT id, sessionId, source, packageName, protocol, method, url, host, path,
               httpVersion, statusCode, statusText, requestHeaders, responseHeaders,
               requestBodyTruncated, responseBodyTruncated,
               requestSize, responseSize, durationMs, time, mimeType
        FROM http_record
        WHERE sessionId = :sessionId
          AND (time < :afterTime OR (time = :afterTime AND id < :afterId))
        ORDER BY time DESC, id DESC LIMIT :limit
        """
    )
    suspend fun httpPage(
        sessionId: String,
        afterTime: Long,
        afterId: Long,
        limit: Int,
    ): List<HttpRecordSummaryEntity>

    @Query("SELECT * FROM http_record WHERE id = :id")
    suspend fun httpById(id: Long): HttpRecordEntity?

    /** substr 切 body（BLOB 不进 CursorWindow）。 */
    @Query("SELECT substr(requestBody, :offset + 1, :length) FROM http_record WHERE id = :id")
    suspend fun requestBodySlice(id: Long, offset: Int, length: Int): ByteArray?

    @Query("SELECT substr(responseBody, :offset + 1, :length) FROM http_record WHERE id = :id")
    suspend fun responseBodySlice(id: Long, offset: Int, length: Int): ByteArray?

    /**
     * 读元数据（不含 BLOB）—— 详情/信息工具用。
     * 【D1】单行大 BLOB 会把 CursorWindow 打爆。
     */
    @Query(
        """
        SELECT id, sessionId, source, packageName, protocol, method, url, host, path,
               httpVersion, statusCode, statusText, requestHeaders, responseHeaders,
               requestBodyTruncated, responseBodyTruncated,
               requestSize, responseSize, durationMs, time, mimeType
        FROM http_record WHERE id = :id
        """
    )
    suspend fun httpMetaById(id: Long): HttpRecordSummaryEntity?

    @Query(
        """
        SELECT id, sessionId, source, packageName, protocol, method, url, host, path,
               httpVersion, statusCode, statusText, requestHeaders, responseHeaders,
               requestBodyTruncated, responseBodyTruncated,
               requestSize, responseSize, durationMs, time, mimeType
        FROM http_record
        WHERE sessionId = :sessionId
          AND (url LIKE '%' || :q || '%' OR host LIKE '%' || :q || '%')
        ORDER BY time DESC LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchHttp(sessionId: String, q: String, limit: Int, offset: Int): List<HttpRecordSummaryEntity>

    @Query("SELECT COUNT(*) FROM http_record WHERE sessionId = :sessionId")
    suspend fun httpCount(sessionId: String): Long

    @Query("DELETE FROM http_record WHERE id IN (:ids)")
    suspend fun deleteHttp(ids: List<Long>)

    // ---------- Packet ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacket(packet: PacketDataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPacketAll(packets: List<PacketDataEntity>): List<Long>

    @Query(
        """
        SELECT id, sessionId, protocol, remoteAddress, remotePort, localPort,
               outbound, size, time, wsFrameType
        FROM packet_data
        WHERE sessionId = :sessionId
          AND (time < :afterTime OR (time = :afterTime AND id < :afterId))
        ORDER BY time DESC, id DESC LIMIT :limit
        """
    )
    suspend fun packetPage(sessionId: String, afterTime: Long, afterId: Long, limit: Int): List<PacketSummaryEntity>

    @Query("SELECT * FROM packet_data WHERE id = :id")
    suspend fun packetById(id: Long): PacketDataEntity?

    @Query(
        """
        SELECT id, sessionId, protocol, remoteAddress, remotePort, localPort,
               outbound, size, time, wsFrameType
        FROM packet_data WHERE sessionId = :sessionId ORDER BY time ASC LIMIT :limit
        """
    )
    suspend fun packetsAsc(sessionId: String, limit: Int): List<PacketSummaryEntity>

    @Query("SELECT COUNT(*) FROM packet_data WHERE sessionId = :sessionId")
    suspend fun packetCount(sessionId: String): Long

    /** 库里还有多少 HTTP 记录（用于校准 MCP 探针）。 */
    @Query("SELECT COUNT(*) FROM http_record")
    suspend fun totalHttpCount(): Long

    /** 某会话下的记录数。 */
    @Query("SELECT COUNT(*) FROM http_record WHERE sessionId = :sessionId")
    suspend fun httpCountBySession(sessionId: String): Int

    /** 记录与所属会话来源不一致的行（修复历史数据用）。 */
    @Query(
        """
        SELECT h.id AS id, h.packageName AS packageName, h.source AS source
        FROM http_record h
        JOIN capture_session s ON h.sessionId = s.sessionId
        WHERE h.source != s.source
        """
    )
    suspend fun httpRecordsWithMismatchedSource(): List<MismatchRow>

    /** 把某条记录挪到另一个会话。 */
    @Query("UPDATE http_record SET sessionId = :sessionId WHERE id = :id")
    suspend fun updateHttpSession(id: Long, sessionId: String)

    /** 把整个会话的记录批量挪到另一个会话（合并碎片会话用）。 */
    @Query("UPDATE http_record SET sessionId = :to WHERE sessionId = :from")
    suspend fun moveHttpSession(from: String, to: String)

    /** 把整个会话的报文挪到另一个会话。 */
    @Query("UPDATE packet_data SET sessionId = :to WHERE sessionId = :from")
    suspend fun movePacketsSession(from: String, to: String)

    /**
     * 按会话来源校正记录的 source 字段。
     *
     * 【为什么需要】MitmController 早期漏传 source="vpn"，
     * 会话被标成 hook、记录却写成 vpn，两边都不对。
     * 会话的 source 是建会话时显式传入的，更可信，以它为准。
     *
     * @return 被校正的记录数
     */
    @Query(
        """
        UPDATE http_record
        SET source = (
            SELECT s.source FROM capture_session s WHERE s.sessionId = http_record.sessionId
        )
        WHERE sessionId IN (SELECT sessionId FROM capture_session)
          AND source != (
            SELECT s.source FROM capture_session s WHERE s.sessionId = http_record.sessionId
          )
        """
    )
    suspend fun syncRecordSourceWithSession(): Int

    /**
     * 某会话的 HTTP 记录（按时间**正序**）。
     *
     * 【为什么需要】
     * httpPage 是倒序游标分页（条件是 `time < afterTime`），
     * 拿不到"从最早开始"的序列。network_packets 在 Hook 模式下
     * 要按时间顺序还原请求序列，所以单独提供正序查询。
     */
    @Query(
        """
        SELECT id, sessionId, source, packageName, protocol, method, url, host, path,
               httpVersion, statusCode, statusText, requestHeaders, responseHeaders,
               requestBodyTruncated, responseBodyTruncated,
               requestSize, responseSize, durationMs, time, mimeType
        FROM http_record WHERE sessionId = :sessionId ORDER BY time ASC, id ASC LIMIT :limit
        """
    )
    suspend fun httpAsc(sessionId: String, limit: Int): List<HttpRecordSummaryEntity>

    @Query("DELETE FROM packet_data WHERE id IN (:ids)")
    suspend fun deletePackets(ids: List<Long>)

    // ---------- 连接 / 调用栈 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConnect(record: ConnectRecordEntity): Long

    @Query("SELECT * FROM connect_record WHERE sessionId = :sessionId ORDER BY connectTime DESC")
    suspend fun connects(sessionId: String): List<ConnectRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStack(record: StackRecordEntity): Long

    @Query("SELECT * FROM stack_record WHERE ownerId = :ownerId LIMIT 1")
    suspend fun stackOf(ownerId: Long): StackRecordEntity?

    /**
     * 删除单条 HTTP 记录关联的调用栈。
     *
     * 【为什么要显式删】
     * stack_record 没有外键约束（Room 的 CASCADE 在这个场景收益不抵复杂度），
     * 删 http_record 时必须手动清，否则堆栈越积越多、
     * 而且会指向不存在的记录（表现为「孤儿堆栈」）。
     */
    @Query("DELETE FROM stack_record WHERE ownerId = :ownerId")
    suspend fun deleteStackOf(ownerId: Long)

    /** 批量删除一组 HTTP 记录关联的调用栈。 */
    @Query("DELETE FROM stack_record WHERE ownerId IN (:ownerIds)")
    suspend fun deleteStacksOf(ownerIds: List<Long>)

    /** 删除整个会话下所有记录的调用栈（按 ownerId 归属）。 */
    @Query(
        """
        DELETE FROM stack_record WHERE ownerId IN (
            SELECT id FROM http_record WHERE sessionId IN (:sessionIds)
        )
        """
    )
    suspend fun deleteStacksBySessions(sessionIds: List<String>)

    /** 清理孤儿堆栈：ownerId 指向的 http_record 已不存在。 */
    @Query(
        """
        DELETE FROM stack_record WHERE ownerId NOT IN (
            SELECT id FROM http_record
        )
        """
    )
    suspend fun deleteOrphanStacks(): Int

    // ---------- 一键删除流量（保留会话） ----------

    /** 全部 HTTP 记录 id（删前先按 ownerId 清堆栈用）。 */
    @Query("SELECT id FROM http_record")
    suspend fun allHttpIds(): List<Long>

    /** 指定应用的所有 HTTP 记录 id。 */
    @Query("SELECT id FROM http_record WHERE packageName = :packageName")
    suspend fun httpIdsByPackage(packageName: String): List<Long>

    /** 删除指定应用的全部 HTTP 记录。 */
    @Query("DELETE FROM http_record WHERE packageName = :packageName")
    suspend fun deleteHttpByPackage(packageName: String)

    /**
     * 删除指定应用会话下的报文记录。
     *
     * packet_data 表没有 packageName 列（报文归属靠 sessionId），
     * 所以通过「该包名的会话」间接定位。
     */
    @Query(
        """
        DELETE FROM packet_data WHERE sessionId IN (
            SELECT sessionId FROM capture_session WHERE packageName = :packageName
        )
        """
    )
    suspend fun deletePacketsByPackage(packageName: String)

    /** 把所有会话的记录数清零（会话本身保留，便于继续抓）。 */
    @Query("UPDATE capture_session SET recordCount = 0")
    suspend fun resetAllRecordCounts()

    // ---------- 清理 ----------

    @Query("DELETE FROM http_record WHERE sessionId IN (:ids)")
    suspend fun deleteHttpBySessions(ids: List<String>)

    @Query("DELETE FROM packet_data WHERE sessionId IN (:ids)")
    suspend fun deletePacketsBySessions(ids: List<String>)

    @Query("DELETE FROM http_record")
    suspend fun clearHttp()

    @Query("DELETE FROM packet_data")
    suspend fun clearPackets()

    @Query("DELETE FROM capture_session")
    suspend fun clearSessions()

/** 记录与会话来源不一致的行（修复历史数据用）。 */
data class MismatchRow(
    val id: Long,
    val packageName: String,
    val source: String,
)

    /** 某会话下所有记录 id。 */
    @Query("SELECT id FROM http_record WHERE sessionId = :sessionId")
    suspend fun httpIdsBySession(sessionId: String): List<Long>

}
