/*
 * subcap.js — 擷取「燒在影片畫面裡」的字幕（硬字幕）
 *
 * 用法：在已登入、正在看課程的分頁執行本檔全部內容（開發者工具 Console，
 * 或由 Claude in Chrome 代為執行）。它會：
 *   1. 找到頁面上的 <video>，靜音後用 2 倍速從目前位置播到結束。
 *   2. 每 200 毫秒截一次畫面下方的字幕帶，只在「字幕換句」時留下一張。
 *   3. 播完後把截下的字幕帶拼成長圖（每張 30 句，附時間），自動下載 PNG。
 * 不下載影片本身，只存字幕帶截圖。
 *
 * 可調整：在執行前設定 window.SUBCAP_OPTS = { rate: 1.5, top: 0.8, ... }
 * 隨時手動結束並下載：subcap.finish()
 */
(() => {
  const o = Object.assign({
    rate: 2,          // 播放倍速
    top: 0.78,        // 字幕帶上緣（佔畫面高度比例）
    bottom: 1.0,      // 字幕帶下緣
    interval: 200,    // 取樣間隔（毫秒）
    bright: 200,      // 字幕文字亮度門檻（白字）
    changeRatio: 0.25,// 文字像素變化超過此比例視為換句
    minInk: 0.004,    // 文字像素少於此比例視為「沒有字幕」
    perSheet: 30,     // 每張長圖幾句
    // 檔名只用英數（中文檔名在部分瀏覽器會變成「download」）
    name: ((location.pathname + location.hash).replace(/[^A-Za-z0-9]+/g, '-').replace(/^-|-$/g, '') || 'video')
          + '_' + new Date().toTimeString().slice(0, 8).replace(/:/g, ''),
  }, window.SUBCAP_OPTS || {});

  const video = [...document.querySelectorAll('video')].sort((a, b) => b.videoWidth - a.videoWidth)[0];
  if (!video) { console.warn('[subcap] 找不到 video'); return; }

  const W = video.videoWidth, H = video.videoHeight;
  const sy = Math.round(H * o.top), sh = Math.round(H * (o.bottom - o.top));
  const cv = document.createElement('canvas'); cv.width = W; cv.height = sh;
  const cx = cv.getContext('2d', { willReadFrequently: true });

  const shots = [];          // { t, url }
  let lastMask = null, pending = null, timer = null, done = false;

  const fmt = s => { s = Math.floor(s); const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60;
    return (h ? h + ':' : '') + String(m).padStart(2, '0') + ':' + String(x).padStart(2, '0'); };

  // 二值化遮罩：亮且接近無彩色的像素 = 字
  function mask() {
    cx.drawImage(video, 0, sy, W, sh, 0, 0, W, sh);
    const d = cx.getImageData(0, 0, W, sh).data, m = new Uint8Array(W * sh);
    let ink = 0;
    for (let i = 0, p = 0; i < d.length; i += 4, p++) {
      const r = d[i], g = d[i + 1], b = d[i + 2];
      if (r > o.bright && g > o.bright && b > o.bright) { m[p] = 1; ink++; }
    }
    return { m, ink: ink / m.length };
  }
  function diff(a, b) {
    let x = 0, n = 0;
    for (let i = 0; i < a.length; i++) { if (a[i] || b[i]) { n++; if (a[i] !== b[i]) x++; } }
    return n ? x / n : 0;
  }

  function tick() {
    if (video.paused || video.seeking) return;
    const { m, ink } = mask();
    if (ink < o.minInk) { lastMask = null; pending = null; return; }        // 沒字幕
    if (lastMask && diff(m, lastMask) < o.changeRatio) { pending = null; return; } // 同一句
    // 新的一句：連續兩次取樣都一樣才算穩定（避開淡入淡出）
    if (pending && diff(m, pending.m) < o.changeRatio) {
      shots.push({ t: pending.t, url: pending.url });
      lastMask = m; pending = null;
      if (shots.length % 50 === 0) console.log(`[subcap] 已擷取 ${shots.length} 句 @ ${fmt(video.currentTime)}`);
    } else {
      pending = { m, t: video.currentTime, url: cv.toDataURL('image/png') };
    }
  }

  async function finish() {
    if (done) return; done = true;
    clearInterval(timer); video.pause();
    console.log(`[subcap] 完成，共 ${shots.length} 句，產生長圖中…`);
    const lab = 70, sheets = Math.ceil(shots.length / o.perSheet);
    for (let s = 0; s < sheets; s++) {
      const part = shots.slice(s * o.perSheet, (s + 1) * o.perSheet);
      const out = document.createElement('canvas'); out.width = W + lab; out.height = part.length * (sh + 2);
      const ox = out.getContext('2d'); ox.fillStyle = '#000'; ox.fillRect(0, 0, out.width, out.height);
      for (let i = 0; i < part.length; i++) {
        const img = new Image(); img.src = part[i].url; await img.decode();
        const y = i * (sh + 2);
        ox.drawImage(img, lab, y);
        ox.fillStyle = '#ff0'; ox.font = '16px monospace'; ox.fillText(fmt(part[i].t), 4, y + sh / 2 + 6);
        ox.fillStyle = '#444'; ox.fillRect(0, y + sh, out.width, 2);
      }
      const a = document.createElement('a');
      const blob = await new Promise(r => out.toBlob(r, 'image/png'));
      a.href = URL.createObjectURL(blob);
      a.download = `${o.name}_subs_${String(s + 1).padStart(2, '0')}of${String(sheets).padStart(2, '0')}.png`;
      document.body.appendChild(a); a.click(); a.remove();
      await new Promise(r => setTimeout(r, 400));
      URL.revokeObjectURL(a.href);
    }
    console.log(`[subcap] 已下載 ${sheets} 張長圖`);
  }

  window.subcap = { finish, shots, opts: o };
  video.muted = true; video.playbackRate = o.rate;
  video.addEventListener('ended', finish, { once: true });
  timer = setInterval(tick, o.interval);
  video.play();
  console.log(`[subcap] 開始：${W}x${H}，字幕帶 y=${sy}~${sy + sh}，${o.rate} 倍速`);
})();
