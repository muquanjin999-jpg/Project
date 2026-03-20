package game.card.effect;

import game.card.CardPlayContext;
import game.card.CardTarget;
import game.card.ability.Ability;
import game.card.ability.AbilityTrigger;

public interface Effect extends Ability {
    void apply(CardPlayContext ctx, String casterPlayerId, CardTarget target);

    @Override
    default AbilityTrigger trigger() {
        return AbilityTrigger.ON_CARD_PLAY;
    }
}
