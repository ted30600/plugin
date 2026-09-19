const express = require('express');
const multer = require('multer');
const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const crypto = require('crypto');
const { Transform } = require('stream');
const { pipeline } = require('stream/promises');

const app = express();
const PORT = Number(process.env.PORT || 3000);
const ROOT = __dirname;
const DATA = path.join(ROOT, 'data');
const STORAGE = path.join(DATA, 'storage');
const UPLOADS = path.join(DATA, 'uploads');
const FILES_FILE = path.join(DATA, 'files.json');
const MANAGERS = ['1', '2', '3'];
const MAX_FILE_SIZE = Number(process.env.MAX_FILE_SIZE || 20 * 1024 * 1024 * 1024);
const CHUNK_SIZE = 25 * 1024 * 1024;
const UPLOAD_TTL_MS = 24 * 60 * 60 * 1000;

function ensureData() {
  fs.mkdirSync(STORAGE, { recursive: true });
  fs.mkdirSync(UPLOADS, { recursive: true });
  if (!fs.existsSync(FILES_FILE)) fs.writeFileSync(FILES_FILE, '[]');
}
function readJson(file) { return JSON.parse(fs.readFileSync(file, 'utf8')); }
function writeJson(file, value) { fs.writeFileSync(file, JSON.stringify(value, null, 2)); }
function safeFileName(name) {
  return path.basename(name).replace(/[^\p{L}\p{N}._ ()-]/gu, '_').slice(0, 180) || 'fichier';
}
function validUploadId(id) {
  return /^[0-9a-f-]{36}$/i.test(String(id || ''));
}
async function cleanupOldUploads() {
  const now = Date.now();
  for (const entry of await fsp.readdir(UPLOADS, { withFileTypes: true }).catch(() => [])) {
    if (!entry.isDirectory()) continue;
    const dir = path.join(UPLOADS, entry.name);
    try {
      const stat = await fsp.stat(dir);
      if (now - stat.mtimeMs > UPLOAD_TTL_MS) await fsp.rm(dir, { recursive: true, force: true });
    } catch {}
  }
}

ensureData();
cleanupOldUploads();
setInterval(cleanupOldUploads, 6 * 60 * 60 * 1000).unref();

app.use(express.json({ limit: '1mb' }));
app.use(express.static(path.join(ROOT, 'public')));

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

/*
 * Vidéos volumineuses :
 * le navigateur découpe la vidéo en morceaux de 25 Mo.
 * Chaque requête reste donc sous la limite Cloudflare de 100 Mo
 * des offres Free/Pro. Les morceaux sont écrits directement sur disque.
 */
app.post('/api/upload/chunk', async (req, res) => {
  const manager = String(req.query.manager || '1');
  const uploadId = String(req.query.uploadId || '');
  const chunkIndex = Number(req.query.chunkIndex);
  const totalChunks = Number(req.query.totalChunks);
  const totalSize = Number(req.query.totalSize);
  const originalName = safeFileName(String(req.query.filename || 'video'));
  const mime = String(req.query.mime || 'video/mp4');

  if (!MANAGERS.includes(manager)) return res.status(400).json({ error: 'Gestionnaire invalide' });
  if (!validUploadId(uploadId)) return res.status(400).json({ error: 'Identifiant d’envoi invalide' });
  if (!Number.isInteger(chunkIndex) || chunkIndex < 0) return res.status(400).json({ error: 'Morceau invalide' });
  if (!Number.isInteger(totalChunks) || totalChunks < 1 || totalChunks > 10000 || chunkIndex >= totalChunks) {
    return res.status(400).json({ error: 'Nombre de morceaux invalide' });
  }
  if (!Number.isSafeInteger(totalSize) || totalSize < 1 || totalSize > MAX_FILE_SIZE) {
    return res.status(400).json({ error: 'Vidéo trop volumineuse' });
  }

  const contentLength = Number(req.headers['content-length'] || 0);
  if (contentLength > CHUNK_SIZE) return res.status(413).json({ error: 'Morceau trop gros (maximum 25 Mo)' });

  const dir = path.join(UPLOADS, uploadId);
  const partPath = path.join(dir, `${String(chunkIndex).padStart(8, '0')}.part`);
  const metaPath = path.join(dir, 'meta.json');

  try {
    await fsp.mkdir(dir, { recursive: true });

    if (fs.existsSync(partPath)) {
      return res.status(409).json({ error: 'Ce morceau a déjà été envoyé' });
    }

    if (fs.existsSync(metaPath)) {
      const meta = readJson(metaPath);
      if (meta.manager !== manager || meta.totalChunks !== totalChunks || meta.totalSize !== totalSize) {
        return res.status(400).json({ error: 'Les informations de l’envoi ne correspondent pas' });
      }
    } else {
      writeJson(metaPath, {
        manager, uploadId, totalChunks, totalSize, originalName, mime,
        createdAt: new Date().toISOString()
      });
    }

    const limiter = new Transform({
      transform(chunk, encoding, callback) {
        this.received = (this.received || 0) + chunk.length;
        if (this.received > CHUNK_SIZE) callback(new Error('Morceau trop gros (maximum 25 Mo)'));
        else callback(null, chunk);
      }
    });

    await pipeline(req, limiter, fs.createWriteStream(partPath, { flags: 'wx' }));
    const receivedBytes = (await fsp.stat(partPath)).size;

    res.json({ ok: true, chunkIndex, receivedBytes, totalChunks });
  } catch (err) {
    await fsp.unlink(partPath).catch(() => {});
    res.status(400).json({ error: err.message || 'Erreur pendant l’envoi du morceau' });
  }
});

app.post('/api/upload/complete', async (req, res) => {
  const manager = String(req.body?.manager || '1');
  const uploadId = String(req.body?.uploadId || '');

  if (!MANAGERS.includes(manager)) return res.status(400).json({ error: 'Gestionnaire invalide' });
  if (!validUploadId(uploadId)) return res.status(400).json({ error: 'Identifiant d’envoi invalide' });

  const dir = path.join(UPLOADS, uploadId);
  const metaPath = path.join(dir, 'meta.json');

  try {
    const meta = readJson(metaPath);
    if (meta.manager !== manager) throw new Error('Gestionnaire invalide pour cet envoi');

    const parts = [];
    let actualSize = 0;

    for (let i = 0; i < meta.totalChunks; i++) {
      const partPath = path.join(dir, `${String(i).padStart(8, '0')}.part`);
      const stat = await fsp.stat(partPath);
      if (!stat.isFile() || stat.size > CHUNK_SIZE) throw new Error(`Morceau ${i + 1}/${meta.totalChunks} manquant ou invalide`);
      parts.push(partPath);
      actualSize += stat.size;
    }

    if (actualSize !== meta.totalSize) throw new Error('Taille finale incorrecte');

    const managerDir = path.join(STORAGE, manager);
    await fsp.mkdir(managerDir, { recursive: true });

    const storedName = `${Date.now()}-${crypto.randomBytes(6).toString('hex')}-${meta.originalName}`;
    const finalPath = path.join(managerDir, storedName);

    try {
      const output = fs.createWriteStream(finalPath, { flags: 'wx' });
      for (const partPath of parts) {
        await pipeline(fs.createReadStream(partPath), output, { end: false });
      }
      await new Promise((resolve, reject) => {
        output.once('error', reject);
        output.end(resolve);
      });
    } catch (err) {
      await fsp.unlink(finalPath).catch(() => {});
      throw err;
    }

    const records = readJson(FILES_FILE);
    const record = {
      id: crypto.randomUUID(),
      manager,
      originalName: meta.originalName,
      storedName,
      path: finalPath,
      size: actualSize,
      mime: meta.mime || 'video/mp4',
      createdAt: new Date().toISOString()
    };

    records.push(record);
    writeJson(FILES_FILE, records);
    await fsp.rm(dir, { recursive: true, force: true });

    res.json({ ok: true, id: record.id, size: actualSize });
  } catch (err) {
    res.status(400).json({ error: err.message || 'Impossible de finaliser la vidéo' });
  }
});

app.delete('/api/upload/:uploadId', async (req, res) => {
  const uploadId = String(req.params.uploadId || '');
  if (!validUploadId(uploadId)) return res.status(400).json({ error: 'Identifiant d’envoi invalide' });
  await fsp.rm(path.join(UPLOADS, uploadId), { recursive: true, force: true });
  res.json({ ok: true });
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
        originalName: safeFileName(file.originalname),
        storedName: file.filename,
        path: file.path,
        size: file.size,
        mime: file.mimetype || 'application/octet-stream',
        createdAt: new Date().toISOString()
      });
    }
    writeJson(FILES_FILE, records);
    res.json({ ok: true, count: (req.files || []).length });
  });
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
