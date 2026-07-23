# DeltaForceStrike

## 友谊之约：反制行动

基于 PaperMC 的 5v5/3v3 回合制战术竞技插件

![PaperMC](https://img.shields.io/badge/PaperMC-Plugin-green)
![Java](https://img.shields.io/badge/Java-25%2B-orange)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2-blue)

---

## 📖 简介

**DeltaForceStrike（友谊之约：反制行动）** 是一个基于 **PaperMC** 的 Minecraft 回合制战术竞技插件。

插件融合了：

- Counter-Strike 式经济与爆破回合
- Valorant 式干员与技能体系
- Minecraft 原生战斗体系
- 固定竞技物品栏
- 战术投掷物系统
- CS2 Major 风格 Live Overlay（HTTP API）

玩家将在独立竞技世界中进行 **5v5 / 3v3 爆破对抗**。

> 设计参考：Counter-Strike、无畏契约、三角洲行动  
> 核心理念：枪法决定胜负，道具创造机会，经济决定长期优势。

---

## ✨ 功能特性

## 🎮 回合制竞技系统

DeltaForceStrike 将完整竞技流程整合为状态驱动系统：

```
进入竞技世界
↓
加入 Match
↓
选择阵营（T / CT）
↓
干员选择（可选）
↓
购买阶段
↓
战斗阶段
↓
爆破 / 歼灭
↓
回合结算
↓
下一回合（半场换边）
```

支持：

- 单竞技世界运行
- 单 Match 生命周期管理
- 回合状态控制
- 半场换边（攻防互换、金钱重置、大招充能清零）
- 比分统计
- 胜利结算
- Title / Scoreboard / TabList / Nametag / ActionBar UI
- 客户端 HUD 同步（Fabric ClientUI 兼容）

---

## ⚔️ 阵营系统

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

## 💰 经济系统

参考 CS 系列经济机制。

支持：

- 初始资金（默认 $800）
- 回合奖励
- 胜负奖励（胜 $3200，败分档 $2000/$2550/$3100）
- 连败补偿
- 手枪局额外奖励（$2550）
- 击杀奖励（$300）
- 安包奖励（$300）
- 拆包奖励（$200）
- CT 死亡补偿（每存活 T +$100）

资金用于购买：

| 类型 | 内容 |
|----|------|
| 武器 | 近战、远程 |
| 防御 | 护甲、盾牌（可选） |
| 工具 | 拆除钳 |
| 道具 | 战术投掷物（烟雾/凋零/高爆） |
| 扩展 | 干员技能槽位（招牌/可购/大招） |

经济上限：$16000

---

## 🛒 商店系统

Minecraft GUI 商店（箱子界面）。

支持：

- 自定义物品
- 自定义价格
- 自定义槽位
- 自定义动作（给予/执行命令/打开子菜单）
- 远程武器自动补箭（上限可配）

---

## 💣 爆破系统

支持完整爆破流程：

- 随机炸弹携带者（每回合仅 1 名 T）
- A / B 两个包点（可配置坐标与半径）
- 安装读条（默认 3 秒，移动打断）
- 引信倒计时（默认 40 秒，音符盒滴滴声，末段加速扩大半径）
- CT 拆除（潜行触发，空手 10s / 拆弹钳 5s，移动打断）
- 爆炸范围伤害（不破坏地形，支持衰减）
- 包点浮动文字标记（可开关、偏移、缩放）

规则：

```
T:
安装 TNT 并保护

CT:
拆除 TNT 或消灭 T
```

---

## 🧨 战术道具

当前支持：

| 道具 | 效果 | 配置项 |
|------|------|--------|
| 烟雾弹 | 阻挡视野，高粒子，可选黑暗效果 | `grenade.smoke.*` |
| 凋零弹 | 区域持续凋零效果 | `grenade.wither.*` |
| 高爆手雷 | 范围伤害 + 击退，可选自伤/点火 | `grenade.incendiary.*` |

粒子倍率可配（`grenade.particle-multiplier`）

---

## 👮 干员系统

4 位干员，各类型 1 位，技能体系：**被动 + 招牌技能 + 可购技能 + 大招**

### 妮可 Niko — 突击
| 槽位 | 技能 | 说明 |
|------|------|------|
| 被动 | 快人一步！ | 永久速度 I |
| 招牌 (7) | **DASH！** | 前向位移，60s CD，击杀 -30s，难以上房 |
| 可购 (8) | **我不打了！** | 应急避险信标，可传送 1 次，2 次购买 |
| 大招 (9) | **狂起来吧！** | 本回合速度 II + 跳跃 I + 抗性 I + 力量 I，消耗 4 点 |

### 布若 Bruo — 工程
| 槽位 | 技能 | 说明 |
|------|------|------|
| 被动 | 久经沙场 | 永久抗性 I |
| 招牌 (7) | **老贝榨** | 喷溅瞬间伤害 I，50s CD，击杀 -30s |
| 可购 (8) | **断后** | 滞留型迟缓，120 ticks，半径 3 格 |
| 大招 (9) | **TNT** | 原地释放引燃 TNT，40 ticks 引信，威力 2.0，消耗 3 点 |

### 艾尔 Aier — 医疗
| 槽位 | 技能 | 说明 |
|------|------|------|
| 被动 | 随行医师 | 存活时队伍成员免疫负面效果（10 ticks/次） |
| 招牌 (7) | **生命恩典** | 喷溅再生 II，2 充能，50s CD，击杀 -30s |
| 可购 (8) | **强效烟幕** | 高粒子烟幕，90 秒，半径 5 格 |
| 大招 (9) | **亡灵** | 队友倒地召唤 2 只 1HP 恼鬼攻击敌人，消耗 4 点 |

### 骛龙 Wulong — 侦察
| 槽位 | 技能 | 说明 |
|------|------|------|
| 被动 | 暗流涌动 | 潜行时速度 II |
| 招牌 (7) | **深入腹地** | 侦查箭，30 格圆柱高亮敌人 3 秒，50s CD |
| 可购 (8) | **腾龙踏云** | 风弹，1 次购买 |
| 大招 (9) | **来打CS吧！** | 禁用**全员**技能 15 秒（含己方），消耗 4 点 |

### 大招充能系统
- 击杀 +1 点
- 回合开始 +1 点
- 上限 4 点（半场换边清零）
- 可在 `operators.yml` 调整

---

## 🎒 固定物品栏

竞技模式使用固定热键槽（1-9）：

| 槽位 | 用途 | 说明 |
|------|------|------|
| 1 | 近战武器 | 刀/斧等 |
| 2 | 远程武器 | 枪/弓等，自动补箭 |
| 3 | TNT / 拆除钳 | T 侧为改造 TNT，CT 侧为拆弹钳 |
| 4-6 | 战术道具 | 烟雾/凋零/高爆手雷 |
| 7 | 招牌技能 | 干员招牌技能（充能制） |
| 8 | 可购技能 | 商店购买的额外技能 |
| 9 | 大招 | 干员大招（消耗充能点） |

系统自动管理：

- 装备发放（购买阶段自动给基础装备）
- 非法物品限制（InventoryLockListener 定期清扫）
- 道具堆叠/充能限制
- 技能物品图标/冷却显示（ClientUI HUD 同步）

---

## 👁 死亡与观战

### 战斗阶段
- 伪死亡（掉落装备、清空物品栏）
- 自动进入旁观模式
- **仅可观战队友**（SpectatorLockService）
- 滚轮切换观战目标

### 购买阶段
- 取消死亡状态
- 返回对应阵营出生点
- 恢复满血满食、发放基础装备、刷新技能栏

### 断线处理
- 购买/结算阶段断线：保留 session，重连可归队
- 战斗/拆弹阶段断线：立即判死，本回合旁观
- 全员断线：自动结束对局

---

## 📺 Live Overlay / HTTP API（CS2 Major 风格）

为导播/观战界面提供实时数据接口。

- **启用**：`live.enabled: true` + `live.token: "your-secret"`
- **端口**：默认 25564（`live.bind: 0.0.0.0` 监听全网卡）
- **鉴权**：Header `Authorization: Bearer <token>` 或查询参数 `?token=<token>` 或 Header `X-DFS-Token`
- **刷新率**：默认 5 ticks（0.25s），金钱/血量近实时
- **地图雷达**：需在配置标定 `live.map.min-x/min-z/max-x/max-z`

**端点**：
- `GET /api/live?token=xxx` — 完整当前帧（玩家、血量、金钱、装备、炸弹、回合、比分、时间）
- `GET /health` — 服务器是否在健康运行
- `GET /overlay?token=xxx` — 内置 HTML 观战页（可直接用 OBS 浏览器源加载）

---

## 🏗 服务器架构

推荐部署方式：

```
大厅服务器
|
| 匹配 / 传送
↓
游戏子服
    |
    ↓
delta_force_strike 世界
    |
    ↓
DeltaForceStrike
```

当前版本：

- 单竞技世界
- 单 Match
- 无数据库依赖
- 无跨服匹配逻辑（留给大厅/Bungee/Velocity）

大厅负责：

- 玩家匹配
- 玩家传送
- 服务器调度

本插件负责：

- 单场游戏运行
- 回合逻辑
- 战斗流程
- 经济/干员/道具/爆破系统
- 实时数据导出

---

## 📦 安装

## 环境要求

| 项目 | 要求                              |
|------|---------------------------------|
| Minecraft | 26.2+                           |
| Server | Paper (建议最新 26.2 版本)            |
| Java | 25+                             |
| 世界 | 独立竞技世界（名为 `delta_force_strike`） |

---

## 安装步骤

### 1. 构建插件

```bash
./gradlew build
```

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
config.yml       # 主配置
items.yml        # 物品/商店配置
operators.yml    # 干员/技能配置
```

首次启动会自动生成默认配置，**修改 src 后不会自动覆盖已有 plugins 配置**，请手动同步或删除后重启。

---

## 🗺️ 地图设置

默认竞技世界：

```
delta_force_strike
```

**必须先创建该世界**，否则插件会报错无法工作。

设置出生点（站在安全方块上执行）：

```
/dfs setspawn queue   # 队列等待区
/dfs setspawn t       # T 侧出生点
/dfs setspawn ct      # CT 侧出生点
```

设置包点（站在包点中心执行）：

```
/dfs setsite a   # A 包点
/dfs setsite b   # B 包点
```

设置后会自动写入 `config.yml` 的 `locations` 节点。

---

### Live Overlay 地图标定（可选）

用于雷达归一化显示，需在配置填写两角世界坐标：

```yaml
live:
  map:
    name: "炼狱小镇"
    min-x: -200.0
    min-z: -200.0
    max-x: 50.0
    max-z: 50.0
```

建议用 `/dfs setsite` 附近或 F3 坐标标定：西南角 min、东北角 max。

---

## 🚀 快速测试

修改 `config.yml` 队列为 1v1：

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
3. 选择 T / CT（点击聊天按钮或 `/dfs team t`）
4. （可选）选择干员 `/dfs agent niko`
5. 购买装备 `/dfs shop`
6. 开始回合

管理员强制开始：

```
/dfs start
```

---

## 📜 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/dfs join` | 加入队列 | deltaforcestrike.use |
| `/dfs leave` | 离开队列 | deltaforcestrike.use |
| `/dfs team <t\|ct>` | 选择阵营 | deltaforcestrike.use |
| `/dfs shop` | 打开商店 | deltaforcestrike.use |
| `/dfs info` | 查看状态 | deltaforcestrike.use |
| `/dfs guide` | 游戏指南 | deltaforcestrike.use |
| `/dfs agent <id>` | 选择干员 | deltaforcestrike.use |
| `/dfs start` | 强制开始 | deltaforcestrike.admin |
| `/dfs stop` | 停止比赛 | deltaforcestrike.admin |
| `/dfs reload` | 重载配置 | deltaforcestrike.admin |
| `/dfs give <id>` | 获取物品 | deltaforcestrike.admin |
| `/dfs setspawn <queue\|t\|ct>` | 设置出生点 | deltaforcestrike.admin |
| `/dfs setsite <a\|b>` | 设置包点 | deltaforcestrike.admin |
| `/dfs config <key> <value>` | 运行时修改配置 | deltaforcestrike.admin |

别名：

```
/delta
/fp
```

权限：

```
deltaforcestrike.use   (默认 true)
deltaforcestrike.admin (默认 op)
```

---

## ⚙️ 配置详解

主配置：`plugins/DeltaForceStrike/config.yml`

### 核心节点

| 节点 | 说明 | 默认值 |
|------|------|--------|
| `world.arena` | 竞技世界名 | `delta_force_strike` |
| `queue.team-size` | 每队人数 | `5` |
| `queue.max-players` | 总人数上限 | `10` |
| `queue.countdown-seconds` | 满人后倒计时 | `15` |
| `queue.cancel-if-not-full` | 倒计时中人数不足取消 | `true` |
| `queue.auto-balance` | 倒计时结束自动平衡 | `true` |
| `queue.lock-during-countdown` | 倒计时阶段禁止加入 | `true` |
| `operator.enabled` | 启用干员系统 | `true` |
| `operator.select-enabled` | 启用干员选择阶段 | `true` |
| `operator.select-seconds` | 选择阶段超时 | `45` |
| `match.half-round` | 半场回合数 | `12` |
| `match.win-target` | 先到胜场数 | `13` |
| `match.max-rounds` | 最多回合数 | `24` |
| `match.friendly-fire` | 友方伤害 | `false` |
| `round.prepare-time` | 购买阶段秒数 | `20` |
| `round.combat-time` | 战斗/进攻时间 | `100` |
| `round.buy-zone-radius` | 购买区半径 | `5` |
| `economy.start-money` | 起始资金 | `800` |
| `economy.max-money` | 资金上限 | `16000` |
| `bomb.plant-time` | 安包读条秒数 | `3` |
| `bomb.explosion-time` | 引信秒数 | `40` |
| `bomb.defuse-time` | 空手拆包秒数 | `10` |
| `bomb.defuse-time-with-kit` | 拆弹钳拆包秒数 | `5` |
| `bomb.damage` | 爆炸基础伤害 | `50` |
| `bomb.damage-radius` | 伤害半径 | `60` |
| `bomb.damage-falloff` | 伤害衰减 | `true` |
| `bomb.beep.enabled` | 安包后滴滴声 | `true` |
| `bomb.site-markers.enabled` | 包点浮动文字 | `true` |
| `grenade.particle-multiplier` | 粒子倍率 | `2.0` |
| `player.max-health` | 最大血量 | `20` |
| `player.shield-enabled` | 盾牌启用 | `false` |
| `spectator.lock-to-teammates` | 仅观战队友 | `true` |
| `shop.arrows-per-ranged` | 远程武器补箭上限 | `25` |
| `live.enabled` | 导播 HTTP 启用 | `false` |
| `live.bind` | 监听地址 | `0.0.0.0` |
| `live.port` | 端口 | `25564` |
| `live.token` | 鉴权密钥（必填） | `""` |
| `live.refresh-ticks` | 快照刷新间隔 | `5` |
| `debug.enabled` | 调试日志 | `false` |

---

### 物品配置：`items.yml`

支持字段：

- `type` — 物品类型（melee/ranged/armor/shield/grenade/bomb/defuse/skill/utility）
- `slot` — 热键槽位（1-9，对应 InventorySlots）
- `action` — 点击动作（give/command/submenu/skill）
- `enchantment` — 附魔
- `material` — 基础材质
- `name` / `lore` — 显示名/描述
- `price` — 商店价格
- `max-stack` / `max-uses` — 堆叠/使用次数限制

---

### 干员配置：`operators.yml`

结构：

```yaml
settings:
  ultimate-points-per-kill: 1
  ultimate-points-per-round: 1
  ultimate-points-max: 4

operators:
  niko:
    display-name: "妮可"
    english-name: "Niko"
    type: ASSAULT
    passive: { ... }
    signature: { handler: dash, ... }
    purchasable: { handler: emergency_beacon, ... }
    ultimate: { handler: niko_berserk, cost: 4, ... }
  # bruo, aier, wulong ...
```

技能处理器（`handler`）已实现：

| handler | 说明 |
|---------|------|
| `none` | 无逻辑（仅用于被动给药水） |
| `dash` | 妮可突进 |
| `emergency_beacon` | 应急传送信标 |
| `niko_berserk` | 妮可大招全属性强化 |
| `splash_harming` | 布若喷溅伤害 |
| `lingering_slowness` | 滞留迟缓区域 |
| `primed_tnt` | 原地引燃 TNT |
| `team_cleanse` | 艾尔被动净化队友负面效果 |
| `splash_regen` | 喷溅再生 |
| `round_smoke` | 持久高粒子烟幕 |
| `summon_vex` | 召唤恼鬼 |
| `sneak_speed` | 潜行加速 |
| `recon_arrow` | 侦查箭高亮 |
| `wind_charge` | 风弹 |
| `global_silence` | 全局禁用技能 |

修改后：`/dfs reload` 或重启生效。

---

## 📁 项目结构

```
org.starset.deltaforcestrike

├── DeltaForceStrike.java          # 主类、生命周期、服务注册

├── manager/
│   └── GameManager.java           # 顶层管理器

├── match/
│   ├── Match.java                 # 对局数据容器
│   ├── MatchManager.java          # 队列、选边、干员选、对局流程
│   ├── MatchState.java            # WAITING/COUNTDOWN/AGENT_SELECT/IN_PROGRESS/ENDING
│   ├── PlayerSession.java         # 玩家会话（金钱、击杀、存活、干员、连败等）
│   ├── Team.java                  # T / CT / NONE
│   └── TeamSelectHolder.java      # 选边书 GUI

├── round/
│   ├── RoundManager.java          # 购买/战斗/拆弹/结算/半场换边/经济结算
│   └── RoundState.java            # IDLE/BUY/COMBAT/BOMB_PLANTED/ROUND_END

├── bomb/
│   ├── BombManager.java           # 安包/拆包/引信/爆炸/包点/滴滴声
│   ├── BombDropGlowService.java   # 掉落 TNT 发光高亮
│   └── BombSiteMarkerService.java # 包点浮动文字

├── grenade/
│   └── GrenadeService.java        # 投掷物物理/粒子/效果

├── operator/
│   ├── OperatorDefinition.java    # 干员定义数据类
│   ├── OperatorKeys.java          # PDC 键
│   ├── OperatorLoadout.java       # 玩家装备的技能组
│   ├── OperatorRegistry.java      # operators.yml 加载/热更
│   ├── OperatorSelectHolder.java  # 干员选择书 GUI
│   ├── OperatorService.java       # 选择/充能/发放/触发
│   ├── OperatorType.java          # ASSAULT/ENGINEER/MEDIC/SCOUT
│   ├── PotionSpec.java            # 药水效果规格
│   ├── SkillDefinition.java       # 技能定义
│   ├── SkillKind.java             # PASSIVE/SIGNATURE/PURCHASABLE/ULTIMATE
│   ├── SkillContext.java          # 技能执行上下文
│   ├── SkillHandler.java          # 技能处理器接口
│   ├── SkillHandlerRegistry.java  # handler 字符串 -> 实现
│   ├── SkillResult.java           # 执行结果
│   └── skill/impl/                # 14 个具体实现
│       ├── DashHandler.java
│       ├── EmergencyBeaconHandler.java
│       ├── EnderPearlHandler.java
│       ├── GlobalSilenceHandler.java
│       ├── LingeringSlownessHandler.java
│       ├── NikoBerserkHandler.java
│       ├── NoopHandler.java
│       ├── PrimedTntHandler.java
│       ├── ReconArrowHandler.java
│       ├── RoundSmokeHandler.java
│       ├── SplashHarmingHandler.java
│       ├── SplashRegenHandler.java
│       ├── SummonVexHandler.java
│       └── WindChargeHandler.java

├── shop/
│   ├── ShopGUI.java               # 箱子 GUI 构建/打开
│   ├── ShopHolder.java            # InventoryHolder
│   └── ShopListener.java          # 点击处理

├── item/
│   ├── GameItem.java              # 竞技物品包装
│   ├── ItemGiveService.java       # 发放/补给/补箭
│   ├── ItemKeys.java              # PDC 键
│   └── ItemManager.java           # items.yml 加载/查找

├── listener/
│   ├── ArenaPlayerListener.java   # 进世界自动入队
│   ├── BombListener.java          # 安包/拆包交互
│   ├── BuyZoneListener.java       # 购买区限制
│   ├── GameModeLockListener.java  # 强制冒险模式
│   ├── GameRulesListener.java     # 世界规则/饱食/禁掉落
│   ├── GrenadeListener.java       # 投掷物右键
│   ├── InventoryLockListener.java # 非法物品清扫
│   ├── ItemProtectListener.java   # 物品保护（防丢/防移动）
│   ├── OperatorSelectListener.java# 干员选择书点击
│   ├── OperatorSkillListener.java # 技能物品右键触发
│   ├── PickupListener.java        # 拾取限制
│   ├── SpectatorLockListener.java # 旁观锁队友
│   └── TeamSelectListener.java    # 选边书点击

├── scoreboard/
│   ├── GameScoreboard.java        # 侧边栏分数板
│   ├── NametagService.java        # 头顶名牌（队伍色+金钱+状态）
│   └── TabListService.java        # Tab 列表（比分+金钱+干员）

├── spectator/
│   └── SpectatorLockService.java  # 仅观战队友、滚轮切换

├── live/
│   ├── LiveHttpServer.java        # Undertow HTTP 服务
│   ├── LiveJson.java              # Jackson 序列化
│   ├── LiveKillFeedService.java   # 击杀事件流
│   ├── LiveOverlayHtml.java       # 内置观战页
│   └── LiveSnapshotService.java   # 快照采集（玩家/炸弹/回合/经济）

├── util/
│   ├── ArenaCleanup.java          # 掉落物/实体清理
│   ├── BombSites.java             # 包点坐标工具
│   ├── ConfigKeys.java            # 配置键常量 + 读取帮助
│   ├── DeathDrops.java            # 死亡掉落装备
│   ├── GameGuide.java             # 玩法说明（可点击）
│   ├── GrenadeKeys.java           # 投掷物 PDC
│   ├── GrenadeType.java           # SMOKE/WITHER/INCENDIARY
│   ├── InventorySlots.java        # 热键槽位常量
│   ├── ItemPlacement.java         # 物品栏布局
│   ├── OperatorSelectUI.java      # 干员选择书构建
│   ├── TeamSelectUI.java          # 选边书构建
│   └── Worlds.java                # 竞技世界判断

└── command/
    └── DFSCommand.java            # /dfs 主命令、Tab 补全
```

---

## 🔧 开发设计

## 状态驱动

### MatchState
- `WAITING` — 等待加入
- `COUNTDOWN` — 倒计时
- `AGENT_SELECT` — 干员选择
- `IN_PROGRESS` — 对局进行中
- `ENDING` — 结算中

### RoundState
- `IDLE` — 空闲
- `BUY` — 购买阶段
- `COMBAT` — 战斗阶段
- `BOMB_PLANTED` — 拆弹阶段
- `ROUND_END` — 回合结算

---

## 模块职责

| 模块 | 职责 |
|------|------|
| Match | 管理整场比赛（队列、选边、干员、比分、半场） |
| Round | 管理单个回合（购买/战斗/拆弹/结算/经济/换边） |
| Listener | Bukkit 事件入口，仅做分发与基础判断 |
| Service | 核心游戏逻辑（炸弹/投掷物/干员/商店/导播/记分板） |
| Manager | 生命周期管理、跨模块协调 |
| Util | 通用工具、配置读取、UI 构建、PDC 键 |

---

## 开发约定

- 竞技逻辑仅在 `Worlds.isArena(player)` 返回 `true` 时执行
- 实体/物品标记使用 `PersistentDataContainer`，避免 `FixedMetadataValue`
- Listener 只负责事件接入，核心逻辑由 Manager / Service 管理
- 定时任务统一在主类 `onEnable` 注册，便于追踪
- 所有广播/标题/ActionBar 使用 Adventure Component API
- 客户端 HUD 通过 `HudSyncService` 定时推送 JSON（Fabric ClientUI 兼容）

---

## 🚧 Roadmap

- [x] 干员系统（4 位干员，14 种技能）
- [x] 技能系统（被动/招牌/可购/大招/充能）
- [x] 战术投掷物（烟雾/凋零/高爆）
- [x] 完整爆破流程（安包/拆包/引信/爆炸/不破坏地形）
- [x] CS 式经济（连败补偿/手枪局/击杀/安拆奖励）
- [x] 半场换边（攻防互换、金钱重置、大招清零）
- [x] Live Overlay / HTTP API（实时快照/击杀流/内置观战页）
- [x] 固定物品栏/装备发放/非法物品清扫
- [x] 旁观锁队友/滚轮切换
- [x] 客户端 HUD 同步
- [ ] 数据统计（战绩/段位/历史回合）
- [ ] 排位系统
- [ ] 多房间 Match（同世界多场并行）
- [ ] 跨服匹配（Redis/Bungee/Velocity 集成）
- [ ] 回放系统（录像/复盘）
- [ ] 更多干员/技能/地图配置

---

## 🎯 总结

DeltaForceStrike 将经典爆破竞技玩法带入 Minecraft。

准备竞技世界：

```
delta_force_strike
```

设置地图：

```
/dfs setspawn queue
/dfs setspawn t
/dfs setspawn ct
/dfs setsite a
/dfs setsite b
```

配置干员与物品（`operators.yml` / `items.yml`），可选启用 Live Overlay（`live.enabled: true` + `live.token`）。

即可开始一场 **Minecraft 战术爆破比赛**。
