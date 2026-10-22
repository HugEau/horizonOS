package fr.horizon.tv.spotify

import android.service.notification.NotificationListenerService

/** Ne traite aucune notification : sert uniquement à obtenir l'accès aux sessions média actives. */
class MediaListenerService : NotificationListenerService()
