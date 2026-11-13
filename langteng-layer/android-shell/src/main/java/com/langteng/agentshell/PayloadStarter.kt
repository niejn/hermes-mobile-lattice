package com.langteng.agentshell

import android.content.Context
import java.io.File

/**
 * 狼腾嵌入壳: 启动预置在 app 私有目录的 hermes-agent 载荷并 exec serve。
 *
 * PoC 实证依据 (2026-11-13):
 *  - 载荷自包含: Termux deb 布局, python ELF 入口 + 相对 venv/site-packages (PoC3)
 *  - HERMES_HOME 必须指向 App 私有目录, 实现配置隔离 (PoC2a)
 *  - HERMES_DASHBOARD_SESSION_TOKEN 钉死认证: 首次安装随机生成, loopback only
 *  - HERMES_DESKTOP=1 豁免 serve 面板 gate (PoC2b, 上游 Desktop 官方连线方式)
 *  - 引擎只走 SSE 流式; 壳层无需代理任何模型流量
 *
 * 载荷就位 (两形态):
 *  - PoC/模拟器: adb root 预置整棵 Termux usr 树到 filesDir/hermes (本文件按此寻径)
 *  - 真机生产: data.tar.xz 首启解压到 filesDir/hermes; 可执行 ELF 走 nativeLibraryDir
 *    (targetSdk 29+ W^X 限制: app_data_file 不可 exec, 必须 jniLibs 打包入口二进制)
 */
object PayloadStarter {

    private const val SERVE_PORT = 9120
    private const val HEALTH_PATH = "/api/status" // PoC3 实证: 200 + auth_required=false

    // hermes3.sh 等价 bootstrap: argv[0]=hermes + site-packages 注入 + CLI main
    private const val BOOTSTRAP =
        "import os, site, sys; sys.argv[0]='hermes'; " +
            "site.addsitedir(os.environ['HERMES_SITE']); " +
            "from hermes_cli.main import main; sys.exit(main())"

    @Volatile private var started = false

    fun payloadRoot(context: Context): File = File(context.filesDir, "hermes")
    fun engineHome(context: Context): File = File(context.filesDir, "hermes-home")

    fun start(
        context: Context,
        onReady: (port: Int, token: String) -> Unit,
        onLog: (String) -> Unit,
    ) {
        if (started) return
        synchronized(this) {
            if (started) return

            val appContext = context.applicationContext
            val root = payloadRoot(appContext)
            val hermes = File(root, "lib/hermes-agent")
            val python = File(hermes, "tools/python/data/data/com.termux/files/usr/bin/python3.14")

            // 已有引擎存活 (前次启动的进程未死): 直接报就绪, 不重复拉起
            if (EngineWatchdog.probe(SERVE_PORT, HEALTH_PATH)) {
                started = true
                onLog("engine already listening :$SERVE_PORT — reuse")
                onReady(SERVE_PORT, TokenStore.getOrCreate(appContext))
                return
            }
            if (!python.canExecute()) {
                onLog("payload missing at $root — engine stays off (plain upstream mode)")
                return
            }

            val home = engineHome(appContext).apply { mkdirs() }
            ensureConfig(home, onLog)
            val token = TokenStore.getOrCreate(appContext)
            val site = File(hermes, "venv/lib/python3.14/site-packages")

            val pb = ProcessBuilder(
                python.absolutePath, "-P", "-c", BOOTSTRAP,
                "serve", "--port", SERVE_PORT.toString(),
            ).apply {
                environment().apply {
                    put("PYTHONPATH", "${File(hermes, "app")}:$site")
                    put("HERMES_SITE", site.absolutePath)
                    put("PYTHONPYCACHEPREFIX", File(appContext.cacheDir, "pycache").apply { mkdirs() }.absolutePath)
                    put("PREFIX", root.absolutePath) // Termux 前缀 (载荷 DT_RUNPATH 死路径由下行覆盖)
                    put("LD_LIBRARY_PATH", File(root, "lib").absolutePath)
                    put("HERMES_PYTHON_SRC_ROOT", File(hermes, "app").absolutePath)
                    put("HERMES_PYTHON", python.absolutePath)
                    put("HERMES_NODE", File(hermes, "tools/node/data/data/com.termux/files/usr/bin/node").absolutePath)
                    put("HERMES_RUNTIME_DIR", File(hermes, "tools").absolutePath)
                    put("HOME", home.absolutePath)
                    put("HERMES_HOME", home.absolutePath)
                    put("HERMES_DESKTOP", "1") // PoC2b: 桌面豁免, loopback serve 不设 gate
                    put("HERMES_DASHBOARD_SESSION_TOKEN", token)
                    put("HERMES_DASHBOARD_PUBLIC_URL", "http://127.0.0.1:$SERVE_PORT")
                    put("TMPDIR", File(appContext.cacheDir, "tmp").apply { mkdirs() }.absolutePath)
                }
                redirectErrorStream(true)
            }

            started = true
            val proc = try {
                pb.start()
            } catch (e: Exception) {
                started = false
                onLog("engine exec failed: $e")
                return
            }
            onLog("engine spawned pid=${proc.pid()} home=${home.absolutePath}")
            EngineWatchdog.watch(
                proc, port = SERVE_PORT,
                onReady = { onReady(SERVE_PORT, token) },
                onLog = onLog, healthPath = HEALTH_PATH,
            )
        }
    }

    /** 首启写入默认端点配置 (狼腾端点模板); 已有配置不动。 */
    private fun ensureConfig(home: File, onLog: (String) -> Unit) {
        val cfg = File(home, "config.yaml")
        if (cfg.exists()) return
        cfg.writeText(
            """
            # 狼腾默认端点 (壳首启生成)
            model:
              provider: custom
              base_url: http://10.0.2.2:9911/v1   # PoC: qemu NAT 回宿主; 真机换狼腾正式端点
              api_key: lt-poc-test-key
              model: langteng-mock-1
            onboarding:
              seen:
                profile_build_offered: true
            """.trimIndent() + "\n",
        )
        onLog("wrote default config.yaml")
    }
}
