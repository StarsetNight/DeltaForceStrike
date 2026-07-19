package org.starset.deltaforcestrike.operator.skill.impl;

import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.operator.OperatorLoadout;
import org.starset.deltaforcestrike.operator.OperatorService;
import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

public class GlobalSilenceHandler implements SkillHandler {
    @Override public String id() { return "global_silence"; }

    @Override
    public SkillResult execute(SkillContext ctx) {
        int sec = ctx.skill().getInt("duration-seconds", 15);
        Match match = ctx.match();
        if (match == null) return SkillResult.fail("无对局");

        OperatorService ops = ctx.plugin().getOperatorService();
        for (Player p : match.onlinePlayers()) {
            OperatorLoadout load = ops.getLoadout(p.getUniqueId());
            if (load != null) {
                load.silenceFor(sec);
            }
            p.sendMessage("§c§l[DFS] 技能被禁用 " + sec + " 秒！§7（来打CS吧！）");
        }
        return SkillResult.ok("来打CS吧！全场沉默 " + sec + "s");
    }
}
