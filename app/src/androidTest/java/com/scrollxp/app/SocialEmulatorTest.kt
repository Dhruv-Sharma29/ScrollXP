package com.scrollxp.app

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.scrollxp.app.online.*
import com.scrollxp.app.data.IslandBackup
import com.scrollxp.app.worker.NightlyStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test

/** Named demo clients only; no default Firebase account or local game database is modified. */
class SocialEmulatorTest {
    @Test fun actualAndroidClientsClaimAddAcceptShareBlockDeleteAndRespectCloudConflicts() = runBlocking {
        if (InstrumentationRegistry.getArguments().getString("socialEmulators") != "true") return@runBlocking
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun client(name: String): Triple<FirebaseApp,FirebaseAuth,FirebaseFirestore> {
            val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder().setProjectId("demo-scrollxp")
                .setApplicationId("1:123456789:android:demo").setApiKey("demo-key").build(), "social-$name-${System.nanoTime()}")
            val auth = FirebaseAuth.getInstance(app); auth.useEmulator("127.0.0.1",9099)
            val db = FirebaseFirestore.getInstance(app); db.useEmulator("127.0.0.1",8180)
            db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            return Triple(app, auth, db)
        }
        val (appA,authA,dbA) = client("a"); val (appB,authB,dbB) = client("b")
        try {
            val a = authA.signInWithEmailAndPassword("social-a@example.test","Emulator-only-123!").await().user!!
            val b = authB.signInWithEmailAndPassword("social-b@example.test","Emulator-only-123!").await().user!!
            assertTrue(a.isEmailVerified); assertTrue(b.isEmailVerified)
            val sa = SocialStore(dbA); val sb = SocialStore(dbB)
            sa.claim(a.uid,"social_alpha"); sb.claim(b.uid,"social_beta")
            sa.request(a.uid, sa.find("@SOCIAL_BETA")!!)
            val incoming = sb.load(b.uid).friends.single(); assertEquals("pending", incoming.status)
            sb.act(b.uid,incoming.id,"accept")
            assertEquals("accepted", sa.load(a.uid).friends.single().status)
            sa.publish(a.uid,emptyList(),"Dehradun"); sb.publish(b.uid,emptyList(),null)
            assertEquals(2,sa.load(a.uid).rows.size); assertEquals(2,sb.load(b.uid).rows.size)
            assertEquals(1,sb.local("Dehradun").size)
            sa.report(a.uid, b.uid)
            sa.act(a.uid,incoming.id,"block")
            assertTrue(sb.load(b.uid).rows.none { it.uid == a.uid })
            try { sb.act(b.uid,incoming.id,"remove"); fail("Blocked users must not unblock themselves") } catch (_: IllegalStateException) { }
            val cloud = CloudAccountStore(dbA)
            val json = InstrumentationRegistry.getInstrumentation().context.assets.open("island-v1.json").bufferedReader().use { it.readText() }
            val island = IslandBackup.decode(json)
            val saved = cloud.saveIfUnchanged(a.uid,island,"")
            val stamp = NightlyStore.stamp(saved.getTimestamp("updatedAt"))
            cloud.save(a.uid,island.copy(profile=island.profile.copy(islandName="Other device")))
            try { cloud.saveIfUnchanged(a.uid,island,stamp); fail("Nightly save must not overwrite another device") } catch (expected: IllegalStateException) { assertEquals("Cloud backup changed.", expected.message) }
            assertEquals("Other device",cloud.decode(cloud.latest(a.uid))!!.profile.islandName)
            val cb = CloudAccountStore(dbB); cb.intent(b.uid); sb.cleanup(b.uid); cb.beginDeletion(b.uid)
            sb.cleanup(b.uid); cb.beginDeletion(b.uid) // Interrupted Auth deletion can retry after guard creation.
            assertTrue(cb.deletionStarted(b.uid)); assertTrue(sa.load(a.uid).friends.isEmpty())
            assertNull(sa.find("social_beta"))
        } finally {
            authA.signOut();authB.signOut();dbA.terminate().await();dbB.terminate().await();appA.delete();appB.delete()
        }
    }
}
