const express = require('express');
const session = require('express-session');
const multer = require('multer');
const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const crypto = require('crypto');

const app = express();
const PORT = Number(process.env.PORT || 3000);
const ROOT = __dirname;
const DATA = path.join(ROOT, 'data');
const STORAGE = path.join(DATA, 'storage');
const USERS_FILE = path.join(DATA, 'users.json');
const FILES_FILE = path.join(DATA, 'files.json');
const MANAGERS = ['1', '2', '3'];

function ensureData() {
  fs.mkdirSync(STORAGE, { recursive: true });
  if (!fs.existsSync(USERS_FILE)) fs.writeFileSync(USERS_FILE, '[]');
  if (!fs.existsSync(FILES_FILE)) fs.writeFileSync(FILES_FILE, '[]');
}
function readJson(file) { return JSON.parse(fs.readFileSync(file, 'utf8')); }
function writeJson(file, value) { fs.writeFileSync(file, JSON.stringify(value, null, 2)); }
function safeUser(name) { return /^[A-Za-z0-9._-]{3,32}$/.test(name); }
function safeFileName(name) { return path.basename(name).replace(/[^\p{L}\p{N}._ ()-]/gu, '_').slice(0, 180) || 'fichier'; }
function hashPassword(password, salt) { return crypto.scryptSync(password, salt, 64).toString('hex'); }
function passwordRecord(password) { const salt = crypto.randomBytes(16).toString('hex'); return { salt, hash: hashPassword(password, salt) }; }
function checkPassword(password, user) {
  const a = Buffer.from(hashPassword(password, user.salt), 'hex');
  const b = Buffer.from(user.hash, 'hex');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
function requireAuth(req, res, next) { if (!req.session.user) return res.status(401).json({ error: 'Non connecte' }); next(); }
function requireAdmin(req, res, next) { if (!req.session.user || req.session.user.role !== 'admin') return res.status(403).json({ error: 'Acces administrateur requis' }); next(); }
function getUser(req) { return readJson(USERS_FILE).find(u => u.username === req.session.user.username); }

ensureData();
app.use(express.json({ limit: '1mb' }));
app.use(session({ secret: process.env.SESSION_SECRET || crypto.randomBytes(32).toString('hex'), resave: false, saveUninitialized: false, cookie: { httpOnly: true, sameSite: 'lax', secure: process.env.NODE_ENV === 'production', maxAge: 7 * 24 * 60 * 60 * 1000 } }));
app.use(express.static(path.join(ROOT, 'public')));

const upload = multer({
  storage: multer.diskStorage({
    destination: (req, file, cb) => {
      const username = req.session.user.username;
      const manager = String(req.query.manager || '1');
      const dir = path.join(STORAGE, username, manager);
      fs.mkdirSync(dir, { recursive: true });
      cb(null, dir);
    },
    filename: (req, file, cb) => cb(null, `${Date.now()}-${crypto.randomBytes(6).toString('hex')}-${safeFileName(file.originalname)}`)
  }),
  limits: { fileSize: Number(process.env.MAX_FILE_SIZE || 5 * 1024 * 1024 * 1024), files: 100 }
});

app.get('/api/setup-status', (req, res) => res.json({ needsSetup: readJson(USERS_FILE).length === 0 }));
app.post('/api/setup', (req, res) => {
  const users = readJson(USERS_FILE);
  if (users.length) return res.status(409).json({ error: 'Configuration deja faite' });
  const { username, password } = req.body || {};
  if (!safeUser(username) || typeof password !== 'string' || password.length < 8) return res.status(400).json({ error: 'Identifiant invalide ou mot de passe de 8 caracteres minimum' });
  const p = passwordRecord(password);
  users.push({ username, role: 'admin', ...p, createdAt: new Date().toISOString() });
  writeJson(USERS_FILE, users);
  req.session.user = { username, role: 'admin' };
  res.json({ ok: true, user: req.session.user });
});

app.post('/api/auth/login', (req, res) => {
  const { username, password } = req.body || {};
  const user = readJson(USERS_FILE).find(u => u.username === username);
  if (!user || typeof password !== 'string' || !checkPassword(password, user)) return res.status(401).json({ error: 'Identifiant ou mot de passe incorrect' });
  req.session.user = { username: user.username, role: user.role };
  res.json({ user: req.session.user });
});
app.post('/api/auth/logout', (req, res) => req.session.destroy(() => res.json({ ok: true })));
app.get('/api/me', (req, res) => res.json({ user: req.session.user || null }));

app.get('/api/files', requireAuth, (req, res) => {
  const manager = String(req.query.manager || '1');
  if (!MANAGERS.includes(manager)) return res.status(400).json({ error: 'Gestionnaire invalide' });
  const username = req.session.user.username;
  const files = readJson(FILES_FILE).filter(f => f.username === username && f.manager === manager).map(f => ({ id: f.id, name: f.originalName, size: f.size, mime: f.mime, createdAt: f.createdAt }));
  res.json({ files });
});
app.post('/api/files', requireAuth, (req, res, next) => {
  const manager = String(req.query.manager || '1');
  if (!MANAGERS.includes(manager)) return res.status(400).json({ error: 'Gestionnaire invalide' });
  upload.array('files', 100)(req, res, err => {
    if (err) return res.status(400).json({ error: err.message });
    const records = readJson(FILES_FILE);
    for (const file of req.files || []) records.push({ id: crypto.randomUUID(), username: req.session.user.username, manager, originalName: safeFileName(file.originalname), storedName: file.filename, path: file.path, size: file.size, mime: file.mimetype || 'application/octet-stream', createdAt: new Date().toISOString() });
    writeJson(FILES_FILE, records);
    res.json({ ok: true, count: (req.files || []).length });
  });
});

function findOwnedFile(req) {
  const f = readJson(FILES_FILE).find(x => x.id === req.params.id && x.username === req.session.user.username);
  return f;
}
app.get('/api/files/:id/view', requireAuth, async (req, res) => {
  const file = findOwnedFile(req);
  if (!file) return res.status(404).json({ error: 'Fichier introuvable' });
  try { await fsp.access(file.path); } catch { return res.status(404).json({ error: 'Fichier absent du stockage' }); }
  res.type(file.mime).sendFile(path.resolve(file.path));
});
app.get('/api/files/:id/download', requireAuth, async (req, res) => {
  const file = findOwnedFile(req);
  if (!file) return res.status(404).json({ error: 'Fichier introuvable' });
  try { await fsp.access(file.path); } catch { return res.status(404).json({ error: 'Fichier absent du stockage' }); }
  res.download(path.resolve(file.path), file.originalName);
});
app.delete('/api/files/:id', requireAuth, async (req, res) => {
  const files = readJson(FILES_FILE);
  const index = files.findIndex(x => x.id === req.params.id && x.username === req.session.user.username);
  if (index < 0) return res.status(404).json({ error: 'Fichier introuvable' });
  const [file] = files.splice(index, 1);
  try { await fsp.unlink(file.path); } catch {}
  writeJson(FILES_FILE, files);
  res.json({ ok: true });
});

app.get('/api/accounts', requireAdmin, (req, res) => res.json({ users: readJson(USERS_FILE).map(u => ({ username: u.username, role: u.role, createdAt: u.createdAt })) }));
app.post('/api/accounts', requireAdmin, (req, res) => {
  const { username, password, role = 'user' } = req.body || {};
  const users = readJson(USERS_FILE);
  if (!safeUser(username) || typeof password !== 'string' || password.length < 8 || !['user', 'admin'].includes(role)) return res.status(400).json({ error: 'Donnees invalides' });
  if (users.some(u => u.username === username)) return res.status(409).json({ error: 'Identifiant deja utilise' });
  users.push({ username, role, ...passwordRecord(password), createdAt: new Date().toISOString() });
  writeJson(USERS_FILE, users);
  fs.mkdirSync(path.join(STORAGE, username), { recursive: true });
  res.json({ ok: true });
});
app.delete('/api/accounts/:username', requireAdmin, async (req, res) => {
  if (req.params.username === req.session.user.username) return res.status(400).json({ error: 'Impossible de supprimer son propre compte' });
  let users = readJson(USERS_FILE);
  if (!users.some(u => u.username === req.params.username)) return res.status(404).json({ error: 'Compte introuvable' });
  users = users.filter(u => u.username !== req.params.username);
  writeJson(USERS_FILE, users);
  const files = readJson(FILES_FILE).filter(f => f.username !== req.params.username);
  writeJson(FILES_FILE, files);
  await fsp.rm(path.join(STORAGE, req.params.username), { recursive: true, force: true });
  res.json({ ok: true });
});

app.get('*', (req, res) => res.sendFile(path.join(ROOT, 'public', 'index.html')));
app.listen(PORT, '0.0.0.0', () => console.log(`Virtual NAS running on http://0.0.0.0:${PORT}`));
