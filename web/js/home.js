(function () {
  'use strict';

  // ---------- Briques d'interface partagées ----------

  H.button = function (icon, label, onOk, primary) {
    var el = H.make('div', 'f btn' + (primary ? ' primary' : ''), (icon ? H.icon(icon, 26) : '') + '<span>' + H.esc(label) + '</span>');
    el._ok = onOk;
    return el;
  };

  H.showOverlay = function (el, opts) {
    opts = opts || {};
    H.$('#overlays').appendChild(el);
    el.offsetWidth; // déclenche la transition d'apparition
    el.className += ' show';
    H.focus.push({
      roots: [el],
      first: opts.first,
      onKey: opts.onKey,
      onBack: opts.onBack,
      onClose: function () {
        if (opts.onClose) opts.onClose();
        el.className = el.className.replace(' show', '');
        setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 260);
      }
    });
  };

  /** Liste de choix : cb(index) après fermeture. */
  H.choose = function (title, options, selected, cb) {
    var ov = H.make('div', 'overlay dim', '<div class="dialog"><h2>' + H.esc(title) + '</h2><div class="opts"></div></div>');
    var box = H.$('.opts', ov), firstEl = null;
    options.forEach(function (name, i) {
      var o = H.make('div', 'f opt', '<span>' + H.esc(name) + '</span>' + (i === selected ? '<b>' + H.icon('check', 24) + '</b>' : ''));
      o._ok = function () { H.focus.pop(); cb(i); };
      box.appendChild(o);
      if (i === selected || !firstEl) firstEl = (i === selected ? o : firstEl || o);
    });
    H.showOverlay(ov, {first: firstEl});
  };

  H.section = function (title, cls) {
    var s = H.make('div', 'sec' + (cls ? ' ' + cls : ''), (title ? '<h2>' + H.esc(title) + '</h2>' : '') + '<div class="row"><div class="track"></div></div>');
    return {el: s, track: H.$('.track', s)};
  };

  // ---------- Ciel et horloge ----------

  var SKY = [
    [0, '#070b1e', '#141c3c'], [5, '#10163a', '#3b2f63'], [7, '#2e4a7d', '#e08a6b'], [10, '#163f73', '#3f86c2'],
    [16, '#16406f', '#4c8cbf'], [19, '#2b2358', '#e8714f'], [21, '#111736', '#30284f'], [24, '#070b1e', '#141c3c']
  ];
  function hex(c) { return [parseInt(c.substr(1, 2), 16), parseInt(c.substr(3, 2), 16), parseInt(c.substr(5, 2), 16)]; }
  function mix(a, b, t) {
    var x = hex(a), y = hex(b), o = [];
    for (var i = 0; i < 3; i++) o.push(Math.round(x[i] + (y[i] - x[i]) * t));
    return 'rgb(' + o.join(',') + ')';
  }
  var skyFlip = 0;
  function paintSky() {
    var d = new Date(), h = d.getHours() + d.getMinutes() / 60, i = 0;
    while (i < SKY.length - 2 && SKY[i + 1][0] <= h) i++;
    var a = SKY[i], b = SKY[i + 1], t = H.clamp((h - a[0]) / (b[0] - a[0]), 0, 1);
    var layers = H.$$('#sky .sky');
    skyFlip = 1 - skyFlip;
    var next = layers[skyFlip];
    var topColor = mix(a[1], b[1], t);
    next.style.background = 'linear-gradient(180deg,' + topColor + ',' + mix(a[2], b[2], t) + ')';
    // Barre du haut : même couleur que le ciel, opaque, pour masquer les rangées qui défilent dessous.
    H.$('#topbg').style.background = 'linear-gradient(180deg,' + topColor + ' 0,' + topColor + ' 140px,' + topColor.replace('rgb', 'rgba').replace(')', ',0)') + ' 100%)';
    layers[skyFlip].className = 'sky on';
    layers[1 - skyFlip].className = 'sky';
  }

  function paintClock() {
    var d = new Date();
    H.$('#clock .time').textContent = H.pad(d.getHours()) + ':' + H.pad(d.getMinutes());
    H.$('#clock .date').textContent = H.DAYS_LONG[d.getDay()] + ' ' + d.getDate() + ' ' + H.MONTHS[d.getMonth()];
  }

  function greeting() {
    var h = new Date().getHours();
    return h < 5 ? 'Bonne nuit' : h < 12 ? 'Bonjour' : h < 18 ? 'Bon après-midi' : 'Bonsoir';
  }

  // ---------- Onglets ----------

  var TABS = [['home', 'Accueil'], ['youtube', 'YouTube'], ['music', 'Musique'], ['films', 'Films'], ['news', 'Actus']];
  var current = 'home', built = {};

  function showTab(id) {
    if (id === current && built[id]) return;
    current = id;
    TABS.forEach(function (t) {
      H.$('#v-' + t[0]).className = 'view' + (t[0] === id ? ' on' : '');
    });
    H.$$('#tabs .tab').forEach(function (el) { el.className = el.className.replace(/ sel\b/g, '') + (el._id === id ? ' sel' : ''); });
    if (VIEWS[id]) VIEWS[id](H.$('#v-' + id + ' .pane'));
    built[id] = true;
    var pane = H.$('#v-' + id + ' .pane');
    pane._y = 0;
    pane.style.webkitTransform = pane.style.transform = 'translate3d(0,0,0)';
    H.$('#top').className = '';
  }

  function buildTabs() {
    var box = H.$('#tabs');
    TABS.forEach(function (t) {
      var el = H.make('div', 'f tab' + (t[0] === current ? ' sel' : ''), H.esc(t[1]));
      el._id = t[0];
      // Comme sur tvOS, l'onglet s'affiche dès qu'il prend le focus.
      el._focus = function () { showTab(t[0]); };
      el._ok = function () { var f = H.focus.first(H.$('#v-' + t[0])); if (f) H.focus.set(f); };
      box.appendChild(el);
    });
    var gear = H.$('#gear');
    gear.innerHTML = H.icon('gear', 34);
    gear._ok = function () { H.openSettings(); };
  }

  // ---------- Accueil ----------

  var heroTimer = 0, heroIndex = 0, heroPaused = false;

  function heroEl() {
    var el = H.make('div', 'f hero', '<div class="hero-bg a"></div><div class="hero-bg b"></div><div class="hero-shade"></div>' +
      '<div class="hero-text"></div><div class="hero-dots"></div>');
    el._focus = function () { heroPaused = true; };
    el._blur = function () { heroPaused = false; };
    return el;
  }

  function paintHero(el, animate) {
    var items = H.news.items.slice(0, 6), it = items[heroIndex % Math.max(1, items.length)];
    var text = H.$('.hero-text', el), dots = H.$('.hero-dots', el), bgs = H.$$('.hero-bg', el);
    var d = new Date();
    if (!it) {
      text.innerHTML = '<div class="kicker">' + d.getDate() + ' ' + H.MONTHS[d.getMonth()].toUpperCase() + '</div><h1>' + greeting() +
        '</h1><p class="hero-wx">&nbsp;</p>';
      var cfg = H.settings.data.weather[0];
      if (cfg) H.weather.get(cfg.lat, cfg.lon, function (err, w) {
        var p = H.$('.hero-wx', el);
        if (w && p) p.textContent = H.weather.temp(w.temp) + '  ·  ' + H.weather.label(w.code) + ' à ' + cfg.city;
      });
      el._ok = null;
      dots.innerHTML = '';
      return;
    }
    text.className = 'hero-text' + (animate ? ' swap' : '');
    text.innerHTML = '<div class="kicker">À LA UNE  ·  ' + H.esc(it.source.toUpperCase()) + '</div><h1>' + H.esc(it.title) + '</h1><p>' + H.rel(it.time) + '</p>';
    if (animate) { text.offsetWidth; text.className = 'hero-text'; }
    var front = bgs[heroIndex % 2], back = bgs[(heroIndex + 1) % 2];
    var hue = H.tint(it.source);
    front.style.backgroundImage = it.image ? 'url("' + it.image.replace(/"/g, '%22') + '")' : 'none';
    front.style.backgroundColor = 'hsl(' + hue + ',40%,22%)';
    front.className = 'hero-bg on';
    back.className = 'hero-bg';
    var html = '';
    for (var i = 0; i < items.length; i++) html += '<i' + (i === heroIndex % items.length ? ' class="on"' : '') + '></i>';
    dots.innerHTML = html;
    el._ok = function () { H.news.open(it); };
  }

  function appTile(a, cls, onOk, badge) {
    var inner = a.icon ? '<img src="' + H.esc(a.icon) + '" width="88" height="88" onerror="this.style.display=\'none\'">' :
      '<div class="letter" style="background:hsl(' + H.tint(a.title) + ',45%,38%)">' + H.esc(a.title.charAt(0)) + '</div>';
    var el = H.make('div', 'f tile app ' + (cls || ''), '<div class="tile-ic">' + inner + '</div><div class="tile-t">' + H.esc(a.title) + '</div>' +
      (badge ? '<div class="badge">' + H.esc(badge) + '</div>' : ''));
    el._ok = onOk;
    return el;
  }

  var DEFAULT_PIN = /netflix|prime video|disney|canal|molotov|apple tv|^max$|hbo|france|tf1|arte|paramount|navigateur|internet/i;

  function autoPinned(a) { return DEFAULT_PIN.test(a.title) && !/tizentube|youtube|spotify/i.test(a.title); }

  function buildApps(sec) {
    H.tv.apps(function (apps) {
      H.tv.find(/tizentube/i, function (tubeApp) {
       H.tv.find(/tizenbrewnextgeneration|tizenbrew/i, function (brew) {
        var tube = tubeApp || brew;
        H.tv.find(/spotify/i, function (spot) {
          sec.track.innerHTML = '';
          sec.track.appendChild(appTile({title: 'YouTube', icon: 'icons/youtube.png'}, 'tube', function () {
            if (tube) H.tv.launch(tube.id, function (ok) { if (!ok) H.toast("TizenTube ne s'ouvre pas"); });
            else showTabAndFocus('youtube');
          }, tubeApp || brew ? 'Sans pub' : 'À installer'));
          sec.track.appendChild(appTile({title: 'Spotify', icon: 'icons/spotify.svg'}, 'spotify', function () {
            if (spot) H.tv.launch(spot.id, function (ok) { if (!ok) H.toast("Spotify ne s'ouvre pas"); });
            else showTabAndFocus('music');
          }));
          var films = H.make('div', 'f tile app films', '<div class="tile-ic">' + H.icon('film', 64) + '</div><div class="tile-t">Films</div><div class="badge">NAS</div>');
          films._ok = function () { showTabAndFocus('films'); };
          sec.track.appendChild(films);

          var pinned = H.settings.data.pinned, chosen = [];
          apps.forEach(function (a) {
            if (a === tubeApp || a === brew || a === spot || /spotify/i.test(a.title)) return;
            if (tubeApp && a.id === H.tv.stockYoutube) return;
            var on = pinned ? pinned.indexOf(a.id) >= 0 : autoPinned(a);
            if (on) chosen.push(a);
          });
          if (pinned) chosen.sort(function (x, y) { return pinned.indexOf(x.id) - pinned.indexOf(y.id); });
          chosen.forEach(function (a) {
            sec.track.appendChild(appTile(a, '', function () { H.tv.launch(a.id, function (ok) { if (!ok) H.toast('Impossible de lancer ' + a.title); }); }));
          });
          var all = H.make('div', 'f tile app all', '<div class="tile-ic">' + H.icon('apps', 56) + '</div><div class="tile-t">Toutes les apps</div>');
          all._ok = allApps;
          sec.track.appendChild(all);
          H.focus.refresh();
        });
       });
      });
    });
  }

  function allApps() {
    H.tv.apps(function (apps) {
      var ov = H.make('div', 'overlay sheet', '<h1>Toutes les applications</h1><div class="b-body"><div class="pane" data-snap="150"><div class="grid"></div></div></div>');
      var grid = H.$('.grid', ov), firstEl = null;
      apps.forEach(function (a) {
        var t = appTile(a, 'small', function () { H.focus.pop(); H.tv.launch(a.id); });
        grid.appendChild(t);
        if (!firstEl) firstEl = t;
      });
      H.showOverlay(ov, {first: firstEl});
    });
  }

  function showTabAndFocus(id) {
    var tab = null;
    H.$$('#tabs .tab').forEach(function (t) { if (t._id === id) tab = t; });
    if (tab) H.focus.set(tab);
  }

  function buildInputs(sec) {
    H.tv.inputs(function (list) {
      sec.track.innerHTML = '';
      list.forEach(function (inp) {
        var el = H.make('div', 'f tile input' + (inp.connected ? ' live' : ''),
          '<div class="tile-ic">' + H.icon(inp.id.indexOf('TV') > 0 && inp.id.indexOf('HDMI') < 0 ? 'tv' : 'hdmi', 40) + '</div><div class="tile-t">' + H.esc(inp.label) +
          '</div><div class="tile-s">' + (inp.connected ? (inp.sub || 'Appareil détecté') : 'Aucun signal') + '</div>');
        el._ok = function () { H.tv.setSource(inp.id, function (ok) { if (!ok) H.toast('Impossible de basculer sur ' + inp.label); }); };
        sec.track.appendChild(el);
      });
    });
  }

  function buildWeather(sec) {
    sec.track.innerHTML = '';
    H.settings.data.weather.forEach(function (cfg) {
      sec.track.appendChild(H.weather.widget(cfg, function () { H.openSettings('weather'); }));
    });
    var add = H.make('div', 'f tile add', '<div class="tile-ic">' + H.icon('plus', 44) + '</div><div class="tile-t">Ajouter un widget</div>');
    add._ok = function () { H.openSettings('weather'); };
    sec.track.appendChild(add);
  }

  function buildNewsRow(sec) {
    sec.track.innerHTML = '';
    var items = H.news.items.slice(0, 10);
    if (!items.length) {
      var e = H.make('div', 'f tile add', '<div class="tile-ic">' + H.icon('refresh', 44) + '</div><div class="tile-t">' + (H.news.loading ? 'Chargement…' : 'Actualiser') + '</div>');
      e._ok = function () { H.news.load(true); };
      sec.track.appendChild(e);
      return;
    }
    items.forEach(function (it) { sec.track.appendChild(H.news.card(it, 'row')); });
    var fb = H.make('div', 'f tile fb', '<div class="tile-ic fbf">f</div><div class="tile-t">Tu sais que tu es d\'Honfleur</div><div class="tile-s">Page Facebook</div>');
    fb._ok = H.news.facebook;
    sec.track.appendChild(fb);
    var more = H.make('div', 'f tile add', '<div class="tile-ic">' + H.icon('right', 44) + '</div><div class="tile-t">Toutes les actus</div>');
    more._ok = function () { showTabAndFocus('news'); };
    sec.track.appendChild(more);
  }

  function buildResume(sec) {
    var rs = H.nas.resumes().slice(0, 8);
    sec.el.style.display = rs.length ? '' : 'none';
    sec.track.innerHTML = '';
    rs.forEach(function (r) { sec.track.appendChild(H.nas.resumeCard(r)); });
  }

  var homeParts = null;

  function buildHome(pane) {
    if (homeParts) { refreshHome(); return; }
    pane.innerHTML = '';
    var hero = heroEl();
    var heroSec = H.make('div', 'sec hero-sec');
    heroSec.appendChild(hero);
    pane.appendChild(heroSec);
    var apps = H.section('Applications'), inputs = H.section('Entrées'), wx = H.section('Météo'), news = H.section('À Honfleur'), resume = H.section('Reprendre la lecture');
    [apps, inputs, wx, news, resume].forEach(function (s) { pane.appendChild(s.el); });
    homeParts = {hero: hero, apps: apps, inputs: inputs, wx: wx, news: news, resume: resume};
    paintHero(hero);
    buildApps(apps); buildInputs(inputs); buildWeather(wx); buildNewsRow(news); buildResume(resume);
    H.news.load(false);
    clearInterval(heroTimer);
    heroTimer = setInterval(function () {
      if (heroPaused || current !== 'home' || H.focus.depth() > 1 || H.news.items.length < 2) return;
      heroIndex++;
      paintHero(hero, true);
    }, 9000);
  }

  function refreshHome() {
    buildInputs(homeParts.inputs); buildResume(homeParts.resume);
    H.$$('.wx', homeParts.wx.track).forEach(function (w) { if (w._refresh) w._refresh(); });
  }

  // ---------- YouTube / Musique ----------

  function installGuide() {
    var url = 'https://github.com/reisxd/TizenTube';
    var svg = '';
    try { var qr = qrcode(0, 'M'); qr.addData(url); qr.make(); svg = qr.createSvgTag(7, 0); } catch (e) {}
    var ov = H.make('div', 'overlay center');
    ov.innerHTML = '<div class="fbpage"><div class="fb-text"><h1>Installer TizenTube</h1>' +
      '<p>TizenTube bloque les pubs YouTube et ajoute SponsorBlock. Horizon ne peut pas le remplacer : il le lance depuis l\'accueil dès qu\'il est installé.</p>' +
      '<p>Deux possibilités, détaillées dans le guide du projet (docs/README.md) :</p>' +
      '<p>• <b>TizenBrewNextGeneration</b> : module <b>@foxreis/tizentube</b>, à ajouter dans l\'app.<br>• <b>Version autonome</b> : fichier .wgt de la dernière version, installé avec Apps2Samsung.</p>' +
      '<div class="btns"></div></div><div class="fb-qr">' + svg + '</div></div>';
    var b1 = H.button('open', 'Ouvrir le guide', function () { H.tv.openUrl(url); }, true);
    H.$('.btns', ov).appendChild(b1);
    H.$('.btns', ov).appendChild(H.button('close', 'Fermer', function () { H.focus.back(); }));
    H.showOverlay(ov, {first: b1});
  }

  function heroPage(cls, iconName, title, text, status, buttons, extra) {
    var el = H.make('div', 'hero-page ' + cls, '<div class="big-ic">' + H.icon(iconName, 72) + '</div><div class="hp-text"><h1>' + H.esc(title) + '</h1>' +
      '<p>' + text + '</p>' + (status ? '<div class="status">' + status + '</div>' : '') + '<div class="btns"></div>' + (extra || '') + '</div>');
    var box = H.$('.btns', el);
    buttons.forEach(function (b) { box.appendChild(b); });
    return el;
  }

  function buildYoutube(pane) {
    pane.innerHTML = '';
    H.tv.find(/tizentube/i, function (tube) {
      H.tv.find(/tizenbrewnextgeneration/i, function (brew) {
        H.tv.find(/youtube/i, function (stock) {
          pane.innerHTML = '';
          var btns = [];
          if (tube) btns.push(H.button('play', 'Ouvrir TizenTube', function () { H.tv.launch(tube.id, function (ok) { if (!ok) H.toast("TizenTube ne s'ouvre pas"); }); }, true));
          else if (brew) btns.push(H.button('play', 'Ouvrir TizenBrewNextGeneration', function () { H.tv.launch(brew.id); }, true));
          else btns.push(H.button('open', 'Comment l\'installer', installGuide, true));
          if (stock && stock !== tube && !/tizentube/i.test(stock.title)) btns.push(H.button('play', 'YouTube officiel (avec pubs)', function () { H.tv.launch(stock.id); }));
          if (tube || brew) btns.push(H.button('open', 'Guide TizenTube', installGuide));
          var status = tube ? '<span class="dot ok"></span>TizenTube détecté : pubs bloquées, SponsorBlock actif' :
            brew ? '<span class="dot ok"></span>TizenTube détecté : YouTube sans pub' :
            '<span class="dot"></span>TizenTube n\'est pas installé sur cette TV';
          pane.appendChild(heroPage('yt', 'play', 'YouTube sans pub',
            'Horizon lance TizenTube, l\'application qui retire les publicités de YouTube, bloque les segments sponsorisés et améliore l\'affichage des titres.',
            status, btns));
          H.focus.refresh();
        });
      });
    });
  }

  function buildMusic(pane) {
    pane.innerHTML = '';
    H.tv.find(/spotify/i, function (spot) {
      var btns = [];
      if (spot) btns.push(H.button('music', 'Ouvrir Spotify', function () { H.tv.launch(spot.id, function (ok) { if (!ok) H.toast("Spotify ne s'ouvre pas"); }); }, true));
      else btns.push(H.button('apps', 'Voir mes applications', allApps, true));
      var status = spot ? '<span class="dot ok"></span>Spotify est installé' : '<span class="dot"></span>Spotify n\'est pas installé : ouvre le Smart Hub › Apps pour l\'ajouter';
      pane.appendChild(heroPage('sp', 'music', 'Spotify',
        'Lance Spotify en un clic. Astuce : depuis ton téléphone, choisis la TV dans « Appareils disponibles » (Spotify Connect) pour envoyer la musique sur l\'écran.',
        status, btns));
      H.focus.refresh();
    });
  }

  // ---------- Actus ----------

  var newsFilter = null;

  function buildNews(pane) {
    pane.innerHTML = '';
    pane.setAttribute('data-snap', '170');
    var head = H.make('div', 'sec news-head', '<div class="row"><div class="track"></div></div>');
    var chips = H.$('.track', head);
    var items = H.news.items;
    var sources = [], seen = {};
    items.forEach(function (it) { if (!seen[it.sourceId]) { seen[it.sourceId] = 1; sources.push([it.sourceId, it.source]); } });
    function chip(id, label) {
      var c = H.make('div', 'f chip' + (newsFilter === id ? ' sel' : ''), H.esc(label));
      c._ok = function () { newsFilter = id; buildNews(pane); H.focus.refresh(); };
      chips.appendChild(c);
    }
    chip(null, 'Tout');
    sources.forEach(function (s) { chip(s[0], s[1]); });
    var rf = H.button('refresh', H.news.loading ? 'Actualisation…' : 'Actualiser', function () { H.news.load(true); });
    rf.className += ' inline';
    chips.appendChild(rf);
    var st = H.button('gear', 'Sources', function () { H.openSettings('news'); });
    st.className += ' inline';
    chips.appendChild(st);
    pane.appendChild(head);

    var fb = H.make('div', 'f fb-banner', '<div class="fbf">f</div><div><h2>Tu sais que tu es d\'Honfleur</h2><p>' +
      (H.settings.data.fbRss ? 'Publications intégrées au fil · OK pour ouvrir la page' : 'Page Facebook · OK pour la scanner ou l\'ouvrir') + '</p></div>');
    fb._ok = H.news.facebook;
    var fbWrap = H.make('div', 'sec'); fbWrap.appendChild(fb); pane.appendChild(fbWrap);

    var grid = H.make('div', 'grid');
    items.filter(function (it) { return !newsFilter || it.sourceId === newsFilter; }).forEach(function (it) { grid.appendChild(H.news.card(it, 'big')); });
    var wrap = H.make('div', 'sec');
    wrap.appendChild(grid);
    if (!items.length) grid.innerHTML = '<div class="b-msg">' + (H.news.loading ? 'Chargement des actualités…' : 'Aucune actualité : vérifie la connexion ou les sources.') + '</div>';
    pane.appendChild(wrap);
  }

  var VIEWS = {
    home: buildHome,
    youtube: buildYoutube,
    music: buildMusic,
    films: function (pane) { H.nas.buildTab(pane); H.focus.refresh(); },
    news: function (pane) { H.news.load(false, function () {}); buildNews(pane); }
  };

  H.home = {
    init: function () {
      buildTabs();
      H.focus.onScroll = function (pane, y) {
        var inView = pane.parentNode && pane.parentNode.className.indexOf('view') >= 0;
        if (inView) H.$('#top').className = y < 0 ? 'solid' : '';
      };
      paintClock(); paintSky();
      setInterval(paintClock, 20000);
      setInterval(paintSky, 5 * 60000);
      showTab('home');
      // Les actus arrivent après coup : on remplit le bandeau, la rangée et l'onglet Actus sans bouger le focus.
      H.news.onChange(function () {
        var f = H.focus.current;
        if (homeParts) {
          paintHero(homeParts.hero);
          if (!(f && homeParts.news.track.contains(f))) buildNewsRow(homeParts.news);
        }
        if (current === 'news') { buildNews(H.$('#v-news .pane')); }
        H.focus.refresh();
      });
      H.news.onImage(function (it) {
        var shown = H.news.items.slice(0, 6)[heroIndex % 6];
        if (homeParts && shown && shown.id === it.id) paintHero(homeParts.hero);
      });
      H.settings.listeners.push(function () {
        built = {}; homeParts = null; newsFilter = null;
        var cur = current; current = ''; showTab(cur);
        H.focus.refresh();
      });
    },
    current: function () { return current; },
    showTab: showTab,
    autoPinned: autoPinned,
    gotoTab: showTabAndFocus,
    visibleRoots: function () { return [H.$('#top'), H.$('.view.on')]; }
  };
})();
