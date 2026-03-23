package game.system.action;

import game.card.Card;
import game.card.UnitCard;
import game.model.GameState;
import game.model.TilePos;

import java.util.HashSet;
import java.util.Set;

/**
 * Dedicated service for summon placement rules.
 * This keeps summon highlighting/validation logic out of UI event handlers.
 */
public class SummonPlacementService {
    private final ActionValidator validator;

    public SummonPlacementService(ActionValidator validator) {
        this.validator = validator;
    }

    public Set<TilePos> validSummonTiles(GameState<Card> state, String ownerId, UnitCard card) {
        Set<TilePos> out = new HashSet<>();
        int boardW = state.getRules().getBoardWidth();
        int boardH = state.getRules().getBoardHeight();

        for (int x = 0; x < boardW; x++) {
            for (int y = 0; y < boardH; y++) {
                TilePos p = new TilePos(x, y);
                ValidationResult vr = validator.validateSummon(state, ownerId, card, p);
                if (vr.isOk()) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    public ValidationResult validateSummonAt(GameState<Card> state, String ownerId, UnitCard card, TilePos tile) {
        return validator.validateSummon(state, ownerId, card, tile);
    }
}
