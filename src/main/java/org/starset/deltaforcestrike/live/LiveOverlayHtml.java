package org.starset.deltaforcestrike.live;

/**
 * OBS 导播覆盖层：透明底，小面积。
 * 顶部比分条；左下 T（左对齐）/ 右下 CT（右对齐）；左上雷达 + 编号。
 */
public final class LiveOverlayHtml {

    private LiveOverlayHtml() {}

    public static String page() {
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>DFS Live Overlay</title>
<style>
  :root {
    --t: #e74c3c;
    --ct: #4aa3ff;
    --text: #f5f7fb;
    --muted: #aab3c5;
    --panel: rgba(8, 10, 16, 0.78);
    --line: rgba(255,255,255,0.10);
    --green: #3ddc84;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; }
  html, body {
    width: 100%; height: 100%;
    background: transparent !important;
    overflow: hidden;
    color: var(--text);
    font-family: "Segoe UI", "Microsoft YaHei", system-ui, sans-serif;
    user-select: none;
  }
  body.debug-bg { background: #1a1f2b !important; }

  /* ========== 顶部中央比分条 ========== */
  #scoreboard {
    position: absolute;
    top: 14px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    align-items: stretch;
    height: 52px;
    background: var(--panel);
    border: 1px solid var(--line);
    border-radius: 10px;
    box-shadow: 0 6px 24px rgba(0,0,0,.45);
    overflow: hidden;
    z-index: 20;
  }
  .sb-team {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 0 14px;
    min-width: 84px;
  }
  .sb-team.t { background: linear-gradient(90deg, rgba(231,76,60,.22), transparent); }
  .sb-team.ct {
    background: linear-gradient(270deg, rgba(74,163,255,.22), transparent);
    flex-direction: row-reverse;
  }
  .sb-tag { font-size: 13px; font-weight: 800; letter-spacing: .12em; }
  .sb-team.t .sb-tag { color: var(--t); }
  .sb-team.ct .sb-tag { color: var(--ct); }
  .sb-score {
    font-size: 30px; font-weight: 800; line-height: 1;
    font-variant-numeric: tabular-nums;
    min-width: 1.2em; text-align: center;
  }
  .sb-team.t .sb-score { color: var(--t); }
  .sb-team.ct .sb-score { color: var(--ct); }
  .sb-center {
    display: flex; flex-direction: column;
    align-items: center; justify-content: center;
    padding: 4px 20px;
    border-left: 1px solid var(--line);
    border-right: 1px solid var(--line);
    min-width: 132px;
  }
  .sb-timer {
    font-size: 24px; font-weight: 800;
    font-variant-numeric: tabular-nums;
    color: #f1c40f; line-height: 1.1;
  }
  .sb-timer.bomb { color: #ff5a5a; animation: pulse 1s infinite; }
  .sb-sub {
    font-size: 11px; color: var(--muted); margin-top: 1px;
    letter-spacing: .04em; white-space: nowrap;
  }
  @keyframes pulse { 50% { opacity: .55; } }

  /* ========== 左下 T（左对齐） / 右下 CT（右对齐） ========== */
  .strip {
    position: absolute;
    bottom: 14px;
    width: min(360px, 30vw);
    display: flex;
    flex-direction: column;
    gap: 4px;
    z-index: 15;
  }
  .strip.t { left: 14px; align-items: stretch; }
  .strip.ct { right: 14px; align-items: stretch; }
  .strip-title {
    font-size: 10px; font-weight: 800; letter-spacing: .14em;
    opacity: .9; margin: 0 2px 2px;
  }
  .strip.t .strip-title { color: var(--t); text-align: left; }
  .strip.ct .strip-title { color: var(--ct); text-align: right; }

  /* 通用行：编号 | 内容 | 金钱 */
  .row {
    display: grid;
    gap: 2px 8px;
    width: 100%;
    padding: 5px 8px 6px;
    background: var(--panel);
    border: 1px solid var(--line);
    border-radius: 6px;
    font-size: 12px;
    align-items: center;
  }
  /* T：左对齐  [num] name ........ $ */
  .strip.t .row {
    grid-template-columns: 22px 1fr auto;
    text-align: left;
  }
  /* CT：右对齐  $ ........ name [num] */
  .strip.ct .row {
    grid-template-columns: auto 1fr 22px;
    text-align: right;
  }
  .row.dead { opacity: .4; filter: grayscale(.4); }

  .num {
    width: 20px; height: 20px;
    border-radius: 4px;
    display: flex; align-items: center; justify-content: center;
    font-size: 11px; font-weight: 800;
    font-variant-numeric: tabular-nums;
    flex-shrink: 0;
  }
  .strip.t .num { background: rgba(231,76,60,.25); color: #ffb4ad; border: 1px solid rgba(231,76,60,.45); }
  .strip.ct .num { background: rgba(74,163,255,.25); color: #b7d9ff; border: 1px solid rgba(74,163,255,.45); justify-self: end; }

  .row .name {
    font-weight: 700;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    min-width: 0;
  }
  .strip.t .name { text-align: left; }
  .strip.ct .name { text-align: right; }

  .row .money {
    color: var(--green); font-weight: 800;
    font-variant-numeric: tabular-nums; white-space: nowrap;
  }
  .strip.t .money { text-align: right; }
  .strip.ct .money { text-align: left; }

  .row .bar {
    grid-column: 1 / -1;
    height: 3px; border-radius: 99px;
    background: rgba(255,255,255,.08); overflow: hidden;
  }
  .strip.t .bar > i {
    display: block; height: 100%; width: 0%;
    background: linear-gradient(90deg, #e74c3c, #f1c40f 50%, #3ddc84);
    transition: width .2s linear;
    margin-left: 0; margin-right: auto;
  }
  .strip.ct .bar > i {
    display: block; height: 100%; width: 0%;
    background: linear-gradient(270deg, #4aa3ff, #f1c40f 50%, #3ddc84);
    transition: width .2s linear;
    margin-left: auto; margin-right: 0;
  }
  .row .meta {
    grid-column: 1 / -1;
    font-size: 10px; color: var(--muted);
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }
  .strip.t .meta { text-align: left; }
  .strip.ct .meta { text-align: right; }
  .row .meta b { color: #d5dce8; font-weight: 600; }

  /* ========== 右上：击杀滚动 ========== */
  #killfeed {
    position: absolute;
    top: 14px;
    right: 14px;
    width: min(320px, 28vw);
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    z-index: 18;
    pointer-events: none;
  }
  .kf {
    display: flex;
    align-items: center;
    gap: 6px;
    max-width: 100%;
    padding: 5px 10px;
    background: var(--panel);
    border: 1px solid var(--line);
    border-radius: 6px;
    font-size: 12px;
    font-weight: 700;
    box-shadow: 0 4px 14px rgba(0,0,0,.35);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    opacity: 1;
    transform: translateX(0);
    transition: opacity .45s ease, transform .45s ease;
  }
  /* 仅新建时播一次入场，之后保持静止 */
  .kf.enter {
    animation: kfIn .28s ease-out both;
  }
  .kf.out {
    opacity: 0 !important;
    transform: translateX(16px);
    pointer-events: none;
  }
  @keyframes kfIn {
    from { opacity: 0; transform: translateX(18px); }
    to { opacity: 1; transform: translateX(0); }
  }
  .kf .k { font-weight: 800; }
  .kf .k.t, .kf .v.t { color: var(--t); }
  .kf .k.ct, .kf .v.ct { color: var(--ct); }
  .kf .w {
    color: #f1c40f; font-weight: 700; font-size: 11px;
    max-width: 110px; overflow: hidden; text-overflow: ellipsis;
  }
  .kf .arrow { color: var(--muted); font-weight: 600; font-size: 11px; }
  .kf .v { font-weight: 800; }
  .kf.bomb .w { color: #ff8b4a; }
  .kf.world .w { color: #c0c8d8; }

  /* ========== 左上小雷达 ========== */
  #minimap {
    position: absolute;
    top: 14px;
    left: 14px;
    width: 168px;
    height: 168px;
    background: rgba(8,10,16,.72);
    border: 1px solid var(--line);
    border-radius: 8px;
    box-shadow: 0 4px 16px rgba(0,0,0,.35);
    z-index: 12;
    overflow: hidden;
  }
  #minimap.hidden { display: none; }
  #radar {
    position: absolute; inset: 0;
    background: radial-gradient(circle at 50% 50%, rgba(255,255,255,.04), transparent 65%), #0e131c;
  }
  .site {
    position: absolute; border: 1.5px solid rgba(231,76,60,.7);
    border-radius: 50%; transform: translate(-50%,-50%);
  }
  .site span {
    position: absolute; left: 50%; top: -14px; transform: translateX(-50%);
    font-size: 9px; font-weight: 800; color: #ff9a9a;
  }
  .dot {
    position: absolute; width: 14px; height: 14px; border-radius: 50%;
    transform: translate(-50%,-50%);
    border: 1px solid rgba(0,0,0,.55);
    display: flex; align-items: center; justify-content: center;
    font-size: 9px; font-weight: 800; color: #fff;
    font-variant-numeric: tabular-nums;
    text-shadow: 0 0 2px #000;
  }
  .dot.t { background: var(--t); }
  .dot.ct { background: var(--ct); }
  .dot.dead { opacity: .35; }
  .bomb-dot {
    position: absolute; width: 9px; height: 9px;
    transform: translate(-50%,-50%);
    background: #f1c40f; border-radius: 2px;
    box-shadow: 0 0 8px #f1c40f;
    animation: pulse 1s infinite;
  }

  #err {
    position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%);
    display: none; padding: 12px 18px;
    background: rgba(0,0,0,.75); color: #ff8b8b;
    border-radius: 8px; font-weight: 700; font-size: 14px; z-index: 50;
  }
  #err.show { display: block; }
</style>
</head>
<body>
  <!-- 左上雷达 -->
  <div id="minimap"><div id="radar"></div></div>

  <!-- 右上击杀滚动 -->
  <div id="killfeed"></div>

  <!-- 顶部中央：T | 计时 | CT -->
  <div id="scoreboard">
    <div class="sb-team t">
      <span class="sb-tag">T</span>
      <span class="sb-score" id="scoreT">0</span>
    </div>
    <div class="sb-center">
      <div class="sb-timer" id="timer">0:00</div>
      <div class="sb-sub" id="sub">—</div>
    </div>
    <div class="sb-team ct">
      <span class="sb-tag">CT</span>
      <span class="sb-score" id="scoreCT">0</span>
    </div>
  </div>

  <div class="strip t">
    <div class="strip-title">T · ATTACK</div>
    <div id="rosterT"></div>
  </div>
  <div class="strip ct">
    <div class="strip-title">CT · DEFEND</div>
    <div id="rosterCT"></div>
  </div>

  <div id="err">未授权或接口不可用</div>

<script>
(function () {
  const params = new URLSearchParams(location.search);
  const token = params.get('token') || '';
  if (params.get('bg') === '1') document.body.classList.add('debug-bg');
  if (params.get('radar') === '0') document.getElementById('minimap').classList.add('hidden');

  const el = id => document.getElementById(id);
  const rosterT = el('rosterT');
  const rosterCT = el('rosterCT');
  const radar = el('radar');
  const killfeed = el('killfeed');

  let lastServerTime = 0;
  let lastSecondsLeft = 0;
  let lastBomb = false;
  /** 已插入 DOM 的击杀 id → 时间戳 */
  const killShown = new Map();
  const KF_HOLD_MS = 5000;
  const KF_FADE_MS = 450;
  const KF_MAX = 8;

  function fmtTime(sec) {
    sec = Math.max(0, Math.floor(sec));
    return Math.floor(sec / 60) + ':' + String(sec % 60).padStart(2, '0');
  }

  function phaseLabel(match) {
    if (!match || !match.active) return '等待对局';
    const ms = match.state || '', rs = match.roundState || '';
    if (ms === 'WAITING') return '队列';
    if (ms === 'COUNTDOWN') return '倒计时';
    if (ms === 'AGENT_SELECT') return '选干员';
    if (ms === 'ENDING') return '结束';
    if (rs === 'BUY') return '购买 · R' + (match.round || 0);
    if (rs === 'COMBAT') return '进攻 · R' + (match.round || 0);
    if (rs === 'BOMB_PLANTED') return '拆弹 · R' + (match.round || 0);
    if (rs === 'ROUND_END') return '结算 · R' + (match.round || 0);
    return ms;
  }

  function escapeHtml(s) {
    return String(s ?? '').replace(/[&<>"']/g, c => ({
      '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
    }[c]));
  }

  function sortByNumber(list) {
    return list.slice().sort((a, b) => (a.number|0) - (b.number|0));
  }

  /** T：左对齐  [n] name $   /  CT：右对齐  $ name [n] */
  function renderPlayer(p, team) {
    const w = p.weapons || {};
    const util = (w.utilities || []).slice(0, 2).join('/') || '—';
    const weapons = [w.melee, w.ranged, w.bomb].filter(Boolean).join(' · ') || '—';
    const num = (p.number|0) || '·';
    const div = document.createElement('div');
    div.className = 'row' + (p.alive ? '' : ' dead');
    div.dataset.uuid = p.uuid || '';
    const numHtml = '<div class="num">' + escapeHtml(String(num)) + '</div>';
    const nameHtml = '<div class="name">' + escapeHtml(p.name) + '</div>';
    const moneyHtml = '<div class="money">$' + (p.money|0) + '</div>';
    const barHtml = '<div class="bar"><i style="width:' + (p.healthPercent|0) + '%"></i></div>';
    const metaHtml = '<div class="meta"><b>' + escapeHtml(weapons) + '</b> · ' + escapeHtml(util)
      + (p.operatorId ? ' · ' + escapeHtml(p.operatorId) : '')
      + ' · ' + (p.kills|0) + '/' + (p.deaths|0) + '</div>';

    if (team === 'CT') {
      // $ | name | num
      div.innerHTML = moneyHtml + nameHtml + numHtml + barHtml + metaHtml;
    } else {
      // num | name | $
      div.innerHTML = numHtml + nameHtml + moneyHtml + barHtml + metaHtml;
    }
    return div;
  }

  function worldToRadar(map, x, z) {
    const w = radar.clientWidth || 168, h = radar.clientHeight || 168;
    const nx = (x - map.minX) / Math.max(1e-6, map.maxX - map.minX);
    const nz = (z - map.minZ) / Math.max(1e-6, map.maxZ - map.minZ);
    return { left: nx * w, top: nz * h };
  }

  function renderRadar(data) {
    if (el('minimap').classList.contains('hidden')) return;
    const map = data.map || {};
    const match = data.match || {};
    const players = data.players || [];
    radar.innerHTML = '';
    (map.sites || []).forEach(site => {
      const pos = worldToRadar(map, site.x, site.z);
      const span = Math.max(map.maxX - map.minX, 1);
      const rPx = Math.max(6, (site.radius / span) * (radar.clientWidth || 168));
      const d = document.createElement('div');
      d.className = 'site';
      d.style.left = pos.left + 'px';
      d.style.top = pos.top + 'px';
      d.style.width = (rPx * 2) + 'px';
      d.style.height = (rPx * 2) + 'px';
      d.innerHTML = '<span>' + escapeHtml(String(site.id||'').toUpperCase()) + '</span>';
      radar.appendChild(d);
    });
    if (match.bombPlanted && match.bombPos) {
      const pos = worldToRadar(map, match.bombPos.x, match.bombPos.z);
      const b = document.createElement('div');
      b.className = 'bomb-dot';
      b.style.left = pos.left + 'px';
      b.style.top = pos.top + 'px';
      radar.appendChild(b);
    }
    players.forEach(p => {
      if (!p.position || (p.team !== 'T' && p.team !== 'CT')) return;
      const pos = worldToRadar(map, p.position.x, p.position.z);
      const d = document.createElement('div');
      d.className = 'dot ' + p.team.toLowerCase() + (p.alive ? '' : ' dead');
      d.style.left = pos.left + 'px';
      d.style.top = pos.top + 'px';
      d.title = '#' + (p.number|0) + ' ' + p.name;
      d.textContent = String((p.number|0) || '');
      radar.appendChild(d);
    });
  }

  function teamClass(t) {
    if (t === 'T') return 't';
    if (t === 'CT') return 'ct';
    return '';
  }

  function buildKillNode(k) {
    const type = k.type || 'player';
    const div = document.createElement('div');
    div.dataset.id = String(k.id);
    if (type === 'bomb') {
      div.className = 'kf bomb enter';
      div.innerHTML = '<span class="w">改造TNT</span>'
        + '<span class="arrow">·</span>'
        + '<span class="v ' + teamClass(k.victimTeam) + '">' + escapeHtml(k.victim) + '</span>';
    } else if (type === 'world') {
      div.className = 'kf world enter';
      div.innerHTML = '<span class="w">' + escapeHtml(k.weapon || '战损') + '</span>'
        + '<span class="arrow">·</span>'
        + '<span class="v ' + teamClass(k.victimTeam) + '">' + escapeHtml(k.victim) + '</span>';
    } else {
      div.className = 'kf player enter';
      div.innerHTML = '<span class="k ' + teamClass(k.killerTeam) + '">' + escapeHtml(k.killer) + '</span>'
        + '<span class="arrow">-</span>'
        + '<span class="w">' + escapeHtml(k.weapon || '?') + '</span>'
        + '<span class="arrow">→</span>'
        + '<span class="v ' + teamClass(k.victimTeam) + '">' + escapeHtml(k.victim) + '</span>';
    }
    // 入场动画只播一次
    div.addEventListener('animationend', () => div.classList.remove('enter'), { once: true });
    return div;
  }

  /**
   * 只追加新击杀，不整表重绘。
   * 静止 KF_HOLD_MS 后加 .out 淡出，再移除。
   */
  function updateKillFeed(kills) {
    const list = Array.isArray(kills) ? kills : [];
    // 服务端最新在前 → 倒序插入，使新条目 prepend
    const newestFirst = list.slice(0, KF_MAX);
    // 从旧到新遍历，prepend 后视觉上仍是新在上
    for (let i = newestFirst.length - 1; i >= 0; i--) {
      const k = newestFirst[i];
      const id = k.id;
      if (id == null || killShown.has(id)) continue;
      killShown.set(id, Date.now());
      const node = buildKillNode(k);
      killfeed.insertBefore(node, killfeed.firstChild);
      // 静止 5 秒 → 淡出 → 移除
      setTimeout(() => {
        node.classList.add('out');
        setTimeout(() => {
          if (node.parentNode) node.parentNode.removeChild(node);
          killShown.delete(id);
        }, KF_FADE_MS);
      }, KF_HOLD_MS);
    }
    // 限制条数：超出从底部（最旧）摘掉
    while (killfeed.children.length > KF_MAX) {
      const last = killfeed.lastElementChild;
      if (!last) break;
      const lid = last.dataset.id;
      if (lid) killShown.delete(isNaN(+lid) ? lid : +lid);
      last.remove();
    }
  }

  function render(data) {
    const match = data.match || {};
    el('scoreT').textContent = match.scoreT ?? 0;
    el('scoreCT').textContent = match.scoreCT ?? 0;

    lastServerTime = data.serverTime || Date.now();
    lastSecondsLeft = match.secondsLeft ?? 0;
    lastBomb = !!match.bombPlanted;
    tickTimerDisplay();

    const mapName = (data.map && data.map.name) ? data.map.name : '';
    el('sub').textContent = phaseLabel(match) + (lastBomb ? ' · C4' : '') + (mapName ? ' · ' + mapName : '');

    const players = data.players || [];
    updateRoster(rosterT, sortByNumber(players.filter(p => p.team === 'T')), 'T');
    updateRoster(rosterCT, sortByNumber(players.filter(p => p.team === 'CT')), 'CT');
    renderRadar(data);
    updateKillFeed(data.kills);
  }

  function updateRoster(container, list, team) {
    const existing = new Map();
    [...container.children].forEach(ch => {
      if (ch.dataset.uuid) existing.set(ch.dataset.uuid, ch);
    });
    const keep = new Set();
    list.forEach(p => {
      const id = p.uuid || p.name;
      keep.add(id);
      let node = existing.get(id);
      if (!node) {
        node = renderPlayer(p, team);
        container.appendChild(node);
      } else {
        node.classList.toggle('dead', !p.alive);
        const moneyEl = node.querySelector('.money');
        if (moneyEl) moneyEl.textContent = '$' + (p.money|0);
        const bar = node.querySelector('.bar > i');
        if (bar) bar.style.width = (p.healthPercent|0) + '%';
        const nameEl = node.querySelector('.name');
        if (nameEl) nameEl.textContent = p.name;
        const numEl = node.querySelector('.num');
        if (numEl) numEl.textContent = String((p.number|0) || '·');
        const w = p.weapons || {};
        const util = (w.utilities || []).slice(0, 2).join('/') || '—';
        const weapons = [w.melee, w.ranged, w.bomb].filter(Boolean).join(' · ') || '—';
        const meta = node.querySelector('.meta');
        if (meta) {
          meta.innerHTML = '<b>' + escapeHtml(weapons) + '</b> · ' + escapeHtml(util)
            + (p.operatorId ? ' · ' + escapeHtml(p.operatorId) : '')
            + ' · ' + (p.kills|0) + '/' + (p.deaths|0);
        }
      }
    });
    existing.forEach((node, id) => { if (!keep.has(id)) node.remove(); });
    list.forEach(p => {
      const id = p.uuid || p.name;
      const node = [...container.children].find(c => c.dataset.uuid === id);
      if (node) container.appendChild(node);
    });
  }

  function tickTimerDisplay() {
    if (!lastServerTime) return;
    const elapsed = (Date.now() - lastServerTime) / 1000;
    const sec = Math.max(0, lastSecondsLeft - elapsed);
    const timer = el('timer');
    timer.textContent = fmtTime(sec);
    timer.classList.toggle('bomb', lastBomb);
  }

  async function poll() {
    try {
      const url = '/api/live' + (token ? ('?token=' + encodeURIComponent(token)) : '');
      const res = await fetch(url, {
        cache: 'no-store',
        headers: token ? { 'Authorization': 'Bearer ' + token } : {}
      });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      el('err').classList.remove('show');
      render(data);
    } catch (e) {
      el('err').classList.add('show');
      el('err').textContent = 'Live 错误: ' + e.message;
    }
  }

  poll();
  setInterval(poll, 250);
  setInterval(tickTimerDisplay, 200);
})();
</script>
</body>
</html>
""";
    }
}
