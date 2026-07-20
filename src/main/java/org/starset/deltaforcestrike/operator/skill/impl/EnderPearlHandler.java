package org.starset.deltaforcestrike.operator.skill.impl;

import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

/**
 * 兼容旧配置 handler: ender_pearl。
 * 实际改为可控水平突进（不再发射末影珍珠，防上房顶）。
 */
public class EnderPearlHandler implements SkillHandler {

    private final DashHandler dash = new DashHandler();

    @Override
    public String id() {
        return "ender_pearl";
    }

    @Override
    public SkillResult execute(SkillContext ctx) {
        return dash.execute(ctx);
    }
}
