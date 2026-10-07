package com.scrollxp.app.online

import com.google.firebase.firestore.*

/** Configure before the first read, whether a background worker or Activity starts first. */
object FirebaseStores {
    @Synchronized fun database(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        val current = firestoreSettings
        if (current.cacheSettings !is MemoryCacheSettings) {
            firestoreSettings = FirebaseFirestoreSettings.Builder(current)
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
    }
}
