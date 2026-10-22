(function () {
  'use strict';
  // Films du NAS. Un navigateur ne parle pas SMB : le NAS doit exposer ses dossiers en HTTP. Deux formats sont compris :
  // WebDAV (Synology « Serveur WebDAV », QNAP, Unraid, Nextcloud…) et les index de dossier HTML (nginx, Apache, Caddy…).
  var VIDEO = /\.(mkv|mp4|m4v|avi|mov|wmv|ts|m2ts|mts|mpg|mpeg|flv|webm|vob|3gp|divx|ogv)$/i;
  var SUBS = /\.(srt|smi|vtt)$/i;

  function origin(url) { var m = /^(https?:\/\/[^\/]+)/i.exec(url); return m ? m[1] : ''; }
  function dirOf(url) { return url.replace(/[^\/]*$/, ''); }

  function resolve(base, href) {
    if (/^https?:\/\//i.test(href)) return href;
    if (href.charAt(0) === '/') return origin(base) + href;
    return dirOf(base) + href;
  }

  function decode(s) { try { return decodeURIComponent(s); } catch (e) { return s; } }

  function sameDir(a, b) { return decode(a).replace(/\/+$/, '') === decode(b).replace(/\/+$/, ''); }

  function lastSegment(url) {
    var seg = url.replace(/\/+$/, '').split('/').pop();
    return decode(seg);
  }

  /** URL avec identifiants, pour AVPlay qui ne sait pas envoyer d'en-tête Authorization. */
  function authUrl(src, url) {
    if (!src || !src.user) return url;
    var cred = encodeURIComponent(src.user) + (src.pass ? ':' + encodeURIComponent(src.pass) : '');
    return url.replace('://', '://' + cred + '@');
  }

  function fromWebdav(xml, url) {
    var doc = new DOMParser().parseFromString(xml, 'text/xml');
    var nodes = doc.getElementsByTagNameNS('DAV:', 'response'), out = [];
    for (var i = 0; i < nodes.length; i++) {
      var n = nodes[i];
      var h = n.getElementsByTagNameNS('DAV:', 'href')[0];
      if (!h) continue;
      var full = resolve(url, h.textContent.trim());
      if (sameDir(full, url)) continue;
      var isDir = n.getElementsByTagNameNS('DAV:', 'collection').length > 0;
      var dn = n.getElementsByTagNameNS('DAV:', 'displayname')[0];
      var name = (dn && dn.textContent) || lastSegment(full);
      out.push({name: name, url: isDir ? full.replace(/\/*$/, '/') : full, dir: isDir});
    }
    return out;
  }

  function fromHtml(html, url) {
    var out = [], re = /<a\s[^>]*href=["']([^"']+)["']/gi, m, seen = {};
    while ((m = re.exec(html))) {
      var href = m[1];
      if (/^(\?|#|\.\.|mailto:|javascript:)/i.test(href) || (/^https?:/i.test(href) && origin(href) !== origin(url))) continue;
      var full = resolve(url, href);
      if (full.indexOf(url) !== 0 && !sameDir(dirOf(full), url)) continue;
      if (sameDir(full, url) || seen[full]) continue;
      seen[full] = true;
      var isDir = /\/$/.test(full);
      out.push({name: lastSegment(full), url: full, dir: isDir});
    }
    return out;
  }

  function clean(list) {
    var vids = [], subs = [], dirs = [];
    list.forEach(function (e) {
      if (/^[.@#$]/.test(e.name) || e.name === 'lost+found') return;
      if (e.dir) dirs.push(e);
      else if (VIDEO.test(e.name)) vids.push(e);
      else if (SUBS.test(e.name)) subs.push(e);
    });
    function cmp(a, b) { var x = a.name.toLowerCase(), y = b.name.toLowerCase(); return x < y ? -1 : x > y ? 1 : 0; }
    dirs.sort(cmp); vids.sort(cmp);
    vids.forEach(function (v) {
      var base = v.name.replace(/\.[^.]+$/, '').toLowerCase();
      v.subs = subs.filter(function (s) { return s.name.toLowerCase().indexOf(base) === 0; }).map(function (s) { return s.url; });
    });
    return dirs.concat(vids);
  }

  /** Liste un dossier ; cb(err, entrées). WebDAV d'abord, index HTML en repli. */
  function list(src, url, cb) {
    var opts = {url: url, user: src.user, pass: src.pass, timeout: 12000};
    var dav = {method: 'PROPFIND', headers: {Depth: '1', 'Content-Type': 'application/xml'}};
    for (var k in opts) dav[k] = opts[k];
    H.http(dav, function (err, x) {
      if (!err && x.status === 207) {
        try { return cb(null, clean(fromWebdav(x.responseText, url))); } catch (e) {}
      }
      if (err && /401|403/.test(err.message) && x.status === 401) return cb(new Error('Identifiants refusés par le NAS (401)'));
      H.http(opts, function (err2, y) {
        if (err2) return cb(err2);
        cb(null, clean(fromHtml(y.responseText, url)));
      });
    });
  }

  // --- Reprise de lecture ---
  function resumes() { return H.load('resume', []); }
  function resumeFor(url) {
    var r = resumes();
    for (var i = 0; i < r.length; i++) if (r[i].url === url) return r[i];
    return null;
  }
  function saveResume(srcId, url, title, pos, dur) {
    var rest = resumes().filter(function (r) { return r.url !== url; });
    var finished = dur > 0 && pos > dur * 0.95;
    if (!finished && pos > 30000) rest.unshift({src: srcId, url: url, title: title, pos: pos, dur: dur, ts: Date.now()});
    H.save('resume', rest.slice(0, 30));
  }

  function pretty(name) { return name.replace(/\.[^.]+$/, '').replace(/[._]+/g, ' ').trim(); }

  function sourceById(id) {
    var l = H.settings.data.nas;
    for (var i = 0; i < l.length; i++) if (l[i].id === id) return l[i];
    return null;
  }

  function sourceCard(src) {
    var m = /^https?:\/\/([^\/]+)/i.exec(src.url);
    var el = H.make('div', 'f tile nas', '<div class="tile-ic">' + H.icon('storage', 44) + '</div><div class="tile-t">' + H.esc(src.name) +
      '</div><div class="tile-s">' + H.esc(m ? m[1] : src.url) + '</div>');
    el._ok = function () { browse(src, src.url, src.name); };
    return el;
  }

  function resumeCard(r) {
    var pct = r.dur ? Math.min(100, Math.round(r.pos * 100 / r.dur)) : 0;
    var el = H.make('div', 'f tile resume', '<div class="tile-ic">' + H.icon('film', 40) + '</div><div class="tile-t">' + H.esc(r.title) +
      '</div><div class="bar"><i style="width:' + pct + '%"></i></div><div class="tile-s">Reste ' + H.fmtTime(r.dur - r.pos) + '</div>');
    el._ok = function () {
      var src = sourceById(r.src);
      if (!src) return H.toast('Source NAS introuvable : ajoute-la dans les réglages');
      H.player.play({src: src, url: r.url, title: r.title, queue: [], subs: []});
    };
    return el;
  }

  function entryCard(e, src) {
    var res = e.dir ? null : resumeFor(e.url);
    var pct = res && res.dur ? Math.min(100, Math.round(res.pos * 100 / res.dur)) : 0;
    return H.make('div', 'f entry' + (e.dir ? ' dir' : ''),
      '<div class="e-ic">' + H.icon(e.dir ? 'folder' : 'film', 34) + '</div><div class="e-t">' + H.esc(e.dir ? e.name : pretty(e.name)) + '</div>' +
      (pct ? '<div class="bar"><i style="width:' + pct + '%"></i></div>' : ''));
  }

  /** Navigateur de dossiers en surimpression ; Retour remonte d'un niveau avant de se fermer. */
  function browse(src, rootUrl, title) {
    var stack = [{url: rootUrl, title: title}];
    var ov = H.make('div', 'overlay browse', '<div class="b-head"><div class="b-title"></div><div class="b-path"></div></div><div class="b-body"><div class="pane" data-snap="150"></div></div>');
    var pane = H.$('.pane', ov), titleEl = H.$('.b-title', ov), pathEl = H.$('.b-path', ov);

    function show(level) {
      titleEl.textContent = level.title;
      pathEl.textContent = decode(level.url.replace(/^https?:\/\/[^\/]+/i, '')) || '/';
      pane.innerHTML = '<div class="b-msg">Chargement…</div>';
      pane._y = 0; pane.style.webkitTransform = pane.style.transform = 'translate3d(0,0,0)';
      list(src, level.url, function (err, entries) {
        if (stack[stack.length - 1] !== level) return;
        if (err) {
          pane.innerHTML = '<div class="b-msg">Connexion impossible : ' + H.esc(err.message) + '</div>';
          var retry = H.button('refresh', 'Réessayer', function () { show(level); }, true);
          var cfg = H.button('gear', 'Réglages NAS', function () { H.openSettings('nas'); });
          var wrap = H.make('div', 'b-actions'); wrap.appendChild(retry); wrap.appendChild(cfg); pane.appendChild(wrap);
          return H.focus.refresh(retry);
        }
        if (!entries.length) { pane.innerHTML = '<div class="b-msg">Aucun film dans ce dossier</div>'; return; }
        var files = entries.filter(function (e) { return !e.dir; });
        pane.innerHTML = '';
        var grid = H.make('div', 'grid');
        entries.forEach(function (e) {
          var c = entryCard(e, src);
          c._ok = function () {
            if (e.dir) { var lv = {url: e.url, title: e.name}; stack.push(lv); show(lv); }
            else H.player.play({src: src, url: e.url, title: pretty(e.name), queue: files, subs: e.subs || []});
          };
          grid.appendChild(c);
        });
        pane.appendChild(grid);
        H.focus.refresh(grid.firstChild);
        if (grid.firstChild) H.focus.set(grid.firstChild);
      });
    }

    H.showOverlay(ov, {
      onBack: function () {
        if (stack.length > 1) { stack.pop(); show(stack[stack.length - 1]); return true; }
        return false;
      }
    });
    show(stack[0]);
  }

  /** Remplit l'onglet Films. */
  function buildTab(pane) {
    var srcs = H.settings.data.nas;
    pane.innerHTML = '';
    if (!srcs.length) {
      var box = H.make('div', 'hero-empty', '<div class="big-ic nas-ic">' + H.icon('film', 64) + '</div><h1>Films &amp; séries</h1>' +
        '<p>Branche ton NAS et lis tes films directement dans Horizon : lecteur natif Samsung (AVPlay), pistes audio, sous-titres, reprise de lecture.</p>' +
        '<p class="dim">Le NAS doit être joignable en HTTP : WebDAV (Synology : paquet « Serveur WebDAV »), ou un index de dossiers web.</p><div class="btns"></div>');
      var b = H.button('storage', 'Ajouter mon NAS', function () { H.openSettings('nas'); }, true);
      H.$('.btns', box).appendChild(b);
      pane.appendChild(box);
      return;
    }
    var sec = H.make('div', 'sec', '<h2>Mon NAS</h2><div class="row"><div class="track"></div></div>');
    var track = H.$('.track', sec);
    srcs.forEach(function (s) { track.appendChild(sourceCard(s)); });
    pane.appendChild(sec);
    var rs = resumes();
    if (rs.length) {
      var sec2 = H.make('div', 'sec', '<h2>Reprendre la lecture</h2><div class="row"><div class="track"></div></div>');
      var t2 = H.$('.track', sec2);
      rs.forEach(function (r) { t2.appendChild(resumeCard(r)); });
      pane.appendChild(sec2);
    }
  }

  H.nas = {
    list: list, browse: browse, buildTab: buildTab, authUrl: authUrl, resumeFor: resumeFor, saveResume: saveResume,
    resumes: resumes, resumeCard: resumeCard, pretty: pretty,
    /** Teste une source : cb(message d'erreur ou null, nombre d'éléments). */
    test: function (src, cb) { list(src, src.url, function (err, e) { cb(err ? err.message : null, e ? e.length : 0); }); }
  };
})();
