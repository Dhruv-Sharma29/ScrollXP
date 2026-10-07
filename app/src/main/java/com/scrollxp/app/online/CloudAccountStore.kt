package com.scrollxp.app.online

import com.google.firebase.firestore.*
import com.scrollxp.app.data.IslandBackup
import kotlinx.coroutines.tasks.await

/** Spark stores only the owner's latest backup and a minimal permanent deletion guard. */
class CloudAccountStore(private val db: FirebaseFirestore) {
    private fun backup(uid: String) = db.document("users/$uid/backups/latest")
    private fun guard(uid: String) = db.document("accountDeletions/$uid")
    suspend fun deletionStarted(uid: String) = guard(uid).get(Source.SERVER).await().exists() || db.document("deletionIntents/$uid").get(Source.SERVER).await().exists()
    suspend fun intent(uid: String) {
        val ref = db.document("deletionIntents/$uid")
        if (!ref.get(Source.SERVER).await().exists() && !guard(uid).get(Source.SERVER).await().exists())
            ref.set(mapOf("createdAt" to FieldValue.serverTimestamp())).await()
    }
    suspend fun latest(uid: String) = backup(uid).get(Source.SERVER).await()
    suspend fun save(uid: String, snapshot: IslandBackup) {
        backup(uid).set(mapOf("version" to IslandBackup.VERSION,"payload" to snapshot.encode(),"name" to snapshot.profile.islandName,
            "xp" to snapshot.xp,"updatedAt" to FieldValue.serverTimestamp())).await()
    }
    suspend fun saveIfUnchanged(uid: String, snapshot: IslandBackup, expected: String): DocumentSnapshot {
        val encoded = snapshot.encode()
        db.runTransaction { tx ->
            val current = tx.get(backup(uid))
            check(com.scrollxp.app.worker.NightlyStore.stamp(current.getTimestamp("updatedAt")) == expected) { "Cloud backup changed." }
            tx.set(backup(uid), mapOf("version" to IslandBackup.VERSION, "payload" to encoded, "name" to snapshot.profile.islandName,
                "xp" to snapshot.xp, "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
        return latest(uid).also { check(it.getString("payload") == encoded) { "Cloud backup changed." } }
    }
    fun decode(document: DocumentSnapshot): IslandBackup? {
        if (!document.exists()) return null
        require(document.get("version") == IslandBackup.VERSION.toLong()) { "Unsupported cloud backup version." }
        val backup = IslandBackup.decode(requireNotNull(document.getString("payload")) { "The cloud backup is incomplete." })
        require(document.getString("name") == backup.profile.islandName && document.get("xp") == backup.xp.toLong()) {
            "The cloud backup metadata does not match its island. Local progress has been kept."
        }
        require(document.getTimestamp("updatedAt") != null) { "The cloud backup is missing its save time." }
        return backup
    }
    suspend fun removeBackup(uid: String) { backup(uid).delete().await() }
    suspend fun beginDeletion(uid: String) {
        db.runTransaction { tx ->
            val guardExists = tx.get(guard(uid)).exists()
            val links = db.document("friendLinks/$uid")
            val circles = tx.get(links).get("circleIds") as? List<*>
            check(circles.isNullOrEmpty()) { "Leave your friend circles before deleting your account." }
            if (!guardExists) tx.set(guard(uid),mapOf("deletedAt" to FieldValue.serverTimestamp()))
            tx.delete(links)
            tx.delete(backup(uid))
            tx.delete(db.document("deletionIntents/$uid"))
        }.await()
    }
}
