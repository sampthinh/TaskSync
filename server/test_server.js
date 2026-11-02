// Kiem thu tu dong server. Chay: node test_server.js   (khi server dang chay o cong 3000)
// Ket qua in ra man hinh va ghi vao file test_results.txt
const fs = require('node:fs');
const BASE = process.env.BASE || 'http://localhost:3000';
const run = 'T' + Date.now();            // tien to id de moi lan chay khong dung nhau
const lines = [];
let pass = 0, fail = 0;

const log = s => { console.log(s); lines.push(s); };
async function call(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch {}
  return { status: res.status, data };
}
function check(code, name, ok, detail = '') {
  ok ? pass++ : fail++;
  log(`${ok ? 'PASS' : 'FAIL'}  ${code}  ${name}${detail ? '  -> ' + detail : ''}`);
}

(async () => {
  log(`Kiem thu server ${BASE}  luc ${new Date().toLocaleString('vi-VN')}`);
  log('-'.repeat(70));
  const id = `${run}-a`;
  let r;

  // --- Tao moi va gui lai (TC01, TC06)
  r = await call('POST', '/tasks', { id, title: 'Hoc bai', description: 'Bao cao' });
  check('TC01', 'Tao cong viec moi -> 201, version 1', r.status === 201 && r.data.version === 1, `status ${r.status}`);

  r = await call('POST', '/tasks', { id, title: 'Hoc bai', description: 'Bao cao' });
  check('TC06', 'Gui lai yeu cau tao cung id -> 200, khong tao trung', r.status === 200, `status ${r.status}`);
  r = await call('GET', '/tasks');
  const same = r.data.filter(t => t.id === id).length;
  check('TC06', 'Danh sach chi co 1 ban ghi cho id do', same === 1, `so ban ghi: ${same}`);

  // --- Sua va xung dot (TC07)
  r = await call('PUT', `/tasks/${id}`, { title: 'Hoc bai 2', baseVersion: 1 });
  check('TC07', 'Sua dung baseVersion -> 200, version 2', r.status === 200 && r.data.version === 2, `status ${r.status}`);

  r = await call('PUT', `/tasks/${id}`, { title: 'May B sua', baseVersion: 1 });
  check('TC07', 'Sua voi baseVersion cu -> 409 kem ban cua server',
    r.status === 409 && r.data.server && r.data.server.version === 2, `status ${r.status}`);

  r = await call('PUT', `/tasks/${id}`, { title: 'May B sua', baseVersion: 1, force: true });
  check('TC07', 'Ghi de bang force=true -> 200, version 3', r.status === 200 && r.data.version === 3, `status ${r.status}`);

  r = await call('PUT', `/tasks/${id}`, { title: 'May B sua', baseVersion: 2, force: true });
  check('TC06', 'Gui lai dung thay doi da ap dung -> 200, khong tang version', r.status === 200 && r.data.version === 3, `version ${r.data && r.data.version}`);

  r = await call('PUT', `/tasks/khong-ton-tai`, { title: 'x', baseVersion: 1 });
  check('API', 'Sua cong viec khong ton tai -> 404', r.status === 404, `status ${r.status}`);

  r = await call('POST', '/tasks', { id: `${run}-bad` });
  check('API', 'Tao thieu title -> 400', r.status === 400, `status ${r.status}`);

  // --- Xoa logic (TC08)
  const before = await call('GET', '/tasks/changes?since=0');
  r = await call('DELETE', `/tasks/${id}`);
  check('TC08', 'Xoa cong viec -> 200, isDeleted = true', r.status === 200 && r.data.isDeleted === true, `status ${r.status}`);
  r = await call('DELETE', `/tasks/${id}`);
  check('TC08', 'Xoa lai lan nua -> van 200 (an toan khi gui lai)', r.status === 200, `status ${r.status}`);
  r = await call('GET', '/tasks');
  check('TC08', 'Viec da xoa khong con trong GET /tasks', !r.data.some(t => t.id === id));
  r = await call('GET', '/tasks/changes?since=0');
  const ch = r.data.changes.find(t => t.id === id);
  check('TC08', 'Viec da xoa van xuat hien trong changes (de may khac biet)', !!ch && ch.isDeleted === true);

  // --- Dong bo tang dan
  const mark = r.data.serverTime;
  r = await call('GET', `/tasks/changes?since=${mark}`);
  check('SYNC', 'Hoi lai sau moc serverTime -> khong co thay doi', r.status === 200 && r.data.changes.length === 0, `so thay doi: ${r.data.changes.length}`);
  const id2 = `${run}-b`;
  await call('POST', '/tasks', { id: id2, title: 'Viec moi sau moc' });
  r = await call('GET', `/tasks/changes?since=${mark}`);
  check('SYNC', 'Chi nhan dung viec moi phat sinh sau moc', r.data.changes.length === 1 && r.data.changes[0].id === id2, `so thay doi: ${r.data.changes.length}`);

  // --- Nhieu ban ghi (TC09)
  const N = 100;
  const ids = Array.from({ length: N }, (_, i) => `${run}-bulk-${i}`);
  let t0 = performance.now();
  let ok201 = 0;
  for (const bid of ids) {
    const x = await call('POST', '/tasks', { id: bid, title: 'Bulk ' + bid });
    if (x.status === 201) ok201++;
  }
  const tCreate = performance.now() - t0;
  t0 = performance.now();
  let ok200 = 0;
  for (const bid of ids) {
    const x = await call('POST', '/tasks', { id: bid, title: 'Bulk ' + bid });
    if (x.status === 200) ok200++;
  }
  const tResend = performance.now() - t0;
  r = await call('GET', '/tasks');
  const bulkCount = r.data.filter(t => t.id.startsWith(`${run}-bulk-`)).length;
  const dup = bulkCount - new Set(r.data.filter(t => t.id.startsWith(`${run}-bulk-`)).map(t => t.id)).size;
  check('TC09', `Tao ${N} ban ghi: ${ok201}/${N} thanh cong`, ok201 === N, `${tCreate.toFixed(0)} ms, TB ${(tCreate / N).toFixed(1)} ms/ban ghi`);
  check('TC06', `Gui lai ca ${N} ban ghi: ${ok200}/${N} tra 200, khong tao trung`, ok200 === N && bulkCount === N && dup === 0,
    `${tResend.toFixed(0)} ms, so ban ghi: ${bulkCount}, trung: ${dup}`);

  log('-'.repeat(70));
  log(`Tong: ${pass} dat, ${fail} that bai, ty le dat ${(100 * pass / (pass + fail)).toFixed(1)}%`);
  fs.writeFileSync('test_results.txt', lines.join('\n') + '\n');
  log('Da ghi ket qua vao test_results.txt');
  process.exit(fail ? 1 : 0);
})().catch(e => {
  console.error('Khong ket noi duoc server. Ban da chay "node server.js" chua?\n', e.message);
  process.exit(2);
});
