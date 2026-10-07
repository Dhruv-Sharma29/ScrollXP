package com.scrollxp.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.FieldValue
import com.scrollxp.app.data.*
import com.scrollxp.app.online.CloudAccountStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in only: the named demo app never touches the default production account or local database. */
@RunWith(AndroidJUnit4::class)
class CloudBackupInteropTest {
    @Test fun exchangeWithNativeSwiftClient() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        if (args.getString("cloudEmulators") != "true") return@runBlocking
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId("demo-scrollxp").setApplicationId("1:123456789:android:demo")
            .setApiKey("demo-key").build(), "backup-contract-${System.nanoTime()}")
        val auth = FirebaseAuth.getInstance(app)
        val db = FirebaseFirestore.getInstance(app)
        auth.useEmulator("127.0.0.1", 9099); db.useEmulator("127.0.0.1", 8180)
        db.firestoreSettings = FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        try {
            val user = auth.signInWithEmailAndPassword("shared-backup@example.test", "Emulator-only-123!").await().user!!
            assertTrue(user.isEmailVerified)
            val cloud = CloudAccountStore(db)
            if (args.getString("backupPhase") == "produce") {
                val json = InstrumentationRegistry.getInstrumentation().context.assets.open("island-v1.json").bufferedReader().use { it.readText() }
                cloud.save(user.uid, IslandBackup.decode(json))
                assertEquals(120, cloud.decode(cloud.latest(user.uid))!!.xp)
                try { cloud.latest("another-owner"); fail("Other accounts' backups must be private") } catch (_: com.google.firebase.firestore.FirebaseFirestoreException) { }
            } else {
                val backup = cloud.decode(cloud.latest(user.uid))!!
                assertEquals("iPhone return", backup.profile.islandName); assertEquals(122, backup.xp)
                assertEquals(1790812860456L, backup.chests[0].openedAt); assertNull(backup.chests[1].openedAt)
                val local = Room.inMemoryDatabaseBuilder(context, ScrollDatabase::class.java).build()
                try {
                    local.dao().saveProfile(Profile(onboarded = true, paused = true, selectedApps = "this.device", zone = "UTC"))
                    repeat(2) { IslandBackupStore(local).restore(backup) { auth.currentUser?.uid == user.uid } }
                    assertEquals(122, local.dao().totalXp()); assertEquals(2, local.dao().chests().size)
                    assertTrue(local.dao().profile()!!.paused); assertEquals("this.device", local.dao().profile()!!.selectedApps)
                    try { IslandBackupStore(local).restore(backup) { false }; fail("Changed account must cancel restore") } catch (_: IllegalStateException) { }
                    assertEquals(122, local.dao().totalXp())
                } finally { local.close() }
                db.document("users/${user.uid}/backups/latest").update(mapOf("xp" to 999, "updatedAt" to FieldValue.serverTimestamp())).await()
                assertThrows(IllegalArgumentException::class.java) { cloud.decode(runBlocking { cloud.latest(user.uid) }) }
                cloud.save(user.uid, backup.copy(profile = backup.profile.copy(islandName = "Android return"),
                    rewards = backup.rewards + Reward("usage:2026-10-03", "2026-10-03", "USAGE", 2)))
            }
        } finally { auth.signOut(); db.terminate().await(); app.delete() }
    }
}
