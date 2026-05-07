# 房间密码功能实现说明

## 📋 功能概述

为 Poker 项目添加了房间密码功能，房主创建房间时可选择设置密码，有密码的房间需要输入正确密码才能加入。

---

## 🗄️ 数据库修改

### 1. 执行迁移脚本

```bash
mysql -u root -p poker < src/main/resources/migration_add_room_password.sql
```

**修改内容**：
- `poker_room` 表新增 `password` 字段（VARCHAR(50)，NULL 表示无密码）

---

## 🔧 后端修改

### 1. 实体类更新

**Room.java**：
```java
private String password; // 房间密码，NULL表示无密码
```

**RoomDTO.java**：
```java
private Boolean hasPassword; // 是否有密码（不返回密码明文）
```

### 2. Service 层修改

**RoomService.java**：

- `createRoom(Long userId, String password)` - 支持创建带密码的房间
- `joinRoom(String roomId, Long userId, String password)` - 加入时验证密码

**密码验证逻辑**：
```java
if (room.getPassword() != null && !room.getPassword().isEmpty()) {
    if (password == null || password.isEmpty()) {
        throw new RuntimeException("该房间需要密码才能加入");
    }
    if (!room.getPassword().equals(password)) {
        throw new RuntimeException("房间密码错误");
    }
}
```

**RoomQueryService.java**：
- `buildRoomDTO` 方法中设置 `hasPassword` 字段（不返回密码明文）

### 3. Controller 层修改

**AuthController.java**：

- `createRoom` 接口新增 `@RequestParam(required = false) String password`
- `joinRoom` 接口新增 `@RequestParam(required = false) String password`

---

## 🎨 前端修改

### 1. 主页 index.html

**创建房间**：
- 新增密码输入框（可选）
- 占位符："设置房间密码（可选）"

**加入房间**：
- 新增密码输入框
- 占位符："房间密码（如有）"

**房间列表**：
- 有密码的房间显示 🔒 图标（amber-500 色）
- 鼠标悬停显示 "密码保护" 提示

**发现房间**：
- 点击房间卡片弹出模态框
- 如果房间有密码，显示密码输入框
- 如果房间无密码，直接提交

### 2. 模态框功能

```javascript
function showJoinModal(roomId, hasPassword) {
    // 根据 hasPassword 决定是否显示密码输入框
    // 有密码：显示输入框，required=true
    // 无密码：隐藏输入框，required=false
}
```

---

## 🎯 使用流程

### 创建带密码的房间

1. 访问主页
2. 在"创建新房间"下方的密码框输入密码（可选）
3. 点击"创建新房间"
4. 房间创建成功，进入房间

### 加入有密码的房间

**方式一：通过房间号加入**
1. 在"加入房间"输入框输入 6 位房间号
2. 在"房间密码"输入框输入密码
3. 点击"加入"

**方式二：通过发现房间列表**
1. 在"发现房间"列表中点击有 🔒 图标的房间
2. 弹出模态框，显示密码输入框
3. 输入密码后点击"加入"

### 加入无密码的房间

- 直接输入房间号，密码留空，点击"加入"
- 或在发现房间列表中点击无 🔒 图标的房间，直接加入

---

## 🔒 安全说明

1. **密码存储**：明文存储（适合轻量级项目，生产环境建议加密）
2. **密码传输**：通过 POST 请求传输，HTTPS 环境下安全
3. **密码显示**：
   - 前端使用 `type="password"` 隐藏输入内容
   - 后端 DTO 只返回 `hasPassword` 布尔值，不返回密码明文
4. **密码验证**：简单字符串比较，区分大小写

---

## ⚠️ 注意事项

1. **密码可选**：创建房间时密码字段为 `required=false`，不填则房间无密码
2. **密码长度**：数据库限制 50 字符，前端未做长度校验
3. **密码找回**：当前不支持密码找回功能
4. **密码修改**：当前不支持创建后修改密码

---

## 🚀 测试建议

1. 创建无密码房间，验证能否直接加入
2. 创建有密码房间，验证：
   - 不输入密码时提示"该房间需要密码才能加入"
   - 输入错误密码时提示"房间密码错误"
   - 输入正确密码时成功加入
3. 验证房间列表中 🔒 图标显示正确
4. 验证发现房间列表的模态框逻辑

---

## 📝 后续优化建议

1. **密码加密**：使用 BCrypt 加密存储密码
2. **密码强度**：添加密码长度和复杂度校验
3. **密码修改**：房主可在房间内修改密码
4. **密码提示**：支持设置密码提示问题
5. **防爆破**：添加密码错误次数限制
