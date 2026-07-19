package org.starset.deltaforcestrike.operator.skill.impl;

import org.starset.deltaforcestrike.operator.skill.SkillContext;
import org.starset.deltaforcestrike.operator.skill.SkillHandler;
import org.starset.deltaforcestrike.operator.skill.SkillResult;

public class NoopHandler implements SkillHandler {
    @Override public String id() { return "none"; }
    @Override public SkillResult execute(SkillContext ctx) {
        return SkillResult.fail("技能未实现: " + ctx.skill().getHandlerId());
    }
}
