package com.scrollxp.app.online

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.*
import com.google.firebase.firestore.*
import com.scrollxp.app.BuildConfig
import com.scrollxp.app.data.*
import com.scrollxp.app.widget.IslandWidget
import com.scrollxp.app.domain.BalanceDay
import com.scrollxp.app.domain.FriendScorePolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

data class OnlineState(val configured: Boolean = false, val uid: String? = null, val email: String? = null,
    val verified: Boolean = false, val busy: Boolean = false, val message: String? = null,
    val backupTime: Long? = null, val backupXp: Int? = null, val backupName: String? = null,
    val deleting: Boolean = false, val circles: List<FriendCircle> = emptyList())

/** Optional Firebase Spark accounts. Signing in never uploads or changes the local game. */
class OnlineViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ScrollRepository(app)
    private val configured = BuildConfig.FIREBASE_CONFIGURED && BuildConfig.ONLINE_ENABLED && FirebaseApp.getApps(app).isNotEmpty()
    private val auth = if (configured) FirebaseAuth.getInstance() else null
    private val firestore = if (configured) FirebaseFirestore.getInstance().apply {
        // The shared SDK instance may already be started when an Activity is recreated.
        val settings = firestoreSettings
        if (settings.cacheSettings !is MemoryCacheSettings) {
            firestoreSettings = FirebaseFirestoreSettings.Builder(settings).setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        }
    } else null
    private val cloud = firestore?.let(::CloudAccountStore)
    private val friends = firestore?.let(::FriendCircleStore)
    private val mutable = MutableStateFlow(OnlineState(configured = configured))
    val state: StateFlow<OnlineState> = mutable.asStateFlow()
    private val listener = FirebaseAuth.AuthStateListener { changed ->
        val current = changed.currentUser
        val previous = mutable.value
        if (previous.uid == current?.uid) mutable.update { it.copy(email = current?.email,verified = current?.isEmailVerified == true) }
        else mutable.value = OnlineState(configured = configured,uid = current?.uid,email = current?.email,
            verified = current?.isEmailVerified == true,busy = previous.busy)
    }
    init { auth?.addAuthStateListener(listener) }
    override fun onCleared() { auth?.removeAuthStateListener(listener); super.onCleared() }

    private fun perform(block: suspend () -> String) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true,message = null) }
        viewModelScope.launch {
            try { val message = withTimeout(30_000) { block() }; mutable.update { it.copy(message = message) } }
            catch (_: TimeoutCancellationException) { mutable.update { it.copy(message = "Connection timed out. Please refresh your account before retrying; your local island is safe.") } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(message = friendly(error)) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    private fun user(): FirebaseUser = requireNotNull(auth?.currentUser) { "Sign in to continue." }.also {
        check(it.isEmailVerified) { "Verify your email, then refresh your account." }
        check(!mutable.value.deleting) { "Account deletion is pending. Choose Delete online account to finish it." }
    }
    private fun same(uid: String) { check(auth?.currentUser?.uid == uid) { "Account changed. Please try again." } }
    fun signIn(email: String, password: String, create: Boolean) = perform {
        val service = requireNotNull(auth) { "Accounts are unavailable in this build." }
        if (create) {
            require(password.length >= 8) { "Choose a password with at least 8 characters." }
            val created = requireNotNull(service.createUserWithEmailAndPassword(email.trim(),password).await().user)
            try { created.sendEmailVerification().await(); "Account created. Check your email to verify it before using cloud backups." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { "Account created. Verification email could not be sent; use Resend verification email in Your account." }
        } else {
            service.signInWithEmailAndPassword(email.trim(),password).await()
            "Signed in. Your local island has not been replaced."
        }
    }
    fun resetPassword(email: String) = perform {
        require(email.trim().contains('@')) { "Enter your account email first." }
        requireNotNull(auth).sendPasswordResetEmail(email.trim()).await()
        "If that address has an account, check its inbox for reset instructions."
    }
    fun resendVerification() = perform { requireNotNull(auth?.currentUser).sendEmailVerification().await(); "Verification email sent." }
    fun refresh() = perform {
        val existing = auth?.currentUser ?: return@perform "Continue locally, or sign in to use cloud backups."
        val uid = existing.uid
        existing.reload().await(); same(uid)
        // Reload updates the user object; a fresh token lets rules see email_verified.
        existing.getIdToken(true).await(); same(uid)
        val deleting = requireNotNull(cloud).deletionStarted(uid); same(uid)
        mutable.update { it.copy(verified = existing.isEmailVerified,deleting = deleting) }
        if (deleting) {
            mutable.update { it.copy(backupTime = null,backupXp = null,backupName = null) }
            return@perform "Your backup is removed and account deletion is pending. Choose Delete online account to finish."
        }
        if (!existing.isEmailVerified) return@perform "Verify your email before using cloud backups."
        val backup = cloud.latest(uid); same(uid)
        cloud.decode(backup)
        mutable.update { it.copy(backupTime = backup.getTimestamp("updatedAt")?.toDate()?.time,
            backupXp = backup.getLong("xp")?.toInt(),backupName = backup.getString("name")) }
        "Account and cloud backup refreshed."
    }
    fun backup() = perform {
        val uid = user().uid
        val snapshot = repository.backup(); same(uid)
        requireNotNull(cloud).save(uid,snapshot); same(uid)
        mutable.update { it.copy(backupTime = System.currentTimeMillis(),backupXp = snapshot.xp,backupName = snapshot.profile.islandName) }
        "Island backup saved to this account."
    }
    fun restore() = perform {
        val uid = user().uid
        val doc = requireNotNull(cloud).latest(uid); same(uid)
        val backup = requireNotNull(cloud.decode(doc)) { "No cloud backup exists for this account." }
        repository.restore(backup) { auth?.currentUser?.uid == uid }
        IslandWidget.refresh(getApplication())
        "Island restored. Local usage history was cleared; device app selection was kept."
    }
    fun deleteBackup() = perform {
        val uid = user().uid; requireNotNull(cloud).removeBackup(uid); same(uid)
        mutable.update { it.copy(backupTime = null,backupXp = null,backupName = null) }
        "Cloud backup deleted. Your local island is unchanged."
    }
    fun signOut() { if (!mutable.value.busy) auth?.signOut() }
    private suspend fun refreshFriends(uid: String) {
        val circles = requireNotNull(friends).list(uid); same(uid)
        mutable.update { it.copy(circles = circles) }
    }
    fun refreshCircles() = perform { val uid = user().uid; refreshFriends(uid); "Friend circles refreshed." }
    fun createCircle(title: String, name: String) = perform {
        val uid = user().uid; requireNotNull(friends).create(uid, title, name); same(uid)
        refreshFriends(uid); "Circle created. Share the invite code only with friends you trust."
    }
    fun joinCircle(code: String, name: String) = perform {
        val uid = user().uid; requireNotNull(friends).join(uid, code, name); same(uid)
        refreshFriends(uid); "Joined the circle. Your usage history remains private."
    }
    fun leaveCircle(code: String) = perform {
        val uid = user().uid; requireNotNull(friends).leave(uid, code); same(uid)
        refreshFriends(uid); "Left the circle. Your shared name and score were removed."
    }
    fun publishCircleScore(code: String) = perform {
        val uid = user().uid
        val circle = mutable.value.circles.firstOrNull { it.code == code } ?: error("Refresh the circle before sharing.")
        val joinedAt = circle.members.firstOrNull { it.uid == uid }?.joinedAt ?: error("Join this circle first.")
        val days = repository.dao.extendedHistory().map { BalanceDay(it.start, it.end, it.goalStatus == "SUCCESS" && !it.partial && it.eligibleSince <= it.start) }
        val score = FriendScorePolicy.score(days, circle.createdAt, joinedAt, circle.endsAt, System.currentTimeMillis()); same(uid)
        requireNotNull(friends).publish(uid, code, score); same(uid); refreshFriends(uid)
        "Your balance score was shared. App names and usage minutes were not uploaded."
    }
    fun deleteAccount(password: String) = perform {
        val current = requireNotNull(auth?.currentUser) { "Sign in to continue." }
        val uid = current.uid
        current.reauthenticate(EmailAuthProvider.getCredential(requireNotNull(current.email),password)).await()
        current.getIdToken(true).await(); same(uid)
        // Remove every shared nickname/score before placing the permanent deletion guard.
        requireNotNull(friends).leaveAll(uid); same(uid)
        mutable.update { it.copy(circles = emptyList()) }
        // Atomic guard + backup removal blocks still-valid tokens before Auth deletion.
        requireNotNull(cloud).beginDeletion(uid); same(uid)
        mutable.update { it.copy(deleting = true,backupTime = null,backupXp = null,backupName = null) }
        try { current.delete().await() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            return@perform "Your cloud backup is deleted. Account deletion is pending; reconnect and choose Delete online account again to finish."
        }
        auth?.signOut()
        "Account, backup and friend memberships deleted. Your local island remains. A minimal security guard is retained. Google Play subscriptions must be cancelled separately."
    }
    private fun friendly(error: Exception): String = when(error) {
        is FirebaseAuthInvalidCredentialsException -> "Couldn't sign in. Check your email and password."
        is FirebaseAuthUserCollisionException -> "That email already has an account. Sign in or reset your password."
        is FirebaseAuthWeakPasswordException -> "Choose a stronger password."
        is FirebaseAuthRecentLoginRequiredException -> "Sign in again before deleting your account."
        is FirebaseFirestoreException -> if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
            "Cloud access was denied. Verify your email and refresh your account. The project also needs the ScrollXP Firestore rules."
            else "Cloud storage is unavailable. Check your connection; your local island is safe."
        is IllegalArgumentException, is IllegalStateException -> error.message?.take(180) ?: "Couldn't complete that request."
        else -> "Couldn't connect right now. Your local island is safe; please try again."
    }
}
