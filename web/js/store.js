(function () {
  'use strict';
  var GN = 'https://news.google.com/rss/search?hl=fr&gl=FR&ceid=FR:fr&q=';

  function defaults() {
    var hon = {city: 'Honfleur', lat: 49.4189, lon: 0.2333, tone: 'auto'};
    var lecor = {city: 'Le Corbier', lat: 45.2395, lon: 6.2715, tone: 'auto'};
    function w(id, style) { return {id: id, city: hon.city, lat: hon.lat, lon: hon.lon, style: style, tone: 'auto'}; }
    function wl(id, style) { return {id: id, city: lecor.city, lat: lecor.lat, lon: lecor.lon, style: style, tone: 'auto'}; }
    return {
      fahrenheit: false,
      weather: [w('w1', 'current'), w('w2', 'daily'), wl('w3', 'details')],
      news: [
        {id: 'honfleur-infos', name: 'Honfleur-Infos', url: 'https://www.honfleur-infos.com/feed/', enabled: true, builtin: true},
        {id: 'pays-auge', name: "Le Pays d'Auge", url: GN + '(Honfleur+OR+%22Pays+d%27Auge%22)+site:actu.fr+when:14d', enabled: true, builtin: true},
        {id: 'presse', name: 'Presse régionale', url: GN + 'Honfleur+when:7d', enabled: true, builtin: true}
      ],
      fbUrl: '',
      fbRss: '',
      nas: [],
      pinned: null
    };
  }

  var saved = H.load('settings', {});
  var data = defaults();
  for (var k in saved) data[k] = saved[k];

  H.settings = {
    data: data,
    save: function () { H.save('settings', data); },
    /** Applique fn(data) puis enregistre et prévient les écouteurs. */
    update: function (fn) {
      fn(data);
      H.save('settings', data);
      for (var i = 0; i < H.settings.listeners.length; i++) H.settings.listeners[i](data);
    },
    listeners: []
  };

  H.TONES = {
    auto: {label: 'Auto (selon la météo)'},
    ocean: {label: 'Océan', a: '#2b6cb0', b: '#1a365d'},
    sunset: {label: 'Couchant', a: '#f6875a', b: '#7b2d5b'},
    forest: {label: 'Forêt', a: '#38a169', b: '#1c4532'},
    graphite: {label: 'Graphite', a: '#4a5568', b: '#171923'}
  };
  H.STYLES = {current: 'Actuelle', hourly: 'Heure par heure', daily: '7 jours', details: 'Détails'};
})();
