package org.starset.deltaforcestrike;

import org.bukkit.plugin.java.JavaPlugin;
import org.starset.deltaforcestrike.command.DFSCommand;
import org.starset.deltaforcestrike.item.ItemManager;
import org.starset.deltaforcestrike.manager.GameManager;

public final class DeltaForceStrike extends JavaPlugin {

    private static DeltaForceStrike instance;

    private GameManager gameManager;
    private ItemManager itemManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        // items.yml 由 ItemManager 负责 saveResource

        itemManager = new ItemManager(this);
        itemManager.loadItems();

        gameManager = new GameManager(this);

        if (getCommand("dfs") != null) {
            DFSCommand cmd = new DFSCommand(this);
            getCommand("dfs").setExecutor(cmd);
            getCommand("dfs").setTabCompleter(cmd);
        } else {
            getLogger().severe("plugin.yml 未注册 dfs 命令！");
        }

        getLogger().info("DeltaForceStrike v" + getPluginMeta().getVersion() + " 已启动");
        getLogger().info("物品数: " + itemManager.getAll().size());
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
}
