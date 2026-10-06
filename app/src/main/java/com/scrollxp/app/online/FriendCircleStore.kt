package com.scrollxp.app.online

import com.google.firebase.Timestamp
import com.google.firebase.firestore.*
import com.scrollxp.app.domain.FriendScorePolicy
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class CircleMember(val uid: String, val name: String, val goalDays: Int, val joinedAt: Long)
data class FriendCircle(val code: String, val title: String, val createdAt: Long, val endsAt: Long,
    val members: List<CircleMember>)

/** Small invite-only circles, manually refreshed, with self-reported balance scores. */
class FriendCircleStore(private val db: FirebaseFirestore) {
    private fun links(uid: String) = db.document("friendLinks/$uid")
    private fun group(code: String) = db.document("friendCircles/$code")
    private fun member(code: String, uid: String) = group(code).collection("members").document(uid)
    private fun ids(snapshot: DocumentSnapshot, key: String): List<String> = (snapshot.get(key) as? List<*>)?.filterIsInstance<String>().orEmpty()
    private fun code(input: String) = FriendScorePolicy.normalizeCode(input).also {
        require(FriendScorePolicy.validCode(it)) { "Paste the full 20-character invite code." }
    }
    private fun name(input: String) = input.trim().also { require(it.length in 1..32) { "Choose a display name with 1–32 characters." } }
    suspend fun list(uid: String): List<FriendCircle> {
        val codes = ids(links(uid).get(Source.SERVER).await(), "circleIds")
        return codes.map { circleId ->
            val metadata = group(code(circleId)).get(Source.SERVER).await()
            check(metadata.exists()) { "A circle changed. Refresh your friends." }
            val members = group(circleId).collection("members").get(Source.SERVER).await().documents.map {
                CircleMember(it.id, it.getString("name").orEmpty(), (it.getLong("goalDays") ?: 0).toInt(), it.getTimestamp("joinedAt")!!.toDate().time)
            }.sortedWith(compareByDescending<CircleMember> { it.goalDays }.thenBy { it.name }.thenBy { it.uid })
            FriendCircle(circleId, metadata.getString("title").orEmpty(), metadata.getTimestamp("createdAt")!!.toDate().time,
                metadata.getTimestamp("endsAt")!!.toDate().time, members)
        }
    }
    suspend fun create(uid: String, title: String, displayName: String) {
        val titleValue = name(title); val display = name(displayName)
        val circleId = UUID.randomUUID().toString().replace("-", "").take(20).uppercase(java.util.Locale.ROOT)
        // Use the SDK server timestamp for creation; rules allow at most 7 days plus 5 minutes of device-clock skew.
        val endsAt = Timestamp(java.util.Date(System.currentTimeMillis() + FriendScorePolicy.WEEK))
        db.runTransaction { tx ->
            val existing = ids(tx.get(links(uid)), "circleIds")
            require(existing.size < 3) { "Leave an old circle before creating another. You can join three at a time." }
            check(!tx.get(group(circleId)).exists()) { "Try creating the circle again." }
            tx.set(group(circleId), mapOf("title" to titleValue, "createdAt" to FieldValue.serverTimestamp(), "endsAt" to endsAt, "memberIds" to listOf(uid)))
            tx.set(member(circleId, uid), mapOf("name" to display, "goalDays" to 0, "joinedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
            tx.set(links(uid), mapOf("circleIds" to existing + circleId))
        }.await()
    }
    suspend fun join(uid: String, input: String, displayName: String) {
        val circleId = code(input); val display = name(displayName)
        db.runTransaction { tx ->
            val existing = ids(tx.get(links(uid)), "circleIds")
            val metadata = tx.get(group(circleId)); check(metadata.exists()) { "That invite code was not found." }
            if (circleId !in existing) {
                require(existing.size < 3) { "Leave an old circle before joining another. You can join three at a time." }
                val roster = ids(metadata, "memberIds")
                require(roster.size < 6) { "That circle is full (six friends maximum)." }
                tx.update(group(circleId), "memberIds", roster + uid)
                tx.set(member(circleId, uid), mapOf("name" to display, "goalDays" to 0, "joinedAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp()))
                tx.set(links(uid), mapOf("circleIds" to existing + circleId))
            }
        }.await()
    }
    suspend fun publish(uid: String, input: String, score: Int) {
        val circleId = code(input); require(score in 0..7)
        db.runTransaction { tx ->
            val current = tx.get(member(circleId, uid)); check(current.exists()) { "Join this circle before sharing a score." }
            tx.update(member(circleId, uid), mapOf("goalDays" to maxOf(score, (current.getLong("goalDays") ?: 0).toInt()), "updatedAt" to FieldValue.serverTimestamp()))
        }.await()
    }
    suspend fun leave(uid: String, input: String) {
        val circleId = code(input)
        db.runTransaction { tx ->
            val index = tx.get(links(uid)); val existing = ids(index, "circleIds")
            val metadata = tx.get(group(circleId)); val roster = ids(metadata, "memberIds")
            if (metadata.exists() && uid in roster) {
                val remaining = roster.filterNot { it == uid }
                if (remaining.isEmpty()) tx.delete(group(circleId)) else tx.update(group(circleId), "memberIds", remaining)
            }
            tx.delete(member(circleId, uid))
            tx.set(links(uid), mapOf("circleIds" to existing.filterNot { it == circleId }))
        }.await()
    }
    suspend fun leaveAll(uid: String) {
        ids(links(uid).get(Source.SERVER).await(), "circleIds").forEach { leave(uid, it) }
    }
}
