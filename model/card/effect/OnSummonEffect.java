package game.card.effect;

import game.card.CardPlayContext;
import game.card.ability.Ability;
import game.card.ability.AbilityTrigger;
import game.model.Unit;

public interface OnSummonEffect extends Ability {
    void apply(CardPlayContext ctx, String ownerId, Unit summonedUnit);

    @Override
    default AbilityTrigger trigger() {
        return AbilityTrigger.ON_SUMMON;
    }
}
