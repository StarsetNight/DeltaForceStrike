package org.starset.deltaforcestrike;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.starset.deltaforcestrike.bomb.BombManager;
import org.starset.deltaforcestrike.command.DFSCommand;
import org.starset.deltaforcestrike.game.GameRulesService;
import org.starset.deltaforcestrike.item.ItemGiveService;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.listener.*;
import org.starset.deltaforcestrike.manager.GameManager;
import org.starset.deltaforcestrike.match.MatchManager;
import org.starset.deltaforcestrike.scoreboard.GameScoreboard;
import org.starset.deltaforcestrike.scoreboard.TabListService;
import org.starset.deltaforcestrike.shop.ShopListener;
import org.starset.deltaforcestrike.util.Worlds;

public final class DeltaForceStrike extends JavaPlugin {

    private static DeltaForceStrike instance;

    private GameManager gameManager;
    private ItemManager itemManager;
    private ItemGiveService itemGiveService;
    private GameRulesService gameRulesService;
    private GameScoreboard scoreboardService;
    private TabListService tabListService;
    private BombManager bombManager;
    private InventoryLockListener inventoryLockListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (Worlds.arenaWorld() == null) {
            getLogger().severe("找不到竞技世界: " + Worlds.arenaName());
        }

        itemManager = new ItemManager(this);
        itemManager.loadItems();
        itemGiveService = new ItemGiveService(itemManager);

        gameRulesService = new GameRulesService(this);
        gameRulesService.applyToArenaWorld();

        bombManager = new BombManager(this);
        scoreboardService = new GameScoreboard(this);
        tabListService = new TabListService(this);
        gameManager = new GameManager(this);

        PluginCommand dfs = getCommand("dfs");
        if (dfs == null) {
            getLogger().severe("未注册 dfs 命令");
        } else {
            DFSCommand cmd = new DFSCommand(this);
            dfs.setExecutor(cmd);
            dfs.setTabCompleter(cmd);
        }

        inventoryLockListener = new InventoryLockListener(this);
        getServer().getPluginManager().registerEvents(new ArenaPlayerListener(this), this);
        getServer().getPluginManager().registerEvents(inventoryLockListener, this);
        getServer().getPluginManager().registerEvents(new ItemProtectListener(this), this);
        getServer().getPluginManager().registerEvents(new PickupListener(this, inventoryLockListener), this);
        getServer().getPluginManager().registerEvents(new GameRulesListener(this, gameRulesService), this);
        getServer().getPluginManager().registerEvents(new BuyZoneListener(this), this);
        getServer().getPluginManager().registerEvents(new BombListener(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);
        getServer().getPluginManager().registerEvents(new GameModeLockListener(this), this);

        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (Worlds.isArena(p) && getMatchManager().isInMatch(p)) {
                    inventoryLockListener.sanitizeAll(p);
                }
            }
        }, 20L, 10L);

        getServer().getScheduler().runTaskTimer(this, gameRulesService::tick, 40L, 40L);
        getServer().getScheduler().runTaskTimer(this, scoreboardService::tick, 20L, 20L);

        getLogger().info("DeltaForceStrike 已启动 | 世界=" + Worlds.arenaName()
                + " | 物品=" + itemManager.getAll().size());
    }

    @Override
    public void onDisable() {
        if (bombManager != null) bombManager.reset();
        if (scoreboardService != null) scoreboardService.removeAll();
        if (gameManager != null) gameManager.shutdown();
    }

    public static DeltaForceStrike getInstance() { return instance; }
    public GameManager getGameManager() { return gameManager; }
    public MatchManager getMatchManager() { return gameManager.getMatchManager(); }
    public ItemManager getItemManager() { return itemManager; }
    public ItemGiveService getItemGiveService() { return itemGiveService; }
    public GameRulesService getGameRulesService() { return gameRulesService; }
    public GameScoreboard getScoreboardService() { return scoreboardService; }
    public TabListService getTabListService() { return tabListService; }
    public BombManager getBombManager() { return bombManager; }
}
