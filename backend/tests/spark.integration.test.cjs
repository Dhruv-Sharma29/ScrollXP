const {test,before,after,beforeEach} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {initializeTestEnvironment} = require('@firebase/rules-unit-testing');
const {initializeApp,deleteApp} = require('firebase/app');
const {getAuth,connectAuthEmulator,createUserWithEmailAndPassword,signInWithEmailAndPassword,signOut,
  sendEmailVerification,sendPasswordResetEmail,reload,getIdToken,EmailAuthProvider,reauthenticateWithCredential,deleteUser} = require('firebase/auth');
const {getFirestore,connectFirestoreEmulator,doc,setDoc,getDoc,serverTimestamp,runTransaction} = require('firebase/firestore');
let env; const apps = [];
before(async () => {
  assert.ok(process.env.FIREBASE_AUTH_EMULATOR_HOST && process.env.FIRESTORE_EMULATOR_HOST,'Local emulators are required');
  env = await initializeTestEnvironment({projectId:'demo-scrollxp',firestore:{host:'127.0.0.1',port:8180,rules:fs.readFileSync('firestore.rules','utf8')}});
});
beforeEach(async () => {
  await env.clearFirestore();
  const response = await fetch('http://127.0.0.1:9099/emulator/v1/projects/demo-scrollxp/accounts',{method:'DELETE'});
  assert.ok(response.ok);
});
after(async () => { await Promise.all(apps.map(deleteApp)); await env?.cleanup(); });
function client() {
  const app = initializeApp({apiKey:'fake-key',projectId:'demo-scrollxp',appId:'demo-scrollxp-test'},`test-${apps.length}`); apps.push(app);
  const auth = getAuth(app); connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});
  const db = getFirestore(app); connectFirestoreEmulator(db,'127.0.0.1',8180);
  return {auth,db};
}
async function verify(user) {
  // Emulator-only admin endpoint: no real email is delivered.
  const response = await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-key',{
    method:'POST',headers:{'Content-Type':'application/json',Authorization:'Bearer owner'},body:JSON.stringify({localId:user.uid,emailVerified:true})});
  assert.ok(response.ok); await reload(user); await getIdToken(user,true);
}
const payload = () => ({version:1,payload:'{"version":1}',name:'Haven',xp:12,updatedAt:serverTimestamp()});
test('Spark signup, verification request, password reset and password sign-in use Auth only',async () => {
  const {auth} = client(); const user = (await createUserWithEmailAndPassword(auth,'signup@example.test','TestOnlyPassword123')).user;
  assert.equal(user.emailVerified,false); await sendEmailVerification(user);
  await sendPasswordResetEmail(auth,user.email); await signOut(auth);
  await assert.rejects(signInWithEmailAndPassword(auth,'signup@example.test','wrong-password'));
  const signed = (await signInWithEmailAndPassword(auth,'signup@example.test','TestOnlyPassword123')).user;
  assert.equal(signed.uid,user.uid);
});
test('fresh verified tokens permit direct backups; other accounts and unverified sessions are denied',async () => {
  const a = client(); const user = (await createUserWithEmailAndPassword(a.auth,'backup@example.test','TestOnlyPassword123')).user;
  const ref = doc(a.db,`users/${user.uid}/backups/latest`);
  await assert.rejects(setDoc(ref,payload())); await verify(user);
  await setDoc(ref,payload()); assert.equal((await getDoc(ref)).data().xp,12);
  const b = client(); await createUserWithEmailAndPassword(b.auth,'other@example.test','TestOnlyPassword123');
  await assert.rejects(getDoc(doc(b.db,`users/${user.uid}/backups/latest`)));
});
test('reauthentication failure keeps data; guard and backup deletion precede Auth deletion',async () => {
  const {auth,db} = client(); const user = (await createUserWithEmailAndPassword(auth,'delete@example.test','TestOnlyPassword123')).user;
  await verify(user); const backup = doc(db,`users/${user.uid}/backups/latest`); const guard = doc(db,`accountDeletions/${user.uid}`);
  await setDoc(backup,payload());
  await assert.rejects(reauthenticateWithCredential(user,EmailAuthProvider.credential(user.email,'wrong-password')));
  assert.equal((await getDoc(backup)).exists(),true);
  await reauthenticateWithCredential(user,EmailAuthProvider.credential(user.email,'TestOnlyPassword123')); await getIdToken(user,true);
  await runTransaction(db,async tx => { if (!(await tx.get(guard)).exists()) tx.set(guard,{deletedAt:serverTimestamp()}); tx.delete(backup); });
  assert.equal((await getDoc(guard)).exists(),true); await assert.rejects(setDoc(backup,payload()));
  await deleteUser(user); assert.equal(auth.currentUser,null);
  await assert.rejects(signInWithEmailAndPassword(auth,'delete@example.test','TestOnlyPassword123'));
  await env.withSecurityRulesDisabled(async ctx => {
    assert.equal((await getDoc(doc(ctx.firestore(),`users/${user.uid}/backups/latest`))).exists(),false);
    assert.deepEqual(Object.keys((await getDoc(doc(ctx.firestore(),`accountDeletions/${user.uid}`))).data()),['deletedAt']);
  });
});
