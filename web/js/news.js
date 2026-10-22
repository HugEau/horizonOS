(function () {
  'use strict';
  var items = [], loading = false, last = 0, listeners = [];
  var FB_SEARCH = 'https://www.facebook.com/search/pages/?q=Tu%20sais%20que%20tu%20es%20d%27Honfleur';
  // Sources parasites observées dans les résultats Google Actualités pour « Honfleur ».
  var BLOCK = /horticulture-abadie/i;

  function htmlToText(html) {
    if (!html) return '';
    var s = html.replace(/<\/(p|h[1-6]|li|div)>|<br\s*\/?>/gi, '\n\n');
    var body = new DOMParser().parseFromString(s, 'text/html').body;
    return (body ? body.textContent : '').replace(/\u00a0/g, ' ');
  }

  function oneLine(s) { return s.replace(/\s+/g, ' ').trim(); }

  function tag(node, name) {
    var n = node.getElementsByTagName(name)[0];
    return n ? n.textContent : '';
  }

  function attr(node, name, a) {
    var n = node.getElementsByTagName(name)[0];
    return n ? n.getAttribute(a) || '' : '';
  }

  function parseDate(s) {
    var t = Date.parse((s || '').trim());
    return isNaN(t) ? Date.now() : t;
  }

  function parse(xml, src) {
    var doc = new DOMParser().parseFromString(xml, 'text/xml');
    var nodes = doc.getElementsByTagName('item');
    if (!nodes.length) nodes = doc.getElementsByTagName('entry');
    var out = [];
    for (var i = 0; i < nodes.length && out.length < 30; i++) {
      var n = nodes[i];
      var link = (tag(n, 'link') || attr(n, 'link', 'href')).trim();
      var title = oneLine(htmlToText(tag(n, 'title')));
      if (!link || !title) continue;
      if (BLOCK.test(oneLine(tag(n, 'source')))) continue;
      var google = link.indexOf('news.google.com') >= 0;
      var source = src.name, gs = oneLine(tag(n, 'source'));
      if (gs) {
        var suffix = ' - ' + gs;
        if (title.length > suffix.length && title.slice(-suffix.length) === suffix) title = title.slice(0, -suffix.length);
        if (src.id !== 'pays-auge' && src.id !== 'honfleur-infos') source = gs;
      }
      var content = tag(n, 'content:encoded') || tag(n, 'content');
      var desc = tag(n, 'description') || tag(n, 'summary');
      var image = '';
      var enc = n.getElementsByTagName('enclosure')[0];
      if (enc && /^image/.test(enc.getAttribute('type') || '')) image = enc.getAttribute('url');
      image = image || attr(n, 'media:thumbnail', 'url') || attr(n, 'media:content', 'url');
      if (!image) { var m = /<img[^>]+src=["']([^"']+)["']/i.exec(content || desc); if (m) image = m[1]; }
      out.push({
        id: link, link: link, title: title, source: source, sourceId: src.id,
        time: parseDate(tag(n, 'pubDate') || tag(n, 'published') || tag(n, 'updated') || tag(n, 'dc:date')),
        summary: google ? '' : oneLine(htmlToText(desc)).replace(/\s*\[…\]$/, '').slice(0, 400),
        body: google ? '' : htmlToText(content).split('\n').map(oneLine).filter(function (l) { return l && !/est apparu en premier sur/.test(l); }).join('\n\n'),
        image: image
      });
    }
    return out;
  }

  function notify() { for (var i = 0; i < listeners.length; i++) listeners[i](items); }

  var cards = {};
  function imageReady(it) {
    (cards[it.id] || []).forEach(function (el) { if (el._setImage) el._setImage(it.image); });
    for (var i = 0; i < imageListeners.length; i++) imageListeners[i](it);
  }
  var imageListeners = [];

  function enrich() {
    var todo = items.filter(function (it) { return !it.image && it.link.indexOf('news.google.com') < 0; }).slice(0, 20), idx = 0, active = 0;
    function next() {
      while (active < 3 && idx < todo.length) {
        (function (it) {
          active++;
          H.http({url: it.link, timeout: 8000}, function (err, x) {
            active--;
            if (!err) {
              var head = x.responseText.slice(0, 100000);
              var m = /<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']/i.exec(head) ||
                /<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:image["']/i.exec(head);
              if (m) { it.image = m[1]; imageReady(it); }
            }
            next();
          });
        })(todo[idx++]);
      }
    }
    next();
  }

  function load(force, cb) {
    if (loading) return;
    if (!force && items.length && Date.now() - last < 10 * 60000) { if (cb) cb(); return; }
    loading = true;
    var s = H.settings.data, srcs = s.news.filter(function (x) { return x.enabled; });
    if (s.fbRss) srcs = srcs.concat([{id: 'facebook', name: "Tu sais que tu es d'Honfleur", url: s.fbRss}]);
    var left = srcs.length, all = [];
    if (!left) { loading = false; items = []; notify(); if (cb) cb(); return; }
    srcs.forEach(function (src) {
      H.http({url: src.url}, function (err, x) {
        if (!err) { try { all = all.concat(parse(x.responseText, src)); } catch (e) {} }
        if (--left) return;
        var seen = {};
        all.sort(function (a, b) { return b.time - a.time; });
        items = all.filter(function (it) {
          var k = it.title.toLowerCase().replace(/[^a-z0-9à-ÿ]/g, '').slice(0, 60);
          if (seen[k]) return false;
          return (seen[k] = true);
        }).slice(0, 60);
        loading = false;
        last = Date.now();
        notify();
        if (cb) cb();
        enrich();
      });
    });
  }

  /** Carte d'article : image (si dispo), voile dégradé, source, titre et ancienneté. */
  function card(it, cls) {
    var hue = H.tint(it.source);
    var el = H.make('div', 'f card ' + (cls || ''),
      '<div class="img"></div><div class="shade"></div><div class="meta"><div class="src">' + H.esc(it.source) + '</div>' +
      '<div class="ttl">' + H.esc(it.title) + '</div><div class="when">' + H.rel(it.time) + '</div></div>');
    el.style.background = 'linear-gradient(150deg,hsl(' + hue + ',45%,34%),hsl(' + ((hue + 40) % 360) + ',50%,14%))';
    el._setImage = function (url) {
      var img = new Image(), box = el.firstChild;
      img.onload = function () { box.style.backgroundImage = 'url("' + url.replace(/"/g, '%22') + '")'; box.className = 'img in'; };
      img.src = url;
    };
    if (it.image) el._setImage(it.image);
    (cards[it.id] = (cards[it.id] || []).filter(function (c) { return document.body.contains(c); })).push(el);
    el._ok = function () { open(it); };
    return el;
  }

  function open(it) {
    if (it.body) reader(it); else H.tv.openUrl(it.link);
  }

  function reader(it) {
    var ov = H.make('div', 'overlay reader');
    ov.innerHTML = '<div class="reader-side"><div class="reader-img"></div><div class="src">' + H.esc(it.source) + '</div>' +
      '<div class="when">' + H.rel(it.time) + '</div><div class="reader-btns"></div></div>' +
      '<div class="reader-main"><div class="reader-pane"><h1>' + H.esc(it.title) + '</h1>' +
      it.body.split('\n\n').map(function (p) { return '<p>' + H.esc(p) + '</p>'; }).join('') + '</div></div>';
    if (it.image) ov.firstChild.firstChild.style.backgroundImage = 'url("' + it.image.replace(/"/g, '%22') + '")';
    var btns = H.$('.reader-btns', ov);
    var b1 = H.button('open', "Ouvrir l'article", function () { H.tv.openUrl(it.link); }, true);
    var b2 = H.button('close', 'Fermer', function () { H.focus.back(); });
    btns.appendChild(b1); btns.appendChild(b2);
    var pane = H.$('.reader-pane', ov), main = pane.parentNode, y = 0;
    H.showOverlay(ov, {
      first: b1,
      onKey: function (code) {
        // Haut/bas font défiler le texte ; les deux boutons sont côte à côte (gauche/droite).
        if (code === 40 || code === 38) {
          var max = Math.max(0, pane.scrollHeight - main.clientHeight + 40);
          y = H.clamp(y + (code === 40 ? 340 : -340), 0, max);
          var t = 'translate3d(0,' + -y + 'px,0)';
          pane.style.webkitTransform = t; pane.style.transform = t;
          return true;
        }
        return false;
      }
    });
  }

  function facebook() {
    var s = H.settings.data, url = s.fbUrl || FB_SEARCH, svg = '';
    try { var qr = qrcode(0, 'M'); qr.addData(url); qr.make(); svg = qr.createSvgTag(8, 0); } catch (e) {}
    var ov = H.make('div', 'overlay center');
    ov.innerHTML = '<div class="fbpage"><div class="fb-text"><h1>Tu sais que tu es d\'Honfleur</h1>' +
      '<p>Facebook ne publie pas de flux public : les publications ne peuvent pas être lues automatiquement sans compte. ' +
      'Scanne le QR code avec ton téléphone, ou ouvre la page dans le navigateur de la TV.</p>' +
      (s.fbUrl ? '' : '<p class="dim">Aucun lien enregistré : le QR code mène à une recherche de la page. Colle son adresse dans Réglages › Actualités.</p>') +
      '<div class="btns"></div></div><div class="fb-qr">' + svg + '</div></div>';
    var btns = H.$('.btns', ov);
    var b1 = H.button('open', 'Ouvrir dans le navigateur', function () { H.tv.openUrl(url); }, true);
    btns.appendChild(b1);
    btns.appendChild(H.button('gear', 'Réglages', function () { H.focus.pop(); H.openSettings('news'); }));
    btns.appendChild(H.button('close', 'Fermer', function () { H.focus.back(); }));
    H.showOverlay(ov, {first: b1});
  }

  H.news = {
    load: load,
    card: card,
    open: open,
    facebook: facebook,
    get items() { return items; },
    get loading() { return loading; },
    onChange: function (fn) { listeners.push(fn); },
    onImage: function (fn) { imageListeners.push(fn); }
  };
})();
