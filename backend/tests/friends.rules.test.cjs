const {test,before,after,beforeEach} = require('node:test');
const fs = require('node:fs');
const {initializeTestEnvironment,assertSucceeds,assertFails} = require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,getDocs,collection,writeBatch,runTransaction,serverTimestamp,Timestamp,updateDoc,deleteDoc} = require('firebase/firestore');
let env;
const CODE='A123456789ABCDEF0123';
before(async () => { env = await initializeTestEnvironment({projectId:'demo-scrollxp',firestore:{host:'127.0.0.1',port:8180,rules:fs.readFileSync('firestore.rules','utf8')}}); });
after(async () => env?.cleanup());
beforeEach(async () => env.clearFirestore());
const user = (uid, verified=true) => env.authenticatedContext(uid,{email_verified:verified,auth_time:Math.floor(Date.now()/1000)}).firestore();
const circle=(db,code=CODE)=>doc(db,`friendCircles/${code}`);
const link=(db,uid)=>doc(db,`friendLinks/${uid}`);
const member=(db,uid,code=CODE)=>doc(db,`friendCircles/${code}/members/${uid}`);
const person=(name)=>({name,goalDays:0,joinedAt:serverTimestamp(),updatedAt:serverTimestamp()});
async function create(db,uid,code=CODE) {
  return runTransaction(db,async tx=>{
    const refs=await tx.get(link(db,uid)); const ids=refs.data()?.circleIds||[];
    await tx.get(circle(db,code));
    tx.set(circle(db,code),{title:'Balance friends',createdAt:serverTimestamp(),endsAt:Timestamp.fromMillis(Date.now()+7*86400000),memberIds:[uid]});
    tx.set(member(db,uid,code),person(uid)); tx.set(link(db,uid),{circleIds:[...ids,code]});
  });
}
async function join(db,uid,code=CODE) {
  return runTransaction(db,async tx=>{
    const index=await tx.get(link(db,uid)); const group=await tx.get(circle(db,code));
    tx.update(circle(db,code),{memberIds:[...group.data().memberIds,uid]});
    tx.set(member(db,uid,code),person(uid)); tx.set(link(db,uid),{circleIds:[...(index.data()?.circleIds||[]),code]});
  });
}
async function leave(db,uid,code=CODE) {
  return runTransaction(db,async tx=>{
    const index=await tx.get(link(db,uid)); const group=await tx.get(circle(db,code));
    const remaining=group.data().memberIds.filter(x=>x!==uid);
    if(remaining.length) tx.update(circle(db,code),{memberIds:remaining}); else tx.delete(circle(db,code));
    tx.delete(member(db,uid,code)); tx.set(link(db,uid),{circleIds:index.data().circleIds.filter(x=>x!==code)});
  });
}
test('verified users atomically create, join, read roster and publish only their score',async()=>{
  const a=user('alice'),b=user('bob'); await assertSucceeds(create(a,'alice')); await assertSucceeds(join(b,'bob'));
  assert.equal((await getDocs(collection(a,`friendCircles/${CODE}/members`))).size,2);
  await assertSucceeds(updateDoc(member(b,'bob'),{goalDays:2,updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(member(a,'bob'),{goalDays:7,updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(member(b,'bob'),{goalDays:1,updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(member(b,'bob'),{goalDays:8,updatedAt:serverTimestamp()}));
  await assertFails(updateDoc(member(b,'bob'),{name:'forged'}));
});
test('invite holders can get metadata but cannot read roster until joining, enumerate, or access backups',async()=>{
  const a=user('alice'),b=user('bob'); await create(a,'alice');
  await assertSucceeds(getDoc(circle(b))); await assertFails(getDoc(member(b,'alice')));
  await assertFails(getDocs(collection(b,'friendCircles'))); await assertFails(getDoc(link(b,'alice')));
  await assertFails(getDoc(doc(b,'users/alice/backups/latest')));
  await assertFails(getDoc(circle(env.unauthenticatedContext().firestore())));
  await assertFails(getDoc(circle(user('unknown',false))));
  await assertFails(join(user('bob',false),'bob'));
});
test('orphan groups, standalone memberships, index loss, extra fields and membership injection are denied',async()=>{
  const a=user('alice'),b=user('bob'); await create(a,'alice');
  await assertFails(setDoc(member(b,'bob'),person('Bob')));
  await assertFails(setDoc(link(b,'bob'),{circleIds:[CODE]}));
  await assertFails(updateDoc(circle(a),{memberIds:['alice','bob']}));
  await assertFails(updateDoc(circle(a),{title:'Replaced'}));
  await assertFails(updateDoc(link(a,'alice'),{circleIds:[]})); await assertFails(deleteDoc(link(a,'alice')));
  await assertFails(deleteDoc(member(a,'alice'))); await assertFails(deleteDoc(circle(a)));
  await assertFails(updateDoc(member(a,'alice'),{appNames:['private'],updatedAt:serverTimestamp()}));
});
test('capacity is six members and three circles, with reads/writes within transaction limits',async()=>{
  await create(user('alice'),'alice');
  for(const uid of ['b','c','d','e','f']) await assertSucceeds(join(user(uid),uid));
  await assertFails(join(user('g'),'g'));
  const second='B123456789ABCDEF0123', third='C123456789ABCDEF0123', fourth='D123456789ABCDEF0123';
  await assertSucceeds(create(user('alice'),'alice',second)); await assertSucceeds(create(user('alice'),'alice',third));
  await assertFails(create(user('alice'),'alice',fourth));
  await assertSucceeds(leave(user('alice'),'alice',second));
  await assertSucceeds(create(user('alice'),'alice',fourth));
});
test('leaving removes shared data and roster access; last member deletes the group',async()=>{
  const a=user('alice'),b=user('bob'); await create(a,'alice'); await join(b,'bob');
  await assertSucceeds(leave(b,'bob')); await assertFails(getDoc(member(b,'alice')));
  assert.equal((await getDoc(member(a,'bob'))).exists(),false);
  await assertSucceeds(leave(a,'alice')); assert.equal((await getDoc(circle(a))).exists(),false);
});

test('a member who loses email verification can remove their memberships but cannot add or publish',async()=>{
  await create(user('alice'),'alice'); await join(user('bob'),'bob');
  const unverified=user('bob',false);
  await assertFails(updateDoc(member(unverified,'bob'),{goalDays:1,updatedAt:serverTimestamp()}));
  await assertFails(create(unverified,'bob','B123456789ABCDEF0123'));
  await assertSucceeds(leave(unverified,'bob'));
  assert.equal((await getDoc(link(unverified,'bob'))).data().circleIds.length,0);
  await assertFails(getDocs(collection(unverified,`friendCircles/${CODE}/members`)));
});
test('deletion requires all memberships removed; stale tokens cannot create/join/publish afterwards',async()=>{
  const a=user('alice'),b=user('bob'); await create(a,'alice'); await join(b,'bob');
  const guard=doc(a,'accountDeletions/alice'); const direct=writeBatch(a); direct.set(guard,{deletedAt:serverTimestamp()}); direct.delete(link(a,'alice'));
  await assertFails(direct.commit()); await assertSucceeds(leave(a,'alice'));
  const remove=writeBatch(a); remove.delete(link(a,'alice')); remove.set(guard,{deletedAt:serverTimestamp()});
  await assertSucceeds(remove.commit()); await assertFails(join(a,'alice'));
  await assertFails(create(a,'alice','E123456789ABCDEF0123'));
  await assertFails(setDoc(member(a,'alice'),person('Alice')));
  await assertSucceeds(getDoc(guard));
});
test('expired rounds block new joins; score publication closes after the grace window',async()=>{
  const a=user('alice'); await create(a,'alice');
  await env.withSecurityRulesDisabled(async c=>updateDoc(circle(c.firestore()),{endsAt:Timestamp.fromMillis(Date.now()-86400000)}));
  await assertFails(join(user('bob'),'bob'));
  await assertSucceeds(updateDoc(member(a,'alice'),{goalDays:1,updatedAt:serverTimestamp()}));
  await env.withSecurityRulesDisabled(async c=>updateDoc(circle(c.firestore()),{endsAt:Timestamp.fromMillis(Date.now()-3*86400000)}));
  await assertFails(updateDoc(member(a,'alice'),{goalDays:2,updatedAt:serverTimestamp()}));
  await assertSucceeds(leave(a,'alice'));
});
test('two competing last-seat joins commit at most one member without partial records',async()=>{
  await create(user('alice'),'alice'); for(const uid of ['b','c','d','e']) await join(user(uid),uid);
  const result=await Promise.allSettled([join(user('f'),'f'),join(user('g'),'g')]);
  assert.equal(result.filter(x=>x.status==='fulfilled').length,1);
  const group=(await getDoc(circle(user('alice')))).data(); assert.equal(group.memberIds.length,6);
  const loser=result[0].status==='rejected'?'f':'g'; assert.equal((await getDoc(link(user(loser),loser))).exists(),false);
});
const assert=require('node:assert/strict');
