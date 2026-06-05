# FixBid push notifications — end-to-end setup

Bật push thật cho FixBid gồm 3 phần ngoài codebase:

1. Tạo Firebase project + lấy `google-services.json` cho client.
2. Áp migration `0006_fcm_tokens_unique.sql` trên Supabase.
3. Deploy Edge Function `push-notification` + tạo Database Webhook.

Sau khi xong, mọi dòng mới trong bảng `notifications` sẽ tự động đẩy push tới
mọi thiết bị của user đó — kể cả khi app đã đóng/bị kill.

---

## 1) Firebase project + google-services.json

1. Vào <https://console.firebase.google.com> → **Add project** → đặt tên (ví dụ
   `fixbid`). Bật/tắt Analytics tuỳ ý.
2. Trong project, **Add app → Android**:
   - Package name: `com.example.fixbid` (đúng namespace của app).
   - SHA-1 không bắt buộc cho FCM, có thể bỏ qua.
3. Tải `google-services.json`. Đặt vào thư mục `app/`:

   ```
   FixBid/app/google-services.json
   ```

4. Build lại app. `app/build.gradle.kts` đã có đoạn:

   ```kotlin
   if (googleServicesJson.exists()) {
       apply(plugin = "com.google.gms.google-services")
   }
   ```

   nên việc đặt file vào sẽ tự kích hoạt plugin và FCM sẽ nhận token thật.

5. Trong Firebase Console → **Project settings → Service accounts → Generate new
   private key**. Tải file JSON. Bạn cần 3 trường từ nó cho bước 3:
   - `project_id`
   - `client_email`
   - `private_key` (giữ nguyên các `\n`)

   Đừng commit file này — chỉ dùng để cấu hình Edge Function.

---

## 2) Supabase migration

Áp file `supabase/migrations/0006_fcm_tokens_unique.sql`:

- **Cách A — CLI:**

  ```bash
  supabase db push
  ```

- **Cách B — Dashboard:**
  Vào Supabase → SQL Editor → paste nội dung file → Run.

Migration này:
- Thêm `UNIQUE (token)` cho `fcm_tokens` (cần cho upsert của client).
- Thêm cột `updated_at` + trigger tự cập nhật.
- Bật RLS để mỗi user chỉ thấy/sửa token của mình.

Sau khi áp xong, đăng nhập lại app — token mới sẽ được upsert đúng cách. Có thể
kiểm tra bằng SQL:

```sql
select user_id, left(token, 12) as token_prefix, updated_at
from public.fcm_tokens
order by updated_at desc;
```

---

## 3) Edge Function + Database Webhook

### 3.1. Cài Supabase CLI nếu chưa có

```bash
npm i -g supabase
supabase login
supabase link --project-ref <your-project-ref>
```

`<your-project-ref>` lấy ở Supabase → Project settings → General → "Reference ID".

### 3.2. Set secrets cho Edge Function

```bash
supabase secrets set FIREBASE_PROJECT_ID="<project_id>"
supabase secrets set FIREBASE_CLIENT_EMAIL="<client_email>"
# Bọc private_key trong 'single quotes' để giữ \n nguyên, KHÔNG dùng "double".
supabase secrets set FIREBASE_PRIVATE_KEY='-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n'
```

`SUPABASE_URL` và `SUPABASE_SERVICE_ROLE_KEY` được Supabase runtime tự inject — không cần set.

### 3.3. Deploy function

```bash
supabase functions deploy push-notification --no-verify-jwt
```

`--no-verify-jwt` cần thiết vì Database Webhook gọi không kèm JWT người dùng.
Function vẫn được bảo vệ bằng webhook secret (xem 3.4).

URL function sẽ có dạng:
`https://<project-ref>.supabase.co/functions/v1/push-notification`

### 3.4. Tạo Database Webhook

Supabase → **Database → Webhooks → Create a new hook**:

- **Name:** `push_on_notification_insert`
- **Table:** `public.notifications`
- **Events:** chỉ tick **Insert**
- **Type:** `Supabase Edge Functions`
- **Edge Function:** `push-notification`
- **HTTP Method:** `POST`
- **HTTP Headers:** mặc định (Authorization Bearer service-role key đã có sẵn)

Save.

### 3.5. Smoke test

Tạo một notification test trên Supabase SQL Editor (thay `<your-uuid>`):

```sql
insert into public.notifications (user_id, title, body, type, reference_id)
values (
  '<your-uuid>',           -- userId của tài khoản đang đăng nhập trên thiết bị thật
  'Test FCM',
  'Nếu bạn đọc được dòng này khi app đã đóng thì FCM hoạt động ✅',
  'system',
  null
);
```

Trong vòng 1–2 giây, thiết bị sẽ rung + hiện thông báo từ kênh `fixbid_bookings`.
Đóng/kill app → tạo thêm notification → thông báo vẫn tới.

Theo dõi log:

```bash
supabase functions logs push-notification --tail
```

Mỗi lần gửi sẽ log JSON dạng:

```json
{ "target_user": "...", "tried": 1, "sent": 1, "cleaned": 0 }
```

---

## Sự cố thường gặp

| Triệu chứng | Nguyên nhân | Cách xử |
|---|---|---|
| `tried: 0, reason: "no tokens"` | App chưa upsert token (chưa đăng nhập / chưa cấp quyền POST_NOTIFICATIONS) | Mở app, đăng nhập, cấp quyền thông báo trên Android 13+ |
| HTTP 401 từ FCM | `FIREBASE_CLIENT_EMAIL` / `FIREBASE_PRIVATE_KEY` sai | Kiểm tra lại secrets, đặc biệt `\n` trong private_key |
| HTTP 404 + `UNREGISTERED` | Token cũ (gỡ app/đổi user) | Function tự xoá token chết; gửi lần sau sẽ ổn |
| Push tới khi app foreground bị trùng (Realtime + FCM) | Bình thường — cả hai kênh đều bắn | Có thể bỏ qua FCM khi app đang foreground bằng cách kiểm tra `ProcessLifecycleOwner` trong service nếu muốn |
| Thiếu icon trắng nhỏ | Manifest đã trỏ `ic_stat_notification`; nếu thay icon nhớ giữ tên hoặc đổi meta-data trong Manifest | — |

---

## Mô hình tin nhắn (cho ai muốn tự gọi FCM)

Function gửi **data message** (không có khối `notification`) với keys:

| key | giá trị |
|---|---|
| `notification_id` | `id` của hàng trong `notifications` (dùng để dedupe) |
| `title` | tiêu đề |
| `body` | nội dung |
| `type` | snake_case của `NotificationType` (vd `booking_confirmed`) |
| `reference_id` | thường là `bookingId`, `reviewId`... để deep-link |

Client (`FixBidMessagingService`) sẽ tự dựng heads-up notification, dùng đúng
kênh + màu + tuỳ chọn âm thanh/rung của user.
