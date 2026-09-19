const express = require('express');
const multer = require('multer');
const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const crypto = require('crypto');
const { pipeline } = require('stream/promises');
const { pipeline } = require('stream/promises');

const app = express();
const PORT = Number(process.env.PORT || 3000);
const ROOT = __dirname;
const DATA = path.join(ROOT, 'data');
const STORAGE = path.join(DATA, 'storage');
const CHUNKS = path.join(DATA, 'chunks');
const UPLOADS = path.join(DATA, 'uploads');
const FILES_FILE = path.join(DATA, 'files.json');
const MANAGERS = ['1', '2', '3'];
const MAX_FILE_SIZE = Number(process.env.MAX_FILE_SIZE || 20 * 1024 * 1024 * 1024);

function ensureData() {
  fs.mkdirSync(STORAGE, { recursive: true });
  fs.mkdirSync(CHUNKS, { recursive: true });
  fs.mkdirSync(UPLOADS, { recursive: true });
  if (!fs.existsSync(FILES_FILE)) fs.writeFileSync(FILES_FILE, '[]');
}
function readJson(file) { return JSON.parse(fs.readFileSync(file, 'utf8')); }
function writeJson(file, value) { fs.writeFileSync(file, JSON.stringify(value, null, 2)); }
function safeFileName(name) {
  return path.basename(name).replace(/[^\p{L}\p{N}._ ()-]/gu, '_').slice(0, 180) || 'fichier';
}
function validUploadId(id) {
  return typeof id === 'string' && /^[a-f0-9-]{20,80}$/i.test(id);
}

ensureData();
app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(ROOT, 'public')));

const MAX_FILE_SIZE = Number(process.env.MAX_FILE_SIZE || 20 * 1024 * 1024 * 1024);
const CHUNK_SIZE = 25 * 1024 * 1024;

const upload = multer({
  storage: multer.diskStorage({
    destination: (req, file, cb) => {
      const manager = String(req.query.manager || '1');
      const dir = path.join(STORAGE, manager);
      fs.mkdirSync(dir, { recursive: true });
      cb(null, dir);
    },
    filename: (req, file, cb) => cb(null, `${Date.now()}-${crypto.randomBytes(6).toString('hex')}-${safeFileName(file.originalname)}`)
  }),
  limits: { fileSize: MAX_FILE_SIZE, files: 100 }
});

async function getDirectorySize(dir) {
  let total = 0;
  for (const entry of await fsp.readdir(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) total += await getDirectorySize(full);
    else if (entry.isFile()) {
      try { total += (await fsp.stat(full)).size; } catch {}
    }
  }
  return total;
}

app.get('/api/status', async (req, res) => {
  const files = readJson(FILES_FILE);
  const stat = fs.statfsSync(STORAGE);
  const totalBytes = stat.blocks * stat.bsize;
  const freeBytes = stat.bavail * stat.bsize;
  const usedBytes = await getDirectorySize(STORAGE);
  res.json({ ok: true, managers: MANAGERS, totalFiles: files.length, storage: { usedBytes, freeBytes, totalBytes } });
});

app.get('/api/files', (req, res) => {
  const manager = String(req.query.manager || '1');
  if (!MANAGERS.includes(manager)) return res.status(400).json({ error: 'Gestionnaire invalide' });
  const files = readJson(FILES_FILE).filter(f => f.manager === manager)
    .map(f => ({ id: f.id, name: f.originalName, size: f.size, mime: f.mime, createdAt: f.createdAt }));
  res.json({ files });
});

app.post('/api/upload/chunk', async (req, res) => {
  const manager = String(req.query.manager || '1');
  const uploadId = String(req.query.uploadId || '');
  const chunkIndex = Number(req.query.chunkIndex);
  const totalChunks = Number(req.query.totalChunks);
  const totalSize = Number(req.query.totalSize);
  const originalName = safeFileName(String(req.query.filename || 'video'));
  const mime = String(req.query.mime || 'video/mp4');

  if (!MANAGERS.includes(manager)) return res.status(400).json({ error: 'Gestionnaire invalide' });
  if (!/^[0-9a-f-]{36}$/i.test(uploadId)) return res.status(400).json({ error: 'Identifiant d’envoi invalide' });
  if (!Number.isInteger(chunkIndex) || chunkIndex < 0) return res.status(400).json({ error: 'Morceau invalide' });
  if (!Number.isInteger(totalChunks) || totalChunks < 1 || totalChunks > 10000) return res.status(400).json({ error: 'Nombre de morceaux invalide' });
  if (chunkIndex >= totalChunks) return res.status(400).json({ error: 'Morceau hors limites' });
  if (!Number.isSafeInteger(totalSize) || totalSize < 1 || totalSize > MAX_FILE_SIZE) return res.status(400).json({ error: 'Taille de vidéo invalide' });

  const dir = path.join(CHUNKS, uploadId);
  const partPath = path.join(dir, String(chunkIndex) + '.part');
  const metaPath = path.join(dir, 'meta.json');

  try {
    await fsp.mkdir(dir, { recursive: true });
    if (fs.existsSync(partPath)) return res.status(409).json({ error: 'Ce morceau a déjà été envoyé' });
    if (fs.existsSync(metaPath)) {
      const meta = readJson(metaPath);
      if (meta.manager !== manager || meta.totalChunks !== totalChunks || meta.totalSize !== totalSize) return res.status(400).json({ error: 'Les informations de l’envoi ne correspondent pas' });
    } else {
      writeJson(metaPath, { manager, uploadId, totalChunks, totalSize, originalName, mime, createdAt: new Date().toISOString() });
    }
    let bytes = 0;
    const out = fs.createWriteStream(partPath, { flags: 'wx' });
    req.on('data', chunk => {
      bytes += chunk.length;
      if (bytes > CHUNK_SIZE) req.destroy(new Error('Morceau trop gros'));
    });
    await pipeline(req, out);
    if (bytes > CHUNK_SIZE) throw new Error('Morceau trop gros');
    res.json({ ok: true, chunkIndex, receivedBytes: bytes });
  } catch (err) {
    try { await fsp.unlink(partPath); } catch {}
    res.status(400).json({ error: err.message || 'Erreur pendant l’envoi du morceau' });
  }
});

app.post('/api/upload/complete', async (req, res) => {
  const manager = String(req.body?.manager || '1');
  const uploadId = String(req.body?.uploadId || '');
  if (!MANAGERS.includes(manager)) return res.status(400).json({ error: 'Gestionnaire invalide' });
  if (!/^[0-9a-f-]{36}$/i.test(uploadId)) return res.status(400).json({ error: 'Identifiant d’envoi invalide' });
  const dir = path.join(CHUNKS, uploadId);
  const metaPath = path.join(dir, 'meta.json');
  try {
    const meta = readJson(metaPath);
    if (meta.manager !== manager) return res.status(400).json({ error: 'Gestionnaire invalide pour cet envoi' });
    const parts = [];
    let totalBytes = 0;
    for (let i = 0; i < meta.totalChunks; i++) {
      const partPath = path.join(dir, String(i) + '.part');
      const stat = await fsp.stat(partPath);
      if (!stat.isFile() || stat.size > CHUNK_SIZE) throw new Error('Morceau manquant ou invalide : ' + (i + 1));
      parts.push(partPath);
      totalBytes += stat.size;
    }
    if (totalBytes !== meta.totalSize) throw new Error('Taille finale incorrecte');
    if (totalBytes > MAX_FILE_SIZE) throw new Error('La vidéo dépasse la taille maximale autorisée');
    const managerDir = path.join(STORAGE, manager);
    await fsp.mkdir(managerDir, { recursive: true });
    const storedName = Date.now() + '-' + crypto.randomBytes(6).toString('hex') + '-' + meta.originalName;
    const finalPath = path.join(managerDir, storedName);
    const out = fs.createWriteStream(finalPath, { flags: 'wx' });
    try {
      for (const partPath of parts) await pipeline(fs.createReadStream(partPath), out, { end: false });
      await new Promise((resolve, reject) => { out.once('error', reject); out.end(resolve); });
    } catch (err) {
      out.destroy();
      try { await fsp.unlink(finalPath); } catch {}
      throw err;
    }
    const records = readJson(FILES_FILE);
    records.push({ id: crypto.randomUUID(), manager, originalName: meta.originalName, storedName, path: finalPath, size: totalBytes, mime: meta.mime || 'video/mp4', createdAt: new Date().toISOString() });
    writeJson(FILES_FILE, records);
    await fsp.rm(dir, { recursive: true, force: true });
    res.json({ ok: true, size: totalBytes });
  } catch (err) {
    res.status(400).json({ error: err.message || 'Impossible de terminer l’envoi' });
  }
});

app.post('/api/files', (req, res) => {
  const manager = String(req.query.manager || '1');
  if (!MANAGERS.includes(manager)) return res.status(400).json({ error: 'Gestionnaire invalide' });
  upload.array('files', 100)(req, res, err => {
    if (err) return res.status(400).json({ error: err.message });
    const records = readJson(FILES_FILE);
    for (const file of req.files || []) {
      records.push({
        id: crypto.randomUUID(), manager,
        originalName: safeFileName(file.originalname), storedName: file.filename,
        path: file.path, size: file.size,
        mime: file.mimetype || 'application/octet-stream',
        createdAt: new Date().toISOString()
      });
    }
    writeJson(FILES_FILE, records);
    res.json({ ok: true, count: (req.files || []).length });
  });
});

// Upload vidéo par morceaux : chaque requête reste sous la limite Cloudflare.
app.post('/api/video/chunk', async (req, res) => {
  const manager = String(req.query.manager || '1');
  const uploadId = String(req.query.uploadId || '');
  const chunkIndex = Number(req.query.chunkIndex);
  const totalChunks = Number(req.query.totalChunks);
  const totalSize = Number(req.query.totalSize);
  const originalName = safeFileName(String(req.query.name || 'video'));
  if (!MANAGERS.includes(manager)) return res.status(400).json({ error: 'Gestionnaire invalide' });
  if (!validUploadId(uploadId)) return res.status(400).json({ error: 'Identifiant d\'upload invalide' });
  if (!Number.isInteger(chunkIndex) || !Number.isInteger(totalChunks) || chunkIndex < 0 || totalChunks < 1 || chunkIndex >= totalChunks) {
    return res.status(400).json({ error: 'Morceau invalide' });
  }
  if (!Number.isFinite(totalSize) || totalSize < 1 || totalSize > MAX_FILE_SIZE) {
    return res.status(400).json({ error: 'Vidéo trop volumineuse' });
  }

  const dir = path.join(UPLOADS, uploadId);
  const filePath = path.join(dir, `${String(chunkIndex).padStart(8, '0')}.part`);
  try {
    await fsp.mkdir(dir, { recursive: true });
    await pipeline(req, fs.createWriteStream(filePath));
    const size = (await fsp.stat(filePath)).size;
    res.json({ ok: true, chunkIndex, size, totalChunks, totalSize });
  } catch (e) {
    try { await fsp.unlink(filePath); } catch {}
    res.status(500).json({ error: 'Échec de l\'envoi du morceau' });
  }
});

app.post('/api/video/complete', async (req, res) => {
  const { manager, uploadId, totalChunks, totalSize, name, mime } = req.body || {};
  const managerId = String(manager || '1');
  const id = String(uploadId || '');
  const count = Number(totalChunks);
  const expectedSize = Number(totalSize);
  const originalName = safeFileName(String(name || 'video'));
  if (!MANAGERS.includes(managerId)) return res.status(400).json({ error: 'Gestionnaire invalide' });
  if (!validUploadId(id) || !Number.isInteger(count) || count < 1 || count > 100000) return res.status(400).json({ error: 'Upload invalide' });
  if (!Number.isFinite(expectedSize) || expectedSize < 1 || expectedSize > MAX_FILE_SIZE) return res.status(400).json({ error: 'Vidéo trop volumineuse' });

  const dir = path.join(UPLOADS, id);
  const finalDir = path.join(STORAGE, managerId);
  const storedName = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}-${originalName}`;
  const finalPath = path.join(finalDir, storedName);
  try {
    await fsp.mkdir(finalDir, { recursive: true });
    const parts = [];
    let actualSize = 0;
    for (let i = 0; i < count; i++) {
      const part = path.join(dir, `${String(i).padStart(8, '0')}.part`);
      const stat = await fsp.stat(part);
      parts.push(part);
      actualSize += stat.size;
    }
    if (actualSize !== expectedSize) throw new Error('Taille finale incorrecte');

    const output = fs.createWriteStream(finalPath);
    try {
      for (const part of parts) await pipeline(fs.createReadStream(part), output, { end: false });
    } finally {
      output.end();
      await new Promise(resolve => output.once('close', resolve));
    }

    const records = readJson(FILES_FILE);
    records.push({
      id: crypto.randomUUID(),
      manager: managerId,
      originalName,
      storedName,
      path: finalPath,
      size: actualSize,
      mime: String(mime || 'video/mp4'),
      createdAt: new Date().toISOString()
    });
    writeJson(FILES_FILE, records);
    await fsp.rm(dir, { recursive: true, force: true });
    res.json({ ok: true, id: records[records.length - 1].id, name: originalName, size: actualSize });
  } catch (e) {
    try { await fsp.unlink(finalPath); } catch {}
    res.status(400).json({ error: e.message === 'Taille finale incorrecte' ? e.message : 'Impossible de finaliser la vidéo' });
  }
});

app.delete('/api/video/:uploadId', async (req, res) => {
  const id = String(req.params.uploadId || '');
  if (!validUploadId(id)) return res.status(400).json({ error: 'Identifiant d\'upload invalide' });
  await fsp.rm(path.join(UPLOADS, id), { recursive: true, force: true });
  res.json({ ok: true });
});

function findFile(id) { return readJson(FILES_FILE).find(f => f.id === id); }

app.get('/api/files/:id/view', async (req, res) => {
  const file = findFile(req.params.id);
  if (!file) return res.status(404).json({ error: 'Fichier introuvable' });
  try { await fsp.access(file.path); } catch { return res.status(404).json({ error: 'Fichier absent du stockage' }); }
  res.type(file.mime).sendFile(path.resolve(file.path));
});

app.get('/api/files/:id/download', async (req, res) => {
  const file = findFile(req.params.id);
  if (!file) return res.status(404).json({ error: 'Fichier introuvable' });
  try { await fsp.access(file.path); } catch { return res.status(404).json({ error: 'Fichier absent du stockage' }); }
  res.download(path.resolve(file.path), file.originalName);
});

app.delete('/api/files/:id', async (req, res) => {
  const files = readJson(FILES_FILE);
  const index = files.findIndex(f => f.id === req.params.id);
  if (index < 0) return res.status(404).json({ error: 'Fichier introuvable' });
  const [file] = files.splice(index, 1);
  try { await fsp.unlink(file.path); } catch {}
  writeJson(FILES_FILE, files);
  res.json({ ok: true });
});

app.use((req, res) => res.sendFile(path.join(ROOT, 'public', 'index.html')));
app.listen(PORT, '0.0.0.0', () => console.log(`Virtual NAS running on http://0.0.0.0:${PORT}`));
