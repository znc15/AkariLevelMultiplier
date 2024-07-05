package com.littlesheep;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import com.github.cpjinan.plugin.akarilevel.api.AkariLevelAPI;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AkariLevelExperienceMultiplier extends JavaPlugin implements TabExecutor, Listener {

    private double globalMultiplier = 1.0;
    private long multiplierEndTime = 0;
    private boolean multiplierEnded = false;
    private final Map<UUID, Double> playerMultipliers = new HashMap<>();
    private boolean isAkariLevelLoaded = false;
    private boolean doubleExpOnCommand;
    private FileConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // 保存默认配置文件
        doubleExpOnCommand = getConfig().getBoolean("double_exp_on_command", true);

        // 加载消息配置
        loadMessages();

        // 输出插件信息
        getLogger().info("==========================================");
        getLogger().info(getDescription().getName());
        getLogger().info("Version/版本: " + getDescription().getVersion());
        getLogger().info("Author/作者: " + String.join(", ", getDescription().getAuthors()));
        getLogger().info("QQ Group/QQ群: 690216634");
        getLogger().info("Github: https://github.com/znc15/AkariLevelExperienceMultiplier");
        getLogger().info(getDescription().getName() + " 已启用！");
        getLogger().info("❛‿˂̵✧");
        getLogger().info("==========================================");

        Objects.requireNonNull(this.getCommand("setMultiplier")).setExecutor(this);
        Objects.requireNonNull(this.getCommand("setMultiplier")).setTabCompleter(this);
        this.getServer().getPluginManager().registerEvents(this, this);

        // 延迟检测以确保 AkariLevel 插件已加载
        Bukkit.getScheduler().runTaskLater(this, this::checkAkariLevel, 20L);

        // 定时任务：定期检查并重置经验倍率
        Bukkit.getScheduler().runTaskTimer(this, this::resetMultipliers, 0L, 20L);
    }

    private void loadMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private String getMessage(String key, Map<String, String> placeholders) {
        String message = messages.getString("messages." + key, key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    private void checkAkariLevel() {
        Plugin plugin = getServer().getPluginManager().getPlugin("AkariLevel");
        if (plugin != null && plugin.isEnabled()) {
            isAkariLevelLoaded = true;
        } else {
            getLogger().severe("AkariLevel 插件未找到或未启用！");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("AkariLevel") && !isAkariLevelLoaded) {
            checkAkariLevel();
        }
    }

    @EventHandler
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        double multiplier = playerMultipliers.getOrDefault(player.getUniqueId(), globalMultiplier);
        if (multiplier == 1.0) {
            return; // 没有设置倍率，直接返回
        }

        int currentExp = AkariLevelAPI.INSTANCE.getPlayerExp(player);
        int newExp = (int) (currentExp * multiplier);
        AkariLevelAPI.INSTANCE.setPlayerExp(player, newExp, "经验倍率应用");
        event.setAmount(0); // 防止默认经验变化
        getLogger().info("玩家 " + player.getName() + " 经验倍率应用: " + multiplier + " 新经验: " + newExp);
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!doubleExpOnCommand) return;

        String[] commandParts = event.getMessage().split(" ");
        if (commandParts.length >= 4 && commandParts[0].equalsIgnoreCase("/akarilevel")
                && commandParts[1].equalsIgnoreCase("exp")
                && commandParts[2].equalsIgnoreCase("add")) {

            Player player = Bukkit.getPlayer(commandParts[3]);
            if (player != null) {
                double multiplier = playerMultipliers.getOrDefault(player.getUniqueId(), globalMultiplier);
                if (multiplier != 1.0) {
                    int expToAdd;
                    try {
                        expToAdd = Integer.parseInt(commandParts[4]);
                    } catch (NumberFormatException e) {
                        return; // 非法经验值，直接返回
                    }
                    int newExpToAdd = (int) (expToAdd * multiplier);
                    commandParts[4] = String.valueOf(newExpToAdd);
                    event.setMessage(String.join(" ", commandParts));
                }
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setMultiplier")) {
            if (!sender.hasPermission("akarilevel.setmultiplier")) {
                sender.sendMessage(getMessage("no_permission", new HashMap<>()));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage("用法: /setMultiplier <player|all> <multiplier> [duration in seconds]");
                return true;
            }

            String target = args[0];
            double multiplier;
            try {
                multiplier = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage("经验倍率必须是一个有效的数字。");
                return true;
            }

            long duration = 0;
            if (args.length > 2) {
                try {
                    duration = Long.parseLong(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("持续时间必须是一个有效的数字。");
                    return true;
                }
            }

            if (target.equalsIgnoreCase("all")) {
                globalMultiplier = multiplier;
                multiplierEndTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(duration);
                multiplierEnded = false;

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("multiplier", String.valueOf(multiplier));
                placeholders.put("duration", String.valueOf(duration));
                sender.sendMessage(getMessage("global_multiplier_set", placeholders));
            } else {
                Player player = Bukkit.getPlayer(target);
                if (player == null) {
                    sender.sendMessage("玩家未找到。");
                    return true;
                }

                playerMultipliers.put(player.getUniqueId(), multiplier);
                if (duration > 0) {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        playerMultipliers.remove(player.getUniqueId());
                        player.sendMessage(getMessage("player_multiplier_ended", new HashMap<>()));
                    }, duration * 20L);
                }

                // 在设置倍率后给玩家增加一定量的经验
                int expToAdd = 100; // 自定义的经验值
                AkariLevelAPI.INSTANCE.addPlayerExp(player, expToAdd, "设置经验倍率");
                player.sendMessage("你获得了 " + expToAdd + " 点经验值");

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("multiplier", String.valueOf(multiplier));
                placeholders.put("duration", String.valueOf(duration));
                sender.sendMessage(getMessage("player_multiplier_set", placeholders));
            }
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("all");
            for (Player player : Bukkit.getOnlinePlayers()) {
                suggestions.add(player.getName());
            }
            return suggestions;
        }
        return Collections.emptyList();
    }

    private void resetMultipliers() {
        if (System.currentTimeMillis() > multiplierEndTime && !multiplierEnded) {
            globalMultiplier = 1.0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(getMessage("global_multiplier_ended", new HashMap<>()));
            }
            multiplierEnded = true;
        }
    }
}
