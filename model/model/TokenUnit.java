package game.model;

/**
 * Explicit model for generated token creatures.
 * This keeps token semantics visible in the domain layer instead of
 * treating every generated unit as a normal creature.
 */
public class TokenUnit extends Unit {
    private final String tokenType;

    public TokenUnit(
            String id,
            String ownerId,
            String name,
            int attack,
            int hp,
            int moveRange,
            int attackRange,
            TilePos position,
            String tokenType
    ) {
        super(id, ownerId, name, attack, hp, moveRange, attackRange, position);
        this.tokenType = tokenType;
    }

    public String getTokenType() {
        return tokenType;
    }
}
