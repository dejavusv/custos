แก้ไขหน้า Task & Backup Engine Hub 
เพิ่ม Dialog แสดงไฟล์และ folder ตาม path storage ใน Server เพื่อให้สามารกำหนด path ในการจัดเก็บได้หรือเลือกไฟล์และ folder มาใช้ในหน้าจอ
โดยให้ตั้งต้นที่ custos.backup.default-directory

แก้ไขหน้า Dashboard
- เอา สถานะความคืบหน้าของโครงการ (Development Roadmap) ออก
- เอา System Status, Security Engine, Active User, Environment ออก
- เพิ่มประวัติ Execution History & Audit 10 รายการล่าสุด
- เพิ่มการแสดงจำนวน Pipelines ในระบบ และแสดงวันเวลาที่ทำงานล่าสุดและผลการรัน

แก้ไขหน้า Task & Backup Engine Hub และ Pipeline Orchestration & Scheduler
- เพิ่ม Task Line Notification โดยดูจากไฟล์ที่แนบมา และตั้งค่าใน Task ได้ดังนี้
    - ระบุ Domain ที่เรียกใช้งาน และ EXTERNAL_NOTIFY_AUTH_TOKEN จากนั้นจะสามารถกดปุ่ม Connect เพื่อดึงรายการ ดูรายการ Task ที่เปิดรับการแจ้งเตือนจากภายนอก มาแสดงได้
    - ระบุรายการ Task ที่สามารถเรียกใช้งานได้
    - ระบุ Message ที่จะส่งแจ้งเตือนได้
    - สามารถกดปุ่ม Call เพื่อส่งข้อความได้



แก้ไขหน้า Pipeline Orchestration & Scheduler
- 