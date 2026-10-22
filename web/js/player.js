(function () {
  'use strict';
  // Lecteur de films : AVPlay (le décodeur natif de la TV, rendu sous la page web) sur Tizen, <video> dans un navigateur.
  var K = H.tv.keys;
  var LANGS = {fra: 'Français', fre: 'Français', eng: 'Anglais', spa: 'Espagnol', ger: 'Allemand', deu: 'Allemand', ita: 'Italien',
    por: 'Portugais', jpn: 'Japonais', kor: 'Coréen', rus: 'Russe', chi: 'Chinois', zho: 'Chinois', dut: 'Néerlandais', nld: 'Néerlandais', und: 'Inconnu'};

  function hasAvplay() { return typeof window.webapis !== 'undefined' && !!window.webapis.avplay; }

  function play(o) {
    var av = hasAvplay(), api = av ? window.webapis.avplay : null;
    var idx = -1, i;
    for (i = 0; i < o.queue.length; i++) if (o.queue[i].url === o.url) idx = i;
    var st = {playing: false, buffering: true, pos: 0, dur: 0, tracks: [], closed: false, chromeVisible: true, touch: Date.now(), startPos: 0, sub: ''};

    var ov = H.make('div', 'overlay player');
    ov.innerHTML = '<div class="p-video"></div><div class="p-sub"></div><div class="p-spin"><i></i></div>' +
      '<div class="p-chrome"><div class="p-top"><div class="p-title"></div><div class="p-meta"></div></div>' +
      '<div class="p-bottom"><div class="p-line"><span class="p-ic"></span><span class="p-pos">0:00</span><div class="p-bar"><i></i></div><span class="p-dur">0:00</span></div>' +
      '<div class="p-actions"></div></div></div><div class="f p-root"></div>';
    var els = {
      video: H.$('.p-video', ov), sub: H.$('.p-sub', ov), spin: H.$('.p-spin', ov), chrome: H.$('.p-chrome', ov),
      title: H.$('.p-title', ov), meta: H.$('.p-meta', ov), ic: H.$('.p-ic', ov), pos: H.$('.p-pos', ov), dur: H.$('.p-dur', ov),
      fill: H.$('.p-bar i', ov), actions: H.$('.p-actions', ov), root: H.$('.p-root', ov)
    };
    var html5 = null, saveTimer = 0, hideTimer = 0;

    function paint() {
      els.ic.innerHTML = H.icon(st.playing ? 'pause' : 'play', 30);
      els.pos.textContent = H.fmtTime(st.pos);
      els.dur.textContent = st.dur ? H.fmtTime(st.dur) : '';
      els.fill.style.width = (st.dur ? H.clamp(st.pos * 100 / st.dur, 0, 100) : 0) + '%';
      els.spin.className = 'p-spin' + (st.buffering ? ' on' : '');
    }

    function showChrome() {
      st.touch = Date.now();
      if (!st.chromeVisible) { st.chromeVisible = true; els.chrome.className = 'p-chrome'; }
      clearTimeout(hideTimer);
      hideTimer = setTimeout(function () {
        var f = H.focus.current;
        if (st.playing && !(f && f !== els.root && ov.contains(f))) { st.chromeVisible = false; els.chrome.className = 'p-chrome off'; }
        else if (st.playing) showChrome();
      }, 4200);
    }

    function trackName(t) {
      var lang = '';
      try { lang = JSON.parse(t.extra_info).language || ''; } catch (e) {}
      return (LANGS[lang] || lang || 'Piste') + ' ' + (t.index + 1);
    }

    function pick(type, title) {
      var list = st.tracks.filter(function (t) { return t.type === type; });
      if (!list.length) return H.toast(type === 'AUDIO' ? 'Aucune autre piste audio' : 'Aucun sous-titre dans ce fichier');
      var names = list.map(trackName);
      if (type === 'TEXT') names.unshift('Désactivés');
      H.choose(title, names, 0, function (i) {
        try {
          if (type === 'TEXT') {
            if (i === 0) { api.setSilentSubtitle(true); st.sub = ''; els.sub.innerHTML = ''; }
            else { api.setSilentSubtitle(false); api.setSelectTrack('TEXT', list[i - 1].index); }
          } else api.setSelectTrack('AUDIO', list[i].index);
        } catch (e) { H.toast('Changement de piste impossible'); }
      });
    }

    function seekBy(d) {
      var t = H.clamp(st.pos + d, 0, st.dur ? st.dur - 1000 : 1e12);
      st.pos = t; paint(); showChrome();
      if (api) { try { api.seekTo(t); } catch (e) {} }
      else if (html5) html5.currentTime = t / 1000;
    }

    function toggle() {
      if (api) { try { if (st.playing) api.pause(); else api.play(); } catch (e) {} st.playing = !st.playing; }
      else if (html5) { if (html5.paused) html5.play(); else html5.pause(); st.playing = !html5.paused; }
      paint(); showChrome();
    }

    function saveNow() {
      if (st.dur) H.nas.saveResume(o.src.id, o.url, o.title, st.pos, st.dur);
    }

    function stop() {
      if (st.closed) return;
      st.closed = true;
      clearInterval(saveTimer); clearTimeout(hideTimer);
      saveNow();
      if (api) { try { api.stop(); } catch (e) {} try { api.close(); } catch (e2) {} }
      document.body.className = document.body.className.replace(/ ?playing/, '');
    }

    function next() {
      if (idx >= 0 && idx < o.queue.length - 1) {
        var n = o.queue[idx + 1];
        saveNow(); H.nas.saveResume(o.src.id, o.url, o.title, 0, 0);
        stop();
        H.focus.pop();
        play({src: o.src, url: n.url, title: H.nas.pretty(n.name), queue: o.queue, subs: n.subs || []});
        return true;
      }
      return false;
    }

    var b = function (icon, label, fn) { var x = H.button(icon, label, fn); els.actions.appendChild(x); return x; };
    if (api) {
      b('audio', 'Audio', function () { pick('AUDIO', 'Piste audio'); });
      b('subs', 'Sous-titres', function () { pick('TEXT', 'Sous-titres'); });
    }
    if (idx >= 0 && idx < o.queue.length - 1) b('next', 'Suivant', function () { next(); });
    b('close', 'Quitter', function () { H.focus.back(); });

    els.title.textContent = o.title;
    els.meta.textContent = 'NAS · ' + o.src.name;

    H.showOverlay(ov, {
      first: els.root,
      onClose: stop,
      onKey: function (code) {
        showChrome();
        var f = H.focus.current, onActions = f && f !== els.root;
        if (code === K.playPause) { toggle(); return true; }
        if (code === K.play) { if (!st.playing) toggle(); return true; }
        if (code === K.pause) { if (st.playing) toggle(); return true; }
        if (code === K.stop) { H.focus.back(); return true; }
        if (code === K.ff) { seekBy(30000); return true; }
        if (code === K.rew) { seekBy(-30000); return true; }
        if (code === K.next && next()) return true;
        if (onActions) {
          if (code === K.up) { H.focus.set(els.root); return true; }
          return false;
        }
        if (code === K.ok) { toggle(); return true; }
        if (code === K.left) { seekBy(-10000); return true; }
        if (code === K.right) { seekBy(10000); return true; }
        if (code === K.down) { H.focus.set(els.actions.firstChild); return true; }
        return code === K.up;
      }
    });
    document.body.className += ' playing';
    paint();
    showChrome();

    var resume = H.nas.resumeFor(o.url);
    st.startPos = resume ? Math.max(0, resume.pos - 3000) : 0;
    var url = H.nas.authUrl(o.src, o.url);

    function onError(msg) {
      H.toast('Lecture impossible : ' + msg);
      H.focus.back();
    }

    if (api) {
      els.video.innerHTML = '<object id="avplayer" type="application/avplayer" style="position:absolute;left:0;top:0;width:1920px;height:1080px"></object>';
      try {
        api.open(url);
        api.setDisplayRect(0, 0, 1920, 1080);
        api.setListener({
          onbufferingstart: function () { st.buffering = true; paint(); },
          onbufferingprogress: function () {},
          onbufferingcomplete: function () { st.buffering = false; paint(); },
          oncurrentplaytime: function (ms) { st.pos = ms; paint(); },
          onevent: function () {},
          onstreamcompleted: function () { if (!next()) H.focus.back(); },
          onerror: function (e) { onError(String(e)); },
          onsubtitlechange: function (duration, text) {
            els.sub.innerHTML = '<span>' + H.esc(String(text).replace(/<[^>]*>/g, '')).replace(/\n/g, '<br>') + '</span>';
            clearTimeout(st.subTimer);
            st.subTimer = setTimeout(function () { els.sub.innerHTML = ''; }, +duration || 3000);
          }
        });
        if (o.subs && o.subs.length) { try { api.setExternalSubtitlePath(H.nas.authUrl(o.src, o.subs[0])); } catch (e) {} }
        api.prepareAsync(function () {
          try {
            st.dur = api.getDuration();
            st.tracks = api.getTotalTrackInfo() || [];
            if (st.startPos) api.seekTo(st.startPos);
            api.play();
            st.playing = true; st.buffering = false; paint(); showChrome();
          } catch (e) { onError(e.message || String(e)); }
        }, function (e) { onError(String(e && e.message || e)); });
      } catch (e) { onError(e.message || String(e)); }
    } else {
      els.video.innerHTML = '<video></video>';
      html5 = els.video.firstChild;
      html5.src = url;
      html5.autoplay = true;
      html5.onloadedmetadata = function () { st.dur = html5.duration * 1000; if (st.startPos) html5.currentTime = st.startPos / 1000; };
      html5.ontimeupdate = function () { st.pos = html5.currentTime * 1000; paint(); };
      html5.onplaying = function () { st.playing = true; st.buffering = false; paint(); showChrome(); };
      html5.onwaiting = function () { st.buffering = true; paint(); };
      html5.onended = function () { if (!next()) H.focus.back(); };
      html5.onerror = function () { onError('format ou accès non pris en charge'); };
    }
    saveTimer = setInterval(saveNow, 15000);
  }

  H.player = {play: play};
})();
