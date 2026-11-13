# 狼腾配置与胶水层 (langteng-layer)

狼腾对上游（hermes-agent / hermes-mobile）的全部改动落点。黄金法则：**本目录之外零改动**，
上游仓只做 `git merge upstream`，保证升级列车永续。

## 布局

```
langteng-layer/
  android-shell/    # APK 壳: 载荷释放 + nativeLibraryDir exec + HERMES_HOME/HERMES_DASHBOARD_SESSION_TOKEN 注入
  config/           # 狼腾端点 custom provider 配置模板 (直连狼腾 / Gemini 中转, 可切换)
  build/            # 载荷 staging 脚本 (包上游 scripts/termux, 不改上游)
  branding/         # 品牌资源: 图标/命名/默认主题
  docs/             # 集成笔记: 协议词汇表, 端点要求(SSE 流式必须), 升级列车流程
```

## 关键决策记录

- 引擎: hermes-agent (嵌 APK, loopback serve, token 钉死认证) — PoC1/2a 证据 2026-11-13
- UI: hermes-mobile fork (连 127.0.0.1, WS JSON-RPC)
- 端点硬性要求: OpenAI 兼容 /v1/chat/completions, **SSE 流式必须** (含 tool_calls 分片),
  /v1/models 提供模型清单
- 配置隔离: HERMES_HOME 必须指向 APK 私有目录, 杜绝读取任何既有配置

## 品牌化构建 (2026-11-13 落地)

```sh
sh build/build-branded.sh            # 默认 HEAD; 可传 upstream ref
```

流程: `git archive` 导出 hermes-mobile 副本 (上游仓永 pristine) →
`branding/patch.py` 应用品牌 (applicationId→com.langteng.agent、
app_name 全域 23 处→狼腾智能体、DEFAULT_BASE_URL→http://127.0.0.1:9120/、
图标矢量 overlay) → gradle assembleDebug。

已验证: BUILD SUCCESSFUL; aapt2 badging `package: com.langteng.agent.dev` +
`application-label:'狼腾智能体'` (全 locale); 模拟器装机 dock 图标深蓝底白 LT。
注意: debug 构建入口被 LeakCanary LAUNCHER 抢占, 显式
`am start -n .../com.m57.hermescontrol.MainActivity` 进入主界面。

## 升级列车流程 (演练结论)

hermes-mobile 本地 = upstream 纯镜像 (langteng/main 与 upstream/main 同 tip,
零自有 commit)。升级:

```sh
git fetch upstream && git merge --ff-only upstream/main   # 恒零冲突
sh build/build-branded.sh && 装机冒烟                      # patch 锚点兜底
```

品牌漂移由 patch.py 锚点检测兜底: 锚点回溯测试 HEAD / HEAD~20 / HEAD~50
(2026-11-02 → 11-12, 三时间点) 全部命中, rc=0。锚点漂移时显式 `[DRIFT]`
报错退出, 不会静默错改。

## 仓位置 (2026-11-13 迁移)

本层现随 **hermes-mobile-lattice** 单仓演进 (上游 hermes-mobile 树 + `langteng-layer/` 狼腾层共存):
上游代码仍保持 pristine, 狼腾改动只落本目录。构建脚本已适配单仓形态
(`build/build-branded.sh` 自动探测: 兄弟 `../hermes-mobile` 或仓根)。

PoC 证据脚本归档: `poc/poc2/` (mock 端点/SDK 复现/ws 探针),
`poc/poc3/` (载荷实测结论 + 端内启动脚本 hermes3.sh)。
