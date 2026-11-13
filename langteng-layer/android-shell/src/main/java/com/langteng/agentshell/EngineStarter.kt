package com.langteng.agentshell

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * 进程启动钩子: ContentProvider 在 Application.onCreate 前被系统实例化,
 * 借此把引擎拉起接进上游 app 而不改任何上游 Activity/Application 代码。
 * manifest 注入由 langteng-layer branding/patch.py 完成 (SHELL_HOOKS 步骤)。
 */
class EngineStarter : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context?.applicationContext ?: return false
        PayloadStarter.start(
            ctx,
            onReady = { port, token -> Log.i(TAG, "engine READY port=$port token=$token") },
            onLog = { Log.i(TAG, it) },
        )
        return true
    }

    override fun getType(uri: Uri): String? = null
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, v: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int = 0

    private companion object { const val TAG = "lt-engine" }
}
