package com.scrollxp.app.online

import com.google.firebase.firestore.*
import com.scrollxp.app.domain.SocialPolicy
import kotlinx.coroutines.tasks.await

data class SocialProfile(val uid: String, val username: String, val since: Long)
data class SocialFriend(val id: String, val other: SocialProfile, val sender: String, val status: String, val blocker: String)
data class RankingRow(val uid: String, val username: String, val days: Int, val updated: Long, val week: Long)
data class SocialSnapshot(val profile: SocialProfile? = null, val friends: List<SocialFriend> = emptyList(),
    val rows: List<RankingRow> = emptyList(), val sharing: Boolean = false, val area: String? = null)

class SocialStore(private val db: FirebaseFirestore) {
    private fun profile(uid: String) = db.document("socialProfiles/$uid")
    private fun index(uid: String) = db.document("socialLinks/$uid")
    private fun pair(id: String) = db.document("socialPairs/$id")
    private fun ids(d: DocumentSnapshot) = (d.get("pairs") as? List<*>)?.filterIsInstance<String>().orEmpty()
    private fun person(d: DocumentSnapshot) = if (d.exists()) SocialProfile(d.id, d.getString("username")!!, d.getTimestamp("createdAt")!!.toDate().time) else null
    suspend fun claim(uid: String, input: String) {
        val name = SocialPolicy.handle(input); require(SocialPolicy.valid(name)) { "Use 3–20 letters, numbers or underscores, starting with a letter." }
        val handle = db.document("usernames/$name")
        db.runTransaction { tx ->
            check(!tx.get(profile(uid)).exists()) { "Your account already has a username." }
            check(!tx.get(handle).exists()) { "That username is taken. Choose another." }
            tx.set(profile(uid), mapOf("username" to name, "createdAt" to FieldValue.serverTimestamp()))
            tx.set(handle, mapOf("uid" to uid))
        }.await()
    }
    suspend fun find(input: String): SocialProfile? {
        val name = SocialPolicy.handle(input); require(SocialPolicy.valid(name)) { "Enter a valid username." }
        val handle = db.document("usernames/$name").get(Source.SERVER).await()
        return handle.getString("uid")?.let { SocialProfile(it, name, 0) }
    }
    suspend fun request(uid: String, other: SocialProfile) {
        require(uid != other.uid) { "You cannot add yourself." }
        val id = SocialPolicy.pair(uid, other.uid)
        db.runTransaction { tx ->
            val existing = tx.get(pair(id)); val mine = ids(tx.get(index(uid)))
            check(!existing.exists()) { "A request, friendship or block already exists. Refresh Friends." }
            require(mine.size < SocialPolicy.LIMIT) { "Your social list is full (20 friends, requests and blocks combined)." }
            tx.set(pair(id), mapOf("members" to listOf(uid, other.uid).sorted(), "sender" to uid,
                "status" to "pending", "blocker" to "", "createdAt" to FieldValue.serverTimestamp()))
            tx.set(index(uid), mapOf("pairs" to mine + id, "lastPair" to id))
            tx.set(index(other.uid), mapOf("pairs" to FieldValue.arrayUnion(id), "lastPair" to id), SetOptions.merge())
        }.await()
    }
    suspend fun act(uid: String, id: String, action: String, deleting: Boolean = false) {
        db.runTransaction { tx ->
            val old = tx.get(pair(id)); if (!old.exists()) return@runTransaction
            val members = (old.get("members") as List<*>).filterIsInstance<String>(); require(uid in members)
            if (action == "accept") tx.update(pair(id), "status", "accepted")
            else if (action == "block") tx.update(pair(id), mapOf("status" to "blocked", "blocker" to uid))
            else {
                check(deleting || old.getString("status") != "blocked" || old.getString("blocker") == uid) { "Only the person who blocked can unblock." }
                tx.delete(pair(id))
                members.forEach { tx.set(index(it), mapOf("pairs" to FieldValue.arrayRemove(id), "lastPair" to id), SetOptions.merge()) }
            }
        }.await()
    }
    suspend fun load(uid: String): SocialSnapshot {
        val me = person(profile(uid).get(Source.SERVER).await()) ?: return SocialSnapshot()
        val list = ids(index(uid).get(Source.SERVER).await()).map { id ->
            val doc = pair(id).get(Source.SERVER).await()
            val other = (doc.get("members") as List<*>).filterIsInstance<String>().first { it != uid }
            // Only exact related profiles are fetched, never a global user list.
            SocialFriend(id, requireNotNull(person(profile(other).get(Source.SERVER).await())), doc.getString("sender")!!,
                doc.getString("status")!!, doc.getString("blocker").orEmpty())
        }
        val scores = (list.filter { it.status == "accepted" }.map { it.other } + me).mapNotNull { p ->
            row(db.document("friendScores/${p.uid}").get(Source.SERVER).await())
        }.sortedWith(compareByDescending<RankingRow> { it.days }.thenBy { it.username })
        val own = db.document("friendScores/$uid").get(Source.SERVER).await()
        val local = db.document("localScores/$uid").get(Source.SERVER).await()
        return SocialSnapshot(me, list, scores, own.exists(), local.getString("area"))
    }
    private fun row(doc: DocumentSnapshot): RankingRow? = if (doc.exists() && doc.getLong("week") == SocialPolicy.week(System.currentTimeMillis()))
        RankingRow(doc.id, doc.getString("username")!!, doc.getLong("days")!!.toInt(), doc.getTimestamp("updatedAt")!!.toDate().time, doc.getLong("week")!!) else null
    suspend fun local(area: String): List<RankingRow> {
        require(area in SocialPolicy.areas)
        return db.collection("localScores").whereEqualTo("area", area).whereEqualTo("week", SocialPolicy.week(System.currentTimeMillis()))
            .orderBy("days", Query.Direction.DESCENDING).orderBy(FieldPath.documentId()).limit(50).get(Source.SERVER).await()
            .documents.mapNotNull(::row)
    }
    suspend fun publish(uid: String, days: List<com.scrollxp.app.domain.BalanceDay>, area: String?) {
        val now = System.currentTimeMillis(); val week = SocialPolicy.week(now)
        db.runTransaction { tx ->
            val me = requireNotNull(person(tx.get(profile(uid)))) { "Choose a username first." }
            val ref = db.document("friendScores/$uid"); val old = tx.get(ref)
            val since = old.getTimestamp("startedAt")?.toDate()?.time ?: now
            val score = if (!old.exists()) 0 else maxOf(SocialPolicy.score(days, since, now),
                if (old.getLong("week") == week) old.getLong("days")!!.toInt() else 0)
            val data = mapOf("username" to me.username, "week" to week, "days" to score,
                "startedAt" to (old.getTimestamp("startedAt") ?: FieldValue.serverTimestamp()), "updatedAt" to FieldValue.serverTimestamp())
            tx.set(ref, data)
            if (area != null) { require(area in SocialPolicy.areas); tx.set(db.document("localScores/$uid"), data + ("area" to area)) }
        }.await()
    }
    suspend fun withdraw(uid: String, localOnly: Boolean) {
        val batch = db.batch().delete(db.document("localScores/$uid"))
        if (!localOnly) batch.delete(db.document("friendScores/$uid"))
        batch.commit().await()
    }
    suspend fun report(uid: String, target: String) {
        val ref = db.document("socialReports/${SocialPolicy.pair(uid, target)}")
        check(!ref.get(Source.SERVER).await().exists()) { "You already reported this profile." }
        ref.set(mapOf("reporter" to uid, "target" to target,
            "reason" to "inappropriate_profile", "createdAt" to FieldValue.serverTimestamp())).await()
    }
    suspend fun cleanup(uid: String) {
        ids(index(uid).get(Source.SERVER).await()).forEach { act(uid, it, "remove", deleting = true) }
        val me = person(profile(uid).get(Source.SERVER).await())
        while (true) {
            val reports = db.collection("socialReports").whereEqualTo("reporter", uid).limit(20).get(Source.SERVER).await()
            if (reports.isEmpty) break
            val remove = db.batch(); reports.documents.forEach { remove.delete(it.reference) }; remove.commit().await()
        }
        val batch = db.batch().delete(index(uid)).delete(profile(uid)).delete(db.document("friendScores/$uid")).delete(db.document("localScores/$uid"))
        me?.let { batch.delete(db.document("usernames/${it.username}")) }
        batch.commit().await()
    }
}
