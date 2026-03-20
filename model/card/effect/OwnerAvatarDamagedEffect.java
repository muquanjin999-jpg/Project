package game.card.effect;

import game.card.CardPlayContext;
import game.card.ability.Ability;
import game.card.ability.AbilityTrigger;
import game.model.Unit;

public interface OwnerAvatarDamagedEffect extends Ability {
    void onOwnerAvatarDamaged(CardPlayContext ctx, String ownerId, Unit unit, int damageDealt);

    @Override
    default AbilityTrigger trigger() {
        return AbilityTrigger.ON_OWNER_AVATAR_DAMAGED;
    }
}
