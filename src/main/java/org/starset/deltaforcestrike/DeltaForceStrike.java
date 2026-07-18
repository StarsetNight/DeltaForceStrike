package org.starset.deltaforcestrike;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.starset.deltaforcestrike.command.DFSCommand;
import org.starset.deltaforcestrike.game.GameRulesService;
import org.starset.deltaforcestrike.item.ItemGiveService;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.listener.ArenaPlayerListener;
import org.starset.deltaforcestrike.listener.GameRulesListener;
import org.starset.deltaforcestrike.listener.InventoryLockListener;
import org.starset.deltaforcestrike.listener.ItemProtectListener;
import org.starset.deltaforcestrike.listener.PickupListener;
import org.starset.deltaforcestrike.manager.GameManager;
import org.starset.deltaforcestrike.match.MatchManager;
import org.starset.deltaforcestrike.scoreboard.GameScoreboard;
import org.starset.deltaforcestrike.util.Worlds;

public final class DeltaForceStrike extends JavaPlugin {

    private static DeltaForceStrike instance;

    private GameManager gameManager;
    private ItemManager itemManager;
    private ItemGiveService itemGiveService;
    private GameRulesService gameRulesService;
    private GameScoreboard scoreboardService;
    private InventoryLockListener inventoryLockListener;
    private GameRulesListener gameRulesListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Worlds.arenaWorld() == null) {
            getLogger().severe("找不到竞技世界: " + Worlds.arenaName()
                    + " — 请先创建该世界，插件队列/对局将无法正常工作。");
        }

        itemManager = new ItemManager(this);
        itemManager.loadItems();
        itemGiveService = new ItemGiveService(itemManager);

        gameRulesService = new GameRulesService(this);
        gameRulesService.applyToArenaWorld();


        gameManager = new GameManager(this);

        PluginCommand dfs = getCommand("dfs");
        if (dfs == null) {
            getLogger().severe("plugin.yml 未注册 dfs 命令！");
        } else {
            DFSCommand cmd = new DFSCommand(this);
            dfs.setExecutor(cmd);
            dfs.setTabCompleter(cmd);
        }

        inventoryLockListener = new InventoryLockListener(this);
        gameRulesListener = new GameRulesListener(this, gameRulesService);
        scoreboardService = new GameScoreboard(this);

        getServer().getPluginManager().registerEvents(new ArenaPlayerListener(this), this);
        getServer().getPluginManager().registerEvents(inventoryLockListener, this);
        getServer().getPluginManager().registerEvents(new ItemProtectListener(this), this);
        getServer().getPluginManager().registerEvents(new PickupListener(this, inventoryLockListener), this);
        getServer().getPluginManager().registerEvents(gameRulesListener, this);

        // 物品栏清扫：仅竞技世界内在局玩家
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (Worlds.isArena(p) && getMatchManager().isInMatch(p)) {
                    inventoryLockListener.sanitizeAll(p);
                }
            }
        }, 20L, 10L);

        getServer().getScheduler().runTaskTimer(this, () -> {
            if (scoreboardService != null) {
                scoreboardService.tick();
            }
        }, 20L, 20L);

        // 规则 tick：仅竞技世界
        getServer().getScheduler().runTaskTimer(this, gameRulesService::tick, 40L, 40L);

        getLogger().info("DeltaForceStrike v" + getPluginMeta().getVersion() + " 已启动");
        getLogger().info("竞技世界: " + Worlds.arenaName() + " | 物品: " + itemManager.getAll().size());
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        getLogger().info("DeltaForceStrike 已关闭");
        if (scoreboardService != null) {
            scoreboardService.removeAll();
        }

    }

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

    public InventoryLockListener getInventoryLockListener() {
        return inventoryLockListener;
    }
}
