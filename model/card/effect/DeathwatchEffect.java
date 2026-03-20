package game.card.effect;

import game.card.CardPlayContext;
import game.card.ability.Ability;
import game.card.ability.AbilityTrigger;
import game.model.Unit;

public interface DeathwatchEffect extends Ability {
    void onUnitDied(CardPlayContext ctx, String ownerId, Unit watcher, Unit deadUnit);

    @Override
    default AbilityTrigger trigger() {
        return AbilityTrigger.ON_DEATHWATCH;
    }
}
