# Poker 项目表名前缀改造说明

## 📋 改造内容

### 1. 数据库层面

#### 表名修改（schema.sql）
所有表名已添加 `poker_` 前缀：
- `user` → `poker_user`
- `persistent_logins` → `poker_persistent_logins`
- `room` → `poker_room`
- `room_player` → `poker_room_player`
- `game` → `poker_game`
- `game_player` → `poker_game_player`
- `action_log` → `poker_action_log`
- `transfer_log` → `poker_transfer_log`
- `borrow_log` → `poker_borrow_log`

#### MyBatis-Plus 全局配置
```yaml
mybatis-plus:
  global-config:
    db-config:
      table-prefix: poker_
```
✅ 配置后，所有Entity的`@TableName`注解会自动加上前缀，无需修改Entity代码！

---

### 2. Security认证层面

#### 问题分析
Spring Security的`JdbcTokenRepositoryImpl`硬编码了表名`persistent_logins`，无法通过全局前缀配置适配。

#### 解决方案
创建自定义的Remember-Me实现：

**新增文件：**
1. ✅ `PersistentLogin.java` - Remember-Me实体类
2. ✅ `PersistentLoginMapper.java` - MyBatis Mapper接口
3. ✅ `CustomPersistentTokenRepository.java` - 自定义Token Repository

**修改文件：**
- ✅ `SecurityConfig.java` - 使用自定义Repository替代`JdbcTokenRepositoryImpl`

---

### 3. 业务层面

#### Entity类
所有Entity的`@TableName`注解保持不变：
```java
@TableName("user")  // 实际映射到 poker_user（因为配置了table-prefix）
public class User { ... }
```

✅ **无需修改任何Entity代码！**

#### Mapper类
所有Mapper接口无需修改，MyBatis-Plus会自动处理表前缀。

---

## 🚀 部署步骤

### 方式1: 新环境（推荐）

直接使用新的`schema.sql`创建数据库，所有表名都带前缀。

```bash
# 执行schema.sql
mysql -u root -p < src/main/resources/schema.sql
```

### 方式2: 现有环境迁移

如果数据库已存在旧表，使用迁移脚本：

```bash
# 执行迁移脚本
mysql -u root -p < src/main/resources/migration_rename_tables.sql
```

迁移脚本会：
1. 重命名所有旧表为新表名
2. 验证表是否都已重命名
3. 检查是否还有旧表残留

---

## ✅ 验证清单

部署后请验证以下内容：

- [ ] 数据库中存在所有带`poker_`前缀的表
- [ ] 旧表名不存在（如果执行了迁移）
- [ ] 用户登录功能正常
- [ ] Remember-Me功能正常（关闭浏览器后仍保持登录）
- [ ] 创建房间功能正常
- [ ] 游戏进行功能正常
- [ ] 历史记录查询正常

---

## 🔍 技术细节

### 为什么Entity的@TableName不需要改？

MyBatis-Plus的`table-prefix`配置会在运行时自动为表名添加前缀：

```java
// Entity定义
@TableName("user")
public class User { }

// 实际执行的SQL
SELECT * FROM poker_user WHERE ...
```

### 为什么Security需要特殊处理？

Spring Security的`JdbcTokenRepositoryImpl`内部硬编码了SQL语句：

```java
// JdbcTokenRepositoryImpl 源码
private static final String CREATE_TABLE_SQL = 
    "create table persistent_logins (...)";
    
private static final String INSERT_TOKEN_SQL = 
    "insert into persistent_logins (...)";
```

无法通过配置修改表名，因此必须自定义实现。

---

## ⚠️ 注意事项

1. **数据备份**：执行迁移前务必备份数据库
2. **停机时间**：迁移过程需要短暂停机
3. **测试环境**：建议先在测试环境验证
4. **回滚方案**：保留旧表直到新表验证通过

### 回滚脚本（如需）

```sql
RENAME TABLE `poker_user` TO `user`;
RENAME TABLE `poker_persistent_logins` TO `persistent_logins`;
-- ... 其他表同理
```

---

## 📊 影响范围

| 模块 | 是否修改 | 说明 |
|------|---------|------|
| Entity类 | ❌ 否 | 全局前缀自动处理 |
| Mapper接口 | ❌ 否 | MyBatis-Plus自动处理 |
| Service层 | ❌ 否 | 无影响 |
| Security配置 | ✅ 是 | 自定义Remember-Me实现 |
| schema.sql | ✅ 是 | 表名加前缀 |

---

## 🎯 改造优势

1. ✅ **避免表名冲突**：与其他项目共用数据库时不会冲突
2. ✅ **统一管理**：所有表名前缀一致，便于识别和管理
3. ✅ **代码简洁**：Entity无需修改，保持代码整洁
4. ✅ **易于维护**：全局配置，一处修改全局生效
