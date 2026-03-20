package game.model;

import java.util.*;

public class DiscardPile<T> {
    private final List<T> cards = new ArrayList<>();

    public void add(T card) {
        cards.add(card);
    }

    public int size() {
        return cards.size();
    }

    public List<T> snapshot() {
        return new ArrayList<>(cards);
    }

    public List<T> drainAll() {
        List<T> out = new ArrayList<>(cards);
        cards.clear();
        return out;
    }

    @Override
    public String toString() {
        return cards.toString();
    }
}
