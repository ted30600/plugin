let manager='1', files=[];
const $=id=>document.getElementById(id);
const VIDEO_CHUNK_SIZE=25*1024*1024;
const CHUNK_SIZE=VIDEO_CHUNK_SIZE;

async function api(url,options={}){const r=await fetch(url,options);const d=await r.json().catch(()=>({}));if(!r.ok)throw new Error(d.error||'Erreur serveur');return d;}
async function loadFiles(){
  try{
    files=(await api('/api/files?manager='+manager)).files;
    render();
    await loadStorage();
  }catch(e){showMessage(e.message,true);}
}
async function loadStorage(){
  const storageInfo=$('storage');
  try{
    const s=(await api('/api/status')).storage;
    storageInfo.textContent='Stockage : '+human(s.usedBytes)+' utilisés · '+human(s.freeBytes)+' restants · '+human(s.totalBytes)+' au total';
  }catch(e){storageInfo.textContent='Stockage : impossible à calculer';}
}
function human(n){if(n<1024)return n+' o';if(n<1048576)return(n/1024).toFixed(1)+' Ko';if(n<1073741824)return(n/1048576).toFixed(1)+' Mo';return(n/1073741824).toFixed(2)+' Go';}
function esc(s){return String(s).replace(/[&<>\"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',"'":'&#39;'}[c]));}
function render(){const q=$('search').value.toLowerCase();const list=files.filter(f=>f.name.toLowerCase().includes(q));$('count').textContent=files.length+' fichier'+(files.length>1?'s':'');$('files').innerHTML=list.map(f=>{const preview=f.mime.startsWith('image/')?'<img src="/api/files/'+f.id+'/view" loading="lazy" alt="">':f.mime.startsWith('video/')?'<video src="/api/files/'+f.id+'/view" controls preload="metadata"></video>':'<div class="icon">FILE</div>';return '<article><div class="preview">'+preview+'</div><div class="meta"><b title="'+esc(f.name)+'">'+esc(f.name)+'</b><small>'+human(f.size)+' · '+new Date(f.createdAt).toLocaleString('fr-FR')+'</small></div><div class="actions"><a href="/api/files/'+f.id+'/download">Télécharger</a><button type="button" onclick="removeFile(\\''+f.id+'\\')">Supprimer</button></div></article>';}).join('')||'<div class="empty">Aucun fichier dans cet espace.</div>'; }
function showMessage(msg,error=false){const el=$('message');el.textContent=msg;el.hidden=false;el.className='message '+(error?'error':'');setTimeout(()=>el.hidden=true,5000);}
function makeUploadId(){
  if(globalThis.crypto?.randomUUID)return globalThis.crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g,c=>{const r=Math.random()*16|0,v=c==='x'?r:(r&3)|8;return v.toString(16);});
}
async function checkVideoDuration(file){
  return new Promise(resolve=>{
    const url=URL.createObjectURL(file),v=document.createElement('video');
    v.preload='metadata';
    v.onloadedmetadata=()=>{const d=v.duration;URL.revokeObjectURL(url);v.remove();resolve(d>=60&&d<=18000?null:'La vidéo « '+file.name+' » doit durer entre 1 minute et 5 heures.');};
    v.onerror=()=>{URL.revokeObjectURL(url);v.remove();resolve('Impossible de vérifier la durée de « '+file.name+' ».');};
    v.src=url;
  });
}
async function uploadInChunks(file){
  if(!file || typeof file.size!=='number' || file.size<=0){
    throw new Error('Le fichier sélectionné est vide ou Android ne fournit pas son contenu. Essaie avec l’application Fichiers/Chrome.');
  }
  const uploadId=makeUploadId();
  const totalChunks=Math.ceil(file.size/CHUNK_SIZE);
  try{
    for(let i=0;i<totalChunks;i++){
      const start=i*CHUNK_SIZE;
      const end=Math.min(file.size,start+CHUNK_SIZE);
      const chunk=file.slice(start,end);
      if(chunk.size<=0)throw new Error('Le morceau '+(i+1)+' est vide.');
      showMessage('Envoi de « '+file.name+' » : '+Math.round(((i+1)/totalChunks)*100)+' %');
      await api('/api/upload/chunk?'+new URLSearchParams({
        manager,
        uploadId,
        chunkIndex:String(i),
        totalChunks:String(totalChunks),
        totalSize:String(file.size),
        filename:file.name,
        mime:file.type||'application/octet-stream'
      }),{
        method:'POST',
        headers:{'Content-Type':file.type||'application/octet-stream'},
        body:chunk
      });
    }
    const done=await api('/api/upload/complete',{
      method:'POST',
      headers:{'Content-Type':'application/json'},
      body:JSON.stringify({manager,uploadId})
    });
    if(done.size!==file.size)throw new Error('La taille reçue ne correspond pas à la taille envoyée.');
  }catch(e){
    try{await api('/api/upload/'+uploadId,{method:'DELETE'});}catch{}
    throw e;
  }
}
async function uploadFiles(input,type='file'){
  if(!input.files.length)return;
  const selected=[...input.files];
  if(type==='video'){
    for(const f of selected){
      if(f.size<=0){input.value='';showMessage('La vidéo sélectionnée est vide. Essaie de la sélectionner depuis l’application Fichiers.',true);return;}
      const error=await checkVideoDuration(f);
      if(error){input.value='';showMessage(error,true);return;}
    }
  }
  try{
    for(const f of selected) await uploadInChunks(f);
    input.value='';
    await loadFiles();
    showMessage(selected.length+' élément'+(selected.length>1?'s':'')+' ajouté'+(selected.length>1?'s':'')+' ✓');
  }catch(e){input.value='';showMessage(e.message,true);}
}
async function removeFile(id){if(!confirm('Supprimer ce fichier ?'))return;try{await api('/api/files/'+id,{method:'DELETE'});await loadFiles();showMessage('Fichier supprimé ✓');}catch(e){showMessage(e.message,true);}}
document.addEventListener('DOMContentLoaded',()=>{document.querySelectorAll('.manager').forEach(b=>b.addEventListener('click',async()=>{manager=b.dataset.m;document.querySelectorAll('.manager').forEach(x=>x.classList.toggle('active',x===b));$('title').textContent=['Ted','Lilan','Atome'][Number(manager)-1];$('search').value='';await loadFiles();}));$('photoInput').addEventListener('change',e=>uploadFiles(e,'photo'));$('videoInput').addEventListener('change',e=>uploadFiles(e,'video'));$('fileInput').addEventListener('change',e=>uploadFiles(e,'file'));$('search').addEventListener('input',render);loadFiles();});