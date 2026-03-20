package game.card;

import game.card.effect.DeathwatchEffect;
import game.card.effect.OnHitEffect;
import game.card.effect.OnSummonEffect;
import game.card.effect.OwnerAvatarDamagedEffect;
import game.card.ability.Ability;
import game.model.TokenUnit;
import game.model.TilePos;
import game.model.Unit;
import game.model.UnitKeyword;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public final class UnitTemplate {
    private final String name;
    private final int attack;
    private final int hp;
    private final int moveRange;
    private final int attackRange;
    private final EnumSet<UnitKeyword> keywords;
    private final boolean token;
    private final String tokenType;

    private final List<OnSummonEffect> onSummonEffects;
    private final List<DeathwatchEffect> deathwatchEffects;
    private final List<OnHitEffect> onHitEffects;
    private final List<OwnerAvatarDamagedEffect> ownerAvatarDamagedEffects;

    // “完整构造器”：兼容 FixedStoryDeckFactory 里 Silverguard Knight 的 new UnitTemplate(...)
    public UnitTemplate(
            String name,
            int attack,
            int hp,
            int moveRange,
            int attackRange,
            EnumSet<UnitKeyword> keywords,
            boolean token,
            String tokenType,
            List<OnSummonEffect> onSummonEffects,
            List<DeathwatchEffect> deathwatchEffects,
            List<OnHitEffect> onHitEffects,
            List<OwnerAvatarDamagedEffect> ownerAvatarDamagedEffects
    ) {
        this.name = name;
        this.attack = attack;
        this.hp = hp;
        this.moveRange = moveRange;
        this.attackRange = attackRange;
        this.keywords = keywords == null ? EnumSet.noneOf(UnitKeyword.class) : keywords.clone();
        this.token = token;
        this.tokenType = tokenType;

        this.onSummonEffects = onSummonEffects == null ? Collections.emptyList() : new ArrayList<>(onSummonEffects);
        this.deathwatchEffects = deathwatchEffects == null ? Collections.emptyList() : new ArrayList<>(deathwatchEffects);
        this.onHitEffects = onHitEffects == null ? Collections.emptyList() : new ArrayList<>(onHitEffects);
        this.ownerAvatarDamagedEffects = ownerAvatarDamagedEffects == null ? Collections.emptyList() : new ArrayList<>(ownerAvatarDamagedEffects);
    }

    // 兼容你原来代码的 simple(...)
    public static UnitTemplate simple(String name, int attack, int hp, int moveRange, int attackRange, EnumSet<UnitKeyword> keywords) {
        return new UnitTemplate(
                name, attack, hp, moveRange, attackRange, keywords, false, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
        );
    }

    public static UnitTemplate token(String name, String tokenType, int attack, int hp, int moveRange, int attackRange, EnumSet<UnitKeyword> keywords) {
        return new UnitTemplate(
                name, attack, hp, moveRange, attackRange, keywords, true, tokenType,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
        );
    }

    // 兼容 FixedStoryDeckFactory 的 withOnSummon(...)
    public static UnitTemplate withOnSummon(
            String name, int attack, int hp, int moveRange, int attackRange,
            EnumSet<UnitKeyword> keywords,
            List<OnSummonEffect> onSummonEffects
    ) {
        return new UnitTemplate(
                name, attack, hp, moveRange, attackRange, keywords, false, null,
                onSummonEffects, Collections.emptyList(), Collections.emptyList(), Collections.emptyList()
        );
    }

    // 兼容 FixedStoryDeckFactory 的 withDeathwatch(...)
    public static UnitTemplate withDeathwatch(
            String name, int attack, int hp, int moveRange, int attackRange,
            EnumSet<UnitKeyword> keywords,
            List<DeathwatchEffect> deathwatchEffects
    ) {
        return new UnitTemplate(
                name, attack, hp, moveRange, attackRange, keywords, false, null,
                Collections.emptyList(), deathwatchEffects, Collections.emptyList(), Collections.emptyList()
        );
    }

    public String getName() { return name; }

    public List<OnSummonEffect> getOnSummonEffects() { return Collections.unmodifiableList(onSummonEffects); }
    public List<DeathwatchEffect> getDeathwatchEffects() { return Collections.unmodifiableList(deathwatchEffects); }
    public List<OnHitEffect> getOnHitEffects() { return Collections.unmodifiableList(onHitEffects); }
    public List<OwnerAvatarDamagedEffect> getOwnerAvatarDamagedEffects() { return Collections.unmodifiableList(ownerAvatarDamagedEffects); }
    public boolean isToken() { return token; }
    public String getTokenType() { return tokenType; }

    public List<Ability> getAbilities() {
        List<Ability> out = new ArrayList<>();
        out.addAll(onSummonEffects);
        out.addAll(deathwatchEffects);
        out.addAll(onHitEffects);
        out.addAll(ownerAvatarDamagedEffects);
        return Collections.unmodifiableList(out);
    }

    public Unit create(String unitId, String ownerId, TilePos pos) {
        Unit unit;
        if (token) {
            unit = new TokenUnit(unitId, ownerId, name, attack, hp, moveRange, attackRange, pos, tokenType);
        } else {
            unit = new Unit(unitId, ownerId, name, attack, hp, moveRange, attackRange, pos);
        }
        for (UnitKeyword kw : keywords) {
            unit.addKeyword(kw);
        }
        return unit;
    }
}
