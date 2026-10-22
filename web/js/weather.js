(function () {
  'use strict';
  var cache = {}, TTL = 10 * 60 * 1000;

  function kind(code) {
    if (code === 0) return 'clear';
    if (code === 1 || code === 2) return 'partly';
    if (code === 3) return 'cloudy';
    if (code === 45 || code === 48) return 'fog';
    if (code >= 51 && code <= 57) return 'drizzle';
    if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return 'rain';
    if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'snow';
    if (code >= 95) return 'storm';
    return 'cloudy';
  }

  var LABELS = {0: 'Ciel dégagé', 1: 'Plutôt dégagé', 2: 'Éclaircies', 3: 'Couvert', 45: 'Brouillard', 48: 'Brouillard givrant',
    51: 'Bruine légère', 53: 'Bruine', 55: 'Bruine dense', 56: 'Bruine verglaçante', 57: 'Bruine verglaçante', 61: 'Pluie faible',
    63: 'Pluie', 65: 'Forte pluie', 66: 'Pluie verglaçante', 67: 'Pluie verglaçante', 71: 'Neige faible', 73: 'Neige',
    75: 'Forte neige', 77: 'Grains de neige', 80: 'Averses', 81: 'Averses', 82: 'Fortes averses', 85: 'Averses de neige',
    86: 'Averses de neige', 95: 'Orage', 96: 'Orage et grêle', 99: 'Orage et grêle'};

  function label(code) { return LABELS[code] || 'Nuageux'; }

  // Dégradés « automatiques » : [jour haut, jour bas, nuit haut, nuit bas]
  var AUTO = {
    clear: ['#3d84d6', '#1d4e8f', '#1b2347', '#0b1030'],
    partly: ['#4b86c4', '#2a5385', '#263256', '#111a38'],
    cloudy: ['#6b7a90', '#37445a', '#3a4254', '#1a1f2c'],
    fog: ['#8593a3', '#4a5666', '#4a5260', '#232830'],
    drizzle: ['#4a6077', '#243344', '#2c3a4a', '#131a24'],
    rain: ['#4a6077', '#243344', '#2c3a4a', '#131a24'],
    snow: ['#8fa7c4', '#4e6585', '#566a86', '#252f40'],
    storm: ['#4b3f72', '#1e1936', '#32294f', '#100c1e']
  };

  function icon(code, isDay, size) {
    var k = kind(code), s = size || 64;
    var sun = '<g stroke="#ffc857" stroke-width="3" stroke-linecap="round"><line x1="32" y1="6" x2="32" y2="12"/><line x1="32" y1="52" x2="32" y2="58"/><line x1="6" y1="32" x2="12" y2="32"/><line x1="52" y1="32" x2="58" y2="32"/><line x1="13.6" y1="13.6" x2="17.8" y2="17.8"/><line x1="46.2" y1="46.2" x2="50.4" y2="50.4"/><line x1="13.6" y1="50.4" x2="17.8" y2="46.2"/><line x1="46.2" y1="17.8" x2="50.4" y2="13.6"/></g><circle cx="32" cy="32" r="13" fill="#ffc857"/>';
    var moon = '<path d="M40 10a22 22 0 1 0 14 38A18 18 0 0 1 40 10z" fill="#ffe9a8"/>';
    function cloud(c, dx, dy, sc) {
      return '<g fill="' + c + '" transform="translate(' + dx + ' ' + dy + ') scale(' + sc + ')"><circle cx="22" cy="40" r="10"/><circle cx="34" cy="31" r="14"/><circle cx="46" cy="41" r="9"/><rect x="20" y="40" width="28" height="11" rx="5"/></g>';
    }
    var body;
    if (k === 'clear') body = isDay ? sun : moon;
    else if (k === 'partly') body = '<g transform="translate(-6 -8) scale(.75)">' + (isDay ? sun : moon) + '</g>' + cloud('#f4f7fb', 4, 4, 0.95);
    else if (k === 'cloudy') body = cloud('#b7c3d4', 8, -6, 0.9) + cloud('#f4f7fb', -2, 4, 0.95);
    else if (k === 'fog') body = cloud('#b7c3d4', 0, -6, 1) + '<g stroke="#fff" stroke-opacity=".8" stroke-width="3" stroke-linecap="round"><line x1="14" y1="48" x2="50" y2="48"/><line x1="18" y1="55" x2="46" y2="55"/></g>';
    else if (k === 'drizzle' || k === 'rain') {
      body = cloud('#b7c3d4', 0, -8, 1) + '<g stroke="#7cc4ff" stroke-width="3.5" stroke-linecap="round"><line x1="22" y1="46" x2="19" y2="54"/><line x1="33" y1="46" x2="30" y2="54"/>' + (k === 'rain' ? '<line x1="44" y1="46" x2="41" y2="54"/>' : '') + '</g>';
    } else if (k === 'snow') body = cloud('#b7c3d4', 0, -8, 1) + '<g fill="#fff"><circle cx="22" cy="50" r="2.6"/><circle cx="33" cy="54" r="2.6"/><circle cx="44" cy="50" r="2.6"/></g>';
    else body = cloud('#8e9ab0', 0, -10, 1) + '<polygon points="35,40 25,54 32,54 28,63 42,47 34,47" fill="#ffd66b"/>';
    return '<svg class="wxi" width="' + s + '" height="' + s + '" viewBox="0 0 64 64">' + body + '</svg>';
  }

  function parse(r) {
    var cur = r.current, h = r.hourly, d = r.daily, now = cur.time, hours = [], days = [], i;
    var cutoff = now.slice(0, 13);
    for (i = 0; i < h.time.length && hours.length < 24; i++) {
      if (h.time[i].slice(0, 13) < cutoff) continue;
      hours.push({t: h.time[i], temp: h.temperature_2m[i], code: h.weather_code[i], rain: h.precipitation_probability[i] || 0, day: h.is_day[i] === 1});
    }
    for (i = 0; i < d.time.length; i++) {
      days.push({date: d.time[i], code: d.weather_code[i], max: d.temperature_2m_max[i], min: d.temperature_2m_min[i], rain: d.precipitation_probability_max[i] || 0});
    }
    return {
      temp: cur.temperature_2m, feels: cur.apparent_temperature, humidity: cur.relative_humidity_2m, code: cur.weather_code,
      day: cur.is_day === 1, wind: cur.wind_speed_10m, windDir: cur.wind_direction_10m, hours: hours, days: days,
      sunrise: (d.sunrise[0] || '').slice(11, 16), sunset: (d.sunset[0] || '').slice(11, 16), uv: d.uv_index_max[0] || 0
    };
  }

  function get(lat, lon, cb) {
    var key = lat.toFixed(3) + ',' + lon.toFixed(3), c = cache[key];
    if (c && Date.now() - c.at < TTL) return cb(null, c.w);
    var url = 'https://api.open-meteo.com/v1/forecast?latitude=' + lat + '&longitude=' + lon +
      '&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,is_day' +
      '&hourly=temperature_2m,weather_code,precipitation_probability,is_day' +
      '&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,precipitation_probability_max,uv_index_max' +
      '&timezone=auto&forecast_days=7';
    H.json(url, function (err, r) {
      if (err) return cb(c ? null : err, c && c.w);
      var w;
      try { w = parse(r); } catch (e) { return cb(e); }
      cache[key] = {at: Date.now(), w: w};
      cb(null, w);
    });
  }

  function search(q, cb) {
    if (q.length < 2) return cb(null, []);
    H.json('https://geocoding-api.open-meteo.com/v1/search?count=8&language=fr&format=json&name=' + encodeURIComponent(q), function (err, r) {
      if (err) return cb(err);
      var out = [], list = r.results || [];
      for (var i = 0; i < list.length; i++) {
        var p = list[i];
        out.push({city: p.name, region: [p.admin1, p.country].filter(function (x) { return x; }).join(', '), lat: p.latitude, lon: p.longitude});
      }
      cb(null, out);
    });
  }

  function temp(c) {
    var f = H.settings.data.fahrenheit;
    return Math.round(f ? c * 9 / 5 + 32 : c) + '°';
  }

  function dir(deg) { return ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'][Math.floor((deg + 22) / 45) % 8]; }

  function background(cfg, w) {
    var t = H.TONES[cfg.tone];
    if (t && t.a) return 'linear-gradient(160deg,' + t.a + ',' + t.b + ')';
    var a = AUTO[w ? kind(w.code) : 'clear'], night = w && !w.day;
    return 'linear-gradient(160deg,' + (night ? a[2] : a[0]) + ',' + (night ? a[3] : a[1]) + ')';
  }

  function head(cfg, w) {
    return '<div class="wx-head"><div><div class="wx-city">' + H.esc(cfg.city) + '</div><div class="wx-desc">' + label(w.code) + '</div></div>';
  }

  var RENDER = {
    current: function (cfg, w) {
      var t = w.days[0];
      return head(cfg, w) + icon(w.code, w.day, 76) + '</div><div class="wx-big">' + temp(w.temp) + '</div>' +
        '<div class="wx-sub">Max ' + temp(t.max) + '  ·  Min ' + temp(t.min) + '</div>';
    },
    hourly: function (cfg, w) {
      var html = head(cfg, w) + '</div><div class="wx-now">' + temp(w.temp) + '</div><div class="wx-cols">', n = 0;
      for (var i = 0; i < w.hours.length && n < 8; i += 2, n++) {
        var h = w.hours[i];
        html += '<div class="wx-col"><div class="wx-t">' + (n === 0 ? 'Auj.' : H.pad(+h.t.slice(11, 13)) + 'h') + '</div>' + icon(h.code, h.day, 44) +
          '<div class="wx-v">' + temp(h.temp) + '</div><div class="wx-r">' + (h.rain >= 20 ? h.rain + '%' : '&nbsp;') + '</div></div>';
      }
      return html + '</div>';
    },
    daily: function (cfg, w) {
      var html = head(cfg, w) + '</div><div class="wx-now small">7 jours</div><div class="wx-cols">';
      for (var i = 0; i < w.days.length; i++) {
        var d = w.days[i], dn = H.DAYS[new Date(d.date + 'T12:00:00').getDay()];
        html += '<div class="wx-col"><div class="wx-t">' + (i === 0 ? 'Auj.' : dn.replace('.', '')) + '</div>' + icon(d.code, true, 44) +
          '<div class="wx-v">' + temp(d.max) + '</div><div class="wx-v dim">' + temp(d.min) + '</div></div>';
      }
      return html + '</div>';
    },
    details: function (cfg, w) {
      var cells = [['Ressenti', temp(w.feels)], ['Humidité', w.humidity + '%'], ['Vent', Math.round(w.wind) + ' km/h ' + dir(w.windDir)],
        ['Indice UV', Math.round(w.uv)], ['Lever', w.sunrise], ['Coucher', w.sunset]];
      var html = head(cfg, w) + '</div><div class="wx-grid">';
      for (var i = 0; i < cells.length; i++) html += '<div class="wx-cell"><div class="wx-k">' + cells[i][0] + '</div><div class="wx-v">' + cells[i][1] + '</div></div>';
      return html + '</div>';
    }
  };

  /** Crée un widget (élément focalisable) ; il se remplit dès que la météo arrive. */
  function widget(cfg, onOk) {
    var el = H.make('div', 'f wx wx-' + cfg.style, '<div class="wx-load">Chargement…</div>');
    el.style.background = background(cfg, null);
    el._ok = onOk;
    function paint() {
      get(cfg.lat, cfg.lon, function (err, w) {
        if (!w) { el.innerHTML = '<div class="wx-load">' + (err ? 'Météo indisponible' : 'Chargement…') + '</div>'; return; }
        el.style.background = background(cfg, w);
        el.innerHTML = '<div class="sheen"></div>' + (RENDER[cfg.style] || RENDER.current)(cfg, w);
      });
    }
    paint();
    el._refresh = paint;
    return el;
  }

  H.weather = {get: get, search: search, widget: widget, icon: icon, label: label, temp: temp, kind: kind};
})();
