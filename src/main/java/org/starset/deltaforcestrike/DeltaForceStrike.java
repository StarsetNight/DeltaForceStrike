package org.starset.deltaforcestrike;

import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.starset.deltaforcestrike.command.DFSCommand;
import org.starset.deltaforcestrike.item.ItemGiveService;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.listener.InventoryLockListener;
import org.starset.deltaforcestrike.listener.ItemProtectListener;
import org.starset.deltaforcestrike.listener.PickupListener;
import org.starset.deltaforcestrike.listener.GameRulesListener;
import org.starset.deltaforcestrike.manager.GameManager;
import org.starset.deltaforcestrike.game.GameRulesService;

public final class DeltaForceStrike extends JavaPlugin {

    private static DeltaForceStrike instance;

    private GameManager gameManager;
    private ItemManager itemManager;
    private ItemGiveService itemGiveService;
    private GameRulesService gameRulesService;
    private InventoryLockListener inventoryLockListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        itemManager = new ItemManager(this);
        itemManager.loadItems();
        itemGiveService = new ItemGiveService(itemManager);
        gameManager = new GameManager(this);

        gameRulesService = new GameRulesService(this);
        gameRulesService.applyToAllWorlds();

        PluginCommand dfs = getCommand("dfs");
        if (dfs == null) {
            getLogger().severe("plugin.yml 未注册 dfs 命令！");
        } else {
            DFSCommand executor = new DFSCommand(this);
            dfs.setExecutor(executor);
            dfs.setTabCompleter(executor);
        }

        inventoryLockListener = new InventoryLockListener(this);
        getServer().getPluginManager().registerEvents(inventoryLockListener, this);
        getServer().getPluginManager().registerEvents(new ItemProtectListener(this), this);
        getServer().getPluginManager().registerEvents(new PickupListener(this, inventoryLockListener), this);
        getServer().getPluginManager().registerEvents(new GameRulesListener(this, gameRulesService), this);

        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) {
                inventoryLockListener.sanitizeAll(p);
            }
        }, 20L, 10L);

        getServer().getScheduler().runTaskTimer(this, gameRulesService::tick, 40L, 40L);

        getLogger().info("DeltaForceStrike v" + getPluginMeta().getVersion() + " 已启动");
        getLogger().info("物品数: " + itemManager.getAll().size());
    }

    public GameRulesService getGameRulesService() {
        return gameRulesService;
    }


    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        getLogger().info("DeltaForceStrike 已关闭");
    }

    public static DeltaForceStrike getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public ItemGiveService getItemGiveService() {
        return itemGiveService;
    }

    public InventoryLockListener getInventoryLockListener() {
        return inventoryLockListener;
    }
}
