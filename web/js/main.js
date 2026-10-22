(function () {
  'use strict';
  // Point d'entrée : couche de navigation de base, touches multimédia, rafraîchissements périodiques.
  function start() {
    H.tv.registerKeys();
    H.home.init();
    H.focus.push({
      roots: function () { return H.home.visibleRoots(); },
      first: function () { return H.$('#tabs .tab'); },
      onBack: function () {
        // Retour : d'abord à l'accueil, puis on masque Horizon pour revenir à l'application précédente.
        if (H.home.current() !== 'home') { H.home.gotoTab('home'); return true; }
        H.tv.exit();
        return true;
      }
    });
    // Au premier lancement, on place le focus sur le bandeau d'accueil plutôt que sur les onglets.
    var hero = H.$('#v-home .hero');
    if (hero) H.focus.set(hero);
    // Les données vivantes se rafraîchissent quand on revient sur l'écran.
    document.addEventListener('visibilitychange', function () {
      if (!document.hidden) { H.news.load(false); }
    });
    setInterval(function () { if (!document.hidden) H.news.load(false); }, 15 * 60000);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();
