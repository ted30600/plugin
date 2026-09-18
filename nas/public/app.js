let manager='1', files=[];
const $=id=>document.getElementById(id);
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
function esc(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));}
function render(){const q=$('search').value.toLowerCase();const list=files.filter(f=>f.name.toLowerCase().includes(q));$('count').textContent=files.length+' fichier'+(files.length>1?'s':'');$('files').innerHTML=list.map(f=>{const preview=f.mime.startsWith('image/')?'<img src="/api/files/'+f.id+'/view" loading="lazy" alt="">':f.mime.startsWith('video/')?'<video src="/api/files/'+f.id+'/view" controls preload="metadata"></video>':'<div class="icon">FILE</div>';return '<article><div class="preview">'+preview+'</div><div class="meta"><b title="'+esc(f.name)+'">'+esc(f.name)+'</b><small>'+human(f.size)+' · '+new Date(f.createdAt).toLocaleString('fr-FR')+'</small></div><div class="actions"><a href="/api/files/'+f.id+'/download">Télécharger</a><button type="button" onclick="removeFile(\''+f.id+'\')">Supprimer</button></div></article>';}).join('')||'<div class="empty">Aucun fichier dans cet espace.</div>'; }
function showMessage(msg,error=false){const el=$('message');el.textContent=msg;el.hidden=false;el.className='message '+(error?'error':'');setTimeout(()=>el.hidden=true,5000);}
async function checkVideoDuration(file){return new Promise(resolve=>{const url=URL.createObjectURL(file);const v=document.createElement('video');v.preload='metadata';v.onloadedmetadata=()=>{const d=v.duration;URL.revokeObjectURL(url);v.remove();resolve(d>=60&&d<=18000?null:'La vidéo « '+file.name+' » doit durer entre 1 minute et 5 heures.');};v.onerror=()=>{URL.revokeObjectURL(url);v.remove();resolve('Impossible de vérifier la durée de « '+file.name+' ».');};v.src=url;});}
async function uploadFiles(input,type='file'){if(!input.files.length)return;const selected=[...input.files];if(type==='video'){for(const f of selected){const error=await checkVideoDuration(f);if(error){input.value='';showMessage(error,true);return;}}}const fd=new FormData();selected.forEach(f=>fd.append('files',f));try{const d=await api('/api/files?manager='+manager,{method:'POST',body:fd});input.value='';showMessage(d.count+' élément'+(d.count>1?'s':'')+' ajouté'+(d.count>1?'s':'')+' ✓');await loadFiles();}catch(e){input.value='';showMessage(e.message,true);}}
async function removeFile(id){if(!confirm('Supprimer ce fichier ?'))return;try{await api('/api/files/'+id,{method:'DELETE'});await loadFiles();showMessage('Fichier supprimé ✓');}catch(e){showMessage(e.message,true);}}
document.addEventListener('DOMContentLoaded',()=>{document.querySelectorAll('.manager').forEach(b=>b.addEventListener('click',async()=>{manager=b.dataset.m;document.querySelectorAll('.manager').forEach(x=>x.classList.toggle('active',x===b));$('title').textContent=['Ted','Lilan','Atome'][Number(manager)-1];$('search').value='';await loadFiles();}));$('photoInput').addEventListener('change',e=>uploadFiles(e.target,'photo'));$('videoInput').addEventListener('change',e=>uploadFiles(e.target,'video'));$('fileInput').addEventListener('change',e=>uploadFiles(e.target,'file'));$('search').addEventListener('input',render);loadFiles();});