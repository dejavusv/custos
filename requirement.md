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
- ปรับแก้ tab แรกเป็น Active & Configured Pipelines เมื่อคลิกที่ Edit ค่อยไปที่หน้า Visual Workflow Builder
- ปรับแก้โครงสร้างของ Pipeline เพิ่ม Node Start สำหรับเริ่มงาน และ Node Stop สำหรับงานที่จบแล้ว โดยที่ใน 1 Pipeline จะมี Node Start แค่ 1 อันเท่านั้น 

- ตอนสร้างเส้นเชื่อมระหว่าง Task ให้เลือกได้สองเงื่อนไขคือ On Success และ On failed โดยที่ On failed จะแสดง Default เป็น Error Message ที่เกิดจาก Task ก่อนหน้ามาใช้งานได้

แก้ไขหน้า  Pipeline Orchestration & Scheduler
- เพิ่มการกรอกชื่อของ pipeline ใด้ 
- ปรับแก้ Cron ให้กำหนดเวลาในการรันได้ดังนี้
    - ระบุได้ว่าเป็นรายวันหรือรายสัปดาห์
        - รายวัน สามารถระบุเวลาในการรันได้
        - รายสัปดาห์ สามารถระบุวัน(จันทร์ถึงอาทิตย์)กับเวลาในการรันได้
- แต่ละ task เพิ่มเมนูให้สามารถ move task ไปที่ pipeline อื่นได้ โดยสามารถกด Ctrl ค้างไว้เพื่อเลือกหลาย Task แล้วย้ายไปพร้อมกันได้ และให้เอา line ที่เชื่อมแต่ละ Task ไปด้วย(ถ้ามี)

ปรับการแสดงผลทุกหน้าจอให้รองรับการแสดงผลบนมือถือโดยมีข้อกำหนดดังนี้
- เมนูให้ Default แสดงแบบย่อเมื่อเข้าด้วยมือถือ
- การแสดงผลในตารางให้แสดงภายในตารางและสามารถปรับ content ให้พอดีกับหน้าจอโดยที่ไม่ต้องเลื่อนจอซ้ายขวา(ถ้าทำได้)

