(function () {
  'use strict';
  var H = window.H = {};

  H.$ = function (sel, root) { return (root || document).querySelector(sel); };
  H.$$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };

  var ENT = {'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'};
  H.esc = function (s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) { return ENT[c]; }); };

  H.make = function (tag, cls, html) {
    var e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html != null) e.innerHTML = html;
    return e;
  };

  H.pad = function (n) { return n < 10 ? '0' + n : '' + n; };
  H.clamp = function (v, lo, hi) { return Math.max(lo, Math.min(hi, v)); };
  H.uid = function () { return 'i' + Date.now().toString(36) + Math.floor(Math.random() * 1e6).toString(36); };

  H.load = function (key, def) {
    try {
      var v = localStorage.getItem('horizon.' + key);
      return v == null ? def : JSON.parse(v);
    } catch (e) { return def; }
  };
  H.save = function (key, value) {
    try { localStorage.setItem('horizon.' + key, JSON.stringify(value)); } catch (e) {}
  };

  var proxy = (/[?&]proxy=([^&]+)/.exec(location.search) || [])[1];
  proxy = proxy ? decodeURIComponent(proxy) : '';

  /** XHR avec délai, authentification Basic facultative ; cb(err, xhr). Les statuts < 400 (dont 207) réussissent. */
  H.http = function (o, cb) {
    var x = new XMLHttpRequest(), done = false;
    function end(err) { if (done) return; done = true; cb(err, x); }
    try {
      // ?proxy= : uniquement pour l'aperçu dans un navigateur de bureau (voir tools/devproxy.py).
      var url = proxy && /^https?:/.test(o.url) && o.url.indexOf('open-meteo') < 0 ? proxy + '?u=' + encodeURIComponent(o.url) : o.url;
      x.open(o.method || 'GET', url, true);
      if (o.user) {
        x.setRequestHeader('Authorization', 'Basic ' + btoa(unescape(encodeURIComponent(o.user + ':' + (o.pass || '')))));
      }
      for (var k in (o.headers || {})) x.setRequestHeader(k, o.headers[k]);
      x.timeout = o.timeout || 15000;
      x.onload = function () { end(x.status >= 200 && x.status < 400 ? null : new Error('HTTP ' + x.status)); };
      x.onerror = function () { end(new Error('réseau injoignable')); };
      x.ontimeout = function () { end(new Error('délai dépassé')); };
      x.send(o.body || null);
    } catch (e) { end(e); }
  };

  H.json = function (url, cb) {
    H.http({url: url}, function (err, x) {
      if (err) return cb(err);
      try { cb(null, JSON.parse(x.responseText)); } catch (e) { cb(e); }
    });
  };

  var toastTimer = 0;
  H.toast = function (msg) {
    var t = H.$('#toast');
    t.innerHTML = '<span>' + H.esc(msg) + '</span>';
    t.className = 'show';
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { t.className = ''; }, 3200);
  };

  H.rel = function (ms) {
    var min = Math.floor((Date.now() - ms) / 60000);
    if (min < 2) return "à l'instant";
    if (min < 60) return 'il y a ' + min + ' min';
    if (min < 1440) return 'il y a ' + Math.floor(min / 60) + ' h';
    if (min < 10080) return 'il y a ' + Math.floor(min / 1440) + ' j';
    var d = new Date(ms);
    return d.getDate() + '/' + H.pad(d.getMonth() + 1);
  };

  H.fmtTime = function (ms) {
    var s = Math.max(0, Math.floor(ms / 1000));
    var h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), sec = s % 60;
    return (h ? h + ':' + H.pad(m) : m) + ':' + H.pad(sec);
  };

  H.DAYS = ['dim.', 'lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.'];
  H.DAYS_LONG = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];
  H.MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre'];

  /** Couleur stable à partir d'un texte (cartes sans image). */
  H.tint = function (s) {
    var h = 0;
    for (var i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
    return h;
  };
})();
