(function () {
  'use strict';
  var SECTIONS = [['weather', 'Météo'], ['news', 'Actualités'], ['nas', 'NAS & films'], ['apps', 'Applications'], ['general', 'Général']];

  function srow(name, sub) {
    return H.make('div', 'srow', '<div class="s-name">' + H.esc(name) + (sub ? '<small>' + H.esc(sub) + '</small>' : '') + '</div><div class="s-ctl"></div>');
  }

  function add(row, el) { H.$('.s-ctl', row).appendChild(el); return el; }

  function cycle(label, options, index, onChange, key) {
    var b = H.button('', label + ' : ' + options[index], function () { onChange((index + 1) % options.length); });
    b.setAttribute('data-k', key || label);
    return b;
  }

  function field(label, value, opts) {
    opts = opts || {};
    var w = H.make('div', 'field', '<label>' + H.esc(label) + '</label><input class="f" type="' + (opts.password ? 'password' : 'text') + '" placeholder="' + H.esc(opts.placeholder || '') + '">');
    var input = H.$('input', w);
    input.value = value || '';
    input.setAttribute('data-k', 'f:' + label);
    if (opts.onInput) input.oninput = function () { opts.onInput(input.value); };
    if (opts.onChange) input.onchange = function () { opts.onChange(input.value); };
    return w;
  }

  function open(sectionId) {
    var ov = H.make('div', 'overlay settings', '<div class="s-nav"><h1>Réglages</h1></div><div class="s-main"><div class="s-scroll"><div class="pane" data-snap="40"></div></div></div>');
    var nav = H.$('.s-nav', ov), pane = H.$('.pane', ov), current = sectionId || 'weather';
    var navEls = {};

    function rebuild(keepKey) {
      var k = keepKey || (H.focus.current && H.focus.current.getAttribute && H.focus.current.getAttribute('data-k'));
      pane.innerHTML = '';
      pane._y = 0; pane.style.webkitTransform = pane.style.transform = 'translate3d(0,0,0)';
      BUILD[current](pane, rebuild);
      for (var id in navEls) navEls[id].className = 'f navitem' + (id === current ? ' sel' : '') + (navEls[id] === H.focus.current ? ' on' : '');
      var target = null;
      if (k) H.$$('[data-k]', pane).forEach(function (e) { if (e.getAttribute('data-k') === k) target = e; });
      if (target) H.focus.set(target);
    }

    SECTIONS.forEach(function (s) {
      var n = H.make('div', 'f navitem', H.esc(s[1]));
      n._focus = function () { if (current !== s[0]) { current = s[0]; rebuild(); } };
      n._ok = function () { var f = H.focus.first(pane); if (f) H.focus.set(f); };
      nav.appendChild(n);
      navEls[s[0]] = n;
    });
    nav.appendChild(H.button('back', 'Fermer', function () { H.focus.back(); }));

    H.showOverlay(ov, {first: navEls[current]});
    rebuild();
  }

  // ---------- Météo ----------
  function weatherSection(pane, rebuild) {
    var d = H.settings.data, styles = Object.keys(H.STYLES), tones = Object.keys(H.TONES);
    pane.appendChild(H.make('h2', '', 'Widgets météo de l\'accueil'));
    pane.appendChild(H.make('p', 'hint', 'Choisis la ville, le style et la couleur de chaque widget. Les widgets apparaissent dans cet ordre sur l\'accueil.'));
    d.weather.forEach(function (w, i) {
      var row = srow(w.city, w.lat.toFixed(2) + ', ' + w.lon.toFixed(2));
      add(row, cycle('Style', styles.map(function (s) { return H.STYLES[s]; }), styles.indexOf(w.style), function (n) {
        H.settings.update(function () { w.style = styles[n]; }); rebuild('s' + i);
      }, 's' + i));
      add(row, cycle('Couleur', tones.map(function (t) { return H.TONES[t].label; }), tones.indexOf(w.tone), function (n) {
        H.settings.update(function () { w.tone = tones[n]; }); rebuild('t' + i);
      }, 't' + i));
      var up = add(row, H.button('up', '', function () { move(i, -1); }));
      var dn = add(row, H.button('down', '', function () { move(i, 1); }));
      up.setAttribute('data-k', 'u' + i); dn.setAttribute('data-k', 'd' + i);
      if (i === 0) up.className += ' disabled';
      if (i === d.weather.length - 1) dn.className += ' disabled';
      var rm = add(row, H.button('close', 'Supprimer', function () { H.settings.update(function () { d.weather.splice(i, 1); }); rebuild(); }));
      rm.setAttribute('data-k', 'x' + i);
      pane.appendChild(row);
    });
    function move(i, dir) {
      H.settings.update(function () { var t = d.weather[i]; d.weather[i] = d.weather[i + dir]; d.weather[i + dir] = t; });
      rebuild((dir < 0 ? 'u' : 'd') + (i + dir));
    }

    pane.appendChild(H.make('h2', '', 'Ajouter une ville'));
    var q = '';
    pane.appendChild(field('Nom de la ville', '', {placeholder: 'Ex. Deauville', onInput: function (v) { q = v; }}));
    var results = H.make('div', 'results');
    var search = H.button('', 'Rechercher', function () {
      results.innerHTML = '<p class="hint">Recherche…</p>';
      H.weather.search(q.trim(), function (err, list) {
        results.innerHTML = '';
        if (err || !list.length) { results.innerHTML = '<p class="hint">' + (err ? 'Recherche impossible' : 'Aucun résultat') + '</p>'; return; }
        list.forEach(function (p) {
          var b = H.button('plus', p.city + (p.region ? ' · ' + p.region : ''), function () {
            H.settings.update(function () { d.weather.push({id: H.uid(), city: p.city, lat: p.lat, lon: p.lon, style: 'current', tone: 'auto'}); });
            H.toast(p.city + ' ajoutée');
            rebuild();
          });
          results.appendChild(b);
        });
        H.focus.set(results.firstChild);
      });
    }, true);
    search.setAttribute('data-k', 'search');
    pane.appendChild(search);
    pane.appendChild(results);
  }

  // ---------- Actualités ----------
  function newsSection(pane, rebuild) {
    var d = H.settings.data;
    pane.appendChild(H.make('h2', '', 'Sources d\'actualités'));
    d.news.forEach(function (s, i) {
      var row = srow(s.name, s.builtin ? 'Source intégrée' : s.url);
      var t = add(row, H.button(s.enabled ? 'check' : '', s.enabled ? 'Activée' : 'Désactivée', function () {
        H.settings.update(function () { s.enabled = !s.enabled; }); H.news.load(true); rebuild('n' + i);
      }));
      t.setAttribute('data-k', 'n' + i);
      if (!s.builtin) {
        var rm = add(row, H.button('close', 'Supprimer', function () { H.settings.update(function () { d.news.splice(i, 1); }); rebuild(); }));
        rm.setAttribute('data-k', 'r' + i);
      }
      pane.appendChild(row);
    });
    pane.appendChild(H.make('h2', '', 'Ajouter un flux RSS'));
    var name = '', url = '';
    pane.appendChild(field('Nom', '', {placeholder: 'Ex. Mairie de Honfleur', onInput: function (v) { name = v; }}));
    pane.appendChild(field('Adresse du flux', '', {placeholder: 'https://…/feed', onInput: function (v) { url = v; }}));
    var addBtn = H.button('plus', 'Ajouter la source', function () {
      if (!/^https?:\/\//i.test(url.trim())) return H.toast('Adresse invalide : elle doit commencer par http');
      H.settings.update(function () { d.news.push({id: H.uid(), name: name.trim() || 'Source', url: url.trim(), enabled: true, builtin: false}); });
      H.news.load(true);
      rebuild();
    }, true);
    pane.appendChild(addBtn);

    pane.appendChild(H.make('h2', '', 'Page Facebook « Tu sais que tu es d\'Honfleur »'));
    pane.appendChild(H.make('p', 'hint', 'Facebook n\'offre pas de flux public. Colle l\'adresse de la page pour le QR code, et, si tu as un pont RSS (RSS-Bridge, FetchRSS…), son flux pour intégrer les publications au fil d\'actus.'));
    pane.appendChild(field('Adresse de la page', d.fbUrl, {placeholder: 'https://www.facebook.com/…', onChange: function (v) { H.settings.update(function () { d.fbUrl = v.trim(); }); }}));
    pane.appendChild(field('Flux RSS du pont (facultatif)', d.fbRss, {placeholder: 'https://…', onChange: function (v) { H.settings.update(function () { d.fbRss = v.trim(); }); H.news.load(true); }}));
  }

  // ---------- NAS ----------
  function nasSection(pane, rebuild) {
    var d = H.settings.data;
    pane.appendChild(H.make('h2', '', 'Sources NAS'));
    pane.appendChild(H.make('p', 'hint', 'Une TV Samsung ne lit pas le SMB : le NAS doit exposer ses films en HTTP. Synology : paquet « Serveur WebDAV » (http://IP:5005/video/). Autres NAS : activer WebDAV, ou un serveur web avec index de dossiers.'));
    var form = {id: '', name: '', url: '', user: '', pass: ''};
    d.nas.forEach(function (s, i) {
      var row = srow(s.name, s.url + (s.user ? '  ·  ' + s.user : ''));
      var ed = add(row, H.button('gear', 'Modifier', function () {
        form.id = s.id; form.name = s.name; form.url = s.url; form.user = s.user; form.pass = s.pass; fill();
      }));
      ed.setAttribute('data-k', 'e' + i);
      var rm = add(row, H.button('close', 'Supprimer', function () { H.settings.update(function () { d.nas.splice(i, 1); }); rebuild(); }));
      rm.setAttribute('data-k', 'r' + i);
      pane.appendChild(row);
    });
    pane.appendChild(H.make('h2', '', 'Ajouter / modifier'));
    var box = H.make('div', 'form');
    pane.appendChild(box);
    var status = H.make('p', 'hint status');

    function fill() {
      box.innerHTML = '';
      box.appendChild(field('Nom', form.name, {placeholder: 'Ex. Synology', onInput: function (v) { form.name = v; }}));
      box.appendChild(field('Adresse du dossier de films', form.url, {placeholder: 'http://192.168.1.20:5005/video/', onInput: function (v) { form.url = v; }}));
      box.appendChild(field('Utilisateur', form.user, {placeholder: 'facultatif', onInput: function (v) { form.user = v; }}));
      box.appendChild(field('Mot de passe', form.pass, {password: true, placeholder: 'facultatif', onInput: function (v) { form.pass = v; }}));
      var actions = H.make('div', 'btns');
      actions.appendChild(H.button('refresh', 'Tester la connexion', function () {
        var src = current();
        if (!src) return;
        status.textContent = 'Test en cours…';
        H.nas.test(src, function (err, n) { status.textContent = err ? 'Échec : ' + err : 'Connexion réussie : ' + n + ' élément(s) trouvés'; });
      }));
      actions.appendChild(H.button('check', 'Enregistrer', function () {
        var src = current();
        if (!src) return;
        H.settings.update(function () {
          var at = -1;
          d.nas.forEach(function (s, i) { if (s.id === src.id) at = i; });
          if (at >= 0) d.nas[at] = src; else d.nas.push(src);
        });
        H.toast('NAS enregistré');
        rebuild();
      }, true));
      box.appendChild(actions);
      box.appendChild(status);
    }

    function current() {
      var url = form.url.trim();
      if (!/^https?:\/\//i.test(url)) { H.toast('Adresse invalide : elle doit commencer par http:// ou https://'); return null; }
      if (url.charAt(url.length - 1) !== '/') url += '/';
      return {id: form.id || H.uid(), name: form.name.trim() || 'Mon NAS', url: url, user: form.user.trim(), pass: form.pass};
    }
    fill();
  }

  // ---------- Applications ----------
  function appsSection(pane, rebuild) {
    pane.appendChild(H.make('h2', '', 'Applications du dock'));
    pane.appendChild(H.make('p', 'hint', 'YouTube (TizenTube), Spotify et Films sont toujours présents. Choisis les autres applications épinglées sur l\'accueil.'));
    H.tv.apps(function (apps) {
      var d = H.settings.data;
      var auto = apps.filter(function (a) { return H.home.autoPinned(a); }).map(function (a) { return a.id; });
      var pinned = d.pinned || auto;
      apps.forEach(function (a, i) {
        if (/tizentube|spotify/i.test(a.title)) return;
        var on = pinned.indexOf(a.id) >= 0;
        var row = srow(a.title, a.id);
        var t = add(row, H.button(on ? 'check' : '', on ? 'Épinglée' : 'Non épinglée', function () {
          var list = (d.pinned || auto).slice();
          var at = list.indexOf(a.id);
          if (at >= 0) list.splice(at, 1); else list.push(a.id);
          H.settings.update(function () { d.pinned = list; });
          rebuild('a' + i);
        }));
        t.setAttribute('data-k', 'a' + i);
        pane.appendChild(row);
      });
      var reset = H.button('refresh', 'Sélection automatique', function () { H.settings.update(function () { d.pinned = null; }); rebuild(); });
      reset.setAttribute('data-k', 'reset');
      pane.appendChild(reset);
    });
  }

  // ---------- Général ----------
  function generalSection(pane, rebuild) {
    var d = H.settings.data;
    pane.appendChild(H.make('h2', '', 'Général'));
    var row = srow('Unité de température');
    add(row, cycle('Unité', ['Celsius (°C)', 'Fahrenheit (°F)'], d.fahrenheit ? 1 : 0, function (n) { H.settings.update(function () { d.fahrenheit = n === 1; }); rebuild('Unité'); }));
    pane.appendChild(row);
    var armed = false;
    var reset = H.button('refresh', 'Réinitialiser tous les réglages', function () {
      if (!armed) { armed = true; reset.lastChild.textContent = 'Appuie encore pour confirmer'; return; }
      try { localStorage.removeItem('horizon.settings'); localStorage.removeItem('horizon.resume'); } catch (e) {}
      location.reload();
    });
    pane.appendChild(reset);
    pane.appendChild(H.make('p', 'hint', 'Horizon 1.0 · météo Open-Meteo · actualités : Honfleur-Infos, Google Actualités · lecteur : AVPlay de Samsung. Interface inspirée du projet Free My TV (MIT).'));
  }

  var BUILD = {weather: weatherSection, news: newsSection, nas: nasSection, apps: appsSection, general: generalSection};

  H.openSettings = open;
})();
