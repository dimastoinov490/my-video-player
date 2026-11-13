(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const byId = (id) => document.getElementById(id);
  const ico = (n) => `<svg><use href="#i-${n}"/></svg>`;
  const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  /* ---------- данные сервера / демо-режим ---------- */
  const DEMO_DATA = {
    disk: { total: 18.4 * 1073741824, free: 17.55 * 1073741824 },
    version: '1.0',
    address: location.host || '192.168.1.64:8765',
    port: 8765,
    online: true,
    authRequired: false,
    folder: 'Все папки',
    features: { photos: true, folders: true },
    stats: { videos: 6, audio: 2, photos: 48, storage: '853.3 MB', storageBytes: 853.3 * 1048576 },
    files: [
      { id: 'v1', title: 'ВОЙНА НЕ ТОЛЬКО', name: '2026-11-06-122610901.mp4', size: '142.0 MB', dur: '20:06', isVideo: true, mime: 'video/mp4', src: 'media/v1.mp4', res: '720p', c: ['#9fb4b8', '#e8eef0'] },
      { id: 'v2', title: 'ВОЙНА НЕ ТОЛЬКО 2', name: '2026-11-06-122610902.mp4', size: '138.4 MB', dur: '20:06', isVideo: true, mime: 'video/mp4', src: 'media/v1.mp4', res: '720p', c: ['#a9b9a9', '#eef2ee'] },
      { id: 'v3', title: 'BLADE RACER', name: 'c670a1b5808fa73482b1.mp4', size: '310.2 MB', dur: '67:08', isVideo: true, mime: 'video/mp4', src: 'media/v2.mp4', res: '1080p', c: ['#2f6d5f', '#a9d6c5'] },
      { id: 'v4', title: 'НОЧНОЙ ГОРОД', name: 'ночной-город-таймлапс.mp4', size: '64.5 MB', dur: '03:42', isVideo: true, mime: 'video/mp4', src: 'media/v2.mp4', res: '1080p', c: ['#1b2a6b', '#6b8cff'] },
      { id: 'v5', title: 'МОРЕ', name: 'поездка-на-море.mp4', size: '88.0 MB', dur: '12:15', isVideo: true, mime: 'video/mp4', src: 'media/v1.mp4', res: '720p', c: ['#0a7fa8', '#9ee6ff'] },
      { id: 'v6', title: 'АРХИВ 2025', name: 'семейный-архив-2025.mp4', size: '104.2 MB', dur: '45:30', isVideo: true, mime: 'video/mp4', src: 'media/v2.mp4', res: '1080p', c: ['#8a5a2b', '#f0cf9a'] },
      { id: 'a1', title: 'Утренний плейлист — часть 1', name: 'morning_1.mp3', size: '3.0 MB', dur: '00:12', isVideo: false, mime: 'audio/mpeg', src: 'media/a1.mp3', art: 'Нексора' },
      { id: 'a2', title: 'Фоновая музыка для работы', name: 'work_bg.mp3', size: '3.0 MB', dur: '00:12', isVideo: false, mime: 'audio/mpeg', src: 'media/a2.mp3', art: 'Нексора' },
    ],
  };

  const DEMO_MODE = !window.NEXORA;
  const D = window.NEXORA || DEMO_DATA;
  D.features = D.features || {};
  const rawFiles = D.files || [];

  const palettes = [['#9fb4b8', '#e8eef0'], ['#2f6d5f', '#a9d6c5'], ['#1b2a6b', '#6b8cff'], ['#0a7fa8', '#9ee6ff'], ['#8a5a2b', '#f0cf9a'], ['#5e72eb', '#ff9190']];
  const extFmt = (name, def) => {
    const p = String(name || '').split('.');
    return p.length > 1 ? p.pop().toUpperCase() : def;
  };

  const videos = rawFiles.filter((f) => f.isVideo).map((f, i) => ({
    id: String(f.id),
    title: f.title || f.name || 'Видео',
    name: f.name || f.title || 'video.mp4',
    dur: f.dur || '—',
    size: f.size || '',
    fmt: extFmt(f.name, 'MP4'),
    res: f.res || f.size || 'HD',
    src: f.src || `/stream?id=${encodeURIComponent(f.id)}`,
    dl: f.src || `/download?id=${encodeURIComponent(f.id)}`,
    thumb: DEMO_MODE ? '' : `/thumb?id=${encodeURIComponent(f.id)}`,
    c: f.c || palettes[i % palettes.length],
  }));

  const audios = rawFiles.filter((f) => !f.isVideo && f.kind !== 'photo').map((f) => ({
    id: String(f.id),
    title: f.title || f.name || 'Аудио',
    name: f.name || f.title || 'audio.mp3',
    art: f.art || f.size || 'Аудиофайл',
    dur: f.dur || '—',
    size: f.size || '',
    src: f.src || `/stream?id=${encodeURIComponent(f.id)}`,
    dl: f.src || `/download?id=${encodeURIComponent(f.id)}`,
  }));

  let ME = D.me || { role: 'user', id: 'user', name: '' };
  if (DEMO_MODE) {
    const adm = /[?&]admin=1/.test(location.search);
    ME = { role: adm ? 'admin' : 'user', id: adm ? 'admin' : 'user', name: adm ? 'admin' : 'user' };
  }
  let isAdmin = ME.role === 'admin';
  let DISK = D.disk || null;

  const savedSigMode = (() => { try { return localStorage.getItem('nx_sigmode') || 'app'; } catch (_) { return 'app'; } })();
  const savedNoAnim = (() => { try { return localStorage.getItem('nx_noanim') === '1'; } catch (_) { return false; } })();

  const state = {
    view: 'home',
    sub: 'general',
    q: '',
    online: D.online !== false,
    sigMode: savedSigMode,
    pendingRegs: D.pendingRegs || 0,
    folderCount: DEMO_MODE ? 2 : null,
    settings: Object.assign({ autoplay: true, thumbs: true, lang: 'ru', anim: !savedNoAnim }, JSON.parse(localStorage.getItem('nx') || '{}')),
  };
  const save = () => {
    localStorage.setItem('nx', JSON.stringify(state.settings));
    try {
      localStorage.setItem('nx_noanim', state.settings.anim ? '0' : '1');
      localStorage.setItem('nx_sigmode', state.sigMode);
    } catch (_) {}
  };

  /* ---------- локализация RU / EN ---------- */
  const I18N = {
    ru: {
      subtitle: 'Твой медиа-сервер',
      search: 'Поиск по библиотеке',
      usedLabel: 'Занято',
      freeLabel: 'Свободно',
      nav: { home: 'Главная', video: 'Видео', audio: 'Аудио', photo: 'Фото', folders: 'Папки', settings: 'Настройки' },
    },
    en: {
      subtitle: 'Your media server',
      search: 'Search library',
      usedLabel: 'Used',
      freeLabel: 'Free',
      nav: { home: 'Home', video: 'Videos', audio: 'Audio', photo: 'Photos', folders: 'Folders', settings: 'Settings' },
    },
  };
  function applyLang() {
    const t = I18N[state.settings.lang] || I18N.ru;
    $('[data-i18n="subtitle"]').textContent = t.subtitle;
    $('[data-i18n="usedLabel"]').textContent = t.usedLabel;
    $('[data-i18n="freeLabel"]').textContent = t.freeLabel;
    $('#search').placeholder = t.search;
    $$('.nav[data-view]').forEach((b) => {
      const sp = $('span', b);
      if (sp && t.nav[b.dataset.view]) sp.textContent = t.nav[b.dataset.view];
    });
  }

  /* ---------- форматирование и утилиты ---------- */
  const two = (n) => (n < 10 ? '0' + n : '' + n);
  function fmtSize(n) {
    n = Number(n) || 0;
    if (n >= 1073741824) return (n / 1073741824).toFixed(1) + ' GB';
    if (n >= 1048576) return (n / 1048576).toFixed(1) + ' MB';
    if (n >= 1024) return Math.round(n / 1024) + ' KB';
    return n + ' B';
  }
  function fmtDT(ms) {
    if (!ms) return '—';
    const d = new Date(ms);
    return `${two(d.getDate())}.${two(d.getMonth() + 1)}.${d.getFullYear()} ${two(d.getHours())}:${two(d.getMinutes())}`;
  }
  function fmtDur(sec) {
    sec = Math.max(0, Math.floor(sec));
    if (sec < 60) return 'меньше минуты';
    const m = Math.floor(sec / 60);
    if (m < 60) return `${m} мин`;
    return `${Math.floor(m / 60)} ч ${m % 60} мин`;
  }
  function sizeToBytes(str) {
    const m = /([\d.]+)\s*(KB|MB|GB|TB)?/i.exec(str || '');
    if (!m) return 0;
    const u = (m[2] || 'MB').toUpperCase();
    return parseFloat(m[1]) * ({ KB: 1024, MB: 1048576, GB: 1073741824, TB: 1099511627776 }[u] || 1);
  }
  function totalMem() {
    if (D.stats && D.stats.storage) return D.stats.storage;
    let mb = 0;
    rawFiles.forEach((f) => {
      const n = parseFloat(f.size);
      if (!isNaN(n)) mb += /GB/i.test(f.size) ? n * 1024 : n;
    });
    return mb >= 1024 ? (mb / 1024).toFixed(1) + ' GB' : mb.toFixed(1) + ' MB';
  }
  function copyStr(text) {
    if (!text) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(() => toast('Адрес скопирован'), () => toast(text));
    } else {
      const ta = document.createElement('textarea');
      ta.value = text;
      document.body.appendChild(ta);
      ta.select();
      try { document.execCommand('copy'); toast('Адрес скопирован'); } catch (_) { toast(text); }
      ta.remove();
    }
  }
  function toast(t) {
    const el = byId('toast');
    el.textContent = t;
    el.classList.add('show');
    clearTimeout(toast.t);
    toast.t = setTimeout(() => el.classList.remove('show'), 2200);
  }

  /* ---------- сетевые запросы + обработка ошибок/блокировки ---------- */
  function opErr(e) {
    const c = e && e.code;
    const map = {
      admin_required: 'Нужны права администратора',
      busy: 'Сканирование уже идёт',
      default_password: 'Сначала смените стандартный пароль администратора (Настройки → Аккаунты)',
      bad_current: 'Неверный текущий пароль',
      login_taken: 'Такой логин уже используется',
      bad_setup_password: 'Неверный пароль создания администратора',
      rate_limited: (e && e.retryAfterSec) ? `Слишком часто. Повторите через ${e.retryAfterSec} с` : 'Слишком много попыток. Подождите несколько минут',
      exists: 'Такая учётная запись уже есть',
      weak_password: 'Пароль слишком короткий (от 4–6 символов)',
      bad_name: 'Логин: 3–32 символа, буквы, цифры, точка, дефис, подчёркивание',
      auth_required: 'Включите защиту паролем в приложении',
      exists_target: 'Файл с таким именем уже есть',
      bad_name_file: 'Недопустимое имя',
      no_access: 'Нет доступа к файлам. Разрешите приложению доступ ко всем файлам',
      bad_secret: 'Укажите секретный вопрос и ответ (минимум 3 символа)',
      not_allowed: 'Для этой учётной записи действие недоступно',
      bad_host: 'Разрешены только локальные IPv4-адреса (10.x, 172.16–31.x, 192.168.x)',
      too_large: 'Превышен допустимый размер тестовых данных',
    };
    return map[c] || 'Не удалось выполнить действие';
  }

  function lockCheck(j) {
    if (!j || !(j.error === 'time_limit' || j.error === 'schedule_blocked')) return false;
    byId('lkT').textContent = j.error === 'time_limit' ? 'Время на сервере закончилось' : 'Сейчас вход запрещён';
    byId('lkP').textContent = j.error === 'time_limit'
      ? 'Дневной лимит времени для этой учётной записи исчерпан. Доступ откроется завтра или когда администратор изменит ограничения.'
      : 'Администратор разрешил вход только в определённое время. Попробуйте позже.';
    const lk = byId('nxLock');
    if (lk && !lk.open) lk.showModal();
    return true;
  }

  function api(url) {
    if (DEMO_MODE) return demoApi(url);
    return fetch(url, { credentials: 'same-origin', cache: 'no-store' }).then((r) => {
      if (r.status === 401) { location.replace('/'); throw new Error('401'); }
      return r.json().then((j) => {
        if (!r.ok) {
          lockCheck(j);
          const e = new Error((j && j.error) || ('HTTP ' + r.status));
          e.code = j && j.error;
          throw e;
        }
        return j;
      });
    });
  }

  function post(url, obj) {
    if (DEMO_MODE) return demoPost(url, obj);
    return fetch(url, {
      method: 'POST',
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { 'Content-Type': 'application/json', 'X-Nexora': '1' },
      body: JSON.stringify(obj || {}),
    }).then((r) => {
      if (r.status === 401) { location.replace('/'); throw new Error('401'); }
      return r.json().then((j) => {
        if (!r.ok) {
          lockCheck(j);
          const e = new Error((j && j.error) || ('HTTP ' + r.status));
          e.code = j && j.error;
          throw e;
        }
        return j;
      });
    });
  }

  function demoApi(url) {
    return new Promise((res, rej) => setTimeout(() => {
      const n = Date.now();
      if (url.startsWith('/api/monitor')) {
        if (!isAdmin) { const er = new Error('admin_required'); er.code = 'admin_required'; return rej(er); }
        return res({
          users: [
            { login: 'admin', role: 'admin', device: 'Edge · Windows', ip: '192.168.1.40', state: 'watching', file: 'BLADE RACER.mp4', watchedSec: 425, posSec: 1240, durSec: 4028, speedBps: 3145728, downBps: 3145728, upBps: 0, bytes: 184549376, since: n - 65 * 60000 },
            { login: 'demo', role: 'user', device: 'Chrome · Android', ip: '192.168.1.23', state: 'downloading', file: 'семейный-архив-2025.mp4', watchedSec: 0, posSec: 0, durSec: 0, speedBps: 5452595, downBps: 5452595, upBps: 0, bytes: 64225280, since: n - 12 * 60000 },
            { login: 'petya', role: 'user', device: 'Safari · iPad', ip: '192.168.1.57', state: 'browsing', file: '', watchedSec: 90, posSec: 0, durSec: 0, speedBps: 18432, downBps: 18432, upBps: 0, bytes: 1254000, since: n - 4 * 60000 },
          ],
          totals: { downBps: 8616755, upBps: 0, active: 3, totalDownBytes: 984210432, totalUpBytes: 12582912 },
        });
      }
      if (url.startsWith('/api/server/info')) {
        if (!isAdmin) { const er = new Error('admin_required'); er.code = 'admin_required'; return rej(er); }
        return res({
          server: { version: D.version || '1.0', protocol: 'http', port: 8765, address: D.address || 'http://192.168.1.64:8765', uptimeSec: 5420, startedAt: n - 5420000, threads: 3, activeSessions: 3, devicesCount: 4, onlineDevicesCount: 2 },
          device: { manufacturer: 'Samsung', model: 'Galaxy S23', device: 'dm1q', androidVersion: '14', sdkInt: 34, abis: 'arm64-v8a, armeabi-v7a', vlcVersion: '3.6.0-eap13' },
          wifi: { ssid: 'Home_WiFi_5G', ip: '192.168.1.64', linkSpeedMbps: 866, frequencyMhz: 5180, rssi: -54, level: 4 },
          interfaces: [{ label: 'Wi-Fi (wlan0)', ip: '192.168.1.64' }, { label: 'Точка доступа (ap0)', ip: '192.168.43.1' }],
          storage: { total: 128 * 1073741824, free: 46.2 * 1073741824, used: 81.8 * 1073741824, mediaBytes: (D.stats && D.stats.storageBytes) || 894784853 },
          memory: { total: 8 * 1073741824, avail: 3.1 * 1073741824, used: 4.9 * 1073741824, lowMemory: false },
          battery: { level: 84, charging: true },
          traffic: { downBytes: 984210432, upBytes: 12582912, totalBytes: 996793344, downBps: 5242880, upBps: 131072, activeTransfers: 2 },
          ftp: { running: false, port: 2121, address: 'ftp://192.168.1.64:2121' },
        });
      }
      if (url.startsWith('/api/status')) {
        return res({
          ok: true, online: true, time: n,
          wifi: { level: 4, rssi: -54 },
          me: ME,
          disk: DEMO_DATA.disk,
          pendingRegs: isAdmin ? 1 : 0,
          version: D.version || '1.0',
          uptimeSec: 5420,
          protocol: 'http',
          port: 8765,
          devicesCount: 4,
          mediaBytes: (D.stats && D.stats.storageBytes) || 894784853,
        });
      }
      if (url.startsWith('/api/devices')) {
        return res({
          now: n,
          devices: [
            { id: 'a', name: 'Chrome · Android', kind: 'phone', ip: '192.168.1.23', account: 'demo', role: 'user', online: true, me: true, blocked: false, sessionStart: n - 12 * 60000, lastLogin: n - 12 * 60000, lastSeen: n },
            { id: 'b', name: 'Edge · Windows', kind: 'pc', ip: '192.168.1.40', account: 'admin', role: 'admin', online: true, me: false, blocked: false, sessionStart: n - 65 * 60000, lastLogin: n - 65 * 60000, lastSeen: n - 2000 },
            { id: 'c', name: 'Safari · iPad', kind: 'tablet', ip: '192.168.1.57', account: 'demo', role: 'user', online: false, me: false, blocked: true, sessionStart: n - 7200000, lastLogin: n - 9000000, lastSeen: n - 7000000 },
          ],
        });
      }
      if (url.startsWith('/api/net/load')) {
        return res({ activeTransfers: 2, downBps: 5242880, upBps: 131072, threads: 3, sessions: 3 });
      }
      if (url.startsWith('/api/net/check')) {
        if (!isAdmin) { const er = new Error('admin_required'); er.code = 'admin_required'; return rej(er); }
        const hm = url.match(/host=([^&]+)/);
        const host = hm ? decodeURIComponent(hm[1]).trim() : '';
        if (!/^(10\.(25[0-5]|2[0-4]\d|1?\d\d?)\.(25[0-5]|2[0-4]\d|1?\d\d?)\.(25[0-5]|2[0-4]\d|1?\d\d?)|172\.(1[6-9]|2\d|3[01])\.(25[0-5]|2[0-4]\d|1?\d\d?)\.(25[0-5]|2[0-4]\d|1?\d\d?)|192\.168\.(25[0-5]|2[0-4]\d|1?\d\d?)\.(25[0-5]|2[0-4]\d|1?\d\d?))$/.test(host)) {
          const er = new Error('bad_host'); er.code = 'bad_host'; return rej(er);
        }
        return res({ ok: true, host, reachable: true, rttMs: 4, openPorts: [80, 443] });
      }
      if (url.startsWith('/api/net')) {
        return res({
          protocol: 'http', ipMode: 'auto', ip: '192.168.1.64', portMode: 'auto', port: 8765, strict: false,
          ips: [{ label: 'Wi-Fi (wlan0)', ip: '192.168.1.64' }, { label: 'Точка доступа (ap0)', ip: '192.168.43.1' }],
          ports: [8765, 8080, 8888, 9000],
          cert: { fingerprint: 'AB:12:CD:34:EF:56', expires: n + 86400000 * 300 },
        });
      }
      if (url.startsWith('/api/lan/devices')) {
        return res({
          scanning: false, progress: 100, finished: n - 60000, subnet: '192.168.1.0/24',
          devices: [
            { ip: '192.168.1.1', hostname: 'Router', kind: 'router', isGateway: true, rtt: 2 },
            { ip: '192.168.1.64', hostname: 'Этот телефон (сервер)', kind: 'phone', isServer: true, rtt: 1 },
            { ip: '192.168.1.23', hostname: 'Pixel-7', kind: 'phone', isMe: true, connected: true, rtt: 6 },
            { ip: '192.168.1.40', hostname: 'DESKTOP-4K2', kind: 'pc', connected: true, rtt: 3 },
          ],
        });
      }
      if (url.startsWith('/api/mail/peers')) {
        return res({
          me: { id: 'a', name: 'Вы' }, pendingRegs: 1,
          peers: [
            { id: 'b', name: 'Edge · Windows', kind: 'pc', online: true, unread: 1, last: { t: n - 60000, kind: 'text', preview: 'Файл получил?' } },
            { id: 'c', name: 'Safari · iPad', kind: 'tablet', online: false, unread: 0, last: null },
          ],
        });
      }
      if (url.startsWith('/api/mail/messages')) {
        if (/after=[1-9]/.test(url)) return res({ messages: [] });
        return res({
          messages: [
            { id: 1, mine: false, kind: 'text', text: 'Привет! Файл получил?', t: n - 120000, read: true },
            { id: 2, mine: true, kind: 'text', text: 'Да, спасибо!', t: n - 90000, read: true },
          ],
        });
      }
      if (url.startsWith('/api/registrations')) {
        return res({ requests: [{ id: 'r1', login: 'petya', t: n - 3600000, ip: '192.168.1.77', name: 'Chrome · Android' }] });
      }
      if (url.startsWith('/api/accounts')) {
        return res({
          me: ME, registrationOpen: true, pendingRegs: 1,
          accounts: [
            { id: 'admin', name: 'admin', role: 'admin', passwordDefault: true, online: ME.id === 'admin', sessions: 1, usedToday: 0, limitMin: 0, remainingSec: null, schedule: null, lastLogin: n - 600000 },
            { id: 'user', name: 'user', role: 'user', passwordDefault: false, deletable: false, hasSecretQuestion: true, secretQuestion: 'Город рождения', online: true, sessions: 1, usedToday: 4320, limitMin: 120, remainingSec: 2880, schedule: { mode: 'allow', windows: [{ days: [1, 2, 3, 4, 5], from: '08:00', to: '22:00' }] }, lastLogin: n - 3600000 },
          ],
        });
      }
      if (url.startsWith('/api/photos')) {
        const m = url.match(/album=([^&]+)/);
        if (!m) return res({ albums: [{ id: '1', name: 'Камера', count: 24, cover: 0 }, { id: '2', name: 'Скриншоты', count: 14, cover: 0 }] });
        return res({ items: Array.from({ length: 12 }, (_, i) => ({ id: String(i + 1), name: `IMG_${2000 + i}.jpg`, size: 2400000 })) });
      }
      const q = url.match(/path=([^&]+)/);
      const p = q ? decodeURIComponent(q[1]) : '/storage/emulated/0';
      const root = '/storage/emulated/0';
      const tree = {
        '/storage/emulated/0': [['Камера', 1, 0, 'other'], ['Загрузки', 1, 0, 'other'], ['Movies', 1, 0, 'other']],
        '/storage/emulated/0/Movies': [['v1.mp4', 0, 142000000, 'video'], ['v2.mp4', 0, 310200000, 'video']],
      };
      const list = tree[p] || [];
      res({
        root, path: p, parent: p === root ? null : p.slice(0, p.lastIndexOf('/')),
        items: list.map((x) => ({ name: x[0], path: `${p}/${x[0]}`, dir: !!x[1], size: x[2] || 0, kind: x[3] || 'other', count: x[1] ? 3 : null })),
      });
    }, 180));
  }

  function demoPost(url, obj) {
    return new Promise((res) => setTimeout(() => {
      if (url === '/api/fs/delete') return res({ deleted: (obj.paths || []).length, failed: [] });
      res({ ok: true });
    }, 160));
  }

  /* ---------- диалог подтверждения / ввода ---------- */
  let dlgCb = null;
  function dlg(opts, cb) {
    byId('dT').textContent = opts.title || '';
    byId('dP').textContent = opts.text || '';
    const inp = byId('dI');
    if (opts.input != null) {
      inp.hidden = false;
      inp.value = opts.input;
    } else {
      inp.hidden = true;
    }
    byId('dNo').hidden = opts.cancel === false;
    const yes = byId('dYes');
    yes.textContent = opts.ok || 'OK';
    yes.className = 'nx-btn ' + (opts.danger ? 'danger' : 'primary');
    dlgCb = cb;
    byId('nxDlg').showModal();
    if (opts.input != null) setTimeout(() => { inp.focus(); inp.select(); }, 50);
  }
  byId('dNo').addEventListener('click', () => { byId('nxDlg').close(); dlgCb = null; });
  byId('dYes').addEventListener('click', () => {
    const cb = dlgCb;
    const val = byId('dI').value;
    byId('nxDlg').close();
    dlgCb = null;
    if (cb) cb(val);
  });

  /* ---------- ленивая подгрузка реальных миниатюр ---------- */
  const LZ = { io: null };
  if ('IntersectionObserver' in window) {
    LZ.io = new IntersectionObserver((entries) => {
      entries.forEach((en) => {
        if (!en.isIntersecting) return;
        const img = en.target;
        LZ.io.unobserve(img);
        const src = img.dataset.src;
        if (src && !img.getAttribute('src')) img.src = src;
      });
    }, { rootMargin: '300px' });
  }
  function observeLazy(root) {
    $$('img.lz', root).forEach((im) => {
      if (im._lz) return;
      im._lz = true;
      im.onload = () => {
        im.classList.add('ld');
        im.closest('.thumb')?.classList.add('has-img');
      };
      im.onerror = () => im.remove();
      if (LZ.io) LZ.io.observe(im);
      else im.src = im.dataset.src;
    });
  }

  /* ---------- шаблоны медиа ---------- */
  const thumbStyle = (v) => `background:linear-gradient(135deg,${v.c[0]},${v.c[1]})`;
  const videoRow = (v) => {
    const imgHtml = !DEMO_MODE && state.settings.thumbs && v.thumb
      ? `<img class="thumb-img lz" data-src="${esc(v.thumb)}" alt="">`
      : '';
    const titleShort = esc(v.title).slice(0, 26);
    return `
    <div class="row" role="button" tabindex="0" data-play="${esc(v.id)}">
      <div class="thumb" style="${state.settings.thumbs ? thumbStyle(v) : 'background:#cfdff5'}">
        ${imgHtml}
        <span class="t-title">${titleShort}</span>
        <span class="play">${ico('play')}</span><span class="dur">${esc(v.dur || '—')}</span>
      </div>
      <div class="meta"><h3 title="${esc(v.name)}">${esc(v.title)}</h3><p>${esc(v.fmt)}${v.size ? ' &bull; ' + esc(v.size) : ''}</p></div>
      <div class="row-actions">
        <a class="act-btn" href="${esc(v.dl)}" download="${esc(v.name)}" aria-label="Скачать" onclick="event.stopPropagation()">${ico('dl')}</a>
        <button class="more" data-more="${esc(v.id)}" aria-label="Действия">${ico('dots')}</button>
      </div>
    </div>`;
  };
  const stat = (cls, icon, n, label, view, idAttr = '') =>
    `<button class="stat ${cls}" data-go="${view}">${ico(icon)}<div><b data-count="${n}" ${idAttr}>0</b><span>${label}</span></div></button>`;

  /* ---------- состояние фото, проводника, настроек ---------- */
  const PH = { albums: null, album: null, name: '', items: [] };
  const FS = { path: null, root: null, items: [], selOn: false, sel: {} };
  const DEV = { tab: 'srv', timer: 0, lanTimer: 0 };
  const NET = { data: null, proto: 'http', strict: false };
  const MON = { timer: 0 };
  const ABOUT = { timer: 0 };
  const ACC = { timer: 0, list: [], open: null, cb: null, reg: D.registrationOpen !== false, pend: D.pendingRegs || 0 };
  const MAIL = { peer: null, peers: [], me: {}, lastId: 0, timer: 0, tick: 0, rec: null, chunks: [], t0: 0 };
  const DAYN = ['Вс', 'Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб'];
  let DELARM = { id: '', t: 0 };

  function stopSubTimers() {
    clearInterval(DEV.timer); DEV.timer = 0;
    clearTimeout(DEV.lanTimer); DEV.lanTimer = 0;
    clearInterval(MON.timer); MON.timer = 0;
    clearInterval(ABOUT.timer); ABOUT.timer = 0;
    clearInterval(ACC.timer); ACC.timer = 0;
    clearInterval(MAIL.timer); MAIL.timer = 0;
  }

  /* ---------- базовые вкладки ---------- */
  const views = {
    home: () => {
      const vCount = (D.stats && D.stats.videos != null) ? D.stats.videos : videos.length;
      const aCount = (D.stats && D.stats.audio != null) ? D.stats.audio : audios.length;
      const pCount = (D.stats && D.stats.photos != null) ? D.stats.photos : 0;
      const fCount = state.folderCount != null ? state.folderCount : 0;
      return `
      <section class="stats">
        ${stat('v', 'play-o', vCount, 'Видео', 'video')}
        ${stat('a', 'note', aCount, 'Аудио', 'audio')}
        ${stat('p', 'image', pCount, 'Фото', 'photo', 'id="homePhotoCount"')}
        ${stat('f', 'folder', fCount, 'Папки', 'folders', 'id="homeFolderCount"')}
      </section>
      <div class="sec-head"><h2>Последние видео</h2><button class="link" data-go="video">Все ${ico('arrow')}</button></div>
      <div class="list">${videos.slice(0, 6).map(videoRow).join('') || '<p class="empty">Видео пока нет</p>'}</div>`;
    },
    video: () => {
      const l = videos.filter((v) => !state.q || v.name.toLowerCase().includes(state.q) || v.title.toLowerCase().includes(state.q));
      return `<div class="sec-head"><h2>Видео</h2><span class="link" style="cursor:default;font-size:16px;color:var(--muted)">${l.length} шт.</span></div>
        <div class="list">${l.map(videoRow).join('') || '<p class="empty">Ничего не найдено</p>'}</div>`;
    },
    audio: () => {
      const l = audios.filter((a) => !state.q || a.name.toLowerCase().includes(state.q) || a.title.toLowerCase().includes(state.q));
      return `<div class="sec-head"><h2>Аудио</h2><span class="link" style="cursor:default;font-size:16px;color:var(--muted)">${l.length} шт.</span></div>
        <div class="tracks">${l.map((a) => `
        <div class="track glass" role="button" tabindex="0" data-audio="${esc(a.id)}"><span class="cover">${ico('note')}</span>
        <div style="min-width:0;flex:1"><h3 style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis">${esc(a.title)}</h3><p>${esc(a.art)}</p></div>
        <span class="dur2">${esc(a.dur)}</span>
        <a class="act-btn" href="${esc(a.dl)}" download="${esc(a.name)}" aria-label="Скачать" onclick="event.stopPropagation()">${ico('dl')}</a>
        </div>`).join('') || '<p class="empty">Ничего не найдено</p>'}</div>`;
    },
    photo: () => `
      <div class="sec-head"><h2>Фото</h2><span class="link" id="photoSubtitle" style="cursor:default;font-size:16px;color:var(--muted)"></span></div>
      <div class="crumbs" id="pcrumbs"></div>
      <div id="lP"><p class="empty">Загрузка…</p></div>`,
    folders: () => `
      <div class="sec-head"><h2>Папки</h2></div>
      <div class="crumbs-bar"><div class="crumbs" id="crumbs"></div><div class="selbar" id="selbar"></div></div>
      <div class="list" id="lF"><p class="empty">Загрузка…</p></div>`,
    settings: () => {
      const subs = [
        ['general', 'gear', 'Основные'],
        ['net', 'globe', 'Сеть'],
        ['devices', 'phone', 'Устройства'],
        ['accounts', 'user', 'Аккаунты'],
        ['mail', 'mail', 'Письмо', isAdmin && state.pendingRegs > 0 ? state.pendingRegs : 0],
        ['tools', 'tools', 'Инструменты'],
        ['about', 'info', 'О сервере'],
      ];
      if (isAdmin) subs.splice(4, 0, ['monitor', 'pulse', 'Монитор']);
      return `
      <div class="settings-shell">
        <div class="sec-head" style="margin-bottom:6px"><h2>Настройки</h2></div>
        <div class="set-tabs glass" role="tablist">
          ${subs.map(([id, ic, lbl, badge]) => `
            <button class="set-tab ${state.sub === id ? 'on' : ''}" data-sub="${id}" role="tab" aria-selected="${state.sub === id}">
              ${ico(ic)}<span>${lbl}</span>${badge ? `<span class="tab-badge">${badge}</span>` : ''}
            </button>`).join('')}
        </div>
        <div class="set-panel" id="setPanel"></div>
      </div>`;
    },
  };

  /* ---------- ФОТО: альбомы -> снимки ---------- */
  function photoCrumbs() {
    const el = byId('pcrumbs');
    if (!el) return;
    el.innerHTML = PH.album == null ? '' : `<button data-act="albums">${ico('back')} Все альбомы</button><span>/</span><b>${esc(PH.name)}</b>`;
  }
  function loadAlbums() {
    PH.album = null;
    photoCrumbs();
    const lp = byId('lP');
    if (!lp) return;
    lp.innerHTML = '<p class="empty">Загрузка…</p>';
    api('/api/photos').then((j) => {
      if (state.view !== 'photo') return;
      PH.albums = (j.albums || []).filter((a) => !state.q || a.name.toLowerCase().includes(state.q));
      const total = (j.albums || []).reduce((s, a) => s + (a.count || 0), 0);
      const sub = byId('photoSubtitle');
      if (sub) sub.textContent = `${total} шт.`;
      lp.innerHTML = PH.albums.length
        ? `<div class="gallery">${PH.albums.map((a) => `
          <button class="shot album" data-album="${esc(a.id)}" data-name="${esc(a.name)}">
            ${a.cover ? `<img class="shot-img lz" data-src="/photo?id=${encodeURIComponent(a.cover)}&thumb=1" alt="">` : ''}
            <div class="shot-info"><b>${esc(a.name)}</b><small>${a.count} фото</small></div>
          </button>`).join('')}</div>`
        : '<p class="empty">Фото не найдены</p>';
      observeLazy(lp);
    }).catch(() => {
      if (lp) lp.innerHTML = '<p class="empty">Не удалось загрузить фото. Проверьте разрешение доступа к фото в приложении.</p>';
    });
  }
  function openAlbum(id, name) {
    PH.album = id;
    PH.name = name;
    photoCrumbs();
    const lp = byId('lP');
    if (!lp) return;
    lp.innerHTML = '<p class="empty">Загрузка…</p>';
    api(`/api/photos?album=${encodeURIComponent(id)}`).then((j) => {
      if (state.view !== 'photo' || PH.album !== id) return;
      PH.items = j.items || [];
      const shown = PH.items.filter((f) => !state.q || f.name.toLowerCase().includes(state.q));
      const sub = byId('photoSubtitle');
      if (sub) sub.textContent = `${shown.length} шт.`;
      lp.innerHTML = shown.length
        ? `<div class="gallery">${shown.map((f) => {
          const origIdx = PH.items.indexOf(f);
          return `<button class="shot" data-photo="${origIdx}" style="background:linear-gradient(135deg,#b7d6ff,#e6f1ff)">
            ${DEMO_MODE ? '' : `<img class="shot-img lz" data-src="/photo?id=${encodeURIComponent(f.id)}&thumb=1" alt="">`}
            <span>${esc(f.name)}</span>
          </button>`;
        }).join('')}</div>`
        : '<p class="empty">В альбоме нет фото</p>';
      observeLazy(lp);
    }).catch(() => {
      if (lp) lp.innerHTML = '<p class="empty">Не удалось открыть альбом</p>';
    });
  }

  /* ---------- ПАПКИ: проводник + выбор и действия администратора ---------- */
  function fsErr(e) {
    if (e && e.code === 'auth_required') return 'Проводник работает при включённой защите паролем. Включите пароль в настройках сервера в приложении.';
    if (e && e.code === 'no_access') return 'Нет доступа к памяти телефона. Разрешите приложению доступ к файлам.';
    return 'Не удалось открыть папку';
  }
  function selIdx() { return Object.keys(FS.sel).filter((k) => FS.sel[k]).map(Number); }
  function renderSelbar() {
    const bar = byId('selbar');
    if (!bar) return;
    if (!isAdmin) { bar.innerHTML = ''; return; }
    const n = selIdx().length;
    bar.innerHTML = FS.selOn
      ? `<span class="sel-count">Выбрано: ${n}</span>
         <button class="nx-btn primary" data-sel="menu" ${n ? '' : 'disabled'}>${ico('edit')}Изменить</button>
         <button class="nx-btn" data-sel="cancel">Отмена</button>
         <div class="mpop" id="mpop">
           <button data-sel="rename" ${n === 1 ? '' : 'disabled'}>${ico('edit')}Переименовать</button>
           <button class="danger" data-sel="delete">${ico('trash')}Удалить</button>
         </div>`
      : `<button class="nx-btn" data-sel="start">${ico('check')}Выбрать</button>`;
  }
  function exitSel() {
    FS.selOn = false;
    FS.sel = {};
    byId('lF')?.classList.remove('selmode');
    $$('#lF .row.sel').forEach((r) => r.classList.remove('sel'));
    renderSelbar();
  }
  function toggleSel(i) {
    FS.selOn = true;
    byId('lF')?.classList.add('selmode');
    FS.sel[i] = !FS.sel[i];
    const row = $(`#lF [data-fsel="${i}"]`);
    if (row) row.classList.toggle('sel', !!FS.sel[i]);
    renderSelbar();
  }
  function doFsDelete() {
    const idx = selIdx();
    if (!idx.length) return;
    const paths = idx.map((i) => FS.items[i].path);
    dlg({ title: 'Удалить выбранное?', text: `Будет удалено элементов: ${paths.length}. Это действие нельзя отменить.`, ok: 'Удалить', danger: true }, () => {
      post('/api/fs/delete', { paths }).then((r) => {
        toast(`Удалено: ${r.deleted || 0}${(r.failed && r.failed.length) ? `, ошибок: ${r.failed.length}` : ''}`);
        exitSel();
        loadDir(FS.path);
      }).catch((e) => toast(opErr(e)));
    });
  }
  function doFsRename() {
    const idx = selIdx();
    if (idx.length !== 1) return;
    const it = FS.items[idx[0]];
    dlg({ title: 'Переименовать', text: `Новое имя для «${it.name}»`, input: it.name, ok: 'Сохранить' }, (v) => {
      v = (v || '').trim();
      if (!v || v === it.name) return;
      post('/api/fs/rename', { path: it.path, name: v }).then(() => {
        toast('Переименовано');
        exitSel();
        loadDir(FS.path);
      }).catch((e) => toast(e && e.code === 'bad_name' ? opErr({ code: 'bad_name_file' }) : opErr(e)));
    });
  }
  function loadDir(path) {
    const lf = byId('lF');
    if (lf) lf.innerHTML = '<p class="empty">Загрузка…</p>';
    api('/api/fs' + (path ? `?path=${encodeURIComponent(path)}` : '')).then((j) => {
      if (state.view !== 'folders') return;
      FS.path = j.path;
      FS.root = j.root;
      FS.items = j.items || [];
      FS.selOn = false;
      FS.sel = {};
      if (j.path === j.root) {
        state.folderCount = FS.items.filter((x) => x.dir).length;
      }
      renderDir();
    }).catch((e) => {
      if (byId('crumbs')) byId('crumbs').innerHTML = '';
      if (byId('selbar')) byId('selbar').innerHTML = '';
      if (lf) lf.innerHTML = `<p class="empty">${esc(fsErr(e))}</p>`;
    });
  }
  function renderDir() {
    const crumbs = byId('crumbs'), lf = byId('lF');
    if (!crumbs || !lf) return;
    const rel = FS.path.indexOf(FS.root) === 0 ? FS.path.slice(FS.root.length) : '';
    const parts = rel.split('/').filter(Boolean);
    let acc = FS.root;
    let ch = `<button data-fs="${esc(FS.root)}">Память телефона</button>`;
    parts.forEach((p, i) => {
      acc += '/' + p;
      ch += `<span>/</span>` + (i === parts.length - 1 ? `<b>${esc(p)}</b>` : `<button data-fs="${esc(acc)}">${esc(p)}</button>`);
    });
    crumbs.innerHTML = ch;
    renderSelbar();
    const list = FS.items.filter((it) => !state.q || it.name.toLowerCase().includes(state.q));
    lf.classList.toggle('selmode', FS.selOn);
    lf.innerHTML = list.length ? list.map((it) => {
      const i = FS.items.indexOf(it);
      const chk = isAdmin ? '<span class="chk"></span>' : '';
      const fsel = isAdmin ? ` data-fsel="${i}"` : '';
      if (it.dir) {
        return `<div class="row clickable" data-fs="${esc(it.path)}"${fsel}>${chk}
          <div class="thumb" style="background:linear-gradient(135deg,#ffd9f5,#f59ee8);color:#c2189f">${ico('folder')}</div>
          <div class="meta"><h3>${esc(it.name)}</h3><p>${it.count != null ? `${it.count} элементов` : 'Папка'}</p></div>
          <div class="row-actions"><span class="act-btn">${ico('arrow')}</span></div>
        </div>`;
      }
      const u = `/file?path=${encodeURIComponent(it.path)}`;
      const icName = it.kind === 'video' ? 'film' : it.kind === 'audio' ? 'note' : it.kind === 'photo' ? 'image' : 'file';
      const thumbImg = (it.kind === 'photo' && !DEMO_MODE) ? `<img class="thumb-img lz" data-src="${u}" alt="">` : '';
      const clickable = it.kind === 'video' || it.kind === 'audio' || it.kind === 'photo';
      return `<div class="row${clickable ? ' clickable' : ''}" ${clickable ? `data-fopen="${i}"` : ''}${fsel}>${chk}
        <div class="thumb" style="background:linear-gradient(135deg,#d4e6ff,#89bdfc);color:#0b4fd6">${thumbImg}${ico(icName)}</div>
        <div class="meta"><h3 title="${esc(it.name)}">${esc(it.name)}</h3><p>${fmtSize(it.size)}</p></div>
        <div class="row-actions">
          <a class="act-btn" href="${u}&dl=1" download="${esc(it.name)}" aria-label="Скачать" onclick="event.stopPropagation()">${ico('dl')}</a>
        </div>
      </div>`;
    }).join('') : '<p class="empty">Папка пуста</p>';
    observeLazy(lf);
  }

  /* ---------- НАСТРОЙКИ (все перенесённые разделы Classic в стиле Nexora Media) ---------- */
  const setRow = (k, t, d, checked) => `
    <div class="set glass">
      <div><h3>${t}</h3><p>${d}</p></div>
      <button class="switch" role="switch" data-set="${k}" aria-checked="${checked}" aria-label="${t}"></button>
    </div>`;

  function fsElem() { return document.fullscreenElement || document.webkitFullscreenElement || null; }
  function toggleFs() {
    const d = document.documentElement;
    if (!(d.requestFullscreen || d.webkitRequestFullscreen)) { toast('Этот браузер не поддерживает полный экран'); return; }
    if (fsElem()) { (document.exitFullscreen || document.webkitExitFullscreen).call(document); return; }
    try {
      const p = d.requestFullscreen ? d.requestFullscreen({ navigationUI: 'hide' }) : d.webkitRequestFullscreen();
      if (p && p.catch) p.catch(() => toast('Не удалось включить полный экран'));
    } catch (_) {}
  }

  function renderSettingsSub() {
    const panel = byId('setPanel');
    if (!panel) return;
    stopSubTimers();
    if (state.sub === 'monitor' && !isAdmin) state.sub = 'general';
    $$('.set-tab').forEach((b) => {
      const on = b.dataset.sub === state.sub;
      b.classList.toggle('on', on);
      b.setAttribute('aria-selected', String(on));
    });

    if (state.sub === 'general') {
      panel.innerHTML = `<div class="settings">
        ${setRow('autoplay', 'Автовоспроизведение', 'Запускать видео и аудио сразу после открытия', state.settings.autoplay)}
        ${setRow('thumbs', 'Превью видео', 'Показывать кадры и обложки в списках', state.settings.thumbs)}
        ${setRow('anim', 'Плавная анимация', 'Анимация переходов и карточек интерфейса', state.settings.anim)}
        <div class="set glass"><div><h3>Полный экран</h3><p>Скрыть строку состояния и кнопки навигации</p></div>
          <button class="switch" id="fsSw" role="switch" aria-checked="${!!fsElem()}" aria-label="Полный экран"></button></div>
        <div class="set glass"><div><h3>Определение сигнала Wi-Fi</h3><p>Источник уровня сигнала на индикаторе сервера</p></div>
          <div class="seg">
            <button class="segb ${state.sigMode === 'app' ? 'on' : ''}" data-sig="app">Приложение · 4 с</button>
            <button class="segb ${state.sigMode === 'rtt' ? 'on' : ''}" data-sig="rtt">По задержке</button>
          </div></div>
        <div class="set glass"><div><h3>Язык интерфейса</h3><p>Применяется сразу для основных разделов</p></div>
          <select class="nx-select" data-lang><option value="ru">Русский</option><option value="en">English</option></select></div>
        <div class="set glass"><div><h3>Адрес сервера</h3><p>${esc(D.address || location.host || 'localhost')}</p></div>
          <button class="nx-btn" data-copyaddr>${ico('copy')}Копировать</button></div>
      </div>`;
      $('[data-lang]', panel).value = state.settings.lang;
      byId('fsSw').addEventListener('click', toggleFs);
    } else if (state.sub === 'net') {
      panel.innerHTML = `
        <div class="sub-bar"><h3>Сеть сервера</h3><small class="nx-note" id="netState">Загрузка…</small></div>
        <div class="settings" id="netBody"><p class="empty">Загрузка параметров сети…</p></div>
        <div class="diag-card glass" id="netDiag">
          <div class="diag-head">
            <div>
              <h3>Диагностика локальной сети</h3>
              <p class="nx-note">Замер задержки (20 пакетов), скорости скачивания/загрузки и текущей нагрузки сервера</p>
            </div>
            <div style="display:flex;gap:8px;flex-wrap:wrap">
              <button class="nx-btn" id="diagLanBtn" type="button">Сканер устройств в сети</button>
              <button class="nx-btn primary" id="diagRunBtn" type="button">Запустить тест</button>
            </div>
          </div>
          <small class="nx-note" id="diagStatus">Нажмите «Запустить тест» для проверки локального канала</small>
          <div class="diag-grid">
            <div class="diag-kpi"><small>Пинг (мин / сред / макс)</small><b id="diagPing">—</b><span id="diagJitter">Джиттер: —</span></div>
            <div class="diag-kpi"><small>От сервера (скачивание)</small><b id="diagDown">—</b><span id="diagDownSub">МБ/с: —</span></div>
            <div class="diag-kpi"><small>На сервер (загрузка)</small><b id="diagUp">—</b><span id="diagUpSub">МБ/с: —</span></div>
            <div class="diag-kpi"><small>Нагрузка сервера</small><b id="diagLoad">—</b><span id="diagLoadSub">Потоки / сессии: —</span></div>
          </div>
          <div class="diag-calc">
            <label for="diagViewers">Одновременные потоки:</label>
            <select class="nx-select" id="diagViewers">
              <option value="1">1 зритель</option>
              <option value="2" selected>2 зрителя</option>
              <option value="3">3 зрителя</option>
              <option value="4">4 зрителя</option>
              <option value="5">5 зрителей</option>
              <option value="6">6 зрителей</option>
            </select>
            <select class="nx-select" id="diagBitrate">
              <option value="12">Full HD (~12 Мбит/с)</option>
              <option value="25">4K UHD (~25 Мбит/с)</option>
            </select>
            <span class="diag-res" id="diagCalcRes">Запустите тест скорости</span>
          </div>
          <div class="diag-check" id="diagHostBox" style="${isAdmin ? '' : 'display:none'}">
            <label class="nx-note" for="diagHost">Проверка узла LAN (только локальный IP):</label>
            <input class="nx-input" id="diagHost" placeholder="192.168.1.1" autocomplete="off">
            <button class="nx-btn" id="diagCheckBtn" type="button">Проверить</button>
            <small class="nx-note" id="diagHostRes"></small>
          </div>
        </div>`;
      loadNet();
      initNetDiagnostics();
    } else if (state.sub === 'devices') {
      panel.innerHTML = `
        <div class="sub-bar">
          <div class="seg">
            <button class="segb ${DEV.tab === 'srv' ? 'on' : ''}" data-dtab="srv">Подключены к серверу</button>
            <button class="segb ${DEV.tab === 'lan' ? 'on' : ''}" data-dtab="lan">Все в локальной сети</button>
          </div>
          <small class="nx-note" id="devCount"></small>
        </div>
        <div id="devArea"></div>`;
      devTab(DEV.tab);
    } else if (state.sub === 'accounts') {
      panel.innerHTML = `
        <div class="sub-bar">
          <h3>Учётные записи</h3>
          <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
            <small class="nx-note" id="accMe"></small>
            <button class="nx-btn primary" id="accNew" style="display:none">+ Создать</button>
          </div>
        </div>
        <div id="accBanner"></div>
        <div class="set glass" id="regBar" style="display:none">
          <div><h3>Заявки на регистрацию</h3><p>Пользователи подают заявку на странице входа, одобряет администратор в разделе «Письмо»</p></div>
          <button class="switch ${ACC.reg ? 'on' : ''}" id="regSw" role="switch" aria-checked="${ACC.reg}" aria-label="Приём заявок"></button>
        </div>
        <div id="ap-create" class="acc-panel glass"></div>
        <div class="dev-list" id="accList"><p class="empty">Загрузка…</p></div>
        <p class="nx-note">Ограничения и данные другой учётной записи меняет только администратор. Свой логин и пароль можно сменить, зная текущий пароль.</p>`;
      byId('accNew').addEventListener('click', accCreatePanel);
      byId('regSw').addEventListener('click', () => {
        if (!isAdmin) return;
        post('/api/accounts/settings', { registrationOpen: !ACC.reg }).then(() => {
          ACC.reg = !ACC.reg;
          byId('regSw').setAttribute('aria-checked', String(ACC.reg));
          toast(ACC.reg ? 'Заявки на регистрацию принимаются' : 'Приём заявок закрыт');
        }).catch((e) => toast(opErr(e)));
      });
      ACC.open = null;
      loadAcc();
      ACC.timer = setInterval(() => {
        if (state.view === 'settings' && state.sub === 'accounts' && !ACC.open) loadAcc();
      }, 5000);
    } else if (state.sub === 'mail') {
      panel.innerHTML = `
        <div class="mail-box">
          <div class="mthreads glass" id="mThreads"><p class="empty">Загрузка…</p></div>
          <div class="mconv glass">
            <div class="mhead">
              <div class="mhead-info"><b id="mTitle">Выберите собеседника</b><small id="mSub">Обмен сообщениями и файлами по локальной сети</small></div>
              <button class="nx-btn" id="mName">Моё имя</button>
            </div>
            <div class="mmsgs" id="mMsgs"><p class="empty">Выберите диалог слева</p></div>
            <div class="mprog" id="mProg"></div>
            <div class="mcomp" id="mComp" style="display:none">
              <button class="act-btn" id="mAttach" aria-label="Прикрепить файл">${ico('attach')}</button>
              <button class="act-btn mic-btn" id="mMic" aria-label="Голосовое сообщение">${ico('mic')}</button>
              <input class="nx-input" id="mText" placeholder="Сообщение" maxlength="4000" autocomplete="off">
              <button class="nx-btn primary" id="mSend">Отправить</button>
              <input type="file" id="mFile" hidden>
            </div>
          </div>
        </div>`;
      bindMailEvents();
      startMail();
    } else if (state.sub === 'tools') {
      renderTools(panel);
    } else if (state.sub === 'monitor') {
      panel.innerHTML = `
        <div class="sub-bar">
          <h3>Монитор активности</h3>
          <small class="nx-note" id="monUpdated">Обновление каждые 1.5 с</small>
        </div>
        <div class="mon-sum" id="monTotals"></div>
        <div class="dev-list" id="monList"><p class="empty">Загрузка…</p></div>`;
      loadMonitor();
      MON.timer = setInterval(() => {
        if (state.view === 'settings' && state.sub === 'monitor' && isAdmin) loadMonitor();
      }, 1500);
    } else if (state.sub === 'about') {
      panel.innerHTML = `
        <div class="set-head-card glass">
          <svg class="logo"><use href="#i-logo"/></svg>
          <div><h3>Nexora <span>Media</span></h3><p class="nx-note">Версия <span id="aboutVer">${esc(D.version || '1.0')}</span> · <span id="aboutUpd">Обновление каждые 5 с</span></p></div>
          <div style="margin-left:auto;display:flex;gap:8px;flex-wrap:wrap">
            <button class="nx-btn" data-copyaddr>${ico('copy')}Копировать адрес</button>
            ${D.authRequired ? `<a class="nx-btn" href="/logout">${ico('out')}Выйти</a>` : ''}
          </div>
        </div>
        <div class="srv-grid" id="srvGrid"><p class="empty">Загрузка сведений о сервере…</p></div>
        <div class="thanks-card glass">${ico('heart')}<span>Спасибо, что используете Nexora Media!</span></div>`;
      loadServerAbout();
      ABOUT.timer = setInterval(() => {
        if (state.view === 'settings' && state.sub === 'about') loadServerAbout();
      }, 5000);
    }
  }

  function fmtUptime(sec) {
    sec = Math.max(0, Math.floor(Number(sec) || 0));
    const d = Math.floor(sec / 86400), h = Math.floor((sec % 86400) / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
    const parts = [];
    if (d) parts.push(`${d} д`);
    if (h || d) parts.push(`${h} ч`);
    parts.push(`${m} мин ${s} с`);
    return parts.join(' ');
  }
  function srvKv(k, v) {
    return `<div class="srv-kv"><span>${esc(k)}</span><b>${esc(v == null || v === '' ? '—' : v)}</b></div>`;
  }
  function renderServerAboutAdmin(j) {
    const grid = byId('srvGrid');
    if (!grid || state.view !== 'settings' || state.sub !== 'about') return;
    const s = j.server || {}, dev = j.device || {}, w = j.wifi || {}, st = j.storage || {}, mem = j.memory || {}, bat = j.battery || {}, tr = j.traffic || {}, ftp = j.ftp || {};
    const vCount = (D.stats && D.stats.videos != null) ? D.stats.videos : videos.length;
    const aCount = (D.stats && D.stats.audio != null) ? D.stats.audio : audios.length;
    const pCount = (D.stats && D.stats.photos != null) ? D.stats.photos : 0;
    if (byId('aboutVer') && s.version) byId('aboutVer').textContent = s.version;
    if (byId('aboutUpd')) byId('aboutUpd').textContent = `Обновлено в ${new Date().toLocaleTimeString('ru-RU')}`;
    const ramPct = mem.total > 0 ? Math.min(100, Math.round((mem.used / mem.total) * 100)) : 0;
    const diskPct = st.total > 0 ? Math.min(100, Math.round((st.used / st.total) * 100)) : 0;
    const freqStr = w.frequencyMhz > 0 ? `${w.frequencyMhz} МГц (${w.frequencyMhz >= 4900 ? '5 ГГц' : '2.4 ГГц'})` : '—';
    const ifacesStr = (j.interfaces || []).map((x) => `${x.label}: ${x.ip}`).join(', ') || '—';
    const batStr = bat.level != null ? `${bat.level}%${bat.charging ? ' · зарядка' : ' · от батареи'}` : '—';
    grid.innerHTML = `
      <div class="srv-card glass">
        <h4>Сервер</h4>
        ${srvKv('Статус', state.online ? 'Активен' : 'Нет связи')}
        ${srvKv('Версия ПО', s.version || '1.0')}
        ${srvKv('Протокол и порт', `${String(s.protocol || 'http').toUpperCase()} · ${s.port || '—'}`)}
        ${srvKv('Адрес', s.address || D.address || location.host)}
        ${srvKv('Время работы', fmtUptime(s.uptimeSec))}
        ${srvKv('Потоки / Сессии', `${s.threads || 0} / ${s.activeSessions || 0}`)}
        ${srvKv('Устройства (онлайн / всего)', `${s.onlineDevicesCount || 0} / ${s.devicesCount || 0}`)}
        ${srvKv('Вещание', D.folder || 'Все папки')}
        ${srvKv('Защита паролем', D.authRequired ? 'Включена' : 'Выключена')}
      </div>
      <div class="srv-card glass">
        <h4>Устройство и система</h4>
        ${srvKv('Модель', [dev.manufacturer, dev.model].filter(Boolean).join(' ') || 'Android')}
        ${srvKv('Android', `Android ${dev.androidVersion || '—'} (SDK ${dev.sdkInt || '—'})`)}
        ${srvKv('Архитектура', dev.abis || '—')}
        ${srvKv('Плеер libVLC', dev.vlcVersion || '3.6.0')}
        ${srvKv('Батарея', batStr)}
      </div>
      <div class="srv-card glass">
        <h4>Сеть и Wi-Fi</h4>
        ${srvKv('Wi-Fi сеть (SSID)', w.ssid || 'скрыто / не подключено')}
        ${srvKv('IP Wi-Fi', w.ip || '—')}
        ${srvKv('Скорость канала', w.linkSpeedMbps > 0 ? `${w.linkSpeedMbps} Мбит/с` : '—')}
        ${srvKv('Частота', freqStr)}
        ${srvKv('Сигнал (RSSI)', w.rssi != null ? `${w.rssi} дБм (${w.level != null ? w.level + '/4' : '—'})` : '—')}
        ${srvKv('Интерфейсы', ifacesStr)}
      </div>
      <div class="srv-card glass">
        <h4>Память и диск</h4>
        ${srvKv('ОЗУ занято / всего', mem.total > 0 ? `${fmtSize(mem.used)} / ${fmtSize(mem.total)} (${ramPct}%)` : '—')}
        ${mem.total > 0 ? `<div class="srv-bar"><i style="width:${ramPct}%"></i></div>` : ''}
        ${srvKv('Диск занято / всего', st.total > 0 ? `${fmtSize(st.used)} / ${fmtSize(st.total)} (${diskPct}%)` : '—')}
        ${st.total > 0 ? `<div class="srv-bar"><i style="width:${diskPct}%"></i></div>` : ''}
        ${srvKv('Свободно на диске', st.free > 0 ? fmtSize(st.free) : '—')}
        ${srvKv('Медиафайлы', st.mediaBytes > 0 ? fmtSize(st.mediaBytes) : totalMem())}
        ${srvKv('Библиотека', `${vCount} видео · ${aCount} аудио · ${pCount} фото`)}
      </div>
      <div class="srv-card glass">
        <h4>Нагрузка и трафик</h4>
        ${srvKv('Активные передачи', tr.activeTransfers || 0)}
        ${srvKv('Отдача сейчас', fmtSpeed(tr.downBps || 0))}
        ${srvKv('Приём сейчас', fmtSpeed(tr.upBps || 0))}
        ${srvKv('Отдано за сеанс', fmtSize(tr.downBytes || 0))}
        ${srvKv('Принято за сеанс', fmtSize(tr.upBytes || 0))}
        ${srvKv('Суммарный трафик', fmtSize(tr.totalBytes || 0))}
      </div>
      <div class="srv-card glass">
        <h4>FTP-сервер</h4>
        ${srvKv('Статус FTP', ftp.running ? 'Запущен' : 'Остановлен')}
        ${srvKv('Порт FTP', ftp.port || 2121)}
        ${srvKv('Адрес FTP', ftp.running ? (ftp.address || '—') : 'Неактивен')}
      </div>`;
  }
  function renderServerAboutPublic(j) {
    const grid = byId('srvGrid');
    if (!grid || state.view !== 'settings' || state.sub !== 'about') return;
    const vCount = (D.stats && D.stats.videos != null) ? D.stats.videos : videos.length;
    const aCount = (D.stats && D.stats.audio != null) ? D.stats.audio : audios.length;
    const pCount = (D.stats && D.stats.photos != null) ? D.stats.photos : 0;
    const disk = j.disk || DISK || { total: 0, free: 0 };
    const mediaStr = j.mediaBytes > 0 ? fmtSize(j.mediaBytes) : totalMem();
    if (byId('aboutVer') && j.version) byId('aboutVer').textContent = j.version;
    if (byId('aboutUpd')) byId('aboutUpd').textContent = `Обновлено в ${new Date().toLocaleTimeString('ru-RU')}`;
    grid.innerHTML = `
      <div class="srv-card glass">
        <h4>Сервер</h4>
        ${srvKv('Wi-Fi сервер', state.online ? 'Активен' : 'Нет связи')}
        ${srvKv('Версия', j.version || D.version || '1.0')}
        ${srvKv('Протокол и порт', `${String(j.protocol || 'http').toUpperCase()} · ${j.port || D.port || '—'}`)}
        ${srvKv('Адрес сервера', D.address || location.host || '—')}
        ${srvKv('Время работы', fmtUptime(j.uptimeSec || 0))}
        ${srvKv('Устройств на сервере', j.devicesCount != null ? j.devicesCount : '—')}
        ${srvKv('Вещание', D.folder || 'Все папки')}
        ${srvKv('Защита паролем', D.authRequired ? 'Включена' : 'Выключена')}
      </div>
      <div class="srv-card glass">
        <h4>Хранилище и медиа</h4>
        ${srvKv('Свободно / Всего', disk.total > 0 ? `${fmtSize(disk.free)} / ${fmtSize(disk.total)}` : '—')}
        ${srvKv('Память медиа', mediaStr)}
        ${srvKv('Видео', `${vCount} файлов`)}
        ${srvKv('Аудио', `${aCount} файлов`)}
        ${srvKv('Фото', `${pCount} шт.`)}
      </div>`;
  }
  function loadServerAbout() {
    if (isAdmin) {
      api('/api/server/info').then(renderServerAboutAdmin).catch(() => {
        api('/api/status').then(renderServerAboutPublic).catch(() => {});
      });
    } else {
      api('/api/status').then(renderServerAboutPublic).catch(() => {});
    }
  }

  /* ---------- МОНИТОР АКТИВНОСТИ (только администратор) ---------- */
  const MON_STATES = {
    watching: 'Смотрит',
    listening: 'Слушает',
    downloading: 'Скачивает',
    uploading: 'Загружает',
    browsing: 'Просмотр',
    idle: 'Неактивен',
  };
  function fmtSpeed(bps) {
    bps = Math.max(0, Number(bps) || 0);
    if (bps >= 1048576) return (bps / 1048576).toFixed(2) + ' МБ/с';
    if (bps >= 1024) return (bps / 1024).toFixed(1) + ' КБ/с';
    return Math.round(bps) + ' Б/с';
  }
  function fmtSecClock(s) {
    s = Math.max(0, Math.floor(Number(s) || 0));
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    return h > 0 ? `${h}:${two(m)}:${two(sec)}` : `${m}:${two(sec)}`;
  }
  function loadMonitor() {
    api('/api/monitor').then((j) => {
      if (state.view !== 'settings' || state.sub !== 'monitor') return;
      const tot = j.totals || {}, list = j.users || [];
      const tb = byId('monTotals'), el = byId('monList');
      if (tb) {
        tb.innerHTML = `
          <div class="mon-kpi glass"><small>Активных сессий</small><b>${tot.active || 0} / ${list.length}</b></div>
          <div class="mon-kpi glass"><small>Отдача (вниз)</small><b>${fmtSpeed(tot.downBps || 0)}</b></div>
          <div class="mon-kpi glass"><small>Приём (вверх)</small><b>${fmtSpeed(tot.upBps || 0)}</b></div>
          <div class="mon-kpi glass"><small>Трафик сервера</small><b>${fmtSize((tot.totalDownBytes || 0) + (tot.totalUpBytes || 0))}</b></div>`;
      }
      if (el) {
        el.innerHTML = list.length ? list.map((u) => {
          const st = u.state || 'browsing';
          const lbl = MON_STATES[st] || st;
          const isOn = st !== 'idle';
          const devName = u.device || 'Устройство';
          const icoName = /Windows|Mac|Linux|Edge/i.test(devName) ? 'pc' : (/iPad|Tablet/i.test(devName) ? 'tablet' : 'phone');
          const role = u.role === 'admin' ? '<span class="bdg adm">админ</span>' : '<span class="bdg usr">пользователь</span>';
          const posStr = (u.durSec > 0) ? `${fmtSecClock(u.posSec)} / ${fmtSecClock(u.durSec)}` : (u.posSec > 0 ? fmtSecClock(u.posSec) : '');
          const pct = (u.durSec > 0) ? Math.min(100, Math.max(0, (u.posSec / u.durSec) * 100)) : 0;
          const fileLine = u.file ? `<small>Файл: <b>${esc(u.file)}</b>${posStr ? ` · ${posStr}` : ''}</small>` : '';
          const progLine = (st === 'watching' || st === 'listening') && u.durSec > 0 ? `<div class="mon-prog"><i style="width:${pct.toFixed(1)}%"></i></div>` : '';
          const timeLine = `Вход: ${fmtDT(u.since)}${u.watchedSec ? ` · Просмотрено: ${fmtDur(u.watchedSec)}` : ''}`;
          return `<div class="mon-card glass ${isOn ? 'on' : ''}">
            <div class="dev-ico">${ico(icoName)}<span class="dev-dot"></span></div>
            <div class="dev-meta">
              <b>${esc(u.login || 'гость')} ${role} <span class="st-bdg st-${esc(st)}">${esc(lbl)}</span></b>
              <small>${esc(devName)} · IP: ${esc(u.ip || '—')}</small>
              ${fileLine}
              ${progLine}
              <small>${timeLine}</small>
            </div>
            <div class="mon-right">
              <b>${fmtSpeed(u.speedBps || 0)}</b>
              <small>Трафик: ${fmtSize(u.bytes || 0)}</small>
            </div>
          </div>`;
        }).join('') : '<p class="empty">Сейчас активных пользователей нет</p>';
      }
    }).catch((e) => {
      const el = byId('monList');
      if (el) el.innerHTML = `<p class="empty">${esc(opErr(e))}</p>`;
    });
  }

  /* ---------- СЕТЬ ---------- */
  function loadNet() {
    api('/api/net').then((j) => {
      if (state.view !== 'settings' || state.sub !== 'net') return;
      NET.data = j;
      NET.proto = j.protocol || 'http';
      NET.strict = !!j.strict;
      renderNet();
    }).catch(() => {
      const nb = byId('netBody');
      if (nb) nb.innerHTML = '<p class="empty">Не удалось загрузить настройки сети</p>';
    });
  }
  function renderNet() {
    const j = NET.data, nb = byId('netBody');
    if (!j || !nb) return;
    byId('netState').textContent = `Сейчас: ${String(j.protocol || 'http').toUpperCase()} · ${j.ip || ''}:${j.port || ''}`;
    const ports = (j.ports || []).slice();
    if (j.port && !ports.includes(j.port)) ports.unshift(j.port);
    const certHtml = (j.protocol === 'https' && j.cert)
      ? `Сертификат SHA-256: ${esc(j.cert.fingerprint)}${j.cert.expires ? `, действует до ${fmtDT(j.cert.expires)}` : ''}. <a href="/cert.crt">Скачать сертификат</a>.`
      : 'Сейчас включён HTTP. Включите HTTPS, если нужны шифрование трафика и голосовые сообщения.';
    const dis = !isAdmin ? 'disabled' : '';
    nb.innerHTML = `
      <div class="set glass"><div><h3>Протокол</h3><p>HTTPS шифрует весь обмен и нужен для записи голоса</p></div>
        <div class="seg">
          <button class="segb" id="npHttp" ${dis}>HTTP</button>
          <button class="segb" id="npHttps" ${dis}>HTTPS</button>
        </div></div>
      <div class="set glass"><div><h3>IP-адрес вещания</h3><p>Интерфейс, на котором слушает сервер</p></div>
        <select class="nx-select" id="netIp" ${dis}>
          <option value="auto">Авто</option>
          ${(j.ips || []).map((x) => `<option value="${esc(x.ip)}">${esc(x.label)} · ${esc(x.ip)}</option>`).join('')}
        </select></div>
      <div class="set glass"><div><h3>Порт</h3><p>«Авто» выбирает свободный из списка</p></div>
        <div style="display:flex;gap:8px;align-items:center">
          <select class="nx-select" id="netPort" ${dis}>
            <option value="auto">Авто (свободный)</option>
            ${ports.map((p) => `<option value="${p}">${p}</option>`).join('')}
            <option value="custom">Свой порт…</option>
          </select>
          <input class="nx-input" id="netPortC" type="number" min="1024" max="65535" placeholder="порт" style="width:110px;display:none" ${dis}>
        </div></div>
      <div class="set glass"><div><h3>Шифрование сигнала (Strict TLS)</h3><p>Только HTTPS TLS 1.2+, защищённые cookie, HTTP отключён</p></div>
        <button class="switch" id="netStrict" role="switch" aria-checked="${NET.strict}" ${dis}></button></div>
      <p class="nx-note">${certHtml}</p>
      <div style="display:flex;align-items:center;gap:12px">
        <button class="nx-btn primary" id="netApply" ${dis}>Применить и перезапустить</button>
        <span class="nx-note">${!isAdmin ? 'Изменять настройки сети может администратор' : ''}</span>
      </div>`;

    byId('netIp').value = j.ipMode === 'auto' ? 'auto' : j.ip;
    byId('netPort').value = j.portMode === 'auto' ? 'auto' : String(j.port);
    const syncNet = () => {
      byId('npHttp').classList.toggle('on', NET.proto === 'http');
      byId('npHttps').classList.toggle('on', NET.proto === 'https');
      byId('npHttp').disabled = NET.strict || !isAdmin;
      byId('netStrict').setAttribute('aria-checked', String(NET.strict));
    };
    syncNet();
    byId('npHttp').onclick = () => { if (!isAdmin || NET.strict) return; NET.proto = 'http'; syncNet(); };
    byId('npHttps').onclick = () => { if (!isAdmin) return; NET.proto = 'https'; syncNet(); };
    byId('netStrict').onclick = () => { if (!isAdmin) return; NET.strict = !NET.strict; if (NET.strict) NET.proto = 'https'; syncNet(); };
    byId('netPort').onchange = function () { byId('netPortC').style.display = this.value === 'custom' ? '' : 'none'; };
    byId('netApply').onclick = () => {
      let port = byId('netPort').value;
      if (port === 'custom') port = parseInt(byId('netPortC').value, 10);
      else if (port !== 'auto') port = parseInt(port, 10);
      if (port !== 'auto' && !(port >= 1024 && port <= 65535)) { toast('Порт: число от 1024 до 65535'); return; }
      const body = { protocol: NET.proto, ip: byId('netIp').value, port, strict: NET.strict };
      dlg({ title: 'Перезапустить сервер?', text: 'Адрес, порт или протокол изменятся. Все устройства будут отключены и должны открыть новый адрес.', ok: 'Применить' }, () => {
        if (DEMO_MODE) { toast('Демо: настройки не применяются'); return; }
        post('/api/net', body).then((r) => {
          toast('Сервер перезапускается…');
          setTimeout(() => { location.href = r.url || location.href; }, 2800);
        }).catch((e) => {
          const c = e && e.code;
          toast(c === 'port_busy' ? 'Порт занят' : c === 'bad_ip' ? 'IP недоступен' : c === 'bad_port' ? 'Недопустимый порт' : opErr(e));
        });
      });
    };
  }

  /* ---------- ДИАГНОСТИКА ЛОКАЛЬНОЙ СЕТИ ---------- */
  const DIAG = { running: false, downMbps: 0, demoDownAt: 0, demoUpAt: 0 };
  function initNetDiagnostics() {
    const runBtn = byId('diagRunBtn');
    const lanBtn = byId('diagLanBtn');
    const chkBtn = byId('diagCheckBtn');
    const vw = byId('diagViewers');
    const br = byId('diagBitrate');
    if (runBtn) runBtn.onclick = runNetDiagTest;
    if (lanBtn) lanBtn.onclick = () => { DEV.tab = 'lan'; state.sub = 'devices'; renderSettingsSub(); };
    if (chkBtn) chkBtn.onclick = runHostCheck;
    if (vw) vw.onchange = updateStreamCalc;
    if (br) br.onchange = updateStreamCalc;
    loadNetLoad();
    updateStreamCalc();
  }
  function updateStreamCalc() {
    const el = byId('diagCalcRes');
    const vw = byId('diagViewers');
    const br = byId('diagBitrate');
    if (!el || !vw || !br) return;
    if (!DIAG.downMbps) {
      el.className = 'diag-res';
      el.textContent = 'Запустите тест для расчёта потоков';
      return;
    }
    const n = parseInt(vw.value, 10) || 1;
    const per = parseInt(br.value, 10) || 12;
    const need = n * per;
    const maxStreams = Math.floor(DIAG.downMbps / per);
    if (DIAG.downMbps >= need * 1.25) {
      el.className = 'diag-res ok';
      el.textContent = `Хватит с запасом (нужно ${need} Мбит/с · до ${maxStreams} потоков)`;
    } else if (DIAG.downMbps >= need) {
      el.className = 'diag-res warn';
      el.textContent = `Впритык (нужно ${need} Мбит/с · максимум ~${maxStreams})`;
    } else {
      el.className = 'diag-res bad';
      el.textContent = `Недостаточно: нужно ${need} Мбит/с (хватит на ${maxStreams})`;
    }
  }
  function loadNetLoad() {
    api('/api/net/load').then((j) => {
      const ld = byId('diagLoad'), sub = byId('diagLoadSub');
      if (ld) ld.textContent = `${j.activeTransfers || 0} передач`;
      if (sub) sub.textContent = `Потоки: ${j.threads || 0} · Сессии: ${j.sessions || 0} · ↓${fmtSpeed(j.downBps || 0)} ↑${fmtSpeed(j.upBps || 0)}`;
    }).catch(() => {});
  }
  async function runNetDiagTest() {
    if (DIAG.running) return;
    DIAG.running = true;
    const btn = byId('diagRunBtn'), st = byId('diagStatus');
    if (btn) btn.disabled = true;
    try {
      // 1. 20 sequential pings
      const rtts = [];
      for (let i = 0; i < 20; i++) {
        if (st) st.textContent = `Проверка задержки: пакет ${i + 1} из 20…`;
        const t0 = performance.now();
        if (DEMO_MODE) {
          await new Promise((r) => setTimeout(r, 12 + Math.random() * 14));
        } else {
          const r = await fetch(`/api/net/ping?n=${i}&t=${Date.now()}`, { credentials: 'same-origin', cache: 'no-store' });
          if (r.status === 401) { location.replace('/'); return; }
          await r.text();
        }
        rtts.push(Math.max(0.2, performance.now() - t0));
      }
      const minR = Math.min(...rtts), maxR = Math.max(...rtts);
      const avgR = rtts.reduce((a, b) => a + b, 0) / rtts.length;
      let jSum = 0;
      for (let i = 1; i < rtts.length; i++) jSum += Math.abs(rtts[i] - rtts[i - 1]);
      const jitter = rtts.length > 1 ? jSum / (rtts.length - 1) : 0;
      if (byId('diagPing')) byId('diagPing').textContent = `${minR.toFixed(1)} / ${avgR.toFixed(1)} / ${maxR.toFixed(1)} мс`;
      if (byId('diagJitter')) byId('diagJitter').textContent = `Джиттер: ${jitter.toFixed(1)} мс`;

      // 2. Download speed (8 MB)
      if (st) st.textContent = 'Тест скорости от сервера (8 МБ)…';
      let downBytes = 8 * 1048576, downMs = 280;
      if (DEMO_MODE) {
        const now = Date.now();
        if (now - DIAG.demoDownAt < 10000) {
          const e = new Error('rate_limited'); e.code = 'rate_limited'; e.retryAfterSec = Math.ceil((10000 - (now - DIAG.demoDownAt)) / 1000); throw e;
        }
        DIAG.demoDownAt = now;
        await new Promise((r) => setTimeout(r, 320));
        downMs = 260 + Math.random() * 60;
      } else {
        const t0 = performance.now();
        const r = await fetch(`/api/net/speed/down?size=${downBytes}&t=${Date.now()}`, { credentials: 'same-origin', cache: 'no-store' });
        if (r.status === 401) { location.replace('/'); return; }
        if (!r.ok) {
          let j = null; try { j = await r.json(); } catch (_) {}
          const e = new Error((j && j.error) || ('HTTP ' + r.status));
          e.code = j && j.error; e.retryAfterSec = j && j.retryAfterSec;
          throw e;
        }
        const buf = await r.arrayBuffer();
        downMs = Math.max(1, performance.now() - t0);
        downBytes = buf.byteLength || downBytes;
      }
      const downMBps = downBytes / (downMs / 1000) / 1048576;
      const downMbps = (downBytes * 8) / (downMs / 1000) / 1000000;
      DIAG.downMbps = downMbps;
      if (byId('diagDown')) byId('diagDown').textContent = `${downMbps.toFixed(1)} Мбит/с`;
      if (byId('diagDownSub')) byId('diagDownSub').textContent = `МБ/с: ${downMBps.toFixed(2)} (${fmtSize(downBytes)})`;
      updateStreamCalc();

      // 3. Upload speed (4 MB)
      if (st) st.textContent = 'Тест скорости на сервер (4 МБ)…';
      let upBytes = 4 * 1048576, upMs = 220;
      if (DEMO_MODE) {
        const now = Date.now();
        if (now - DIAG.demoUpAt < 10000) {
          const e = new Error('rate_limited'); e.code = 'rate_limited'; e.retryAfterSec = Math.ceil((10000 - (now - DIAG.demoUpAt)) / 1000); throw e;
        }
        DIAG.demoUpAt = now;
        await new Promise((r) => setTimeout(r, 240));
        upMs = 210 + Math.random() * 50;
      } else {
        const payload = new Uint8Array(upBytes);
        for (let i = 0; i < upBytes; i += 4096) payload[i] = (i * 31) & 0xff;
        const t0 = performance.now();
        const r = await fetch('/api/net/speed/up', {
          method: 'POST',
          credentials: 'same-origin',
          cache: 'no-store',
          headers: { 'Content-Type': 'application/octet-stream', 'X-Nexora': '1' },
          body: payload,
        });
        if (r.status === 401) { location.replace('/'); return; }
        let j = null; try { j = await r.json(); } catch (_) {}
        if (!r.ok) {
          const e = new Error((j && j.error) || ('HTTP ' + r.status));
          e.code = j && j.error; e.retryAfterSec = j && j.retryAfterSec;
          throw e;
        }
        upMs = Math.max(1, (j && j.ms) ? Number(j.ms) : (performance.now() - t0));
        upBytes = (j && j.bytes) ? Number(j.bytes) : upBytes;
      }
      const upMBps = upBytes / (upMs / 1000) / 1048576;
      const upMbps = (upBytes * 8) / (upMs / 1000) / 1000000;
      if (byId('diagUp')) byId('diagUp').textContent = `${upMbps.toFixed(1)} Мбит/с`;
      if (byId('diagUpSub')) byId('diagUpSub').textContent = `МБ/с: ${upMBps.toFixed(2)} (${fmtSize(upBytes)})`;

      // 4. Load snapshot
      loadNetLoad();
      if (st) st.textContent = `Тест завершён в ${new Date().toLocaleTimeString('ru-RU')}`;
    } catch (e) {
      const msg = opErr(e);
      if (st) st.textContent = msg;
      toast(msg);
    } finally {
      DIAG.running = false;
      if (btn) btn.disabled = false;
    }
  }
  function runHostCheck() {
    if (!isAdmin) return;
    const inp = byId('diagHost'), out = byId('diagHostRes'), btn = byId('diagCheckBtn');
    const host = (inp && inp.value || '').trim();
    if (!host) { toast('Введите локальный IPv4-адрес'); return; }
    if (btn) btn.disabled = true;
    if (out) out.textContent = 'Проверка…';
    api(`/api/net/check?host=${encodeURIComponent(host)}`).then((j) => {
      const ports = (j.openPorts && j.openPorts.length) ? ` · открытые порты: ${j.openPorts.join(', ')}` : ' · стандартные порты закрыты';
      const rtt = j.rttMs != null ? ` (${j.rttMs} мс)` : '';
      if (out) out.textContent = j.reachable ? `Доступен${rtt}${ports}` : `Не отвечает${ports}`;
    }).catch((e) => {
      const msg = opErr(e);
      if (out) out.textContent = msg;
      toast(msg);
    }).finally(() => {
      if (btn) btn.disabled = false;
    });
  }

  /* ---------- УСТРОЙСТВА И LAN ---------- */
  function devTab(t) {
    DEV.tab = t;
    $$('[data-dtab]').forEach((b) => b.classList.toggle('on', b.dataset.dtab === t));
    clearInterval(DEV.timer); clearTimeout(DEV.lanTimer);
    const area = byId('devArea');
    if (!area) return;
    if (t === 'srv') {
      area.innerHTML = `<div class="dev-list" id="devs"><p class="empty">Загрузка…</p></div><p class="nx-note" style="margin-top:10px">${isAdmin ? '' : 'Блокировать устройства может только администратор.'}</p>`;
      loadDevices();
      DEV.timer = setInterval(() => { if (state.view === 'settings' && state.sub === 'devices' && DEV.tab === 'srv') loadDevices(); }, 8000);
    } else {
      area.innerHTML = `
        <div class="sub-bar" style="margin-bottom:10px">
          <p class="nx-note" id="lanInfo">Нажмите «Сканировать»</p>
          <button class="nx-btn primary" id="lanBtn" ${!isAdmin ? 'disabled' : ''}>${isAdmin ? 'Сканировать' : 'Только администратор'}</button>
        </div>
        <div class="prog" style="margin-bottom:12px"><i id="lanProg"></i></div>
        <div class="dev-list" id="lanList"><p class="empty">Загрузка…</p></div>
        <p class="nx-note" style="margin-top:10px">Сканер ищет устройства в той же Wi-Fi сети, что и телефон с сервером.</p>`;
      byId('lanBtn').onclick = () => {
        if (!isAdmin) return;
        byId('lanBtn').disabled = true;
        byId('lanBtn').textContent = 'Идёт поиск…';
        byId('lanProg').style.width = '4%';
        byId('lanInfo').textContent = 'Запуск сканирования…';
        post('/api/lan/scan', {}).then(loadLan).catch((e) => {
          byId('lanBtn').disabled = false;
          byId('lanBtn').textContent = 'Сканировать';
          toast(opErr(e));
        });
      };
      loadLan();
    }
  }
  function loadDevices() {
    api('/api/devices').then((j) => {
      if (state.view !== 'settings' || state.sub !== 'devices' || DEV.tab !== 'srv') return;
      const list = j.devices || [], now = j.now || Date.now();
      const cnt = byId('devCount');
      if (cnt) cnt.textContent = `Всего: ${list.length}, в сети: ${list.filter((d) => d.online).length}`;
      const el = byId('devs');
      if (!el) return;
      el.innerHTML = list.length ? list.map((d) => {
        const k = d.kind === 'pc' ? 'pc' : d.kind === 'tablet' ? 'tablet' : 'phone';
        const role = d.role === 'admin' ? '<span class="bdg adm">админ</span>' : '<span class="bdg usr">пользователь</span>';
        const time = d.online ? `На сайте: ${fmtDur((now - (d.sessionStart || now)) / 1000)}` : `Был в сети: ${fmtDT(d.lastSeen)}`;
        const btn = (isAdmin && !d.me)
          ? `<button class="nx-btn ${d.blocked ? '' : 'danger'}" data-block="${esc(d.id)}" data-to="${d.blocked ? '0' : '1'}">${ico(d.blocked ? 'play' : 'ban')}${d.blocked ? 'Разблокировать' : 'Заблокировать'}</button>`
          : '';
        return `<div class="dev-card glass ${d.online ? 'on' : ''}">
          <div class="dev-ico">${ico(k)}<span class="dev-dot"></span></div>
          <div class="dev-meta">
            <b>${esc(d.name || 'Устройство')}${d.me ? '<span class="bdg me">это вы</span>' : ''}${d.blocked ? '<span class="bdg blk">заблокировано</span>' : ''}</b>
            <small>IP: ${esc(d.ip)} &bull; ${time} &bull; Вход: ${fmtDT(d.lastLogin)}</small>
            <small>Аккаунт: ${d.account ? esc(d.account) : 'без входа'} ${role}</small>
          </div>
          <div class="dev-act">${btn}</div>
        </div>`;
      }).join('') : '<p class="empty">Подключённых устройств нет</p>';
    }).catch((e) => {
      const el = byId('devs');
      if (el) el.innerHTML = `<p class="empty">${e && e.code === 'auth_required' ? 'Список устройств доступен при включённой защите паролем' : 'Не удалось загрузить список устройств'}</p>`;
    });
  }
  function loadLan() {
    api('/api/lan/devices').then((j) => {
      if (state.view !== 'settings' || state.sub !== 'devices' || DEV.tab !== 'lan') return;
      const list = j.devices || [];
      const prog = byId('lanProg'), info = byId('lanInfo'), el = byId('lanList'), btn = byId('lanBtn'), cnt = byId('devCount');
      if (cnt) cnt.textContent = list.length ? `Найдено: ${list.length}` : '';
      if (prog) prog.style.width = `${j.scanning ? (j.progress || 0) : (list.length ? 100 : 0)}%`;
      if (info) info.textContent = j.scanning
        ? `Сканирование… ${j.progress || 0}%${j.subnet ? ` · ${j.subnet}` : ''}`
        : (j.finished ? `Последнее сканирование: ${fmtDT(j.finished)} · найдено: ${list.length}${j.subnet ? ` · ${j.subnet}` : ''}` : 'Нажмите «Сканировать»');
      if (btn) { btn.disabled = !!j.scanning || !isAdmin; btn.textContent = !isAdmin ? 'Только администратор' : (j.scanning ? 'Идёт поиск…' : 'Сканировать'); }
      if (el) {
        el.innerHTML = list.length ? list.map((d) => {
          const k = d.kind === 'phone' ? 'phone' : d.kind === 'tablet' ? 'tablet' : (d.kind === 'router' ? 'wifi' : 'pc');
          const badges = (d.isServer ? '<span class="bdg me">сервер</span>' : '') + (d.isMe ? '<span class="bdg me">это вы</span>' : '') + (d.isGateway ? '<span class="bdg usr">роутер</span>' : '') + (d.connected ? '<span class="bdg adm">на сервере</span>' : '');
          return `<div class="dev-card glass on"><div class="dev-ico">${ico(k)}<span class="dev-dot"></span></div>
            <div class="dev-meta"><b>${esc(d.hostname || d.vendor || 'Неизвестное устройство')}${badges}</b>
            <small>IP: ${esc(d.ip)}${d.mac ? ` · MAC: ${esc(d.mac)}` : ''}${d.rtt != null ? ` · Отклик: ${d.rtt} мс` : ''}</small>
            ${d.vendor && d.hostname ? `<small>Производитель: ${esc(d.vendor)}</small>` : ''}</div></div>`;
        }).join('') : `<p class="empty">${j.scanning ? 'Идёт поиск устройств…' : 'Устройства ещё не найдены'}</p>`;
      }
      clearTimeout(DEV.lanTimer);
      if (j.scanning && DEV.tab === 'lan') DEV.lanTimer = setTimeout(loadLan, 1500);
    }).catch(() => {
      const el = byId('lanList');
      if (el) el.innerHTML = '<p class="empty">Сканер недоступен</p>';
    });
  }

  /* ---------- УЧЁТНЫЕ ЗАПИСИ ---------- */
  function schedText(s) {
    if (!s || s.mode === 'off' || !(s.windows || []).length) return 'Вход в любое время';
    const w = s.windows.map((x) => {
      const d = (x.days && x.days.length && x.days.length < 7) ? x.days.map((i) => DAYN[i]).join(',') : 'каждый день';
      return `${d} ${x.from}–${x.to}`;
    }).join('; ');
    return (s.mode === 'allow' ? 'Вход только: ' : 'Вход запрещён: ') + w;
  }
  function updateBadges(n) {
    state.pendingRegs = n || 0;
    const mb = byId('menuBadge'), nd = byId('navSetDot');
    const show = isAdmin && state.pendingRegs > 0;
    if (mb) { mb.hidden = !show; mb.textContent = String(state.pendingRegs); }
    if (nd) nd.hidden = !show;
  }
  function loadAcc() {
    api('/api/accounts').then(renderAcc).catch((e) => {
      const el = byId('accList');
      if (el) el.innerHTML = `<p class="empty">${e && e.code === 'auth_required' ? 'Учётные записи работают при включённой защите паролем' : 'Не удалось загрузить учётные записи'}</p>`;
    });
  }
  function renderAcc(j) {
    if (state.view !== 'settings' || state.sub !== 'accounts') return;
    ACC.list = j.accounts || [];
    ACC.reg = j.registrationOpen !== false;
    ACC.pend = j.pendingRegs || 0;
    if (j.me) { ME.id = j.me.id; ME.role = j.me.role; if (j.me.name) ME.name = j.me.name; isAdmin = ME.role === 'admin'; }
    updateBadges(ACC.pend);
    byId('accMe').textContent = `Вы: ${ME.name || ''} · ${isAdmin ? 'администратор' : 'пользователь'}`;
    byId('accNew').style.display = isAdmin ? '' : 'none';
    byId('regBar').style.display = isAdmin ? '' : 'none';
    byId('regSw').setAttribute('aria-checked', String(ACC.reg));

    const mine = ACC.list.find((a) => a.id === ME.id);
    let ban = '';
    if (mine && mine.passwordDefault) {
      ban += `<div class="warnbox">${ico('shield')}<span>Вы используете стандартный пароль. Смените его кнопкой «Логин и пароль».${isAdmin ? ' Пока пароль администратора стандартный, управление файлами, блокировка устройств, ограничения и одобрение заявок недоступны.' : ''}</span></div>`;
    }
    if (isAdmin && ACC.pend > 0) {
      ban += `<div class="warnbox">${ico('mail')}<span>Заявок на регистрацию: ${ACC.pend}. Одобрить или отклонить их можно в разделе «Письмо».</span><button class="nx-btn" data-subjump="mail:__req">Открыть</button></div>`;
    }
    byId('accBanner').innerHTML = ban;
    if (ACC.open) return;

    byId('accList').innerHTML = ACC.list.map((a) => {
      const me = a.id === ME.id, canCred = isAdmin || me, canLim = isAdmin && a.role !== 'admin', canDel = isAdmin && a.deletable;
      const left = a.limitMin > 0 ? ((a.remainingSec || 0) <= 0 ? 'лимит исчерпан' : fmtDur(a.remainingSec)) : 'без ограничений';
      return `<div>
        <div class="dev-card glass ${a.online ? 'on' : ''}">
          <div class="dev-ico">${ico(a.role === 'admin' ? 'shield' : 'user')}<span class="dev-dot"></span></div>
          <div class="dev-meta">
            <b>${esc(a.name)} ${a.role === 'admin' ? '<span class="bdg adm">администратор</span>' : '<span class="bdg usr">пользователь</span>'}${me ? '<span class="bdg me">это вы</span>' : ''}${a.passwordDefault ? '<span class="bdg blk">стандартный пароль</span>' : ''}</b>
            <small>${a.online ? `В сети · сессий: ${a.sessions || 1}` : 'Не в сети'}${a.lastLogin ? ` · последний вход: ${fmtDT(a.lastLogin)}` : ''}</small>
            ${a.role === 'admin' ? '' : `<small>Сегодня: ${fmtDur(a.usedToday || 0)} · Осталось: ${left} · ${esc(schedText(a.schedule))}</small><small>Секретный вопрос: ${a.hasSecretQuestion ? esc(a.secretQuestion || 'задан') : 'не задан (восстановление отключено)'}</small>`}
          </div>
          <div class="dev-act">
            ${canCred ? `<button class="nx-btn" data-accedit="${esc(a.id)}">${ico('edit')}Логин и пароль</button>` : ''}
            ${canLim ? `<button class="nx-btn" data-acclim="${esc(a.id)}">${ico('clock')}Ограничения</button>` : ''}
            ${canDel ? `<button class="nx-btn danger" data-accdel="${esc(a.id)}">${ico('trash')}Удалить</button>` : ''}
          </div>
        </div>
        <div class="acc-panel glass" id="ap-${esc(a.id)}"></div>
      </div>`;
    }).join('');
  }
  function closeAccPanel() {
    if (ACC.open) {
      const p = byId('ap-' + ACC.open.id);
      if (p) { p.classList.remove('open'); p.innerHTML = ''; }
    }
    ACC.open = null;
    ACC.cb = null;
  }
  function openAccPanel(id, type, title, hint, body, ok, cb) {
    if (ACC.open && ACC.open.id === id && ACC.open.type === type) { closeAccPanel(); return null; }
    closeAccPanel();
    const p = byId('ap-' + id);
    if (!p) return null;
    ACC.open = { id, type };
    ACC.cb = cb;
    p.innerHTML = `<div class="acc-form">
      <h4>${esc(title)}</h4>
      ${hint ? `<p class="nx-note">${esc(hint)}</p>` : ''}
      ${body}
      <div class="acc-err" id="apErr"></div>
      <div style="display:flex;justify-content:flex-end;gap:10px">
        <button class="nx-btn" data-apcancel type="button">Отмена</button>
        <button class="nx-btn primary" data-apsave type="button">${esc(ok || 'Сохранить')}</button>
      </div>
    </div>`;
    p.classList.add('open');
    return p;
  }
  const SECRET_QS = [
    'Девичья фамилия матери',
    'Кличка первого питомца',
    'Город рождения',
    'Любимая книга',
  ];
  function secretSelectHtml(selId, cusId, curQ) {
    const isPreset = curQ && SECRET_QS.includes(curQ);
    const isCustom = curQ && !isPreset;
    const opts = ['<option value="">Не менять секретный вопрос</option>']
      .concat(SECRET_QS.map((q) => `<option value="${esc(q)}"${curQ === q ? ' selected' : ''}>${esc(q)}</option>`))
      .concat([`<option value="__custom"${isCustom ? ' selected' : ''}>Свой вопрос…</option>`])
      .join('');
    return `<select class="nx-input" id="${selId}">${opts}</select>
      <input class="nx-input" id="${cusId}" placeholder="Введите свой секретный вопрос" value="${isCustom ? esc(curQ) : ''}" style="display:${isCustom ? '' : 'none'}" maxlength="120" autocomplete="off">`;
  }
  function bindSecretSelect(p, selId, cusId) {
    const sel = byId(selId), cus = byId(cusId);
    if (!sel || !cus) return;
    sel.onchange = () => {
      cus.style.display = sel.value === '__custom' ? '' : 'none';
      if (sel.value === '__custom') cus.focus();
    };
  }
  function readSecretPair(selId, cusId, ansId, ans2Id) {
    const sel = byId(selId), cus = byId(cusId), a1 = byId(ansId), a2 = byId(ans2Id);
    if (!sel || !a1 || !a2) return { active: false };
    const q = sel.value === '__custom' ? (cus ? cus.value.trim() : '') : sel.value;
    const ans = a1.value.trim(), ans2 = a2.value.trim();
    const anyTyped = !!(sel.value && (sel.value !== (sel.dataset.initial || '') || ans || ans2)) || !!(ans || ans2);
    if (!anyTyped) return { active: false };
    if (!q || q.length < 3) return { error: 'Укажите секретный вопрос (от 3 символов)' };
    if (!ans || ans.replace(/\s+/g, ' ').length < 3) return { error: 'Ответ на секретный вопрос — минимум 3 символа' };
    if (ans.toLowerCase().replace(/\s+/g, ' ') !== ans2.toLowerCase().replace(/\s+/g, ' ')) return { error: 'Ответы на секретный вопрос не совпадают' };
    return { active: true, question: q, answer: ans };
  }
  function accCredPanel(a) {
    const me = a.id === ME.id;
    const isRegUser = a.role !== 'admin';
    const curPh = me ? 'Текущий пароль' : (isAdmin ? 'Ваш пароль администратора или текущий пароль' : 'Текущий пароль');
    const secHtml = isRegUser
      ? `<label>Восстановление пароля (секретный вопрос)</label>
         ${secretSelectHtml('fSecSel', 'fSecCustom', a.secretQuestion || '')}
         <input class="nx-input" id="fSecA" type="password" placeholder="Новый ответ (пусто = не менять, мин. 3 симв.)" autocomplete="off">
         <input class="nx-input" id="fSecA2" type="password" placeholder="Повторите ответ" autocomplete="off">`
      : '';
    const p = openAccPanel(
      a.id, 'cred', `Логин и пароль: ${a.name}`,
      a.passwordDefault ? 'Пароль ещё не менялся: введите стандартный пароль из приложения.' : 'Введите текущий пароль для подтверждения.',
      `<input class="nx-input" id="fCur" type="password" placeholder="${esc(curPh)}" autocomplete="off">
       <input class="nx-input" id="fLogin" placeholder="Новый логин" value="${esc(a.name)}" autocomplete="off" autocapitalize="none" spellcheck="false">
       <input class="nx-input" id="fNew" type="password" placeholder="Новый пароль (пусто = не менять)" autocomplete="new-password">
       <input class="nx-input" id="fNew2" type="password" placeholder="Повторите новый пароль" autocomplete="new-password">
       ${secHtml}`,
      'Сохранить',
      (done, fail) => {
        const c = byId('fCur').value, l = byId('fLogin').value.trim(), pass = byId('fNew').value, p2 = byId('fNew2').value;
        if (!c) { fail('Введите текущий пароль'); return; }
        if (!/^[A-Za-z0-9._-]{3,32}$/.test(l)) { fail('Логин: 3–32 символа (буквы, цифры, . _ -)'); return; }
        if (pass && pass !== p2) { fail('Новые пароли не совпадают'); return; }
        const sec = isRegUser ? readSecretPair('fSecSel', 'fSecCustom', 'fSecA', 'fSecA2') : { active: false };
        if (sec.error) { fail(sec.error); return; }
        if (l === a.name && !pass && !sec.active) { fail('Ничего не изменено'); return; }
        const payload = { id: a.id, currentPassword: c, newLogin: l !== a.name ? l : null, newPassword: pass || null };
        if (sec.active) { payload.secretQuestion = sec.question; payload.secretAnswer = sec.answer; }
        post('/api/accounts/update', payload)
          .then((r) => { if (me && r && r.name) ME.name = r.name; toast('Данные сохранены'); done(); })
          .catch((e) => fail(opErr(e)));
      }
    );
    if (p && isRegUser) {
      const sel = byId('fSecSel');
      if (sel) sel.dataset.initial = sel.value;
      bindSecretSelect(p, 'fSecSel', 'fSecCustom');
    }
  }
  function accCreatePanel() {
    const p = openAccPanel(
      'create', 'create', 'Новая учётная запись',
      'Пользователь сразу сможет войти. Права: только просмотр и скачивание.',
      `<input class="nx-input" id="cLogin" placeholder="Логин (3–32 символа)" autocomplete="off" autocapitalize="none" spellcheck="false">
       <input class="nx-input" id="cPass" type="password" placeholder="Пароль (от 4 символов)" autocomplete="new-password">
       <input class="nx-input" id="cPass2" type="password" placeholder="Повторите пароль" autocomplete="new-password">
       <label>Секретный вопрос для сброса пароля (необязательно)</label>
       ${secretSelectHtml('cSecSel', 'cSecCustom', '')}
       <input class="nx-input" id="cSecA" type="password" placeholder="Ответ на секретный вопрос (от 3 символов)" autocomplete="off">
       <input class="nx-input" id="cSecA2" type="password" placeholder="Повторите ответ" autocomplete="off">`,
      'Создать',
      (done, fail) => {
        const l = byId('cLogin').value.trim(), pass = byId('cPass').value, p2 = byId('cPass2').value;
        if (!/^[A-Za-z0-9._-]{3,32}$/.test(l)) { fail('Логин: 3–32 символа (буквы, цифры, . _ -)'); return; }
        if (pass.length < 4) { fail('Пароль слишком короткий'); return; }
        if (pass !== p2) { fail('Пароли не совпадают'); return; }
        const sec = readSecretPair('cSecSel', 'cSecCustom', 'cSecA', 'cSecA2');
        if (sec.error) { fail(sec.error); return; }
        const payload = { name: l, password: pass };
        if (sec.active) { payload.secretQuestion = sec.question; payload.secretAnswer = sec.answer; }
        post('/api/accounts/create', payload).then(() => { toast('Учётная запись создана'); done(); }).catch((e) => fail(opErr(e)));
      }
    );
    if (p) bindSecretSelect(p, 'cSecSel', 'cSecCustom');
  }
  function winRowHtml(w) {
    const days = [1, 2, 3, 4, 5, 6, 0].map((d) => {
      const on = !w.days || !w.days.length || w.days.includes(d);
      return `<button class="dchip ${on ? 'on' : ''}" data-d="${d}" type="button">${DAYN[d]}</button>`;
    }).join('');
    return `<div class="win-row"><div class="days">${days}</div>
      <input class="nx-input" type="time" value="${esc(w.from || '08:00')}">
      <span>–</span>
      <input class="nx-input" type="time" value="${esc(w.to || '22:00')}">
      <button class="nx-btn" data-rw type="button">${ico('close')}</button></div>`;
  }
  function accLimitPanel(a) {
    const sc = a.schedule || { mode: 'off', windows: [] };
    let mode = sc.mode || 'off';
    const p = openAccPanel(
      a.id, 'lim', `Ограничения: ${a.name}`,
      'Действуют по времени телефона. Если конец раньше начала — интервал переходит через полночь.',
      `<label>Лимит времени в день, минут (0 = без лимита)</label>
       <input class="nx-input" id="fLim" type="number" min="0" max="1440" value="${a.limitMin || 0}">
       <label>Когда можно входить</label>
       <div class="seg"><button class="segb" data-lm="off" type="button">В любое время</button><button class="segb" data-lm="allow" type="button">Только в интервалы</button><button class="segb" data-lm="deny" type="button">Запретить интервалы</button></div>
       <div id="fWin" style="display:flex;flex-direction:column;gap:8px"></div>
       <button class="nx-btn" id="fAddWin" type="button" style="align-self:flex-start">+ Добавить интервал</button>`,
      'Сохранить',
      (done, fail) => {
        const lim = parseInt(byId('fLim').value, 10) || 0;
        if (lim < 0 || lim > 1440) { fail('Лимит: от 0 до 1440 минут'); return; }
        const wins = [];
        if (mode !== 'off') {
          const rows = $$('#fWin .win-row');
          for (const row of rows) {
            const ds = $$('.dchip.on', row).map((c) => parseInt(c.dataset.d, 10));
            const ts = $$('input', row), f = ts[0].value, t = ts[1].value;
            if (!f || !t || f === t) { fail('Укажите время начала и конца'); return; }
            wins.push({ days: ds.length ? ds : [0, 1, 2, 3, 4, 5, 6], from: f, to: t });
          }
          if (!wins.length) { fail('Добавьте хотя бы один интервал'); return; }
        }
        post('/api/accounts/limits', { id: a.id, limitMin: lim, schedule: { mode, windows: wins } })
          .then(() => { toast('Ограничения сохранены'); done(); })
          .catch((e) => fail(opErr(e)));
      }
    );
    if (!p) return;
    const paint = () => {
      $$('[data-lm]', p).forEach((b) => b.classList.toggle('on', b.dataset.lm === mode));
      byId('fWin').style.display = byId('fAddWin').style.display = mode === 'off' ? 'none' : 'flex';
    };
    byId('fWin').innerHTML = (sc.windows || []).map(winRowHtml).join('');
    paint();
    p.onclick = (e) => {
      const m = e.target.closest('[data-lm]');
      if (m) {
        mode = m.dataset.lm;
        if (mode !== 'off' && !byId('fWin').children.length) byId('fWin').innerHTML = winRowHtml({ days: [1, 2, 3, 4, 5], from: '08:00', to: '22:00' });
        paint();
        return;
      }
      const d = e.target.closest('.dchip');
      if (d) { d.classList.toggle('on'); return; }
      const r = e.target.closest('[data-rw]');
      if (r) { r.closest('.win-row')?.remove(); return; }
      if (e.target.closest('#fAddWin')) byId('fWin').insertAdjacentHTML('beforeend', winRowHtml({ days: [1, 2, 3, 4, 5, 6, 0], from: '08:00', to: '22:00' }));
    };
  }

  /* ---------- ПИСЬМО (чат и заявки) ---------- */
  const fmtClock = (s) => { s = Math.round(s); return `${Math.floor(s / 60)}:${two(s % 60)}`; };
  function startMail() {
    clearInterval(MAIL.timer);
    loadPeers();
    MAIL.tick = 0;
    MAIL.timer = setInterval(() => {
      if (state.view !== 'settings' || state.sub !== 'mail') { clearInterval(MAIL.timer); return; }
      MAIL.tick++;
      if (MAIL.tick % 3 === 0) loadPeers();
      if (MAIL.peer === '__req') loadRegs();
      else if (MAIL.peer) loadMsgs();
    }, 2500);
  }
  function loadPeers() {
    api('/api/mail/peers').then((j) => {
      if (state.view !== 'settings' || state.sub !== 'mail') return;
      MAIL.peers = j.peers || [];
      MAIL.me = j.me || {};
      updateBadges(j.pendingRegs || 0);
      if (byId('mName')) byId('mName').textContent = MAIL.me.name || 'Моё имя';
      renderPeers();
      if (MAIL.peer === '__req') openReq();
    }).catch((e) => {
      const el = byId('mThreads');
      if (el) el.innerHTML = `<p class="empty">${e && e.code === 'auth_required' ? 'Письмо работает при включённой защите паролем' : 'Не удалось загрузить список'}</p>`;
    });
  }
  function renderPeers() {
    const el = byId('mThreads');
    if (!el) return;
    const req = isAdmin
      ? `<button class="mt ${MAIL.peer === '__req' ? 'on' : ''}" data-mreq><div class="av on">${ico('user')}<i></i></div><div class="tx"><b>Заявки на регистрацию</b><small>${state.pendingRegs ? `Ожидают: ${state.pendingRegs}` : 'Нет новых заявок'}</small></div>${state.pendingRegs ? `<span class="un">${state.pendingRegs}</span>` : ''}</button>`
      : '';
    const peersHtml = MAIL.peers.length ? MAIL.peers.map((p) => {
      const k = p.kind === 'pc' ? 'pc' : p.kind === 'tablet' ? 'tablet' : 'phone';
      const last = p.last ? (p.last.kind === 'voice' ? 'Голосовое сообщение' : p.last.kind === 'file' ? 'Файл' : (p.last.preview || '')) : 'Нет сообщений';
      return `<button class="mt ${MAIL.peer === p.id ? 'on' : ''}" data-mpeer="${esc(p.id)}"><div class="av ${p.online ? 'on' : ''}">${ico(k)}<i></i></div><div class="tx"><b>${esc(p.name)}</b><small>${esc(last)}</small></div>${p.unread ? `<span class="un">${p.unread}</span>` : ''}</button>`;
    }).join('') : '<p class="empty">Других устройств пока нет</p>';
    el.innerHTML = req + peersHtml;
  }
  function openPeer(id) {
    MAIL.peer = id; MAIL.lastId = 0;
    byId('mComp').style.display = '';
    byId('mMsgs').innerHTML = '';
    const p = MAIL.peers.find((x) => x.id === id) || {};
    byId('mTitle').textContent = p.name || 'Собеседник';
    byId('mSub').textContent = p.online ? 'в сети' : 'не в сети';
    renderPeers();
    loadMsgs(true);
    post('/api/mail/read', { peer: id }).catch(() => {});
  }
  function openReq() {
    MAIL.peer = '__req'; MAIL.lastId = 0;
    byId('mTitle').textContent = 'Заявки на регистрацию';
    byId('mSub').textContent = 'Одобряет администратор';
    byId('mProg').textContent = '';
    byId('mComp').style.display = 'none';
    byId('mMsgs').innerHTML = '';
    renderPeers();
    loadRegs();
  }
  function loadRegs() {
    api('/api/registrations').then((j) => {
      if (state.view !== 'settings' || state.sub !== 'mail' || MAIL.peer !== '__req') return;
      const list = j.requests || [];
      updateBadges(list.length);
      byId('mMsgs').innerHTML = list.length ? list.map((r) => `
        <div class="dev-card glass">
          <div class="dev-ico">${ico('user')}</div>
          <div class="dev-meta"><b>${esc(r.login)} <span class="bdg usr">ожидает</span></b><small>Устройство: ${esc(r.name || 'неизвестно')} · IP: ${esc(r.ip || '')} · ${fmtDT(r.t)}</small></div>
          <div class="dev-act"><button class="nx-btn primary" data-regok="${esc(r.id)}">Одобрить</button><button class="nx-btn danger" data-regno="${esc(r.id)}">Отклонить</button></div>
        </div>`).join('') : '<p class="empty">Новых заявок нет</p>';
    }).catch((e) => {
      const box = byId('mMsgs');
      if (box) box.innerHTML = `<p class="empty">${esc(opErr(e))}</p>`;
    });
  }
  function msgHtml(m) {
    let body = '', f = m.file || {};
    if (m.kind === 'voice') body = `<audio controls preload="none" src="/api/mail/file?id=${encodeURIComponent(f.id)}"></audio>${f.dur ? `<small class="mt2" style="margin:0">${fmtClock(f.dur)}</small>` : ''}`;
    else if (m.kind === 'file') body = `<a class="mfile" href="/api/mail/file?id=${encodeURIComponent(f.id)}&dl=1" download="${esc(f.name)}">${ico('file')}<span><b>${esc(f.name)}</b><small>${fmtSize(f.size)}</small></span>${ico('dl')}</a>`;
    if (m.kind === 'text' || m.text) body += `<span>${esc(m.text).replace(/\n/g, '<br>')}</span>`;
    return `<div class="mb ${m.mine ? 'me' : ''}" data-mid="${m.id}">${body}<small class="mt2">${fmtDT(m.t)}${m.mine ? (m.read ? ' · прочитано' : ' · отправлено') : ''}${(m.mine || isAdmin) ? ` <a data-mdel="${m.id}">удалить</a>` : ''}</small></div>`;
  }
  function loadMsgs(first) {
    const peer = MAIL.peer;
    if (!peer || peer === '__req') return;
    api(`/api/mail/messages?peer=${encodeURIComponent(peer)}&after=${MAIL.lastId}`).then((j) => {
      if (peer !== MAIL.peer || state.view !== 'settings' || state.sub !== 'mail') return;
      const box = byId('mMsgs'), list = j.messages || [];
      if (!box) return;
      const near = (box.scrollHeight - box.scrollTop - box.clientHeight) < 60;
      if (list.length) {
        if (first) box.innerHTML = '';
        box.insertAdjacentHTML('beforeend', list.map(msgHtml).join(''));
        list.forEach((m) => { if (m.id > MAIL.lastId) MAIL.lastId = m.id; });
        if (first || near) box.scrollTop = box.scrollHeight;
        if (!first && list.some((m) => !m.mine)) post('/api/mail/read', { peer }).catch(() => {});
      } else if (first) {
        box.innerHTML = '<p class="empty">Сообщений пока нет. Напишите первым.</p>';
      }
      (j.deleted || []).forEach((id) => box.querySelector(`[data-mid="${id}"]`)?.remove());
    }).catch(() => {});
  }
  function mailUpload(blob, name, mime, dur) {
    return new Promise((res, rej) => {
      if (DEMO_MODE) { setTimeout(() => res({ fileId: 'demo' }), 250); return; }
      const x = new XMLHttpRequest();
      x.open('POST', `/api/mail/upload?name=${encodeURIComponent(name)}&mime=${encodeURIComponent(mime || '')}&dur=${dur || 0}`);
      x.setRequestHeader('X-Nexora', '1');
      x.upload.onprogress = (e) => { if (e.lengthComputable && byId('mProg')) byId('mProg').textContent = `Отправка: ${Math.round(e.loaded * 100 / e.total)}%`; };
      x.onload = () => {
        let j = {}; try { j = JSON.parse(x.responseText); } catch (_) {}
        if (x.status >= 200 && x.status < 300) res(j);
        else { const e = new Error(j.error || ('HTTP ' + x.status)); e.code = j.error; rej(e); }
      };
      x.onerror = () => rej(new Error('net'));
      x.send(blob);
    });
  }
  function sendBlob(blob, name, mime, dur, kind) {
    if (!MAIL.peer || MAIL.peer === '__req') { toast('Выберите собеседника'); return; }
    byId('mProg').textContent = 'Отправка…';
    mailUpload(blob, name, mime, dur)
      .then((r) => post('/api/mail/send', { to: MAIL.peer, kind, fileId: r.fileId, text: '' }))
      .then(() => { if (byId('mProg')) byId('mProg').textContent = ''; loadMsgs(); })
      .catch((e) => {
        if (byId('mProg')) byId('mProg').textContent = '';
        toast(e && e.code === 'too_large' ? 'Файл слишком большой' : e && e.code === 'quota' ? 'Хранилище письма заполнено' : 'Не удалось отправить');
      });
  }
  function bindMailEvents() {
    const sendText = () => {
      const inp = byId('mText'), t = inp.value.trim();
      if (!MAIL.peer || MAIL.peer === '__req') { toast('Выберите собеседника'); return; }
      if (!t) return;
      inp.value = '';
      post('/api/mail/send', { to: MAIL.peer, kind: 'text', text: t }).then(() => loadMsgs()).catch((e) => {
        inp.value = t;
        toast(e && e.code === 'blocked' ? 'Устройство заблокировано' : 'Не удалось отправить');
      });
    };
    byId('mSend').onclick = sendText;
    byId('mText').onkeydown = (e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendText(); } };
    byId('mAttach').onclick = () => { if (!MAIL.peer || MAIL.peer === '__req') { toast('Выберите собеседника'); return; } byId('mFile').click(); };
    byId('mFile').onchange = function () {
      const f = this.files && this.files[0];
      this.value = '';
      if (f) sendBlob(f, f.name, f.type || 'application/octet-stream', 0, 'file');
    };
    byId('mMic').onclick = () => {
      if (MAIL.rec && MAIL.rec.state !== 'inactive') { MAIL.rec.stop(); return; }
      if (!MAIL.peer || MAIL.peer === '__req') { toast('Выберите собеседника'); return; }
      if (!(window.isSecureContext && navigator.mediaDevices && navigator.mediaDevices.getUserMedia && window.MediaRecorder)) {
        toast('Голосовые сообщения работают только по HTTPS (Настройки → Сеть)');
        return;
      }
      navigator.mediaDevices.getUserMedia({ audio: true }).then((stream) => {
        const mt = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4'].find((t) => MediaRecorder.isTypeSupported(t)) || '';
        const r = mt ? new MediaRecorder(stream, { mimeType: mt }) : new MediaRecorder(stream);
        MAIL.chunks = []; MAIL.rec = r; MAIL.t0 = Date.now();
        r.ondataavailable = (e) => { if (e.data && e.data.size) MAIL.chunks.push(e.data); };
        r.onstop = () => {
          stream.getTracks().forEach((tr) => tr.stop());
          MAIL.rec = null;
          byId('mMic')?.classList.remove('rec');
          const dur = Math.round((Date.now() - MAIL.t0) / 1000), type = r.mimeType || mt || 'audio/webm';
          if (dur < 1) { toast('Слишком короткое сообщение'); return; }
          sendBlob(new Blob(MAIL.chunks, { type }), `voice-${Date.now()}${type.includes('mp4') ? '.m4a' : '.webm'}`, type, dur, 'voice');
        };
        r.start();
        byId('mMic')?.classList.add('rec');
      }).catch(() => toast('Нет доступа к микрофону'));
    };
    byId('mName').onclick = () => {
      dlg({ title: 'Ваше имя в письме', text: 'Так вас увидят другие пользователи сервера.', input: MAIL.me.name || '', ok: 'Сохранить' }, (v) => {
        v = (v || '').trim();
        if (v) post('/api/mail/profile', { name: v }).then(() => { toast('Имя сохранено'); loadPeers(); }).catch(() => toast('Не удалось сохранить имя'));
      });
    };
  }

  /* ---------- ИНСТРУМЕНТЫ (таймер, секундомер, калькулятор) ---------- */
  const TM = { run: false, paused: false, end: 0, left: 0, tid: 0 };
  const SW = { run: false, t0: 0, acc: 0, tid: 0, laps: [] };
  const CALC = { e: '', done: false };
  let activeTool = 'timer';

  function beep() {
    try {
      const C = window.AudioContext || window.webkitAudioContext, ctx = new C();
      [0, 0.3, 0.6, 0.9].forEach((t) => {
        const o = ctx.createOscillator(), g = ctx.createGain();
        o.frequency.value = 880; g.gain.value = 0.2;
        o.connect(g); g.connect(ctx.destination);
        o.start(ctx.currentTime + t); o.stop(ctx.currentTime + t + 0.18);
      });
    } catch (_) {}
    if (navigator.vibrate) navigator.vibrate([250, 120, 250]);
  }
  function tmFmt(s) { s = Math.max(0, Math.ceil(s)); return `${two(Math.floor(s / 3600))}:${two(Math.floor(s % 3600 / 60))}:${two(s % 60)}`; }
  function swFmt(ms) { const t = Math.floor(ms / 10), cs = t % 100, s = Math.floor(t / 100) % 60, m = Math.floor(t / 6000); return `${two(m)}:${two(s)}.${two(cs)}`; }
  const swNow = () => SW.acc + (SW.run ? Date.now() - SW.t0 : 0);

  function calcParse(src) {
    const s = src.replace(/×/g, '*').replace(/÷/g, '/').replace(/−/g, '-').replace(/,/g, '.').replace(/\s+/g, '');
    let i = 0;
    const peek = () => s.charAt(i);
    function expr() { let v = term(); while (peek() === '+' || peek() === '-') { const o = s.charAt(i++); const r = term(); v = o === '+' ? v + r : v - r; } return v; }
    function term() { let v = factor(); while (peek() === '*' || peek() === '/') { const o = s.charAt(i++); const r = factor(); v = o === '*' ? v * r : v / r; } return v; }
    function factor() { const b = unary(); if (peek() === '^') { i++; return Math.pow(b, factor()); } return b; }
    function unary() { if (peek() === '-') { i++; return -unary(); } if (peek() === '+') { i++; return unary(); } return post(); }
    function post() { let v = prim(); while (peek() === '%') { i++; v /= 100; } return v; }
    function prim() {
      const c = peek();
      if (c === '(') { i++; const v = expr(); if (peek() !== ')') throw new Error('p'); i++; return v; }
      if (s.substr(i, 5) === 'sqrt(') { i += 5; const q = expr(); if (peek() !== ')') throw new Error('p'); i++; if (q < 0) throw new Error('neg'); return Math.sqrt(q); }
      const m = /^\d+(\.\d+)?|^\.\d+/.exec(s.slice(i)); if (!m) throw new Error('n'); i += m[0].length; return parseFloat(m[0]);
    }
    const r = expr(); if (i !== s.length) throw new Error('x'); return r;
  }
  const calcClean = (e) => {
    e = e.replace(/[+×÷−^]+$/, '');
    const o = (e.match(/\(/g) || []).length;
    let c = (e.match(/\)/g) || []).length;
    while (c < o) { e += ')'; c++; }
    return e;
  };
  const calcFmt = (v) => (!isFinite(v) ? 'Ошибка' : String(parseFloat(v.toPrecision(12))));

  function renderTools(panel) {
    panel.innerHTML = `
      <div class="sub-bar">
        <div class="seg">
          <button class="segb ${activeTool === 'timer' ? 'on' : ''}" data-tool="timer">Таймер</button>
          <button class="segb ${activeTool === 'stopwatch' ? 'on' : ''}" data-tool="stopwatch">Секундомер</button>
          <button class="segb ${activeTool === 'calc' ? 'on' : ''}" data-tool="calc">Калькулятор</button>
        </div>
      </div>
      <div id="toolHost"></div>`;
    paintTool();
  }
  function paintTool() {
    const host = byId('toolHost');
    if (!host) return;
    $$('[data-tool]').forEach((b) => b.classList.toggle('on', b.dataset.tool === activeTool));
    if (activeTool === 'timer') {
      const disp = TM.run ? tmFmt((TM.end - Date.now()) / 1000) : (TM.paused ? tmFmt(TM.left) : '00:00:00');
      host.innerHTML = `<div class="tool-card glass ${TM.run ? 'run' : ''}" id="tmCard">
        <div class="tdisp" id="tmDisp">${disp}</div>
        <div class="ttime">
          <div class="tf"><input class="nx-input" id="tmH" type="number" min="0" max="99" placeholder="00"><small>часы</small></div><span>:</span>
          <div class="tf"><input class="nx-input" id="tmM" type="number" min="0" max="59" placeholder="00"><small>минуты</small></div><span>:</span>
          <div class="tf"><input class="nx-input" id="tmS" type="number" min="0" max="59" placeholder="00"><small>секунды</small></div>
        </div>
        <div class="chips">${[60, 300, 600, 900, 1800].map((s) => `<button class="chip" data-tp="${s}">${s / 60} мин</button>`).join('')}</div>
        <div class="trow"><button class="nx-btn" id="tmReset">Сброс</button><button class="nx-btn primary" id="tmGo">${TM.run ? 'Пауза' : (TM.paused ? 'Продолжить' : 'Старт')}</button></div>
      </div>`;
      const tmTick = () => {
        const left = (TM.end - Date.now()) / 1000;
        if (left <= 0) {
          clearInterval(TM.tid); TM.run = false; TM.paused = false; TM.left = 0;
          if (byId('tmCard')) byId('tmCard').classList.remove('run');
          if (byId('tmDisp')) byId('tmDisp').textContent = '00:00:00';
          if (byId('tmGo')) byId('tmGo').textContent = 'Старт';
          beep();
          dlg({ title: 'Таймер', text: 'Время вышло', ok: 'OK', cancel: false }, () => {});
          return;
        }
        TM.left = left;
        if (byId('tmDisp')) byId('tmDisp').textContent = tmFmt(left);
      };
      if (TM.run) { clearInterval(TM.tid); TM.tid = setInterval(tmTick, 200); }
      byId('tmGo').onclick = () => {
        if (TM.run) { clearInterval(TM.tid); TM.run = false; TM.paused = true; TM.left = (TM.end - Date.now()) / 1000; byId('tmGo').textContent = 'Продолжить'; byId('tmCard').classList.remove('run'); return; }
        const inpS = (parseInt(byId('tmH').value, 10) || 0) * 3600 + (parseInt(byId('tmM').value, 10) || 0) * 60 + (parseInt(byId('tmS').value, 10) || 0);
        const s = TM.paused ? TM.left : inpS;
        if (s <= 0) { toast('Задайте время'); return; }
        TM.end = Date.now() + s * 1000; TM.run = true; TM.paused = false;
        byId('tmGo').textContent = 'Пауза'; byId('tmCard').classList.add('run');
        clearInterval(TM.tid); TM.tid = setInterval(tmTick, 200); tmTick();
      };
      byId('tmReset').onclick = () => { clearInterval(TM.tid); TM.run = false; TM.paused = false; TM.left = 0; byId('tmCard').classList.remove('run'); byId('tmDisp').textContent = '00:00:00'; byId('tmGo').textContent = 'Старт'; };
      $$('[data-tp]', host).forEach((b) => {
        b.onclick = () => {
          const s = parseInt(b.dataset.tp, 10);
          byId('tmH').value = Math.floor(s / 3600) || ''; byId('tmM').value = Math.floor(s % 3600 / 60) || ''; byId('tmS').value = s % 60 || '';
          clearInterval(TM.tid); TM.run = false; TM.paused = false; TM.left = 0;
          byId('tmCard').classList.remove('run'); byId('tmDisp').textContent = tmFmt(s); byId('tmGo').textContent = 'Старт';
        };
      });
    } else if (activeTool === 'stopwatch') {
      const renderLaps = () => SW.laps.map((l, i) => `<div><span>Круг ${i + 1}</span><span>${swFmt(l.lap)}</span><span>${swFmt(l.total)}</span></div>`).reverse().join('');
      host.innerHTML = `<div class="tool-card glass ${SW.run ? 'run' : ''}">
        <div class="tdisp" id="swDisp">${swFmt(swNow())}</div>
        <div class="trow"><button class="nx-btn" id="swReset">Сброс</button><button class="nx-btn ${SW.run ? 'danger' : 'primary'}" id="swGo">${SW.run ? 'Стоп' : (SW.acc > 0 ? 'Далее' : 'Старт')}</button><button class="nx-btn" id="swLap">Круг</button></div>
        <div class="laps" id="swLaps">${renderLaps()}</div>
      </div>`;
      const swDraw = () => { if (byId('swDisp')) byId('swDisp').textContent = swFmt(swNow()); };
      if (SW.run) { clearInterval(SW.tid); SW.tid = setInterval(swDraw, 40); }
      byId('swGo').onclick = () => {
        if (SW.run) { SW.acc += Date.now() - SW.t0; SW.run = false; clearInterval(SW.tid); swDraw(); }
        else { SW.t0 = Date.now(); SW.run = true; clearInterval(SW.tid); SW.tid = setInterval(swDraw, 40); }
        const btn = byId('swGo');
        btn.textContent = SW.run ? 'Стоп' : (SW.acc > 0 ? 'Далее' : 'Старт');
        btn.className = `nx-btn ${SW.run ? 'danger' : 'primary'}`;
      };
      byId('swLap').onclick = () => {
        if (!SW.run) return;
        const now = swNow(), prev = SW.laps.length ? SW.laps[SW.laps.length - 1].total : 0;
        SW.laps.push({ total: now, lap: now - prev });
        byId('swLaps').innerHTML = renderLaps();
      };
      byId('swReset').onclick = () => {
        clearInterval(SW.tid);
        SW.run = false; SW.t0 = 0; SW.acc = 0; SW.laps = [];
        byId('swDisp').textContent = '00:00.00';
        byId('swLaps').innerHTML = '';
        byId('swGo').textContent = 'Старт';
        byId('swGo').className = 'nx-btn primary';
      };
    } else {
      const keys = (list, cls = '') => list.map((k) => {
        const c = 'ckey' + (cls ? ' ' + cls : '') + (/^[÷×−+]$/.test(k) ? ' op' : '') + (k === '=' ? ' eq' : '');
        return `<button class="${c}" data-ck="${k}" type="button">${k}</button>`;
      }).join('');
      host.innerHTML = `<div class="tool-card glass" style="max-width:860px"><div class="tcalc">
        <div class="cleft"><div class="cdisp"><small id="cExpr"></small><b id="cRes">0</b></div><div class="cfn">${keys(['(', ')', '√', 'x²'], 'fn')}</div></div>
        <div class="cpad">${keys(['C', '⌫', '%', '÷', '7', '8', '9', '×', '4', '5', '6', '−', '1', '2', '3', '+', '±', '0', '.', '='])}</div>
      </div></div>`;
      const draw = () => {
        byId('cExpr').textContent = CALC.e;
        let r = '';
        try { if (CALC.e && /[\d)%]$/.test(CALC.e)) r = calcFmt(calcParse(calcClean(CALC.e))); } catch (_) {}
        byId('cRes').textContent = r || '0';
      };
      draw();
      host.onclick = (e) => {
        const b = e.target.closest('[data-ck]');
        if (!b) return;
        const k = b.dataset.ck;
        if (k === 'C') { CALC.e = ''; CALC.done = false; byId('cRes').textContent = '0'; byId('cExpr').textContent = ''; return; }
        if (k === '⌫') { CALC.e = CALC.e.replace(/(sqrt\(|.)$/, ''); CALC.done = false; draw(); return; }
        if (k === '=') {
          if (!CALC.e) return;
          try { const v = calcFmt(calcParse(calcClean(CALC.e))); byId('cExpr').textContent = `${CALC.e} =`; byId('cRes').textContent = v; CALC.e = v === 'Ошибка' ? '' : v; CALC.done = true; }
          catch (_) { byId('cRes').textContent = 'Ошибка'; CALC.e = ''; CALC.done = true; }
          return;
        }
        if (CALC.done && /[\d.(√]/.test(k)) CALC.e = '';
        CALC.done = false;
        if (k === '±') { CALC.e = CALC.e ? (CALC.e.charAt(0) === '−' ? CALC.e.slice(1) : '−' + CALC.e) : '−'; draw(); return; }
        if (k === '√') { CALC.e += 'sqrt('; draw(); return; }
        if (k === 'x²') { if (/[\d)]$/.test(CALC.e)) CALC.e += '^2'; draw(); return; }
        if (/^[+×÷−]$/.test(k) && !CALC.e && k !== '−') return;
        if (/^[+×÷−]$/.test(k)) CALC.e = CALC.e.replace(/[+×÷−]$/, '');
        CALC.e += k; draw();
      };
    }
  }

  /* ---------- рендер и навигация ---------- */
  const main = byId('main'), app = byId('app');
  function render() {
    app.dataset.view = state.view;
    $$('.nav').forEach((n) => n.classList.toggle('active', n.dataset.view === state.view));
    main.innerHTML = `<div class="${state.settings.anim ? 'view-in' : ''}">${views[state.view]()}</div>`;
    observeLazy(main);
    if (state.view === 'home') countUp();
    if (state.view === 'photo') loadAlbums();
    if (state.view === 'folders') loadDir(FS.path);
    if (state.view === 'settings') renderSettingsSub();
    else stopSubTimers();
  }
  function go(v, sub) {
    const allowed = ['home', 'video', 'audio', 'photo', 'folders', 'settings'];
    if (!allowed.includes(v)) v = 'home';
    state.view = v;
    if (sub) state.sub = sub;
    state.q = '';
    byId('search').value = '';
    const h = v === 'settings' ? `#settings/${state.sub}` : `#${v}`;
    if (location.hash !== h) history.replaceState(null, '', h);
    render();
    main.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function countUp() {
    $$('[data-count]').forEach((el) => {
      const n = +el.dataset.count, t0 = performance.now();
      if (!state.settings.anim) { el.textContent = String(n); return; }
      const step = (t) => {
        const k = Math.min((t - t0) / 800, 1);
        el.textContent = String(Math.round(n * (1 - Math.pow(1 - k, 3))));
        if (k < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
    });
  }

  /* ---------- хранилище и сигнал Wi-Fi ---------- */
  function storage() {
    const mediaBytes = (D.stats && D.stats.storageBytes != null) ? D.stats.storageBytes : sizeToBytes(totalMem());
    let total = 0, used = 0, free = 0;
    if (DISK && DISK.total > 0) {
      total = DISK.total;
      free = Math.max(0, Math.min(total, DISK.free));
      used = Math.max(0, total - free);
    } else if (DEMO_MODE) {
      total = 18.4 * 1073741824;
      used = 853.3 * 1048576;
      free = total - used;
    }
    const pct = total > 0 ? Math.min(100, used / total * 100) : 0;
    byId('usedTxt').textContent = total > 0 ? fmtSize(mediaBytes > 0 ? mediaBytes : used) : '—';
    byId('totalTxt').textContent = total > 0 ? fmtSize(total) : '—';
    byId('pctTxt').textContent = total > 0 ? `${pct.toFixed(1)}%` : '—';
    byId('freeTxt').textContent = total > 0 ? fmtSize(free) : '—';
    requestAnimationFrame(() => setTimeout(() => {
      byId('barFill').style.width = `${pct}%`;
      byId('ringFg').style.strokeDashoffset = 326.7 * (1 - pct / 100);
    }, 120));
  }

  const SIGTXT = ['Нет сигнала', 'Слабый', 'Средний', 'Хороший', 'Отличный'];
  let failCount = 0;
  function updateSignalUi(level, info = {}) {
    level = Math.max(0, Math.min(4, level | 0));
    let txt = SIGTXT[level];
    if (info.rssi != null) txt += ` (${info.rssi} дБм)`;
    if (info.src === 'rtt' && info.rtt != null) txt += ` · ${info.rtt} мс`;
    byId('popAddr').textContent = D.address || location.host || '—';
    byId('popSig').textContent = txt;
  }
  function setOnline(on) {
    state.online = on;
    byId('status').classList.toggle('off', !on);
    byId('statusTxt').textContent = on ? 'Сервер онлайн' : 'Нет связи';
    if (!on) updateSignalUi(0, {});
  }
  const levelFromRtt = (ms) => (ms < 35 ? 4 : ms < 90 ? 3 : ms < 220 ? 2 : 1);
  function pollStatus() {
    if (DEMO_MODE) {
      updateSignalUi(3, { rssi: -54 });
      return;
    }
    const t0 = performance.now();
    fetch('/api/status', { credentials: 'same-origin', cache: 'no-store' }).then((r) => {
      if (r.status === 401) { location.replace('/'); return null; }
      return r.json();
    }).then((j) => {
      if (!j || lockCheck(j)) return;
      const rtt = Math.round(performance.now() - t0);
      failCount = 0;
      setOnline(true);
      if (j.disk) { DISK = j.disk; storage(); }
      if (isAdmin && typeof j.pendingRegs === 'number') updateBadges(j.pendingRegs);
      const w = j.wifi || {}, useApp = state.sigMode === 'app' && typeof w.level === 'number';
      const lv = useApp ? w.level : levelFromRtt(rtt);
      updateSignalUi(lv, { rtt, rssi: useApp && typeof w.rssi === 'number' ? w.rssi : null, src: useApp ? 'app' : 'rtt' });
    }).catch(() => {
      failCount++;
      if (failCount >= 2) setOnline(false);
    });
  }

  /* ---------- плеер (видео, аудио, фото + навигация + полный экран) ---------- */
  const dlgPlayer = byId('player'), pBody = byId('pBody');
  const PVW = { list: null, idx: 0, beatTimer: 0 };
  function stopPlayerBeat() {
    clearInterval(PVW.beatTimer);
    PVW.beatTimer = 0;
  }
  function openMedia(opts) {
    stopPlayerBeat();
    byId('pTitle').textContent = opts.title || '';
    byId('pMeta').textContent = opts.meta || '';
    const prev = byId('pPrev'), next = byId('pNext'), fs = byId('pFs'), dl = byId('pDl');
    const isList = !!(PVW.list && PVW.list.length > 1);
    prev.hidden = !isList;
    next.hidden = !isList;
    fs.hidden = opts.kind === 'audio';
    if (opts.dl) { dl.hidden = false; dl.href = opts.dl; dl.setAttribute('download', opts.name || ''); }
    else dl.hidden = true;
    dlgPlayer.classList.remove('full');
    pBody.innerHTML = opts.html;
    if (!dlgPlayer.open) dlgPlayer.showModal();
    const m = $('video,audio', pBody);
    if (m) {
      const sendBeat = () => {
        if (!dlgPlayer.open || m.paused || m.ended) return;
        post('/api/monitor/beat', {
          file: opts.name || opts.title || '',
          pos: m.currentTime || 0,
          dur: (isFinite(m.duration) ? m.duration : 0) || 0,
          kind: opts.kind,
        }).catch(() => {});
      };
      m.addEventListener('play', sendBeat);
      PVW.beatTimer = setInterval(sendBeat, 5000);
      if (state.settings.autoplay) m.play().catch(() => {});
    }
  }
  function closePlayer() {
    stopPlayerBeat();
    const m = $('video,audio', pBody);
    if (m) m.pause();
    dlgPlayer.classList.remove('full');
    if (dlgPlayer.open) dlgPlayer.close();
    pBody.innerHTML = '';
    PVW.list = null;
  }
  function playVideo(id) {
    const v = videos.find((x) => x.id === String(id));
    if (!v) return;
    PVW.list = null;
    openMedia({
      kind: 'video', title: v.title, name: v.name, meta: `${v.fmt}${v.size ? ' • ' + v.size : ''} • ${v.dur}`,
      dl: v.dl, html: `<video controls playsinline src="${esc(v.src)}"></video>`,
    });
  }
  function playAudio(id) {
    const a = audios.find((x) => x.id === String(id));
    if (!a) return;
    PVW.list = null;
    openMedia({
      kind: 'audio', title: a.title, name: a.name, meta: a.art,
      dl: a.dl, html: `<div class="art" style="background:linear-gradient(135deg,#43d95a,#0d6e8f)">${ico('note')}</div><audio controls src="${esc(a.src)}"></audio>`,
    });
  }
  function openPhoto(idx) {
    PVW.list = PH.items;
    PVW.idx = idx;
    const f = PH.items[idx];
    if (!f) return;
    if (DEMO_MODE) {
      const c = palettes[idx % palettes.length];
      const svg = `<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 800 500'><defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'><stop stop-color='${c[0]}'/><stop offset='1' stop-color='${c[1]}'/></linearGradient></defs><rect width='800' height='500' fill='url(#g)'/><circle cx='620' cy='130' r='55' fill='rgba(255,255,255,.55)'/><path d='M0 400 L220 230 L390 340 L560 190 L800 370 L800 500 L0 500Z' fill='rgba(255,255,255,.45)'/></svg>`;
      const u = `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
      openMedia({
        kind: 'photo', title: f.name, name: f.name, meta: f.size ? fmtSize(f.size) : 'Фото',
        dl: '', html: `<img alt="${esc(f.name)}" src="${u}">`,
      });
      return;
    }
    const u = `/photo?id=${encodeURIComponent(f.id)}`;
    openMedia({
      kind: 'photo', title: f.name, name: f.name, meta: f.size ? fmtSize(f.size) : 'Фото',
      dl: `${u}&dl=1`, html: `<img alt="${esc(f.name)}" src="${u}">`,
    });
  }
  function openFsFile(idx) {
    const it = FS.items[idx];
    if (!it) return;
    PVW.list = null;
    const u = `/file?path=${encodeURIComponent(it.path)}`;
    const html = it.kind === 'video'
      ? `<video controls playsinline src="${u}"></video>`
      : it.kind === 'audio'
        ? `<div class="art" style="background:linear-gradient(135deg,#43d95a,#0d6e8f)">${ico('note')}</div><audio controls src="${u}"></audio>`
        : `<img alt="${esc(it.name)}" src="${u}">`;
    openMedia({ kind: it.kind, title: it.name, name: it.name, meta: fmtSize(it.size), dl: `${u}&dl=1`, html });
  }
  byId('pClose').onclick = closePlayer;
  byId('pPrev').onclick = () => { if (PVW.list && PVW.list.length) openPhoto((PVW.idx - 1 + PVW.list.length) % PVW.list.length); };
  byId('pNext').onclick = () => { if (PVW.list && PVW.list.length) openPhoto((PVW.idx + 1) % PVW.list.length); };
  byId('pFs').onclick = () => dlgPlayer.classList.toggle('full');
  dlgPlayer.addEventListener('click', (e) => { if (e.target === dlgPlayer) closePlayer(); });
  dlgPlayer.addEventListener('cancel', () => setTimeout(() => { stopPlayerBeat(); pBody.innerHTML = ''; PVW.list = null; }, 0));

  /* ---------- глобальные клики и события ---------- */
  document.addEventListener('click', (e) => {
    const t = e.target;
    let el;
    if (!t.closest('#status')) byId('statusPop')?.classList.remove('show');
    if (!t.closest('#selbar')) byId('mpop')?.classList.remove('show');

    if ((el = t.closest('.menu button'))) { menuAction(el.dataset.act, el.dataset.id); return; }
    $$('.menu').forEach((m) => m.remove());

    if ((el = t.closest('[data-more]'))) {
      e.stopPropagation();
      const v = videos.find((x) => x.id === el.dataset.more);
      el.insertAdjacentHTML('beforeend', `<div class="menu">
        <button data-act="play" data-id="${esc(el.dataset.more)}">Воспроизвести</button>
        ${v ? `<button data-act="dl" data-id="${esc(el.dataset.more)}">Скачать</button>` : ''}
        <button data-act="copy" data-id="${esc(el.dataset.more)}">Скопировать ссылку</button>
        <button data-act="info" data-id="${esc(el.dataset.more)}">Свойства</button>
      </div>`);
    } else if ((el = t.closest('[data-play]'))) playVideo(el.dataset.play);
    else if ((el = t.closest('[data-go]'))) go(el.dataset.go);
    else if ((el = t.closest('.nav'))) go(el.dataset.view);
    else if ((el = t.closest('[data-audio]'))) playAudio(el.dataset.audio);
    else if ((el = t.closest('[data-album]'))) openAlbum(el.dataset.album, el.dataset.name);
    else if ((el = t.closest('[data-photo]'))) openPhoto(parseInt(el.dataset.photo, 10));
    else if ((el = t.closest('[data-act="albums"]'))) loadAlbums();
    else if ((el = t.closest('[data-fsel]')) && isAdmin && !t.closest('a,button')) {
      const ix = parseInt(el.dataset.fsel, 10), itx = FS.items[ix];
      if (FS.selOn || (itx && !itx.dir)) toggleSel(ix);
    } else if ((el = t.closest('[data-fs]'))) loadDir(el.dataset.fs);
    else if ((el = t.closest('[data-fopen]'))) openFsFile(parseInt(el.dataset.fopen, 10));
    else if ((el = t.closest('[data-sel]'))) {
      const a = el.dataset.sel;
      if (a === 'start') { FS.selOn = true; byId('lF')?.classList.add('selmode'); renderSelbar(); }
      else if (a === 'cancel') exitSel();
      else if (a === 'menu') byId('mpop')?.classList.toggle('show');
      else { byId('mpop')?.classList.remove('show'); if (a === 'delete') doFsDelete(); if (a === 'rename') doFsRename(); }
    } else if ((el = t.closest('[data-sub]'))) {
      state.sub = el.dataset.sub;
      history.replaceState(null, '', `#settings/${state.sub}`);
      renderSettingsSub();
    } else if ((el = t.closest('[data-subjump]'))) {
      const [sub, peer] = el.dataset.subjump.split(':');
      state.sub = sub;
      if (peer) MAIL.peer = peer;
      renderSettingsSub();
    } else if ((el = t.closest('[data-set]'))) {
      const k = el.dataset.set;
      state.settings[k] = !state.settings[k];
      el.setAttribute('aria-checked', String(state.settings[k]));
      save();
    } else if ((el = t.closest('[data-sig]'))) {
      state.sigMode = el.dataset.sig;
      save();
      $$('[data-sig]').forEach((b) => b.classList.toggle('on', b.dataset.sig === state.sigMode));
      pollStatus();
    } else if (t.closest('[data-copyaddr]')) copyStr(D.address || location.href);
    else if ((el = t.closest('[data-dtab]'))) devTab(el.dataset.dtab);
    else if ((el = t.closest('[data-block]'))) {
      const id = el.dataset.block, to = el.dataset.to === '1';
      const exec = () => post('/api/devices/block', { id, blocked: to }).then(() => { toast(to ? 'Устройство заблокировано' : 'Устройство разблокировано'); loadDevices(); }).catch((er) => toast(opErr(er)));
      if (to) dlg({ title: 'Заблокировать устройство?', text: 'Оно потеряет доступ к серверу, пока вы его не разблокируете.', ok: 'Заблокировать', danger: true }, exec);
      else exec();
    } else if ((el = t.closest('[data-accedit]'))) { const a = ACC.list.find((x) => x.id === el.dataset.accedit); if (a) accCredPanel(a); }
    else if ((el = t.closest('[data-acclim]'))) { const a = ACC.list.find((x) => x.id === el.dataset.acclim); if (a) accLimitPanel(a); }
    else if ((el = t.closest('[data-apcancel]'))) { closeAccPanel(); loadAcc(); }
    else if ((el = t.closest('[data-apsave]'))) {
      if (!ACC.cb) return;
      el.disabled = true;
      ACC.cb(() => { closeAccPanel(); loadAcc(); }, (msg) => { const e2 = byId('apErr'); if (e2) e2.textContent = msg; el.disabled = false; });
    } else if ((el = t.closest('[data-accdel]'))) {
      const id = el.dataset.accdel;
      if (DELARM.id !== id || Date.now() - DELARM.t > 3500) {
        DELARM = { id, t: Date.now() };
        el.textContent = 'Точно удалить?';
        setTimeout(() => { if (DELARM.id === id && el.parentNode) { el.innerHTML = `${ico('trash')}Удалить`; DELARM.id = ''; } }, 3500);
      } else {
        DELARM.id = '';
        post('/api/accounts/delete', { id }).then(() => { toast('Учётная запись удалена'); loadAcc(); }).catch((er) => toast(opErr(er)));
      }
    } else if ((el = t.closest('[data-mpeer]'))) openPeer(el.dataset.mpeer);
    else if (t.closest('[data-mreq]')) openReq();
    else if ((el = t.closest('[data-regok]'))) post('/api/registrations/decide', { id: el.dataset.regok, approve: true }).then(() => { toast('Учётная запись активирована'); loadRegs(); loadPeers(); }).catch((er) => toast(opErr(er)));
    else if ((el = t.closest('[data-regno]'))) post('/api/registrations/decide', { id: el.dataset.regno, approve: false }).then(() => { toast('Заявка отклонена'); loadRegs(); loadPeers(); }).catch((er) => toast(opErr(er)));
    else if ((el = t.closest('[data-mdel]'))) {
      const mid = el.dataset.mdel;
      dlg({ title: 'Удалить сообщение?', text: 'Оно исчезнет у обоих участников.', ok: 'Удалить', danger: true }, () => {
        post('/api/mail/delete', { id: Number(mid) }).then(() => document.querySelector(`#mMsgs [data-mid="${mid}"]`)?.remove()).catch((er) => toast(opErr(er)));
      });
    } else if ((el = t.closest('[data-tool]'))) { activeTool = el.dataset.tool; paintTool(); }
    else if (t.closest('#menuBtn')) {
      if (isAdmin && state.pendingRegs > 0) { MAIL.peer = '__req'; go('settings', 'mail'); }
      else go('settings');
    } else if (t.closest('#status')) {
      byId('statusPop')?.classList.toggle('show');
    }
  });

  document.addEventListener('keydown', (e) => {
    if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('.row[data-play]')) { e.preventDefault(); playVideo(e.target.dataset.play); }
    if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('.track[data-audio]')) { e.preventDefault(); playAudio(e.target.dataset.audio); }
  });
  document.addEventListener('change', (e) => {
    if (e.target.matches('[data-lang]')) { state.settings.lang = e.target.value; save(); applyLang(); toast('Язык сохранён'); }
  });
  document.addEventListener('fullscreenchange', () => { const sw = byId('fsSw'); if (sw) sw.setAttribute('aria-checked', String(!!fsElem())); });

  byId('search').addEventListener('input', (e) => {
    state.q = e.target.value.trim().toLowerCase();
    if (state.q && state.view === 'home') state.view = 'video';
    if (state.view === 'photo') { PH.album ? openAlbum(PH.album, PH.name) : loadAlbums(); return; }
    if (state.view === 'folders') { renderDir(); return; }
    if (state.view !== 'settings') render();
  });

  function menuAction(act, id) {
    $$('.menu').forEach((m) => m.remove());
    const v = videos.find((x) => x.id === id);
    if (!v) return;
    if (act === 'play') playVideo(id);
    if (act === 'dl') { const a = document.createElement('a'); a.href = v.dl; a.download = v.name; a.click(); }
    if (act === 'copy') copyStr(new URL(v.src, location.href).href);
    if (act === 'info') toast(`${v.title} — ${v.fmt}${v.size ? ', ' + v.size : ''}, ${v.dur}`);
  }

  /* ---------- сцена 1600x900 (16:9) + портретный поворот на 90° + свайп-прокрутка ---------- */
  let rotated = false, curScale = 1, suppressClick = 0;
  function fit() {
    const W = innerWidth, H = innerHeight, rot = H > W, dpr = devicePixelRatio || 1;
    const vw = rot ? H : W, vh = rot ? W : H;
    const s = Math.min(vw / 1600, vh / 900);
    rotated = rot;
    curScale = s;
    const snap = (v) => Math.round(v * dpr) / dpr;
    const r = document.documentElement.style;
    r.setProperty('--s', s);
    r.setProperty('--r', rot ? '90deg' : '0deg');
    r.setProperty('--tx', snap(rot ? (W + 900 * s) / 2 : (W - 1600 * s) / 2) + 'px');
    r.setProperty('--ty', snap(rot ? (H - 1600 * s) / 2 : (H - 900 * s) / 2) + 'px');
  }
  addEventListener('resize', fit);
  addEventListener('orientationchange', () => setTimeout(fit, 120));
  fit();

  /* плавный свайп пальцем в повёрнутом на 90° макете */
  (() => {
    let st = null, raf = 0;
    const scrollerOf = (t) => {
      while (t && t !== document) {
        if (t === main || (t.classList && (t.classList.contains('mmsgs') || t.classList.contains('mthreads') || t.classList.contains('laps')))) return t;
        t = t.parentNode;
      }
      return null;
    };
    document.addEventListener('touchstart', (e) => {
      cancelAnimationFrame(raf);
      if (!rotated || e.touches.length !== 1) { st = null; return; }
      const el = scrollerOf(e.target);
      if (!el) { st = null; return; }
      const t = e.touches[0];
      st = { el, x: t.clientX, y: t.clientY, sx: t.clientX, sy: t.clientY, t: Date.now(), moved: false, v: 0 };
    }, { passive: true });
    document.addEventListener('touchmove', (e) => {
      if (!st) return;
      const t = e.touches[0];
      if (!st.moved && Math.abs(t.clientX - st.sx) + Math.abs(t.clientY - st.sy) < 8) return;
      st.moved = true;
      if (e.cancelable) e.preventDefault();
      const dx = t.clientX - st.x, dy = t.clientY - st.y;
      const d = (dx - dy) / curScale;
      st.el.scrollTop += d;
      const now = Date.now();
      st.v = d / Math.max(1, now - st.t);
      st.t = now; st.x = t.clientX; st.y = t.clientY;
    }, { passive: false });
    document.addEventListener('touchend', () => {
      if (!st) return;
      const s = st; st = null;
      if (s.moved) {
        suppressClick = Date.now() + 300;
        let v = s.v * 16;
        (function step() {
          if (Math.abs(v) < 0.4) return;
          s.el.scrollTop += v; v *= 0.95;
          raf = requestAnimationFrame(step);
        })();
      }
    }, { passive: true });
    document.addEventListener('click', (e) => { if (Date.now() < suppressClick) { e.stopPropagation(); e.preventDefault(); } }, true);
  })();

  /* ---------- инициализация ---------- */
  applyLang();
  updateBadges(state.pendingRegs);
  if (D.wifi && typeof D.wifi.level === 'number') updateSignalUi(D.wifi.level, { rssi: D.wifi.rssi });
  else updateSignalUi(3, {});

  const parseHash = () => {
    const h = location.hash.slice(1);
    if (!h) return ['home', 'general'];
    if (h.startsWith('settings/')) return ['settings', h.slice(9) || 'general'];
    return [h, 'general'];
  };
  const [initV, initSub] = parseHash();
  state.view = ['home', 'video', 'audio', 'photo', 'folders', 'settings'].includes(initV) ? initV : 'home';
  const validSubs = isAdmin ? ['general', 'net', 'devices', 'accounts', 'monitor', 'mail', 'tools', 'about'] : ['general', 'net', 'devices', 'accounts', 'mail', 'tools', 'about'];
  state.sub = validSubs.includes(initSub) ? initSub : 'general';
  addEventListener('hashchange', () => {
    const [v, s] = parseHash();
    go(v, s);
  });

  if (!DEMO_MODE && D.authRequired) {
    api('/api/fs').then((j) => {
      state.folderCount = (j.items || []).filter((x) => x.dir).length;
      const fc = byId('homeFolderCount');
      if (fc) fc.textContent = String(state.folderCount);
    }).catch(() => {});
  }

  render();
  storage();
  setInterval(pollStatus, 4000);
  pollStatus();
})();
