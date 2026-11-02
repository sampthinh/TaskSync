
const http = require('node:http');
const { DatabaseSync } = require('node:sqlite');

const PORT = process.env.PORT || 3000;
const db = new DatabaseSync(process.env.DB_FILE || 'tasks.db');

db.exec(`CREATE TABLE IF NOT EXISTS tasks (
  id          TEXT PRIMARY KEY,           -- UUID do app tạo (chống tạo trùng khi gửi lại)
  title       TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  isCompleted INTEGER NOT NULL DEFAULT 0,
  isDeleted   INTEGER NOT NULL DEFAULT 0, -- xóa logic: không xóa hẳn để app khác biết
  updatedAt   INTEGER NOT NULL,           -- thời điểm server cập nhật (mili giây)
  version     INTEGER NOT NULL DEFAULT 1  -- tăng 1 mỗi lần sửa, dùng phát hiện xung đột
)`);
db.exec('CREATE INDEX IF NOT EXISTS idx_tasks_updatedAt ON tasks(updatedAt)');

// ---------- tiện ích ----------
const toTask = r => r && ({ ...r, isCompleted: !!r.isCompleted, isDeleted: !!r.isDeleted });
const getTask = id => toTask(db.prepare('SELECT * FROM tasks WHERE id = ?').get(id));
// đảm bảo updatedAt luôn tăng dần để "changes?since=" không bỏ sót
let lastStamp = 0;
const stamp = () => (lastStamp = Math.max(Date.now(), lastStamp + 1));

function send(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', c => (data += c));
    req.on('end', () => { try { resolve(data ? JSON.parse(data) : {}); } catch { reject(new Error('JSON không hợp lệ')); } });
  });
}
const sameContent = (a, b) =>
  a.title === b.title && a.description === b.description &&
  a.isCompleted === b.isCompleted && a.isDeleted === b.isDeleted;

// ---------- xử lý yêu cầu ----------
async function handle(req, res) {
  const url = new URL(req.url, 'http://x');
  const parts = url.pathname.split('/').filter(Boolean); // ['tasks', id?]
  const m = req.method;

  if (url.pathname === '/health') return send(res, 200, { ok: true, serverTime: Date.now() });
  if (parts[0] !== 'tasks') return send(res, 404, { error: 'Không tìm thấy' });

  // GET /tasks  -> danh sách công việc chưa xóa
  if (m === 'GET' && parts.length === 1) {
    const rows = db.prepare('SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY updatedAt DESC').all();
    return send(res, 200, rows.map(toTask));
  }

  // GET /tasks/changes?since=<ms>  -> mọi thay đổi (kể cả đã xóa) sau mốc since
  if (m === 'GET' && parts[1] === 'changes') {
    const since = Number(url.searchParams.get('since') || 0);
    const rows = db.prepare('SELECT * FROM tasks WHERE updatedAt > ? ORDER BY updatedAt ASC').all(since);
    const changes = rows.map(toTask);
    const serverTime = changes.length ? changes[changes.length - 1].updatedAt : since;
    return send(res, 200, { serverTime, changes }); // lần sau gửi since=serverTime
  }

  // POST /tasks  -> tạo mới (gửi lại cùng id thì KHÔNG tạo trùng)
  if (m === 'POST' && parts.length === 1) {
    const b = await readBody(req);
    if (!b.id || !b.title) return send(res, 400, { error: 'Thiếu id hoặc title' });
    const existing = getTask(b.id);
    if (existing) return send(res, 200, existing); // gửi lại -> trả bản đã có
    db.prepare('INSERT INTO tasks (id,title,description,isCompleted,isDeleted,updatedAt,version) VALUES (?,?,?,?,?,?,1)')
      .run(b.id, b.title, b.description || '', b.isCompleted ? 1 : 0, b.isDeleted ? 1 : 0, stamp());
    return send(res, 201, getTask(b.id));
  }

  // PUT /tasks/:id  -> sửa. Body có baseVersion (phiên bản app đang giữ). force:true = ghi đè
  if (m === 'PUT' && parts.length === 2) {
    const b = await readBody(req);
    const cur = getTask(parts[1]);
    if (!cur) return send(res, 404, { error: 'Không có công việc này' });
    const next = {
      title: b.title ?? cur.title,
      description: b.description ?? cur.description,
      isCompleted: b.isCompleted ?? cur.isCompleted,
      isDeleted: b.isDeleted ?? cur.isDeleted,
    };
    if (sameContent(cur, next)) return send(res, 200, cur); // gửi lại cùng thay đổi -> không làm gì thêm
    if (!b.force && b.baseVersion !== cur.version)
      return send(res, 409, { error: 'Xung đột phiên bản', server: cur }); // app xử lý theo chính sách
    db.prepare('UPDATE tasks SET title=?,description=?,isCompleted=?,isDeleted=?,updatedAt=?,version=version+1 WHERE id=?')
      .run(next.title, next.description, next.isCompleted ? 1 : 0, next.isDeleted ? 1 : 0, stamp(), cur.id);
    return send(res, 200, getTask(cur.id));
  }

  // DELETE /tasks/:id  -> xóa logic (xóa lại nhiều lần vẫn an toàn)
  if (m === 'DELETE' && parts.length === 2) {
    const cur = getTask(parts[1]);
    if (!cur) return send(res, 404, { error: 'Không có công việc này' });
    if (cur.isDeleted) return send(res, 200, cur);
    db.prepare('UPDATE tasks SET isDeleted=1,updatedAt=?,version=version+1 WHERE id=?').run(stamp(), cur.id);
    return send(res, 200, getTask(cur.id));
  }

  send(res, 405, { error: 'Không hỗ trợ' });
}

http.createServer((req, res) => {
  handle(req, res).catch(e => send(res, 400, { error: e.message }));
}).listen(PORT, '0.0.0.0', () => console.log(`Server chạy tại http://localhost:${PORT}`));
