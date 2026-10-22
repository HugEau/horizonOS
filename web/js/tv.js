(function () {
  'use strict';
  // Côté TV : applications installées, lancement, entrées HDMI, navigateur, touche Retour. Sur Tizen, ce sont les API web
  // de Samsung ; dans un navigateur de bureau, un simulacre permet de prévisualiser l'écran.
  // Approche reprise du projet Free My TV (licence MIT, voir LICENSE-free-my-tv).
  var tizenOk = typeof window.tizen !== 'undefined' && !!window.tizen.application;
  var demo = !tizenOk;

  var SAMSUNG_VIEWER = 'org.tizen.tv-viewer';
  var STOCK_YOUTUBE = '9Ur5IzDKqV.TizenYouTube';

  // Applications de Samsung à ne pas lister (Smart Hub, services, claviers…), même filtre que Free My TV.
  var HIDDEN = new RegExp('^(org\\.volt\\.|org\\.tizen\\.(?!browser$|netflix)|org\\.tizen\\.netflixlowmem$|ise-|ug-|d75857a5-)' +
      '|[Ss]ervice$|[Dd]aemon|Preview$|syspopup|[Hh]elper$|AdPlayer|AdSuite|[Kk]eyboard|\\.OTApp$' +
      '|^com\\.samsung\\.(calendar-account-manager|tv\\.(cobalt-|aria-(dummy|engine)|airplay-app|ambient-support|' +
      'built-in-app-info-viewer|commonsearch|searchall|preview-|tv-scheduler|csfs|notification-app|mycontents|' +
      'safLaunchBridge|shortcut|remote-workspace|voiceassistant|mybixby|weather-viewer))');

  // Smart Hub ne fournit pas toujours de vraie icône : logos locaux pour les services courants.
  var ICONS = {
    '9Ur5IzDKqV.TizenYouTube': 'icons/youtube.png',
    'evKhCgZelL.AmazonIgnitionLauncher2': 'icons/prime.png',
    'RN1MCdNq8t.Netflix': 'icons/netflix.svg',
    'org.tizen.netflix-app': 'icons/netflix.svg',
    'NvZ26VG9Yw.HBOGOLAG': 'icons/max.svg',
    'rJeHak5zRg.Spotify': 'icons/spotify.svg',
    'cexr1qp97S.Deezer': 'icons/deezer.svg'
  };
  var TITLES = {'NvZ26VG9Yw.HBOGOLAG': 'Max', 'rJeHak5zRg.Spotify': 'Spotify', 'evKhCgZelL.AmazonIgnitionLauncher2': 'Prime Video'};

  var SAMPLE = [
    {id: 'demo.tizentube', title: 'TizenTube', icon: 'icons/youtube.png'},
    {id: 'rJeHak5zRg.Spotify', title: 'Spotify', icon: 'icons/spotify.svg'},
    {id: 'RN1MCdNq8t.Netflix', title: 'Netflix', icon: 'icons/netflix.svg'},
    {id: 'evKhCgZelL.AmazonIgnitionLauncher2', title: 'Prime Video', icon: 'icons/prime.png'},
    {id: 'demo.disney', title: 'Disney+', icon: ''},
    {id: 'demo.canal', title: 'MyCanal', icon: ''},
    {id: 'demo.arte', title: 'ARTE', icon: ''},
    {id: 'demo.francetv', title: 'france.tv', icon: ''},
    {id: 'org.tizen.browser', title: 'Navigateur Internet', icon: ''}
  ];

  var cache = null, pending = null;

  function listApps(done) {
    if (cache) return done(cache);
    if (pending) return pending.push(done);
    pending = [done];
    function finish(list) {
      cache = list;
      var q = pending; pending = null;
      for (var i = 0; i < q.length; i++) q[i](list);
    }
    if (demo) {
      var nodemo = /[?&]notube/.test(location.search);
      return setTimeout(function () {
        finish(SAMPLE.filter(function (a) { return !(nodemo && a.id === 'demo.tizentube'); }));
      }, 50);
    }
    var self = '';
    try { self = window.tizen.application.getCurrentApplication().appInfo.id; } catch (e) {}
    try {
      window.tizen.application.getAppsInfo(function (list) {
        var out = [];
        for (var i = 0; i < list.length; i++) {
          var a = list[i];
          if (!a.id || a.show === false || a.id === self || HIDDEN.test(a.id)) continue;
          var icon = a.iconPath || '';
          if (icon && icon.indexOf('://') < 0) icon = 'file://' + icon;
          if (ICONS[a.id]) icon = ICONS[a.id];
          out.push({id: a.id, title: TITLES[a.id] || a.name || a.id, icon: icon});
        }
        var store = out.some(function (x) { return /\.Netflix$/.test(x.id); });
        if (store) out = out.filter(function (x) { return x.id.indexOf('org.tizen.netflix') !== 0; });
        out.sort(function (x, y) { var p = x.title.toLowerCase(), q = y.title.toLowerCase(); return p < q ? -1 : p > q ? 1 : 0; });
        finish(out);
      }, function () { finish([]); });
    } catch (e) { finish([]); }
  }

  function launch(id, done) {
    if (demo) { H.toast('Aperçu : lancement de « ' + id + ' »'); return done && setTimeout(function () { done(true); }, 0); }
    try {
      window.tizen.application.launch(id, function () { if (done) done(true); }, function () { if (done) done(false); });
    } catch (e) { if (done) done(false); }
  }

  function sources(done) {
    try {
      window.tizen.systeminfo.getPropertyValue('VIDEOSOURCE', function (v) { done(v && v.connected ? v.connected : null); },
          function () { done(null); });
    } catch (e) { done(null); }
  }

  /** Bascule la TV sur une source ("source:HDMI:2") puis ramène le lecteur TV de Samsung au premier plan. */
  function setSource(spec, done) {
    if (demo) { H.toast('Aperçu : bascule sur ' + spec.split(':').slice(1).join(' ')); return done && done(true); }
    var parts = spec.split(':'), type = parts[1], number = +parts[2];
    function show(ok) {
      if (!ok) return done && done(false);
      launch(SAMSUNG_VIEWER, function (shown) {
        if (!shown) { try { window.tizen.application.getCurrentApplication().hide(); } catch (e) {} }
        if (done) done(true);
      });
    }
    sources(function (list) {
      var source = null;
      for (var i = 0; list && i < list.length; i++) {
        if (list[i].type === type && list[i].number === number) source = list[i];
      }
      if (!source) return show(type === 'TV');
      try { window.tizen.tvwindow.setSource(source, function () { show(true); }, function () { show(false); }); }
      catch (e) { show(false); }
    });
  }

  function inputs(done) {
    var list = [{id: 'source:TV:1', label: 'TV', sub: 'Antenne / TNT', connected: true}];
    for (var n = 1; n <= 4; n++) list.push({id: 'source:HDMI:' + n, label: 'HDMI ' + n, sub: '', connected: true});
    if (demo) { list[3].connected = false; list[4].connected = false; return done(list); }
    sources(function (found) {
      if (found) {
        for (var i = 1; i < list.length; i++) list[i].connected = false;
        for (var j = 0; j < found.length; j++) {
          var s = found[j];
          if (s.type === 'HDMI' && s.number >= 1 && s.number <= 4) list[s.number].connected = true;
        }
      }
      done(list);
    });
  }

  /** Ouvre une adresse dans le navigateur de la TV. */
  function openUrl(url) {
    if (demo) { window.open(url, '_blank'); return; }
    function viaBrowserApp() {
      try {
        var c = new window.tizen.ApplicationControl('http://tizen.org/appcontrol/operation/view', url, null, null, []);
        window.tizen.application.launchAppControl(c, 'org.tizen.browser', function () {}, function () {
          H.toast("Impossible d'ouvrir le navigateur de la TV");
        });
      } catch (e) { H.toast("Impossible d'ouvrir le navigateur de la TV"); }
    }
    try {
      var control = new window.tizen.ApplicationControl('http://tizen.org/appcontrol/operation/view', url, null, null, []);
      window.tizen.application.launchAppControl(control, null, function () {}, viaBrowserApp);
    } catch (e) { viaBrowserApp(); }
  }

  function registerKeys() {
    if (demo) return;
    var names = ['MediaPlay', 'MediaPause', 'MediaPlayPause', 'MediaStop', 'MediaFastForward', 'MediaRewind',
      'MediaTrackNext', 'MediaTrackPrevious'];
    for (var i = 0; i < names.length; i++) {
      try { window.tizen.tvinputdevice.registerKey(names[i]); } catch (e) {}
    }
  }

  H.tv = {
    demo: demo,
    stockYoutube: STOCK_YOUTUBE,
    apps: listApps,
    /** Première application dont le nom ou l'identifiant correspond ; cb(app | null). */
    find: function (re, cb) {
      listApps(function (list) {
        for (var i = 0; i < list.length; i++) {
          if (re.test(list[i].title) || re.test(list[i].id)) return cb(list[i]);
        }
        cb(null);
      });
    },
    launch: launch,
    inputs: inputs,
    setSource: setSource,
    openUrl: openUrl,
    registerKeys: registerKeys,
    /** Retour au premier écran : on masque Horizon pour revenir où l'on était. */
    exit: function () {
      if (demo) return H.toast('Aperçu : Horizon se masquerait ici');
      try { window.tizen.application.getCurrentApplication().hide(); } catch (e) {}
    },
    keys: {left: 37, up: 38, right: 39, down: 40, ok: 13, back: 10009, play: 415, pause: 19, playPause: 10252,
      stop: 413, ff: 417, rew: 412, next: 10233, prev: 10232}
  };
})();
