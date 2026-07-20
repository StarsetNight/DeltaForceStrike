package org.starset.deltaforcestrike;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.starset.deltaforcestrike.bomb.BombDropGlowService;
import org.starset.deltaforcestrike.bomb.BombManager;
import org.starset.deltaforcestrike.bomb.BombSiteMarkerService;
import org.starset.deltaforcestrike.command.DFSCommand;
import org.starset.deltaforcestrike.game.GameRulesService;
import org.starset.deltaforcestrike.grenade.GrenadeService;
import org.starset.deltaforcestrike.item.ItemGiveService;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.listener.ArenaPlayerListener;
import org.starset.deltaforcestrike.listener.BombListener;
import org.starset.deltaforcestrike.listener.BuyZoneListener;
import org.starset.deltaforcestrike.listener.GameModeLockListener;
import org.starset.deltaforcestrike.listener.GameRulesListener;
import org.starset.deltaforcestrike.listener.GrenadeListener;
import org.starset.deltaforcestrike.listener.InventoryLockListener;
import org.starset.deltaforcestrike.listener.ItemProtectListener;
import org.starset.deltaforcestrike.listener.OperatorSelectListener;
import org.starset.deltaforcestrike.listener.OperatorSkillListener;
import org.starset.deltaforcestrike.listener.PickupListener;
import org.starset.deltaforcestrike.listener.SpectatorLockListener;
import org.starset.deltaforcestrike.manager.GameManager;
import org.starset.deltaforcestrike.match.MatchManager;
import org.starset.deltaforcestrike.operator.OperatorService;
import org.starset.deltaforcestrike.scoreboard.GameScoreboard;
import org.starset.deltaforcestrike.scoreboard.NametagService;
import org.starset.deltaforcestrike.scoreboard.TabListService;
import org.starset.deltaforcestrike.shop.ShopListener;
import org.starset.deltaforcestrike.spectator.SpectatorLockService;
import org.starset.deltaforcestrike.util.Worlds;

/**
 * 友谊之约：反制行动 — 主类
 * 单世界 delta_force_strike / 全服单对局
 */
public final class DeltaForceStrike extends JavaPlugin {

    private static DeltaForceStrike instance;

    private GameManager gameManager;
    private ItemManager itemManager;
    private ItemGiveService itemGiveService;
    private GameRulesService gameRulesService;
    private GameScoreboard scoreboardService;
    private TabListService tabListService;
    private NametagService nametagService;
    private BombManager bombManager;
    private BombDropGlowService bombDropGlowService;
    private BombSiteMarkerService bombSiteMarkerService;
    private GrenadeService grenadeService;
    private OperatorService operatorService;
    private SpectatorLockService spectatorLockService;
    private InventoryLockListener inventoryLockListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Worlds.arenaWorld() == null) {
            getLogger().severe("找不到竞技世界: " + Worlds.arenaName()
                    + " — 请先创建该世界，否则队列/对局无法正常工作。");
        }

        // ---------- 物品 ----------
        itemManager = new ItemManager(this);
        itemManager.loadItems();
        itemGiveService = new ItemGiveService(itemManager);

        // ---------- 规则 / 子系统 ----------
        gameRulesService = new GameRulesService(this);
        gameRulesService.applyToArenaWorld();

        bombManager = new BombManager(this);
        bombDropGlowService = new BombDropGlowService(this);
        bombSiteMarkerService = new BombSiteMarkerService(this);
        grenadeService = new GrenadeService(this);
        operatorService = new OperatorService(this);

        scoreboardService = new GameScoreboard(this);
        tabListService = new TabListService(this);
        nametagService = new NametagService(this);
        spectatorLockService = new SpectatorLockService(this);

        gameManager = new GameManager(this);

        // ---------- 命令 ----------
        PluginCommand dfs = getCommand("dfs");
        if (dfs == null) {
            getLogger().severe("plugin.yml 未注册 dfs 命令！");
        } else {
            DFSCommand cmd = new DFSCommand(this);
            dfs.setExecutor(cmd);
            dfs.setTabCompleter(cmd);
        }

        // ---------- 监听器 ----------
        inventoryLockListener = new InventoryLockListener(this);

        getServer().getPluginManager().registerEvents(new ArenaPlayerListener(this), this);
        getServer().getPluginManager().registerEvents(inventoryLockListener, this);
        getServer().getPluginManager().registerEvents(new ItemProtectListener(this), this);
        getServer().getPluginManager().registerEvents(new PickupListener(this, inventoryLockListener), this);
        getServer().getPluginManager().registerEvents(new GameRulesListener(this, gameRulesService), this);
        getServer().getPluginManager().registerEvents(new GameModeLockListener(this), this);
        getServer().getPluginManager().registerEvents(new BuyZoneListener(this), this);
        getServer().getPluginManager().registerEvents(new BombListener(this), this);
        getServer().getPluginManager().registerEvents(bombDropGlowService, this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new GrenadeListener(this, grenadeService), this);
        getServer().getPluginManager().registerEvents(new OperatorSkillListener(this), this);
        getServer().getPluginManager().registerEvents(new OperatorSelectListener(this), this);
        getServer().getPluginManager().registerEvents(
                new SpectatorLockListener(this, spectatorLockService), this);

        // ---------- 定时任务 ----------
        // 物品栏清扫（仅竞技世界内在局玩家）
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (Worlds.isArena(p) && getMatchManager().isInMatch(p)) {
                    inventoryLockListener.sanitizeAll(p);
                }
            }
        }, 20L, 10L);

        // 世界规则 + 饱食（静默，勿刷 log）
        getServer().getScheduler().runTaskTimer(this, gameRulesService::tick, 40L, 40L);

        // 侧边栏 + 铭牌
        getServer().getScheduler().runTaskTimer(this, scoreboardService::tick, 20L, 20L);

        // 旁观锁友方
        getServer().getScheduler().runTaskTimer(this, spectatorLockService::tickAll, 20L, 10L);

        // 干员被动 / 充能 / 烟幕等
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (operatorService != null) {
                operatorService.tick();
            }
        }, 2L, 2L);

        bombDropGlowService.start();
        bombSiteMarkerService.start();

        getLogger().info("DeltaForceStrike v" + getPluginMeta().getVersion() + " 已启动");
        getLogger().info("竞技世界: " + Worlds.arenaName()
                + " | 物品: " + itemManager.getAll().size()
                + " | 干员: " + (operatorService.getRegistry() == null
                ? 0 : operatorService.getRegistry().allUnique().size()));
    }

    @Override
    public void onDisable() {
        if (bombDropGlowService != null) {
            bombDropGlowService.stop();
        }
        if (bombSiteMarkerService != null) {
            bombSiteMarkerService.stop();
        }
        if (bombManager != null) {
            bombManager.reset();
        }
        if (scoreboardService != null) {
            scoreboardService.removeAll();
        }
        if (gameManager != null) {
            gameManager.shutdown();
        }
        getLogger().info("DeltaForceStrike 已关闭");
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public static DeltaForceStrike getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public MatchManager getMatchManager() {
        return gameManager.getMatchManager();
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public ItemGiveService getItemGiveService() {
        return itemGiveService;
    }

    public GameRulesService getGameRulesService() {
        return gameRulesService;
    }

    public GameScoreboard getScoreboardService() {
        return scoreboardService;
    }

    public TabListService getTabListService() {
        return tabListService;
    }

    public NametagService getNametagService() {
        return nametagService;
    }

    public BombManager getBombManager() {
        return bombManager;
    }

    public BombDropGlowService getBombDropGlowService() {
        return bombDropGlowService;
    }

    public BombSiteMarkerService getBombSiteMarkerService() {
        return bombSiteMarkerService;
    }

    public GrenadeService getGrenadeService() {
        return grenadeService;
    }

    public OperatorService getOperatorService() {
        return operatorService;
    }

    public SpectatorLockService getSpectatorLockService() {
        return spectatorLockService;
    }

    public InventoryLockListener getInventoryLockListener() {
        return inventoryLockListener;
    }
}
