const {test,before,after,beforeEach} = require('node:test');
const fs = require('node:fs');
const {initializeTestEnvironment,assertSucceeds,assertFails} = require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,deleteDoc,serverTimestamp,collection,getDocs,writeBatch,runTransaction} = require('firebase/firestore');
let env;
before(async () => { env = await initializeTestEnvironment({projectId:'demo-scrollxp',firestore:{host:'127.0.0.1',port:8180,rules:fs.readFileSync('firestore.rules','utf8')}}); });
after(async () => env?.cleanup());
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    await setDoc(doc(ctx.firestore(),'challenges/private'),{members:['alice'],names:{alice:'A'},scores:{alice:[]}});
    await setDoc(doc(ctx.firestore(),'entitlements/alice'),{active:true});
    await setDoc(doc(ctx.firestore(),'invites/secret'),{challengeId:'private'});
  });
});
const alice = () => env.authenticatedContext('alice',{email_verified:true,auth_time:Math.floor(Date.now()/1000)}).firestore();
const bob = () => env.authenticatedContext('bob',{email_verified:true}).firestore();
const backup = () => ({version:1,payload:'{}',name:'Haven',xp:12,updatedAt:serverTimestamp()});
test('verified owner can save, read, and delete their backup',async () => {
  const ref = doc(alice(),'users/alice/backups/latest');
  await assertSucceeds(setDoc(ref,backup())); await assertSucceeds(getDoc(ref)); await assertSucceeds(deleteDoc(ref));
});
test('other accounts and unverified users cannot access private backups',async () => {
  await setDoc(doc(alice(),'users/alice/backups/latest'),backup());
  await assertFails(getDoc(doc(bob(),'users/alice/backups/latest')));
  await assertFails(setDoc(doc(bob(),'users/alice/backups/latest'),backup()));
  const unverified = env.authenticatedContext('alice',{email_verified:false}).firestore();
  await assertFails(getDoc(doc(unverified,'users/alice/backups/latest')));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'users/alice/backups/latest')));
});
test('malformed backup and extra fields are denied',async () => {
  const ref = doc(alice(),'users/alice/backups/latest');
  await assertFails(setDoc(ref,{...backup(),xp:-1}));
  await assertFails(setDoc(ref,{...backup(),xp:100000001}));
  await assertFails(setDoc(ref,{...backup(),appNames:['private']}));
  await assertFails(setDoc(ref,{...backup(),version:2}));
});
test('legacy server challenges and their collection scans remain inaccessible',async () => {
  await assertFails(getDoc(doc(alice(),'challenges/private')));
  await assertFails(getDoc(doc(bob(),'challenges/private')));
  await assertFails(getDocs(collection(alice(),'challenges')));
  await assertFails(setDoc(doc(alice(),'challenges/private'),{members:['alice','bob'],scores:{alice:['2026-10-01']}}));
});
test('entitlements cannot be read or forged even by their former owner',async () => {
  await assertFails(getDoc(doc(alice(),'entitlements/alice')));
  await assertFails(getDoc(doc(bob(),'entitlements/alice')));
  await assertFails(setDoc(doc(alice(),'entitlements/alice'),{active:true}));
  await assertFails(getDoc(doc(alice(),'invites/secret')));
});
test('atomic deletion removes backup and permanently blocks stale sessions; retry is safe',async () => {
  const db = alice(); const ref = doc(db,'users/alice/backups/latest'); const guard = doc(db,'accountDeletions/alice');
  await setDoc(ref,backup());
  const remove = () => runTransaction(db,async tx => {
    if (!(await tx.get(guard)).exists()) tx.set(guard,{deletedAt:serverTimestamp()});
    tx.delete(ref);
  });
  await assertSucceeds(remove()); await assertSucceeds(remove());
  await assertSucceeds(getDoc(guard));
  await assertFails(setDoc(doc(alice(),'users/alice/backups/latest'),backup()));
  await assertFails(getDoc(ref));
  await assertFails(deleteDoc(guard));
  await assertFails(setDoc(guard,{deletedAt:serverTimestamp()}));
  await assertFails(getDoc(doc(bob(),'accountDeletions/alice')));
});
test('deletion requires recent authentication and backup removal in the same atomic operation',async () => {
  const db = alice(); const guard = doc(db,'accountDeletions/alice');
  await setDoc(doc(db,'users/alice/backups/latest'),backup());
  await assertFails(setDoc(guard,{deletedAt:serverTimestamp()}));
  const old = env.authenticatedContext('alice',{email_verified:true,auth_time:Math.floor(Date.now()/1000)-3600}).firestore();
  const batch = writeBatch(old); batch.delete(doc(old,'users/alice/backups/latest')); batch.set(doc(old,'accountDeletions/alice'),{deletedAt:serverTimestamp()});
  await assertFails(batch.commit());
  await assertSucceeds(getDoc(doc(db,'users/alice/backups/latest')));
});
test('unverified owners can delete their own backup and account guard blocks mixed save batches',async () => {
  await setDoc(doc(alice(),'users/alice/backups/latest'),backup());
  const db = env.authenticatedContext('alice',{email_verified:false,auth_time:Math.floor(Date.now()/1000)}).firestore();
  const batch = writeBatch(db); batch.delete(doc(db,'users/alice/backups/latest')); batch.set(doc(db,'accountDeletions/alice'),{deletedAt:serverTimestamp()});
  await assertSucceeds(batch.commit());
  const another = bob(); const mixed = writeBatch(another);
  mixed.set(doc(another,'accountDeletions/bob'),{deletedAt:serverTimestamp()});
  mixed.set(doc(another,'users/bob/backups/latest'),backup());
  await assertFails(mixed.commit());
});
