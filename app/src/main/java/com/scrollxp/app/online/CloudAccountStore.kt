package com.scrollxp.app.online

import com.google.firebase.firestore.*
import com.scrollxp.app.data.IslandBackup
import kotlinx.coroutines.tasks.await

/** Spark stores only the owner's latest backup and a minimal permanent deletion guard. */
class CloudAccountStore(private val db: FirebaseFirestore) {
    private fun backup(uid: String) = db.document("users/$uid/backups/latest")
    private fun guard(uid: String) = db.document("accountDeletions/$uid")
    suspend fun deletionStarted(uid: String) = guard(uid).get(Source.SERVER).await().exists()
    suspend fun latest(uid: String) = backup(uid).get(Source.SERVER).await()
    suspend fun save(uid: String, snapshot: IslandBackup) {
        backup(uid).set(mapOf("version" to IslandBackup.VERSION,"payload" to snapshot.encode(),"name" to snapshot.profile.islandName,
            "xp" to snapshot.xp,"updatedAt" to FieldValue.serverTimestamp())).await()
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
        }.await()
    }
}
