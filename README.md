# 算法助手公益版

Android 逆向分析工具：**加密算法日志** + **双通道抓包** + **MCP 服务**（让 AI 直接读数据）。

不需要电脑和数据线，手机上直接看加密调用的明文、密钥与调用堆栈；抓包覆盖 Java 层与原生层。

> 仅用于自有设备上的应用调试与协议分析。

**交流反馈**：[加入社区](https://docs.qq.com/doc/DUHZkb1hLWmhQQXN5) · [开源仓库](https://github.com/1455466930/AidePro)

---

## 功能

### 加密算法日志

Hook `MessageDigest` / `Mac` / `Cipher`，记录算法名、密钥（区分公私钥）、明文密文、调用堆栈。

详情页字段分类展示，支持 text / hex / base64 切换与长按选中复制。

### 双通道抓包

| 方式 | 原理 | 适用 |
|---|---|---|
| **Hook 抓包** | Java 层拦截 OkHttp / HttpURLConnection / WebView | 明文直取，无需证书，兼容 R8 混淆 |
| **代理抓包** | VPN 路由层接管 + MITM 解密 | 覆盖 Flutter / Cronet / 自研网络栈 |

两种互斥，开一个自动关另一个（避免同一请求被记两遍）。数据入库时标记来源，各看各的。

### MCP 服务

56 个工具，让 AI 客户端直接读日志、分析抓包、断点改包、注入 Frida 脚本。

**工具按需暴露**：没抓包时只暴露 24 个（省 58% 上下文），开抓包后自动补齐。

---

## 前置要求

- Android 7.0+
- Root（Magisk / KernelSU / APatch）
- LSPosed（Zygisk 版）

---

## 安装

1. 下载 `AlgorithmAidePro-x.x.x.apk`
2. 安装后在 **LSPosed** 中启用模块并勾选作用域
3. 重启目标应用

代理抓包需把 CA 证书装进系统信任区：抓包页 → 帮助 → 一键安装（需 root）。

---

## 构建

```bash
# 需要 JDK 17+、Android SDK 36
./gradlew :app:assembleRelease
```

> Windows 中文用户名环境下需设置 `GRADLE_USER_HOME` 到纯英文路径，否则 Gradle worker 会报 `ClassNotFoundException`。

签名配置放 `key.properties`（已在 `.gitignore` 中，不提交）：

```properties
storeFile=keystore/xxx.jks
storePassword=***
keyAlias=***
keyPassword=***
```

没有该文件时构建会跳过签名，方便别人 clone 后直接跑 debug。

---

## 使用

**日志**：应用页启用 Hook → 启动目标应用 → 日志页看该应用的全部记录。

**抓包**：抓包页 → 点「Hook 抓包」或「代理抓包」→ 进入请求列表 → 点某条看详情。

**MCP**：设置页开启服务后连接：

```
本地：http://127.0.0.1:19191/mcp
局域网：http://<手机IP>:19191/mcp
```

PC 端可 `adb forward tcp:19191 tcp:19191` 后连 `127.0.0.1:19191`。

`GET /health` 查状态：

```json
{"status":"ok","tools":24,"toolsTotal":56,"port":19191,"lan":true}
```

---

## 已知限制

| 项 | 说明 |
|---|---|
| Hook 抓包无原始报文 | Java 层拿不到字节流，`network_packets` 会回退成 HTTP 序列 |
| 部分应用证书校验 | 需开「SSL 证书信任」开关（有风险，某些应用会闪退） |
| Frida | 依赖 `frida-inject`；arm64 已内置（零联网），其他 ABI 需下载 |

---

## 项目结构

```
app/src/main/java/com/sfzsgybb/ai/
├── hook/         Xposed 模块（注入目标进程）
│   ├── engine/   日志上报、调用栈采集
│   └── modules/  各 Hook 模块（HTTP / 加密 / 存储 / 系统）
├── ipc/          跨进程日志通道（AIDL + Bundle）
├── capture/      会话管理与查询
├── mitm/         MITM 解密引擎（SSLEngine + 动态签发）
├── vpn/          VpnService + 用户态 TCP/IP 栈
├── mcp/          MCP 服务与工具
├── data/         Room 数据库
└── ui/           Compose 界面
```

---

## 实现要点

几个容易踩的坑，记在这里免得后面的人再犯：

- **证书格式**：系统 CA 目录只认 PEM，写成 DER 会导致所有 HTTPS 握手失败
- **VPN 白名单**：`addAllowedApplication` 与 `addDisallowedApplication` 不能混用
- **CursorWindow**：列表查询不能带 BLOB，用投影 + `substr` 切片
- **MCP 读 body**：按字节读；`BufferedReader` 按字符读会在中文时卡死
- **frida-inject**：默认 qjs runtime 没有 Java bridge，必须 `-R v8`

---

## 许可证

仅供学习交流。使用者自行承担因使用本工具产生的一切后果。
