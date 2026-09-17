package com.rootrecord.minecraft.rootadmin.command;

import com.rootrecord.minecraft.common.command.ServerRestartBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/** Hosts /rootrestart and /rootstop when Root-Restart fails to register with Paper. */
public final class RestartHostCommand implements CommandExecutor, TabCompleter {

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (ServerRestartBridge.dispatch(sender, label, args)) {
      return true;
    }
    sender.sendMessage(
        "\u00A7cRestart/stop unavailable \u2014 Root-Restart is not loaded. Check \u00A7f/plugins \u00A7cand server startup logs.");
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
    return ServerRestartBridge.tabComplete(sender, alias, args);
  }
}
