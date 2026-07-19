# DeltaForceStrike

## 友谊之约：反制行动

基于 PaperMC 的 3v3/5v5 回合制战术竞技插件

![PaperMC](https://img.shields.io/badge/PaperMC-Plugin-green)
![Java](https://img.shields.io/badge/Java-21%2B-orange)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-blue)

---

## 📖 简介

**DeltaForceStrike（友谊之约：反制行动）** 是一个基于 **PaperMC** 的 Minecraft 回合制战术竞技插件。

插件融合了：

- Counter-Strike 式经济与爆破回合
- Minecraft 原生战斗体系
- 固定竞技物品栏
- 战术投掷物系统
- 可扩展干员接口

玩家将在独立竞技世界中进行 **3v3/5v5 爆破对抗**。

> 设计参考：Counter-Strike、无畏契约、三角洲行动  
> 核心理念：枪法决定胜负，道具创造机会，经济决定长期优势。

---

# ✨ 功能特性

## 🎮 回合制竞技系统

DeltaForceStrike 将完整竞技流程整合为状态驱动系统：

```

进入竞技世界
↓
加入 Match
↓
选择阵营
↓
购买阶段
↓
战斗阶段
↓
爆破 / 歼灭
↓
回合结算
↓
下一回合

```

支持：

- 单竞技世界运行
- 单 Match 生命周期管理
- 回合状态控制
- 半场换边
- 比分统计
- 胜利结算
- Title / Scoreboard UI

---

# ⚔️ 阵营系统

## T（进攻方）

目标：

- 携带并安装改造 TNT
- 保护 TNT 至爆炸
- 消灭 CT


## CT（防守方）

目标：

- 阻止 TNT 安装
- 拆除 TNT
- 消灭 T

---

# 💰 经济系统

参考 CS 系列经济机制。

支持：

- 初始资金
- 回合奖励
- 胜负奖励
- 连败补偿
- 击杀奖励
- 安包奖励
- 拆包奖励

资金用于购买：

| 类型 | 内容    |
|----|-------|
| 武器 | 近战、远程 |
| 防御 | 护甲    |
| 工具 | 拆除钳   |
| 道具 | 战术投掷物 |
| 扩展 | 技能槽位  |

---

# 🛒 商店系统

Minecraft GUI 商店。

支持：

- 箱子 GUI
- 自定义物品
- 自定义价格
- 自定义槽位
- 自定义动作

---

# 💣 爆破系统

支持完整爆破流程：

- 随机炸弹携带者
- A / B 包点
- 安装读条
- 引信倒计时
- CT 拆除
- 拆除钳加速
- 爆炸范围伤害

规则：

```

T:
安装 TNT 并保护

CT:
拆除 TNT 或消灭 T

```

爆炸：

- 不破坏地图
- 支持范围伤害衰减

---

# 🧨 战术道具

当前支持：

| 道具   | 效果      |
|------|---------|
| 烟雾   | 阻挡视野    |
| 凋零区域 | 持续区域效果  |
| 高爆手雷 | 范围伤害与击退 |

---

# 🎒 固定物品栏

竞技模式使用固定热键槽：

| 槽位  | 用途        |
|-----|-----------|
| 1   | 近战武器      |
| 2   | 远程武器      |
| 3   | TNT / 拆除钳 |
| 4-6 | 战术道具      |
| 7-9 | 技能槽位      |

系统自动管理：

- 装备发放
- 非法物品限制
- 道具堆叠限制

---

# 👁 死亡与观战

## 战斗阶段

支持：

- 伪死亡
- 武器掉落
- 自动进入旁观


## 购买阶段

支持：

- 取消死亡
- 返回出生点
- 恢复比赛状态


观战支持：

- 队友观战限制
- 目标切换

---

# 🏗 服务器架构

推荐部署方式：

```

大厅服务器
|
| 匹配 / 传送
↓
游戏子服

```
    |
    ↓
```

delta_force_strike 世界

```
    |
    ↓
```

DeltaForceStrike

````

当前版本：

- 单竞技世界
- 单 Match
- 无数据库依赖
- 无跨服匹配逻辑

大厅负责：

- 玩家匹配
- 玩家传送
- 服务器调度

本插件负责：

- 单场游戏运行
- 回合逻辑
- 战斗流程

---

# 📦 安装

## 环境要求

| 项目        | 要求     |
|-----------|--------|
| Minecraft | 26.2+  |
| Server    | Paper  |
| Java      | 21+    |
| 世界        | 独立竞技世界 |

---

## 安装步骤

### 1. 构建插件

```bash
./gradlew build
````

Windows：

```bash
gradlew build
```

生成：

```
build/libs/DeltaForceStrike-*.jar
```

---

### 2. 安装插件

复制：

```
DeltaForceStrike-*.jar
```

到：

```
plugins/
```

启动服务器。

---

### 3. 配置

配置目录：

```
plugins/DeltaForceStrike/
```

包含：

```
config.yml
items.yml
```

---

# 🗺️ 地图设置

默认竞技世界：

```
delta_force_strike
```

设置出生点：

```
/dfs setspawn queue
/dfs setspawn t
/dfs setspawn ct
```

设置包点：

```
/dfs setsite a
/dfs setsite b
```

---

# 🚀 快速测试

修改：

```yaml
queue:
  team-size: 1
  max-players: 2
  countdown-seconds: 15
  cancel-if-not-full: true
```

流程：

1. 玩家进入竞技世界
2. 自动加入 Match
3. 选择 T / CT
4. 购买装备
5. 开始回合

管理员测试：

```
/dfs start
```

---

# 📜 命令

| 命令                  | 说明    | 权限                     |
|---------------------|-------|------------------------|
| `/dfs join`         | 加入比赛  | deltaforcestrike.use   |
| `/dfs leave`        | 离开比赛  | deltaforcestrike.use   |
| `/dfs team <t\|ct>` | 选择阵营  | deltaforcestrike.use   |
| `/dfs shop`         | 打开商店  | deltaforcestrike.use   |
| `/dfs info`         | 查看状态  | deltaforcestrike.use   |
| `/dfs guide`        | 游戏指南  | deltaforcestrike.use   |
| `/dfs agent <id>`   | 干员接口  | deltaforcestrike.use   |
| `/dfs start`        | 强制开始  | deltaforcestrike.admin |
| `/dfs stop`         | 停止比赛  | deltaforcestrike.admin |
| `/dfs reload`       | 重载配置  | deltaforcestrike.admin |
| `/dfs give <id>`    | 获取物品  | deltaforcestrike.admin |
| `/dfs setspawn`     | 设置出生点 | deltaforcestrike.admin |
| `/dfs setsite`      | 设置包点  | deltaforcestrike.admin |

别名：

```
/delta
/fp
```

权限：

```
deltaforcestrike.use
deltaforcestrike.admin
```

---

# ⚙️ 配置

主配置：

```
plugins/DeltaForceStrike/config.yml
```

主要节点：

| 配置                      | 说明   |
|-------------------------|------|
| `world.arena`           | 竞技世界 |
| `match.*`               | 比赛规则 |
| `round.*`               | 回合时间 |
| `economy.*`             | 经济系统 |
| `bomb.*`                | 爆破系统 |
| `grenade.*`             | 战术道具 |
| `spectator.*`           | 观战设置 |
| `player.shield-enabled` | 护盾开关 |
| `debug.enabled`         | 调试日志 |

物品配置：

```
items.yml
```

支持：

* type
* slot
* action
* enchantment

---

# 📁 项目结构

```
org.starset.deltaforcestrike

├── DeltaForceStrike.java

├── manager/
│   └── GameManager.java

├── match/
│   ├── Match.java
│   ├── MatchManager.java
│   ├── MatchState.java
│   ├── PlayerSession.java
│   └── Team.java

├── round/
│   ├── RoundManager.java
│   └── RoundState.java

├── bomb/
│   └── BombManager.java

├── grenade/
│   └── GrenadeService.java

├── shop/
│   ├── ShopGUI.java
│   ├── ShopHolder.java
│   └── ShopListener.java

├── item/
│   ├── GameItem.java
│   ├── ItemGiveService.java
│   ├── ItemKeys.java
│   └── ItemManager.java

├── listener/

├── scoreboard/

├── spectator/

├── game/

├── command/

└── util/
```

---

# 🔧 开发设计

## 状态驱动

比赛：

```
MatchState
```

负责：

* 等待
* 进行
* 结束

回合：

```
RoundState
```

负责：

* 购买
* 战斗
* 结算

---

## 模块职责

| 模块       | 职责          |
|----------|-------------|
| Match    | 管理整场比赛      |
| Round    | 管理单个回合      |
| Listener | Bukkit 事件入口 |
| Service  | 游戏逻辑        |
| Manager  | 生命周期管理      |
| Util     | 通用工具        |

---

## 开发约定

竞技逻辑仅在：

```
Worlds.isArena(player)
```

返回 true 时执行。

实体标记使用：

```
PersistentDataContainer
```

避免：

```
FixedMetadataValue
```

Listener 只负责事件接入，核心逻辑由 Manager / Service 管理。

---

# 🚧 Roadmap

* [ ] 干员系统
* [ ] 技能系统
* [ ] 数据统计
* [ ] 排位系统
* [ ] 多房间 Match
* [ ] 跨服匹配
* [ ] 回放系统

---

# 🎯 总结

DeltaForceStrike 将经典爆破竞技玩法带入 Minecraft。

准备竞技世界：

```
delta_force_strike
```

设置地图：

```
/dfs setspawn
/dfs setsite [a/b]
```

即可开始一场 Minecraft 战术爆破比赛。