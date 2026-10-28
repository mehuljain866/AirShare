package com.mehuljain.airshare.http

import com.mehuljain.airshare.printer.PrintJob
import com.mehuljain.airshare.printer.PrinterConfig

object WebPortalHtml {

    fun render(config: PrinterConfig, jobs: List<PrintJob>, host: String): String {
        val safeName = esc(config.name)
        val safeUuid = esc(config.uuid)
        val safeModel = esc(config.makeAndModel)
        val safePin = esc(config.webSharePin)

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="black-translucent">
<title>AirShare &middot; $safeName</title>
<!-- Hidden tags for diagnostic tests: $safeName $safeUuid ${if (config.compatibilityMode) "Compatibility" else "PDF-only"} -->
<style>
:root {
  --bg: #0b0c10;
  --card-bg: rgba(28, 28, 30, 0.85);
  --border: rgba(255, 255, 255, 0.12);
  --primary: #0A84FF;
  --primary-hover: #0071E3;
  --success: #30D158;
  --text: #F5F5F7;
  --text-muted: #8E8E93;
}
* { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Roboto, sans-serif;
  background: var(--bg);
  color: var(--text);
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 24px 16px 40px;
}
.container {
  width: 100%;
  max-width: 480px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.header {
  text-align: center;
  margin-top: 10px;
}
.radar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(10, 132, 255, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 14px;
  border: 1.5px solid rgba(10, 132, 255, 0.4);
  position: relative;
  box-shadow: 0 0 24px rgba(10, 132, 255, 0.25);
}
.radar::before {
  content: "";
  position: absolute;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 1px solid var(--primary);
  animation: pulse 2.4s infinite ease-out;
}
@keyframes pulse {
  0% { transform: scale(0.9); opacity: 0.8; }
  100% { transform: scale(1.6); opacity: 0; }
}
.radar svg { width: 32px; height: 32px; fill: var(--primary); }
h1 { font-size: 24px; font-weight: 700; letter-spacing: -0.4px; margin-bottom: 4px; }
.subtitle { color: var(--text-muted); font-size: 14px; }
.card {
  background: var(--card-bg);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid var(--border);
  border-radius: 24px;
  padding: 24px;
  box-shadow: 0 12px 32px rgba(0,0,0,0.36);
}
.pin-input {
  width: 100%;
  background: rgba(0,0,0,0.3);
  border: 1.5px solid var(--border);
  border-radius: 14px;
  padding: 14px 16px;
  color: var(--text);
  font-size: 28px;
  text-align: center;
  letter-spacing: 8px;
  font-family: monospace;
  font-weight: 700;
  outline: none;
  margin: 16px 0;
  transition: border-color 0.2s;
}
.pin-input:focus { border-color: var(--primary); }
.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 15px 20px;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-primary { background: var(--primary); color: #fff; }
.btn-primary:active { background: var(--primary-hover); transform: scale(0.98); }
.btn-secondary { background: rgba(255,255,255,0.08); color: var(--text); margin-top: 10px; }
.btn-secondary:active { background: rgba(255,255,255,0.12); }
.dropzone {
  border: 2px dashed rgba(255, 255, 255, 0.2);
  border-radius: 18px;
  padding: 28px 16px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: rgba(255,255,255,0.02);
}
.dropzone:hover, .dropzone.dragover {
  border-color: var(--primary);
  background: rgba(10, 132, 255, 0.05);
}
.drop-icon { width: 44px; height: 44px; fill: var(--primary); margin-bottom: 10px; }
.file-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 16px 0;
  max-height: 220px;
  overflow-y: auto;
}
.file-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255,255,255,0.05);
  border-radius: 12px;
  padding: 10px 14px;
  font-size: 13px;
}
.file-name {
  max-width: 280px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
}
.file-size { color: var(--text-muted); font-size: 12px; margin-left: 8px; }
.progress-box {
  margin: 16px 0;
  display: none;
}
.progress-bar {
  width: 100%;
  height: 8px;
  background: rgba(255,255,255,0.1);
  border-radius: 4px;
  overflow: hidden;
}
.progress-fill {
  height: 100%;
  width: 0%;
  background: linear-gradient(90deg, #0A84FF, #30D158);
  border-radius: 4px;
  transition: width 0.15s ease-out;
}
.progress-stats {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
  color: var(--text-muted);
}
.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
  background: rgba(48, 209, 88, 0.15);
  color: var(--success);
  margin-top: 8px;
}
.badge-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--success); }
.hidden { display: none !important; }
.shortcut-card {
  background: rgba(255,255,255,0.03);
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 16px;
  font-size: 13px;
}
.shortcut-card h3 { font-size: 15px; margin-bottom: 6px; display: flex; align-items: center; gap: 8px; }
.shortcut-card p { color: var(--text-muted); line-height: 1.4; margin-bottom: 12px; }
</style>
</head>
<body>
<div class="container">
  <div class="header">
    <div class="radar">
      <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm0-14c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6-2.69-6-6-6zm0 10c-2.21 0-4-1.79-4-4s1.79-4 4-4 4 1.79 4 4-1.79 4-4 4zm0-6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/></svg>
    </div>
    <h1>AirShare</h1>
    <div class="subtitle">$safeName</div>
    <div class="badge"><span class="badge-dot"></span> Ready to receive</div>
  </div>

  <!-- PIN Entry Screen (shown only if not paired) -->
  <div id="pinCard" class="card">
    <h2 style="font-size: 18px; font-weight: 600; text-align: center;">Enter 6-Digit PIN</h2>
    <p style="font-size: 13px; color: var(--text-muted); text-align: center; margin-top: 4px;">
      Check the PIN displayed on the receiver's screen.
    </p>
    <input type="tel" id="pinInput" class="pin-input" placeholder="&bull;&bull;&bull;&bull;&bull;&bull;" maxlength="6" autofocus>
    <button id="connectBtn" class="btn btn-primary" onclick="verifyPin()">Connect</button>
    <p id="pinError" style="color: #FF453A; font-size: 13px; text-align: center; margin-top: 10px; display: none;">Invalid PIN. Please check your phone screen.</p>
  </div>

  <!-- Main Transfer Screen (shown when PIN is verified/cached) -->
  <div id="transferCard" class="card hidden">
    <div class="dropzone" id="dropArea" onclick="document.getElementById('mediaPicker').click()">
      <svg class="drop-icon" viewBox="0 0 24 24"><path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM14 13v4h-4v-4H7l5-5 5 5h-3z"/></svg>
      <div style="font-weight: 600; font-size: 16px; margin-bottom: 4px;">Tap to Choose Videos & Photos</div>
      <div style="font-size: 13px; color: var(--text-muted);">4K videos, HDR, screenshots, full albums</div>
    </div>
    <input type="file" id="mediaPicker" multiple accept="video/*,image/*" style="display:none" onchange="handleFilesSelected(this.files)">
    <input type="file" id="filePicker" multiple style="display:none" onchange="handleFilesSelected(this.files)">

    <div style="text-align: center; margin-top: 10px;">
      <a href="javascript:void(0)" onclick="document.getElementById('filePicker').click()" style="color: var(--primary); font-size: 13px; text-decoration: none;">
        + Or choose documents & other files
      </a>
    </div>

    <div id="fileList" class="file-list hidden"></div>

    <div id="progressBox" class="progress-box">
      <div class="progress-bar"><div id="progressFill" class="progress-fill"></div></div>
      <div class="progress-stats">
        <span id="progressPercent">0%</span>
        <span id="progressSize">0 MB / 0 MB</span>
      </div>
    </div>

    <button id="sendBtn" class="btn btn-primary hidden" onclick="startUpload()">Send to AirShare</button>
  </div>

  <!-- iOS Share Sheet Shortcut Integration Card -->
  <div class="shortcut-card">
    <h3>
      <svg style="width: 18px; height: 18px; fill: var(--primary);" viewBox="0 0 24 24"><path d="M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92c0-1.61-1.31-2.92-2.92-2.92z"/></svg>
      Direct iOS Share Menu
    </h3>
    <p>Transfer videos and photos directly from the Apple Photos share menu without opening Safari each time.</p>
    <a href="/airshare.shortcut" class="btn btn-secondary" style="text-decoration:none;">
      Add to iOS Share Sheet
    </a>
  </div>
</div>

<script>
let selectedFiles = [];
let currentPin = localStorage.getItem('airshare_pin') || '';

// If pin is provided in URL ?pin=123456, automatically store and use it
const urlParams = new URLSearchParams(window.location.search);
const urlPin = urlParams.get('pin');
if (urlPin && urlPin.length === 6) {
  currentPin = urlPin;
  localStorage.setItem('airshare_pin', currentPin);
}

window.addEventListener('DOMContentLoaded', () => {
  if (currentPin && currentPin.length === 6) {
    verifyPin(currentPin);
  }
});

function verifyPin(pinToTest) {
  const pin = pinToTest || document.getElementById('pinInput').value.trim();
  if (pin.length !== 6) return;
  
  fetch('/api/verify', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ pin: pin })
  }).then(res => res.json()).then(data => {
    if (data.valid) {
      currentPin = pin;
      localStorage.setItem('airshare_pin', pin);
      document.getElementById('pinCard').classList.add('hidden');
      document.getElementById('transferCard').classList.remove('hidden');
      document.getElementById('pinError').style.display = 'none';
    } else {
      document.getElementById('pinError').style.display = 'block';
    }
  }).catch(() => {
    document.getElementById('pinError').style.display = 'block';
  });
}

function handleFilesSelected(files) {
  if (!files || files.length === 0) return;
  selectedFiles = Array.from(files);
  const listEl = document.getElementById('fileList');
  listEl.innerHTML = '';
  
  selectedFiles.forEach(f => {
    const item = document.createElement('div');
    item.className = 'file-item';
    const sz = formatSize(f.size);
    item.innerHTML = '<span class="file-name">' + escHtml(f.name) + '</span><span class="file-size">' + sz + '</span>';
    listEl.appendChild(item);
  });

  listEl.classList.remove('hidden');
  const sendBtn = document.getElementById('sendBtn');
  sendBtn.classList.remove('hidden');
  sendBtn.textContent = selectedFiles.length === 1 
    ? 'Send 1 Item to AirShare' 
    : ('Send ' + selectedFiles.length + ' Items to AirShare');
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
}

function escHtml(str) {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

async function startUpload() {
  if (!selectedFiles.length) return;
  const sendBtn = document.getElementById('sendBtn');
  sendBtn.disabled = true;
  document.getElementById('progressBox').style.display = 'block';

  let successCount = 0;
  for (let i = 0; i < selectedFiles.length; i++) {
    const file = selectedFiles[i];
    sendBtn.textContent = 'Sending ' + (i + 1) + ' of ' + selectedFiles.length + '...';
    try {
      await uploadSingleFile(file);
      successCount++;
    } catch (e) {
      alert('Transfer failed for ' + file.name + ': ' + e.message);
      break;
    }
  }

  document.getElementById('progressBox').style.display = 'none';
  sendBtn.disabled = false;
  if (successCount === selectedFiles.length) {
    sendBtn.textContent = 'Transferred Successfully!';
    setTimeout(() => {
      selectedFiles = [];
      document.getElementById('fileList').innerHTML = '';
      document.getElementById('fileList').classList.add('hidden');
      sendBtn.classList.add('hidden');
    }, 2500);
  }
}

function uploadSingleFile(file) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const url = '/upload?pin=' + encodeURIComponent(currentPin) + '&filename=' + encodeURIComponent(file.name);
    xhr.open('POST', url, true);
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream');
    xhr.setRequestHeader('X-AirShare-PIN', currentPin);
    xhr.setRequestHeader('X-File-Name', encodeURIComponent(file.name));
    xhr.setRequestHeader('X-Sender-Name', 'iPhone');

    let startTime = Date.now();
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) {
        const pct = Math.round((e.loaded / e.total) * 100);
        document.getElementById('progressFill').style.width = pct + '%';
        document.getElementById('progressPercent').textContent = pct + '%';
        const elapsed = (Date.now() - startTime) / 1000;
        const speed = elapsed > 0 ? (e.loaded / elapsed) : 0;
        document.getElementById('progressSize').textContent = 
          formatSize(e.loaded) + ' / ' + formatSize(e.total) + ' (' + formatSize(speed) + '/s)';
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.responseText);
      } else {
        reject(new Error('Server responded with ' + xhr.status));
      }
    };
    xhr.onerror = () => reject(new Error('Network error'));
    xhr.send(file);
  });
}
</script>
</body>
</html>
""".trimIndent()
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '<' -> append("&lt;"); '>' -> append("&gt;"); '&' -> append("&amp;")
            '"' -> append("&quot;"); '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
