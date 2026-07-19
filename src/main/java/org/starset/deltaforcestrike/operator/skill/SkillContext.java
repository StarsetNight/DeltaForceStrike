package org.starset.deltaforcestrike.operator.skill;

import org.bukkit.entity.Player;
import org.starset.deltaforcestrike.DeltaForceStrike;
import org.starset.deltaforcestrike.match.Match;
import org.starset.deltaforcestrike.operator.OperatorLoadout;
import org.starset.deltaforcestrike.operator.SkillDefinition;
import org.starset.deltaforcestrike.operator.SkillKind;

public record SkillContext(
        DeltaForceStrike plugin,
        Player player,
        Match match,
        OperatorLoadout loadout,
        SkillDefinition skill,
        SkillKind kind
) {}
