package com.sfzsgybb.ai.data.model

/**
 * 日志分组：与原版 LogDataV2.GROUP 对齐。
 *
 * 【分组编号的来历】
 * 0..5 沿用原版常量；6、7 是本工程为「数据库」和「SharedPreferences」
 * 拆出来的两组 —— 这两类 Hook 在「应用详情」页里占 9 个开关，
 * 如果全塞进 STORAGE（存储）分组，存储组会膨胀到 22 个开关，
 * 而日志页按分组筛选时也分不出「读了个文件」和「执行了条 SQL」。
 *
 * 【为什么默认是 -1 而不是 0】
 * 0 是合法的分组值（DEFAULT）。如果拿 0 当「默认分组」，
 * 那么「筛选默认分组」和「未筛选」就没法区分了。
 */
object LogGroup {
    /** 未知/未指定分组。注意不是「筛选全部」的哨兵值，哨兵用 -1。 */
    const val DEFAULT = 0
    const val CRYPTO = 1
    const val NETWORK = 2
    const val STORAGE = 3
    const val UI = 4
    const val SYSTEM = 5
    const val DATABASE = 6
    const val PREFS = 7

    /** 全部分组的编号，顺序即界面展示顺序。 */
    val ALL: List<Int> = listOf(DEFAULT, CRYPTO, NETWORK, STORAGE, DATABASE, PREFS, UI, SYSTEM)

    /** 「不筛选」哨兵值，对应界面上的「全部」标签。 */
    const val ANY = -1

    fun label(g: Int): String = when (g) {
        DEFAULT -> "默认"
        CRYPTO -> "加密"
        NETWORK -> "网络"
        STORAGE -> "存储"
        DATABASE -> "数据库"
        PREFS -> "配置"
        UI -> "界面"
        SYSTEM -> "系统"
        else -> "其他"
    }
}

/**
 * 报文视图模式：与原版 PACKET_TYPE_* 对齐。
 */
enum class PacketView { HEAD, BODY, RAW, WEBSOCKET }

/** 日志值渲染模式：对应原版 base64Switch / hexSwitch。 */
enum class ValueMode { TEXT, BASE64, HEX }

/** 抓包会话状态。 */
enum class CaptureState { IDLE, RUNNING, PAUSED, STOPPED, ERROR }

/** 流量类型。 */
enum class Protocol { HTTP, WEBSOCKET, TCP, UNKNOWN }
