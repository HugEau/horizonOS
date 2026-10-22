(function () {
  'use strict';
  // Navigation à la télécommande : on déplace une classe .on entre les éléments .f (focusables), au plus proche dans
  // la direction demandée. Le défilement passe par des transformations CSS (GPU) : lignes horizontales (.track) et
  // colonne verticale (.pane). Les couches (overlays) se superposent et restituent le focus à leur fermeture.
  var K = H.tv.keys;
  var layers = [];
  var cur = null;

  function ancestor(el, cls) {
    for (var p = el.parentNode; p && p.nodeType === 1; p = p.parentNode) {
      if ((' ' + p.className + ' ').indexOf(' ' + cls + ' ') >= 0) return p;
    }
    return null;
  }

  function visible(el) {
    return el.offsetParent !== null && !(el.className.indexOf('disabled') >= 0);
  }

  function top() { return layers[layers.length - 1]; }

  function roots() {
    var l = top();
    if (!l) return [];
    return typeof l.roots === 'function' ? l.roots() : l.roots;
  }

  function candidates() {
    var out = [], rs = roots();
    for (var i = 0; i < rs.length; i++) {
      if (!rs[i]) continue;
      var list = rs[i].querySelectorAll('.f');
      for (var j = 0; j < list.length; j++) if (visible(list[j])) out.push(list[j]);
    }
    return out;
  }

  function setTransform(el, x, y) {
    var t = 'translate3d(' + x + 'px,' + y + 'px,0)';
    el.style.webkitTransform = t;
    el.style.transform = t;
  }

  function offsetWithin(el, container) {
    var y = 0;
    for (var p = el; p && p !== container; p = p.offsetParent) y += p.offsetTop;
    return y;
  }

  function reveal(el) {
    var track = ancestor(el, 'track');
    if (track) {
      var row = track.parentNode, vw = row.clientWidth, pad = 96;
      var left = el.offsetLeft, right = left + el.offsetWidth, x = track._x || 0;
      if (left - x < pad) x = left - pad;
      else if (right - x > vw - pad) x = right - vw + pad;
      x = H.clamp(x, 0, Math.max(0, track.scrollWidth - vw));
      if (x !== (track._x || 0)) { track._x = x; setTransform(track, -x, 0); }
    }
    var pane = ancestor(el, 'pane');
    if (pane) {
      var snap = +(pane.getAttribute('data-snap') || 250), vh = pane.parentNode.clientHeight || 1080;
      var t = offsetWithin(el, pane);
      var y = t < snap + 60 ? 0 : -(t - snap);
      y = H.clamp(y, -Math.max(0, pane.scrollHeight - vh + 60), 0);
      if (y !== (pane._y || 0)) { pane._y = y; setTransform(pane, 0, y); if (H.focus.onScroll) H.focus.onScroll(pane, y); }
    }
  }

  function set(el) {
    if (!el) return;
    if (cur && cur !== el) {
      cur.className = cur.className.replace(/ on\b/g, '');
      if (cur._blur) cur._blur(cur);
    }
    cur = el;
    if (el.className.indexOf(' on') < 0) el.className += ' on';
    reveal(el);
    if (el._focus) el._focus(el);
    var l = top();
    if (l) l.last = el;
  }

  function rect(el) { return el.getBoundingClientRect(); }

  function move(dir) {
    var list = candidates();
    if (!cur || !visible(cur) || list.indexOf(cur) < 0) { set(first(list)); return; }
    // 1re passe : seuls les éléments franchement situés dans la direction ; 2e passe : simple demi-plan (secours).
    var best = pick(list, dir, true) || pick(list, dir, false);
    if (best) set(best);
  }

  function pick(list, dir, strict) {
    var a = rect(cur), best = null, bestScore = 1e12, slack = 24;
    var ax = (a.left + a.right) / 2, ay = (a.top + a.bottom) / 2;
    for (var i = 0; i < list.length; i++) {
      var c = list[i];
      if (c === cur) continue;
      var b = rect(c), bx = (b.left + b.right) / 2, by = (b.top + b.bottom) / 2;
      var prim, perpGap, perpCenter;
      if (dir === 'right') { if (strict ? b.left < a.right - slack : bx <= ax + 2) continue; prim = Math.max(0, b.left - a.right); perpGap = Math.max(0, b.top - a.bottom, a.top - b.bottom); perpCenter = Math.abs(by - ay); }
      else if (dir === 'left') { if (strict ? b.right > a.left + slack : bx >= ax - 2) continue; prim = Math.max(0, a.left - b.right); perpGap = Math.max(0, b.top - a.bottom, a.top - b.bottom); perpCenter = Math.abs(by - ay); }
      else if (dir === 'down') { if (strict ? b.top < a.bottom - slack : by <= ay + 2) continue; prim = Math.max(0, b.top - a.bottom); perpGap = Math.max(0, b.left - a.right, a.left - b.right); perpCenter = Math.abs(bx - ax); }
      else { if (strict ? b.bottom > a.top + slack : by >= ay - 2) continue; prim = Math.max(0, a.top - b.bottom); perpGap = Math.max(0, b.left - a.right, a.left - b.right); perpCenter = Math.abs(bx - ax); }
      var score = prim + perpGap * 4 + perpCenter * 0.6;
      if (score < bestScore) { bestScore = score; best = c; }
    }
    return best;
  }

  function first(list) {
    var best = null;
    for (var i = 0; i < list.length; i++) {
      if (!best) { best = list[i]; continue; }
      var r = rect(list[i]), q = rect(best);
      if (r.top < q.top - 4 || (Math.abs(r.top - q.top) <= 4 && r.left < q.left)) best = list[i];
    }
    return best;
  }

  function isField(el) { return el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA'); }

  function press() {
    if (!cur) return;
    if (isField(cur)) { cur.focus(); return; }
    if (cur._ok) cur._ok(cur);
  }

  function back() {
    var l = top();
    if (l && l.onBack && l.onBack() === true) return;
    if (layers.length > 1) H.focus.pop();
  }

  document.addEventListener('keydown', function (e) {
    var code = e.keyCode, l = top();
    var active = document.activeElement;
    if (isField(active)) {
      // Pendant la saisie : OK/Retour referme le clavier, haut/bas reprend la navigation.
      if (code === K.ok || code === K.back || code === 27) { active.blur(); e.preventDefault(); return; }
      if (code === K.up || code === K.down) { active.blur(); }
      else return;
    }
    if (l && l.onKey && l.onKey(code, e) === true) { e.preventDefault(); return; }
    switch (code) {
      case K.left: move('left'); break;
      case K.right: move('right'); break;
      case K.up: move('up'); break;
      case K.down: move('down'); break;
      case K.ok: press(); break;
      case K.back: case 27: case 8: back(); break;
      default: return;
    }
    e.preventDefault();
  });

  H.focus = {
    set: set,
    get current() { return cur; },
    /** Empile une couche : {roots, first, onKey(code), onBack() -> true si géré}. */
    push: function (layer) {
      var prev = top();
      if (prev) prev.last = cur;
      layers.push(layer);
      var f = layer.first;
      if (typeof f === 'function') f = f();
      set(f || first(candidates()));
    },
    pop: function () {
      var l = layers.pop();
      if (l && l.onClose) l.onClose();
      var t = top();
      if (t) {
        var list = candidates();
        set(t.last && list.indexOf(t.last) >= 0 ? t.last : first(list));
      }
    },
    depth: function () { return layers.length; },
    /** Replace le focus quand la page a été reconstruite sous nos pieds. */
    refresh: function (preferred) {
      var list = candidates();
      if (preferred && list.indexOf(preferred) >= 0) return set(preferred);
      if (!cur || list.indexOf(cur) < 0) set(first(list));
    },
    reveal: reveal,
    back: back,
    first: function (root) { return first((function () { var o = [], l = root.querySelectorAll('.f'); for (var i = 0; i < l.length; i++) if (visible(l[i])) o.push(l[i]); return o; })()); }
  };
})();
