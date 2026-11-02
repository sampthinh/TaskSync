# Server đồng bộ công việc (TaskSync)

Server REST API cho ứng dụng Android quản lý công việc hỗ trợ ngoại tuyến (Offline-First). Server lưu dữ liệu dùng chung, cung cấp API tạo, sửa, xóa, truy vấn công việc và API lấy các thay đổi từ lần đồng bộ trước.

- Ngôn ngữ: Node.js, **không cần cài thư viện ngoài** (dùng module `http` và `node:sqlite` có sẵn).
- Lưu trữ: SQLite, dữ liệu nằm trong file `tasks.db` (tự tạo khi chạy lần đầu).
- Định dạng dữ liệu: JSON (UTF-8).

## 1. Yêu cầu và cách chạy

**Yêu cầu:** Node.js từ bản **22.13** trở lên (kiểm tra bằng `node -v`). Tải bản LTS tại nodejs.org.

```
cd server
node server.js
```

Thấy dòng `Server chạy tại http://localhost:3000` là server đã chạy. Có thể hiện cảnh báo `ExperimentalWarning` về SQLite, đó là bình thường.

Kiểm tra nhanh: mở trình duyệt vào `http://localhost:3000/health`, kết quả có `"ok":true`.

**Tùy chọn cấu hình (biến môi trường):**

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `PORT` | `3000` | Cổng server lắng nghe |
| `DB_FILE` | `tasks.db` | Đường dẫn file cơ sở dữ liệu |

Ví dụ trên PowerShell: `$env:PORT=4000; node server.js`

**Muốn xóa sạch dữ liệu:** tắt server (Ctrl+C), xóa file `tasks.db`, chạy lại.

## 2. Kết nối từ ứng dụng Android

| Thiết bị | Địa chỉ gốc |
|---|---|
| Máy ảo Android (emulator) | `http://10.0.2.2:3000/` (10.0.2.2 là máy tính đang chạy server) |
| Điện thoại thật | `http://<IP mạng LAN của máy tính>:3000/`, ví dụ `http://192.168.1.10:3000/` (điện thoại và máy tính phải cùng Wi-Fi, và cho phép cổng 3000 qua tường lửa) |

Vì server chạy HTTP (không phải HTTPS), ứng dụng cần khai báo quyền `INTERNET` và `android:usesCleartextTraffic="true"` trong `AndroidManifest.xml`.

## 3. Mô hình dữ liệu

Bảng `tasks`:

| Trường | Kiểu | Ý nghĩa |
|---|---|---|
| `id` | Chuỗi (UUID) | Khóa chính, **do ứng dụng tạo**. Nhờ đó gửi lại cùng một yêu cầu tạo không sinh bản ghi trùng |
| `title` | Chuỗi | Tên công việc (bắt buộc khi tạo) |
| `description` | Chuỗi | Mô tả, mặc định rỗng |
| `isCompleted` | Đúng/sai | Đã hoàn thành chưa |
| `isDeleted` | Đúng/sai | **Xóa logic**: chỉ đánh dấu đã xóa để các thiết bị khác biết, không xóa hẳn khỏi bảng |
| `updatedAt` | Số (mili giây) | Thời điểm server cập nhật lần cuối. Luôn tăng dần giữa các lần ghi |
| `version` | Số nguyên | Bắt đầu từ 1, **tăng 1 mỗi lần bản ghi thay đổi**. Dùng để phát hiện xung đột |

Ví dụ một công việc:

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "title": "Hoàn thành báo cáo lý thuyết",
  "description": "Viết D1: Offline-First, Room, Retrofit",
  "isCompleted": false,
  "isDeleted": false,
  "updatedAt": 1793617527713,
  "version": 1
}
```

## 4. Danh sách API

Mọi yêu cầu có body gửi kèm header `Content-Type: application/json`.

| Phương thức và đường dẫn | Việc làm |
|---|---|
| `GET /health` | Kiểm tra server đang chạy |
| `GET /tasks` | Lấy các công việc chưa xóa |
| `POST /tasks` | Tạo công việc mới |
| `PUT /tasks/{id}` | Sửa công việc (có kiểm tra phiên bản) |
| `DELETE /tasks/{id}` | Xóa logic công việc |
| `GET /tasks/changes?since={ms}` | Lấy mọi thay đổi (kể cả đã xóa) sau mốc `since` |

### 4.1 `GET /health`

Trả `200`: `{"ok": true, "serverTime": 1793617527713}`

### 4.2 `GET /tasks`

Trả `200`: mảng các công việc có `isDeleted = false`, sắp xếp mới cập nhật trước.

### 4.3 `POST /tasks` (tạo mới)

Body: `id` và `title` là bắt buộc; `description`, `isCompleted`, `isDeleted` là tùy chọn.

```json
{ "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7", "title": "Làm bài tập", "description": "" }
```

| Mã | Khi nào |
|---|---|
| `201` | Tạo thành công, body là công việc vừa tạo (`version = 1`) |
| `200` | **`id` đã tồn tại**: trả về bản đã có, không tạo trùng (an toàn khi ứng dụng gửi lại do mất phản hồi) |
| `400` | Thiếu `id` hoặc `title`, hoặc JSON không hợp lệ |

### 4.4 `PUT /tasks/{id}` (sửa)

Body: các trường cần sửa (`title`, `description`, `isCompleted`, `isDeleted`) cùng **`baseVersion`** (phiên bản ứng dụng đang giữ). Trường nào không gửi thì giữ nguyên. Thêm `"force": true` để ghi đè bất chấp xung đột.

```json
{ "title": "Làm bài tập 2", "isCompleted": true, "baseVersion": 1 }
```

| Mã | Khi nào |
|---|---|
| `200` | Sửa thành công, `version` tăng 1. Cũng trả `200` (không tăng version) nếu nội dung gửi lên **trùng với bản đang có**, tức là yêu cầu bị gửi lại |
| `409` | `baseVersion` khác `version` hiện tại trên server (đã có nơi khác sửa trước). Body: `{"error": "Xung đột phiên bản", "server": { ...bản hiện tại trên server... }}`. Ứng dụng xử lý theo chính sách xung đột |
| `404` | Không có công việc với `id` này |

### 4.5 `DELETE /tasks/{id}` (xóa logic)

Đặt `isDeleted = true` và tăng `version`. **Không kiểm tra phiên bản**, tức là yêu cầu xóa luôn được chấp nhận.

| Mã | Khi nào |
|---|---|
| `200` | Đã xóa. Xóa lại lần nữa vẫn trả `200` (không tăng version) |
| `404` | Không có công việc với `id` này |

### 4.6 `GET /tasks/changes?since={ms}` (đồng bộ tăng dần)

Trả mọi công việc có `updatedAt > since`, **kể cả công việc đã xóa** (`isDeleted = true`), sắp xếp theo `updatedAt` tăng dần. Lần đồng bộ đầu tiên dùng `since=0`.

```json
{
  "serverTime": 1793617527713,
  "changes": [ { "id": "...", "title": "...", "isDeleted": false, "updatedAt": 1793617527713, "version": 1 } ]
}
```

`serverTime` là `updatedAt` của thay đổi cuối cùng (hoặc đúng `since` nếu không có thay đổi). Ứng dụng lưu giá trị này và gửi làm `since` cho lần sau. Không dùng giờ của điện thoại để tránh lệch đồng hồ.

## 5. Cơ chế đảm bảo đồng bộ đúng

| Vấn đề | Cách xử lý |
|---|---|
| Gửi lại yêu cầu tạo (mất phản hồi, thử lại) | `id` do ứng dụng sinh; `POST` với `id` đã có trả `200` và bản cũ, không tạo trùng |
| Gửi lại yêu cầu sửa | Nếu nội dung đã được áp dụng thì trả `200`, không tăng `version` |
| Gửi lại yêu cầu xóa | Xóa lặp vẫn `200`, không đổi dữ liệu |
| Hai thiết bị cùng sửa một công việc | So `baseVersion` với `version` trên server; lệch thì trả `409` kèm bản của server |
| Thiết bị khác cần biết công việc đã bị xóa | Xóa logic: bản ghi còn lại với `isDeleted = true` và xuất hiện trong `changes` |
| Không bỏ sót thay đổi khi lấy tăng dần | `updatedAt` luôn tăng chặt giữa các lần ghi, nên không có hai thay đổi trùng mốc |

## 6. Dữ liệu mẫu

File `sample_data.json` có 5 công việc với `id` cố định. Nạp vào server (đang chạy) bằng:

```
node seed.js
```

Chạy lại nhiều lần không tạo trùng (kết quả `tạo mới 0, đã có sẵn 5`).

## 7. Ví dụ thử bằng curl hoặc Postman

```
curl http://localhost:3000/tasks

curl -X POST http://localhost:3000/tasks -H "Content-Type: application/json" ^
     -d "{\"id\":\"task-001\",\"title\":\"Hoc bai\"}"

curl -X PUT http://localhost:3000/tasks/task-001 -H "Content-Type: application/json" ^
     -d "{\"title\":\"Hoc bai 2\",\"baseVersion\":1}"

curl -X DELETE http://localhost:3000/tasks/task-001

curl "http://localhost:3000/tasks/changes?since=0"
```

(Dấu `^` là ký tự xuống dòng của cmd Windows. Trên PowerShell dùng dấu `` ` ``, hoặc viết cả lệnh trên một dòng. Trong Postman chọn Body, raw, JSON và dán nội dung JSON.)

## 8. Kiểm thử

Với server đang chạy, mở một terminal khác:

```
node test_server.js
```

Script chạy 17 kiểm tra tự động (tạo mới, gửi lại không trùng, xung đột 409, ghi đè, xóa logic, đồng bộ tăng dần, tạo 100 bản ghi) và ghi kết quả vào `test_results.txt`. Các mục kiểm tra được đánh nhãn theo mã kịch bản của đề (TC01, TC06, TC07, TC08, TC09).

## 9. Cấu trúc thư mục

| File | Nội dung |
|---|---|
| `server.js` | Mã nguồn server |
| `package.json` | Thông tin dự án (không có thư viện phụ thuộc) |
| `sample_data.json` | Dữ liệu mẫu |
| `seed.js` | Nạp dữ liệu mẫu vào server |
| `test_server.js` | Kiểm thử tự động |
| `tasks.db` | Cơ sở dữ liệu (tự tạo khi chạy, không đưa lên Git) |

## 10. Giới hạn

- Chưa có xác thực người dùng: mọi thiết bị dùng chung một danh sách công việc.
- `DELETE` luôn thắng `PUT`: nếu một thiết bị xóa trong khi thiết bị khác sửa thì bản sửa bị mất.
- Chạy HTTP không mã hóa, chỉ phù hợp mạng nội bộ khi thử nghiệm.
