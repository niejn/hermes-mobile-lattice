# PoC3 载荷实测结果（2026-11-13）

## 结论：arm64-bionic 载荷在 Android 上完全可跑，分发策略定为 APK 内嵌 + app 私有目录解压

## 实测数据（API 35 x86_64 模拟器 + ndk_translation 转译层，最恶劣环境）

| 项 | 值 |
|---|---|
| CLI 冒烟 | `hermes --version` OK（Hermes Agent v0.22.0+canary, Python 3.14.6, 8s 含转译冷启） |
| serve | `HERMES_DASHBOARD_READY port=9120`，`/api/status` 200（auth_required=false，HERMES_DESKTOP=1 豁免生效） |
| serve 稳态内存 | PSS 358 MB / RSS 395 MB |
| 其中转译税 | ≈200 MB（Other mmap 125 + Unknown 140 含 ndk_translation 代码缓存/JIT） |
| arm64 真机预估 | ≈150–200 MB RSS（Native Heap 63 + .so 67 + 运行时杂项） |
| 载荷体积 | data.tar.xz 173 MB（解压 762 MB，27,694 文件） |
| 解压 | toybox tar 无 xz → 推未压缩 tar（2.7s @265MB/s）或首次启动内置解压 |

## 就位方式（已在模拟器验证）

- Termux 载荷 python ELF 的 DT_RUNPATH 硬编码 `/data/data/com.termux/files/usr/lib`
- 本实验：root 后把树 mv 到该路径 + runtime-libs 集中 + libpython symlink → 直跑成功
- **狼腾 app 生产路径（无需 root、无需 com.termux）**：真机 arm64 原生执行，无转译 runner，`LD_LIBRARY_PATH`（launcher 自带 export）直接有效——把载荷解压到 app 私有目录 `filesDir/hermes/`，设 `PREFIX` 指向它，`bin/hermes` 原样启动。模拟器里 LD_LIBRARY_PATH 失败仅因 x86_64 转译 runner 被同路径 arm64 库污染，真机无此问题
- 载荷放 `/data/data/<pkg>/files/`（app 自有），不受 secondary storage 执行限制

## 目标设备（用户拍板 2026-11-13）

小米最新 arm64 真机（HyperOS / Android 15+），不考虑华为等广谱适配。arm64-v8a 原生命中。

## 分发形态建议

1. APK 内嵌 data.tar.xz（173MB，AAB 内 asset）或首启下载
2. 首启解压到 filesDir/hermes/ → 复刻 launcher 环境（PREFIX/LD_LIBRARY_PATH/HERMES_HOME）→ `app_process` 或 `exec` 起 serve（127.0.0.1:9120）
3. app UI 连 http://127.0.0.1:9120（PoC2b 已证 TOKEN_ONLY + HERMES_DESKTOP=1 豁免链路）
4. 内存预算：真机 serve ≈150–200MB + app UI ≈300MB，8GB 设备从容

## 单设备全栈闭环（2026-11-13 补记，最终里程碑）

品牌 APK（com.langteng.agent.dev，label=狼腾智能体）→ **端内 serve**（127.0.0.1:9120，
ndk_translation 转译运行）→ 模型端点（config.yaml provider=custom → 宿主 mock
10.0.2.2:9911，生产替换狼腾域名即可）→ 工具调用在端内真实执行：

- 会话创建：`Session created`（app 直连端内 serve，TOKEN 校验通过）
- `toolgo` 消息 → mock 返回 tool_calls → **端内执行 `read_file`**（结果
  `File not found: /poc2/tool_test.txt`，工具真实跑了）→ 结果回传 → 终答渲染
- mock 侧三轮 POST：tools=21（主轮）→ 含 tool 角色回传 → 终答
- 证据截图：`C:/lt/dl/full8.png`（完整线程含工具卡）；`C:/lt/dl/brand3.png`（dock 图标）

拓扑要点：端内视角宿主 = 10.0.2.2（qemu 用户网络栈别名）；真机形态无此别名，
config.yaml 的 base_url 直写狼腾正式端点。engine 日志：
`OpenAI client created provider=custom base_url=… model=langteng-mock-1`。
