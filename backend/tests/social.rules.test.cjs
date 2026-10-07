const {test,before,after,beforeEach}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,getDoc,getDocs,collection,setDoc,writeBatch,runTransaction,serverTimestamp,Timestamp,arrayUnion,arrayRemove,query,where,limit,updateDoc,deleteDoc}=require('firebase/firestore');
let env;
before(async()=>{env=await initializeTestEnvironment({projectId:'demo-scrollxp',firestore:{host:'127.0.0.1',port:8180,rules:fs.readFileSync('firestore.rules','utf8')}})});
after(async()=>env?.cleanup());beforeEach(async()=>env.clearFirestore());
const user=(uid,v=true)=>env.authenticatedContext(uid,{email_verified:v,auth_time:Math.floor(Date.now()/1000)}).firestore();
const ref=(db,p)=>doc(db,p),key=(a,b)=>[a,b].sort().join('~');
async function claim(db,uid,name){const b=writeBatch(db);b.set(ref(db,`socialProfiles/${uid}`),{username:name,createdAt:serverTimestamp()});b.set(ref(db,`usernames/${name}`),{uid});return b.commit()}
async function request(db,a,b){const id=key(a,b),batch=writeBatch(db);batch.set(ref(db,`socialPairs/${id}`),{members:[a,b].sort(),sender:a,status:'pending',blocker:'',createdAt:serverTimestamp()});for(const uid of [a,b])batch.set(ref(db,`socialLinks/${uid}`),{pairs:arrayUnion(id),lastPair:id},{merge:true});return batch.commit()}
async function remove(db,a,b){const id=key(a,b),batch=writeBatch(db);batch.delete(ref(db,`socialPairs/${id}`));for(const uid of[a,b])batch.set(ref(db,`socialLinks/${uid}`),{pairs:arrayRemove(id),lastPair:id},{merge:true});return batch.commit()}
async function setup(){await claim(user('alice'),'alice','alice29');await claim(user('bob'),'bob','bobby29');await request(user('alice'),'alice','bob')}
const week=()=>Math.floor((Date.now()-345600000)/604800000);
const score=(name)=>({username:name,week:week(),days:0,startedAt:serverTimestamp(),updatedAt:serverTimestamp()});
test('atomic unique handle ownership and case/shape protection',async()=>{
 const result=await Promise.allSettled([claim(user('alice'),'alice','same_name'),claim(user('bob'),'bob','same_name')]);assert.equal(result.filter(x=>x.status==='fulfilled').length,1);
 await assertFails(claim(user('c'),'c','Admin'));await assertFails(claim(user('c'),'c','admin'));await assertFails(claim(user('c',false),'c','charlie'));
 await assertFails(setDoc(ref(user('d'),'usernames/unlinked'),{uid:'d'}));
 await assertFails(getDocs(collection(user('d'),'usernames')));
 await assertSucceeds(getDoc(ref(user('d'),'usernames/same_name')));
});
test('one handle per uid and private profiles; no unrelated lookup by uid',async()=>{
 const a=user('alice'),b=user('bob');await claim(a,'alice','alice29');await assertFails(claim(a,'alice','alice_other'));
 await assertFails(getDoc(ref(b,'socialProfiles/alice')));await assertFails(setDoc(ref(b,'socialProfiles/alice'),{username:'bobby29',createdAt:serverTimestamp()}));
});
test('requests require both indexed named accounts, recipient acceptance and immutable identities',async()=>{
 await setup();const a=user('alice'),b=user('bob'),c=user('c');
 await assertFails(updateDoc(ref(a,'socialPairs/alice~bob'),{status:'accepted'}));
 await assertSucceeds(updateDoc(ref(b,'socialPairs/alice~bob'),{status:'accepted'}));
 await assertFails(updateDoc(ref(b,'socialPairs/alice~bob'),{sender:'bob'}));
 await assertFails(getDoc(ref(c,'socialPairs/alice~bob')));await assertFails(getDoc(ref(b,'socialLinks/alice')));
 await assertSucceeds(getDoc(ref(b,'socialProfiles/alice')));
 await assertFails(setDoc(ref(a,'socialLinks/alice'),{pairs:[],lastPair:'alice~bob'}));
 await assertFails(request(a,'alice','bob'));await assertFails(request(a,'alice','alice'));
});
test('blocking hides private scores and cannot be undone by blocked member even with fresh auth',async()=>{
 await setup();const a=user('alice'),b=user('bob');await updateDoc(ref(b,'socialPairs/alice~bob'),{status:'accepted'});
 await setDoc(ref(a,'friendScores/alice'),score('alice29'));await assertSucceeds(getDoc(ref(b,'friendScores/alice')));
 await updateDoc(ref(a,'socialPairs/alice~bob'),{status:'blocked',blocker:'alice'});
 await assertFails(getDoc(ref(b,'friendScores/alice')));await assertFails(remove(b,'alice','bob'));
 await assertFails(updateDoc(ref(b,'socialPairs/alice~bob'),{status:'accepted',blocker:''}));
 await assertSucceeds(remove(a,'alice','bob'));await assertFails(getDoc(ref(b,'socialProfiles/alice')));
});
test('bounded indexes enforce the combined twenty relationship limit without scanning them',async()=>{
 const a=user('alice');await claim(a,'alice','alice29');
 for(let i=0;i<20;i++){const id='b'+i;await claim(user(id),id,'person_'+i);await assertSucceeds(request(a,'alice',id))}
 await claim(user('extra'),'extra','extra_person');await assertFails(request(a,'alice','extra'));
 assert.equal((await getDoc(ref(a,'socialLinks/alice'))).data().pairs.length,20);
 await remove(a,'alice','b0');await assertSucceeds(request(a,'alice','extra'));
});
test('weekly scores are owner-only, zero on consent, capped, monotonic and immutable start',async()=>{
 await setup();const a=user('alice'),b=user('bob');await assertFails(getDoc(ref(b,'friendScores/alice')));
 await assertFails(setDoc(ref(a,'friendScores/alice'),{...score('alice29'),days:1}));
 await assertSucceeds(setDoc(ref(a,'friendScores/alice'),score('alice29')));
 await assertSucceeds(updateDoc(ref(a,'friendScores/alice'),{days:2,updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(ref(a,'friendScores/alice'),{days:1,updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(ref(a,'friendScores/alice'),{days:8,updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(ref(a,'friendScores/alice'),{week:week()+1,updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(ref(a,'friendScores/alice'),{startedAt:serverTimestamp(),updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(ref(b,'friendScores/alice'),{days:7,updatedAt:serverTimestamp()}));
});
test('local board is public opt-in only, current-week limited queries and owner-matched counts',async()=>{
 const a=user('alice'),b=user('bob');await claim(a,'alice','alice29');await setDoc(ref(a,'friendScores/alice'),score('alice29'));
 const saved=(await getDoc(ref(a,'friendScores/alice'))).data();await setDoc(ref(a,'localScores/alice'),{...saved,area:'Dehradun',updatedAt:serverTimestamp()});
 await assertSucceeds(getDocs(query(collection(b,'localScores'),where('area','==','Dehradun'),where('week','==',week()),limit(50))));
 await assertFails(getDocs(collection(b,'localScores')));await assertFails(getDocs(query(collection(b,'localScores'),limit(50))));
 await assertFails(getDocs(query(collection(b,'localScores'),where('week','==',week()),limit(51))));
 await assertFails(updateDoc(ref(b,'localScores/alice'),{area:'Delhi'}));
 await assertFails(updateDoc(ref(a,'localScores/alice'),{days:4,updatedAt:serverTimestamp()}));
 await assertSucceeds(deleteDoc(ref(a,'localScores/alice')));assert.equal((await getDoc(ref(b,'localScores/alice'))).exists(),false);
});
test('deletion intent enables blocked cleanup, prevents new pairs and requires complete cleanup before guard',async()=>{
 await setup();const a=user('alice'),b=user('bob');await updateDoc(ref(a,'socialPairs/alice~bob'),{status:'blocked',blocker:'alice'});
 await assertFails(setDoc(ref(b,'accountDeletions/bob'),{deletedAt:serverTimestamp()}));
 await assertSucceeds(setDoc(ref(b,'deletionIntents/bob'),{createdAt:serverTimestamp()}));
 await assertSucceeds(remove(b,'alice','bob'));
 await assertFails(request(a,'alice','bob'));
 const batch=writeBatch(b);batch.delete(ref(b,'socialLinks/bob'));batch.delete(ref(b,'socialProfiles/bob'));batch.delete(ref(b,'usernames/bobby29'));batch.set(ref(b,'accountDeletions/bob'),{deletedAt:serverTimestamp()});batch.delete(ref(b,'deletionIntents/bob'));
 await assertSucceeds(batch.commit());await assertFails(claim(b,'bob','new_bobby'));await assertFails(request(a,'alice','bob'));
});
test('reports are private, immutable and only for existing relationships',async()=>{
 await setup();const a=user('alice'),b=user('bob');const data={reporter:'alice',target:'bob',reason:'inappropriate_profile',createdAt:serverTimestamp()};
 await assertSucceeds(setDoc(ref(a,'socialReports/alice~bob'),data));await assertFails(getDoc(ref(b,'socialReports/alice~bob')));
 await assertFails(setDoc(ref(b,'socialReports/bob~charlie'),{...data,reporter:'bob',target:'charlie'}));
 await assertSucceeds(getDocs(query(collection(a,'socialReports'),where('reporter','==','alice'),limit(20))));
});
