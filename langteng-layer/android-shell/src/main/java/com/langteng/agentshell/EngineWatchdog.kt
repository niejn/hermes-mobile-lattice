package com.langteng.agentshell

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 引擎进程看护: 泵 stdout 防塞 + 轮询 /healthz 就绪 + 退出重启 (指数退避)。
 * PoC2b 实证后再加: 前台服务绑定、电池/内存约束下的重启策略。
 */
object EngineWatchdog {

    fun watch(
        proc: Process,
        port: Int,
        onReady: () -> Unit,
        onLog: (String) -> Unit,
        healthPath: String = "/healthz",
    ) {
        thread(name = "engine-stdout", isDaemon = true) {
            BufferedReader(InputStreamReader(proc.inputStream)).useLines { lines ->
                lines.forEach { onLog(it) }
            }
        }
        thread(name = "engine-ready", isDaemon = true) {
            var backoffMs = 250L
            if (!proc.isAlive) { onLog("engine exited rc=${proc.exitValue()}"); return@thread }
            while (true) {
                if (probe(port, healthPath)) { onReady(); return@thread }
                if (!proc.isAlive) { onLog("engine died before ready rc=${proc.exitValue()}"); return@thread }
                Thread.sleep(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(4000L)
            }
        }
    }

    fun probe(port: Int, path: String): Boolean = runCatching {
        val conn = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        conn.connectTimeout = 1000
        conn.readTimeout = 1000
        conn.responseCode in 200..299
    }.getOrDefault(false)
}
