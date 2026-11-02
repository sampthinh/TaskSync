// Nạp dữ liệu mẫu vào server. Chạy: node seed.js   (khi server đang chạy)
// Có thể chạy nhiều lần: cùng id nên không tạo trùng.
const fs = require('node:fs');
const BASE = process.env.BASE || 'http://localhost:3000';
const tasks = JSON.parse(fs.readFileSync(__dirname + '/sample_data.json', 'utf8'));

(async () => {
  let created = 0, existed = 0;
  for (const t of tasks) {
    const res = await fetch(BASE + '/tasks', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(t),
    });
    if (res.status === 201) created++;
    else if (res.status === 200) existed++;
    else console.log('Lỗi với', t.id, res.status);
  }
  console.log(`Xong: tạo mới ${created}, đã có sẵn ${existed}.`);
})().catch(e => console.error('Không kết nối được server. Bạn đã chạy "node server.js" chưa?\n', e.message));
