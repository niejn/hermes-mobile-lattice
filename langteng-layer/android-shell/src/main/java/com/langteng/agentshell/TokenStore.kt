package com.langteng.agentshell

import android.content.Context
import java.io.File
import java.security.SecureRandom
/**
 * serve 面板 token: 首次安装生成一次, 存 EncryptedSharedPreferences 之前先用私有文件
 * (PoC 阶段; 生产换 androidx.security.crypto)。UI 侧从同文件读, 经 ws ?token= 注入。
 */
object TokenStore {
    private const val FILE = "engine_token"

    fun getOrCreate(context: Context): String =
        File(context.filesDir, FILE).let { f ->
            if (f.exists()) {
                f.readText().trim()
            } else {
                val bytes = ByteArray(24)
                SecureRandom().nextBytes(bytes)
                val token = "lt-" + bytes.joinToString("") { "%02x".format(it) }
                f.writeText(token)
                token
            }
        }
}
