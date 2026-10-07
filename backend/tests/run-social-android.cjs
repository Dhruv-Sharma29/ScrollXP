const {execFileSync}=require('node:child_process');
const fs=require('node:fs');
const adb='/Users/dhruvsharma/Library/Android/sdk/platform-tools/adb';
async function post(path,body,admin=false){const response=await fetch(`http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/${path}?key=demo-key`,{method:'POST',headers:{'Content-Type':'application/json',...(admin?{Authorization:'Bearer owner'}:{})},body:JSON.stringify(body)});const data=await response.json();if(!response.ok)throw Error(JSON.stringify(data));return data}
(async()=>{
 for(const email of ['social-a@example.test','social-b@example.test']){
  const created=await post('accounts:signUp',{email,password:'Emulator-only-123!',returnSecureToken:true});
  await post('accounts:update',{localId:created.localId,emailVerified:true},true);
 }
 for(const port of [8180,9099])execFileSync(adb,['reverse',`tcp:${port}`,`tcp:${port}`]);
 const output=execFileSync(adb,['shell','am','instrument','-w','-e','socialEmulators','true','-e','class','com.scrollxp.app.SocialEmulatorTest','com.scrollxp.app.test/androidx.test.runner.AndroidJUnitRunner'],{encoding:'utf8',timeout:120000});
 fs.writeFileSync('/private/tmp/scrollxp-social-sdk-test.log',output);process.stdout.write(output);
 if(!output.includes('OK (1 test)'))process.exitCode=1;
})().catch(error=>{console.error(error);process.exitCode=1});
