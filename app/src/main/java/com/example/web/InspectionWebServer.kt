package com.example.web

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import com.example.data.InspectionRecord
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.NetworkInterface

class InspectionWebServer(
    private val port: Int = 8080,
    private val onReviewCallback: (id: Long, status: String, note: String) -> Unit,
    private val onCommandCallback: (command: String, value: String) -> Unit,
    private val getLatestStateProvider: () -> WebServerState
) : NanoHTTPD(port) {

    data class WebServerState(
        val isDetecting: Boolean,
        val fps: Int,
        val latencyMs: Long,
        val modelName: String,
        val delegateName: String,
        val confThreshold: Float,
        val currentDefects: List<InspectionRecord>,
        val historyRecords: List<InspectionRecord>,
        val currentFrameBytes: ByteArray?,
        val droneSignal: String = "强 (-62dBm)"
    )

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            uri == "/" -> serveDashboardHtml()
            uri == "/api/status" -> serveStatusJson()
            uri == "/api/records" -> serveRecordsJson()
            uri == "/api/stream" -> serveFrameJpeg()
            uri == "/api/review" && method == Method.POST -> handleReviewPost(session)
            uri == "/api/command" && method == Method.POST -> handleCommandPost(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
        }
    }

    private fun serveStatusJson(): Response {
        val state = getLatestStateProvider()
        val json = JSONObject().apply {
            put("status", "running")
            put("isDetecting", state.isDetecting)
            put("fps", state.fps)
            put("latencyMs", state.latencyMs)
            put("modelName", state.modelName)
            put("delegateName", state.delegateName)
            put("confThreshold", state.confThreshold)
            put("droneSignal", state.droneSignal)

            val defectArray = JSONArray()
            state.currentDefects.forEach { record ->
                defectArray.put(recordToJson(record))
            }
            put("currentDefects", defectArray)
        }
        val response = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json.toString())
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun serveRecordsJson(): Response {
        val state = getLatestStateProvider()
        val array = JSONArray()
        state.historyRecords.forEach { record ->
            array.put(recordToJson(record))
        }
        val response = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", array.toString())
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    private fun serveFrameJpeg(): Response {
        val state = getLatestStateProvider()
        val bytes = state.currentFrameBytes
        return if (bytes != null && bytes.isNotEmpty()) {
            val response = newFixedLengthResponse(Response.Status.OK, "image/jpeg", ByteArrayInputStream(bytes), bytes.size.toLong())
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            response
        } else {
            newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "")
        }
    }

    private fun handleReviewPost(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
            val postData = files["postData"] ?: ""
            val json = JSONObject(postData)
            val id = json.getLong("id")
            val status = json.getString("status") // 已确认, 误报, 待复核
            val note = json.optString("note", "")

            onReviewCallback(id, status, note)

            val resJson = JSONObject().apply {
                put("success", true)
                put("message", "缺陷复核操作成功")
            }
            val response = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", resJson.toString())
            response.addHeader("Access-Control-Allow-Origin", "*")
            return response
        } catch (e: Exception) {
            val resJson = JSONObject().apply {
                put("success", false)
                put("message", e.localizedMessage ?: "解析错误")
            }
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json; charset=utf-8", resJson.toString())
        }
    }

    private fun handleCommandPost(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        try {
            session.parseBody(files)
            val postData = files["postData"] ?: ""
            val json = JSONObject(postData)
            val command = json.getString("command") // trigger_alarm, set_threshold, snapshot
            val value = json.optString("value", "")

            onCommandCallback(command, value)

            val resJson = JSONObject().apply {
                put("success", true)
                put("message", "远程指挥指令已下发至边缘终端")
            }
            val response = newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", resJson.toString())
            response.addHeader("Access-Control-Allow-Origin", "*")
            return response
        } catch (e: Exception) {
            val resJson = JSONObject().apply {
                put("success", false)
                put("message", e.localizedMessage ?: "解析错误")
            }
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json; charset=utf-8", resJson.toString())
        }
    }

    private fun recordToJson(record: InspectionRecord): JSONObject {
        return JSONObject().apply {
            put("id", record.id)
            put("defectType", record.defectType)
            
            put("confidence", (record.confidence * 100).toInt())
            put("location", record.location)
            put("reviewStatus", record.reviewStatus)
            put("reviewNote", record.reviewNote)
            put("timestamp", record.timestamp)
            put("bbox", JSONObject().apply {
                put("x", record.bboxX)
                put("y", record.bboxY)
                put("w", record.bboxW)
                put("h", record.bboxH)
            })
        }
    }

    private fun serveDashboardHtml(): Response {
        val html = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>配电缺陷智能识别 - 远程指挥与二次复核控制台</title>
                <style>
                    :root {
                        --bg-dark: #0f172a;
                        --card-bg: #1e293b;
                        --accent-blue: #0284c7;
                        --accent-cyan: #38bdf8;
                        --danger-red: #ef4444;
                        --warning-orange: #f97316;
                        --text-light: #f8fafc;
                        --text-muted: #94a3b8;
                        --border-color: #334155;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", sans-serif;
                        background-color: var(--bg-dark);
                        color: var(--text-light);
                        display: flex;
                        flex-direction: column;
                        min-height: 100vh;
                    }
                    header {
                        background-color: #0b1120;
                        padding: 1rem 2rem;
                        border-bottom: 1px solid var(--border-color);
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .logo-area { display: flex; align-items: center; gap: 0.75rem; }
                    .logo-icon { width: 32px; height: 32px; background: var(--accent-blue); border-radius: 6px; display: grid; place-items: center; font-weight: bold; }
                    h1 { font-size: 1.25rem; font-weight: 600; color: #ffffff; }
                    .status-pill {
                        display: inline-flex; align-items: center; gap: 0.5rem;
                        background: rgba(16, 185, 129, 0.15); color: #10b981;
                        padding: 0.25rem 0.75rem; border-radius: 999px; font-size: 0.875rem; font-weight: 500;
                    }
                    .dot { width: 8px; height: 8px; background: #10b981; border-radius: 50%; }
                    
                    .main-container {
                        display: grid;
                        grid-template-columns: 2fr 1fr;
                        gap: 1.5rem;
                        padding: 1.5rem 2rem;
                        flex: 1;
                    }
                    @media (max-width: 1024px) {
                        .main-container { grid-template-columns: 1fr; }
                    }
                    .panel {
                        background: var(--card-bg);
                        border: 1px solid var(--border-color);
                        border-radius: 12px;
                        padding: 1.25rem;
                        display: flex;
                        flex-direction: column;
                        gap: 1rem;
                    }
                    .panel-title {
                        font-size: 1.1rem;
                        font-weight: 600;
                        color: var(--accent-cyan);
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .video-container {
                        position: relative;
                        background: #000;
                        border-radius: 8px;
                        overflow: hidden;
                        aspect-ratio: 16/9;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                    }
                    #videoFeed {
                        width: 100%;
                        height: 100%;
                        object-fit: contain;
                    }
                    .video-overlay {
                        position: absolute;
                        top: 12px; left: 12px; right: 12px;
                        display: flex;
                        justify-content: space-between;
                        pointer-events: none;
                    }
                    .hud-badge {
                        background: rgba(15, 23, 42, 0.75);
                        backdrop-filter: blur(8px);
                        padding: 0.35rem 0.75rem;
                        border-radius: 6px;
                        font-size: 0.8rem;
                        border: 1px solid rgba(255,255,255,0.1);
                    }
                    .telemetry-grid {
                        display: grid;
                        grid-template-columns: repeat(4, 1fr);
                        gap: 0.75rem;
                    }
                    .stat-box {
                        background: #0f172a;
                        padding: 0.75rem;
                        border-radius: 8px;
                        border: 1px solid var(--border-color);
                    }
                    .stat-label { font-size: 0.75rem; color: var(--text-muted); }
                    .stat-val { font-size: 1.1rem; font-weight: 700; color: #fff; margin-top: 4px; }
                    
                    /* Defect Table */
                    .defect-list {
                        display: flex;
                        flex-direction: column;
                        gap: 0.75rem;
                        max-height: 520px;
                        overflow-y: auto;
                    }
                    .defect-card {
                        background: #0f172a;
                        border: 1px solid var(--border-color);
                        border-left: 4px solid var(--accent-blue);
                        border-radius: 8px;
                        padding: 0.85rem;
                        display: flex;
                        flex-direction: column;
                        gap: 0.5rem;
                    }
                    .defect-card.紧急缺陷 { border-left-color: var(--danger-red); }
                    .defect-card.重大缺陷 { border-left-color: var(--warning-orange); }
                    .defect-header { display: flex; justify-content: space-between; align-items: center; }
                    .defect-name { font-weight: 600; font-size: 0.95rem; }
                    .badge {
                        padding: 2px 8px; border-radius: 4px; font-size: 0.75rem; font-weight: bold;
                    }
                    .badge.紧急缺陷 { background: rgba(239, 68, 68, 0.2); color: var(--danger-red); }
                    .badge.重大缺陷 { background: rgba(249, 115, 22, 0.2); color: var(--warning-orange); }
                    .badge.一般缺陷 { background: rgba(234, 179, 8, 0.2); color: #eab308; }
                    .defect-info { font-size: 0.8rem; color: var(--text-muted); line-height: 1.4; }
                    
                    .action-btns { display: flex; gap: 0.5rem; margin-top: 4px; }
                    .btn {
                        padding: 0.35rem 0.75rem; border-radius: 6px; font-size: 0.8rem; font-weight: 500;
                        border: none; cursor: pointer; transition: all 0.2s;
                    }
                    .btn-confirm { background: #10b981; color: white; }
                    .btn-confirm:hover { background: #059669; }
                    .btn-reject { background: #64748b; color: white; }
                    .btn-reject:hover { background: #475569; }
                    .btn-alarm { background: var(--danger-red); color: white; width: 100%; padding: 0.75rem; font-size: 0.95rem; margin-top: 0.5rem; }
                    .btn-alarm:hover { background: #dc2626; }

                    /* Review modal style */
                    .review-note { background: #1e293b; border: 1px solid var(--border-color); color: white; padding: 4px 8px; border-radius: 4px; font-size: 0.8rem; width: 100%; }
                </style>
            </head>
            <body>
                <header>
                    <div class="logo-area">
                        <div class="logo-icon">⚡</div>
                        <div>
                            <h1>配电缺陷智能识别边缘控制台</h1>
                            <p style="font-size:0.75rem; color:var(--text-muted)">五天集训 · 无人机巡检 YOLO/TFLite 终端部署复核系统</p>
                        </div>
                    </div>
                    <div class="status-pill">
                        <div class="dot"></div>
                        <span>边缘终端在线 (端口 8080)</span>
                    </div>
                </header>

                <div class="main-container">
                    <!-- Left Column: Video Feed & Telemetry -->
                    <div style="display:flex; flex-direction:column; gap:1.5rem;">
                        <div class="panel">
                            <div class="panel-title">
                                <span>高清巡检实时监控流</span>
                                <span style="font-size:0.8rem; color:var(--text-muted)">YOLOv8 Edge Realtime Analytics</span>
                            </div>
                            <div class="video-container">
                                <img id="videoFeed" src="/api/stream" alt="巡检实时流" onerror="this.src='data:image/svg+xml;utf8,<svg xmlns=\'http://www.w3.org/2000/svg\' width=\'640\' height=\'360\'><rect width=\'100%\' height=\'100%\' fill=\'%230f172a\'/><text x=\'50%\' y=\'50%\' fill=\'%2394a3b8\' text-anchor=\'middle\'>无人机实时视频流加载中...</text></svg>'">
                                <div class="video-overlay">
                                    <div class="hud-badge">帧率: <span id="fpsVal">--</span> FPS</div>
                                    <div class="hud-badge">推理延迟: <span id="latencyVal">--</span> ms</div>
                                    <div class="hud-badge">算法模型: <span id="modelVal">--</span></div>
                                </div>
                            </div>
                            
                            <div class="telemetry-grid">
                                <div class="stat-box">
                                    <div class="stat-label">图传信号质量</div>
                                    <div class="stat-val" id="sigVal">--</div>
                                </div>
                                <div class="stat-box">
                                    <div class="stat-label">置信度过滤阈值</div>
                                    <div class="stat-val" id="confVal">--</div>
                                </div>
                            </div>
                        </div>

                        <!-- Remote Control Command Panel -->
                        <div class="panel">
                            <div class="panel-title">远程指挥与应急响应控制</div>
                            <div style="display:grid; grid-template-columns: 1fr 1fr; gap:1rem;">
                                <button class="btn btn-alarm" onclick="sendRemoteCommand('trigger_alarm', 'ON')">🚨 远程触发终端现场声光告警</button>
                                <button class="btn" style="background:var(--accent-blue); color:white;" onclick="sendRemoteCommand('snapshot', 'NOW')">📸 抓拍高清巡检云端存档</button>
                            </div>
                        </div>
                    </div>

                    <!-- Right Column: Defect Feed & Secondary Review -->
                    <div class="panel">
                        <div class="panel-title">
                            <span>实时缺陷识别与二次复核</span>
                            <span id="defectCountBadge" style="font-size:0.8rem; background:rgba(2,132,199,0.2); color:var(--accent-cyan); padding:2px 8px; border-radius:12px;">0 条</span>
                        </div>
                        
                        <div class="defect-list" id="defectListContainer">
                            <div style="text-align:center; padding:2rem; color:var(--text-muted)">正在接收终端缺陷数据...</div>
                        </div>
                    </div>
                </div>

                <script>
                    let lastState = null;

                    async function fetchStatus() {
                        try {
                            const res = await fetch('/api/status');
                            const data = await res.json();
                            lastState = data;

                            document.getElementById('fpsVal').innerText = data.fps || '--';
                            document.getElementById('latencyVal').innerText = data.latencyMs || '--';
                            document.getElementById('modelVal').innerText = data.modelName || '--';
                            document.getElementById('sigVal').innerText = data.droneSignal || '良好';
                            document.getElementById('confVal').innerText = ((data.confThreshold || 0.5) * 100).toFixed(0) + '%';

                            // Refresh frame image
                            document.getElementById('videoFeed').src = '/api/stream?t=' + Date.now();

                            renderDefects(data.currentDefects || []);
                        } catch (e) {
                            console.error('Fetch status error', e);
                        }
                    }

                    function renderDefects(defects) {
                        const container = document.getElementById('defectListContainer');
                        document.getElementById('defectCountBadge').innerText = defects.length + ' 条';

                        if (defects.length === 0) {
                            container.innerHTML = '<div style="text-align:center; padding:2rem; color:var(--text-muted)">当前视场内未发现明显设备缺陷</div>';
                            return;
                        }

                        let html = '';
                        defects.forEach(function(d) {
                            var statusColor = d.reviewStatus === '已确认' ? '#10b981' : (d.reviewStatus === '误报' ? '#ef4444' : '#eab308');
                            var noteHtml = d.reviewNote ? '<div>备注: ' + d.reviewNote + '</div>' : '';
                            html += '<div class="defect-card">' +
                                '<div class="defect-header">' +
                                    '<span class="defect-name">' + d.defectType + '</span>' +
                                    '<span class="badge"> 置信度: ' + d.confidence + '%</span>' +
                                '</div>' +
                                '<div class="defect-info">' +
                                    '<div>位置: ' + d.location + '</div>' +
                                    '<div>状态: <strong style="color:' + statusColor + '">' + d.reviewStatus + '</strong></div>' +
                                    noteHtml +
                                '</div>' +
                                '<div style="margin-top:4px;">' +
                                    '<input type="text" id="note_' + d.id + '" class="review-note" placeholder="填写二次复核意见..." value="' + (d.reviewNote || '') + '">' +
                                '</div>' +
                                '<div class="action-btns">' +
                                    '<button class="btn btn-confirm" onclick="submitReview(' + d.id + ', \'已确认\')">✅ 确认缺陷</button>' +
                                    '<button class="btn btn-reject" onclick="submitReview(' + d.id + ', \'误报\')">❌ 标记误报</button>' +
                                '</div>' +
                            '</div>';
                        });
                        container.innerHTML = html;
                    }

                    async function submitReview(id, status) {
                        const note = document.getElementById('note_' + id)?.value || '';
                        try {
                            const res = await fetch('/api/review', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ id: id, status: status, note: note })
                            });
                            const result = await res.json();
                            alert(result.message || '复核成功');
                            fetchStatus();
                        } catch (e) {
                            alert('操作失败: ' + e.message);
                        }
                    }

                    async function sendRemoteCommand(cmd, val) {
                        try {
                            const res = await fetch('/api/command', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ command: cmd, value: val })
                            });
                            const result = await res.json();
                            alert(result.message || '指令发送成功');
                        } catch (e) {
                            alert('指令发送失败: ' + e.message);
                        }
                    }

                    setInterval(fetchStatus, 1500);
                    fetchStatus();
                </script>
            </body>
            </html>
        """.trimIndent()

        val response = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    companion object {
        fun getLocalIpAddress(context: Context): String {
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ipInt = wifiManager.connectionInfo.ipAddress
                if (ipInt != 0) {
                    return Formatter.formatIpAddress(ipInt)
                }
            } catch (e: Exception) {
                // Fallback to NetworkInterface
            }

            try {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val intf = interfaces.nextElement()
                    val enumIpAddr = intf.inetAddresses
                    while (enumIpAddr.hasMoreElements()) {
                        val inetAddress = enumIpAddr.nextElement()
                        if (!inetAddress.isLoopbackAddress && inetAddress.hostAddress != null) {
                            val ip = inetAddress.hostAddress
                            if (ip != null && ip.indexOf(':') < 0) {
                                return ip
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return "127.0.0.1"
        }
    }
}
