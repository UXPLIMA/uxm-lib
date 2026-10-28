package com.uxplima.uxmlib.condition.currency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The currencies of {@code currencies.conf}, in the order the operator gave them.
 *
 * <p>The order matters twice: it is the order a menu shows, and the first entry is what a price with no
 * currency is written in. HOCON does not keep the order a file was written in, so the order is a number
 * in the file and not the shape of it. See {@link CurrencyFile}.
 *
 * <p>A currency whose plugin is missing stays in the list and answers that it is not ready, so an
 * operator who installs that plugin later needs to change nothing.
 */
public final class CurrencyRegistry implements Currencies {

    private final List<Currency> inOrder;
    private final Map<CurrencyId, Currency> byId;

    public CurrencyRegistry(List<Currency> inOrder) {
        Objects.requireNonNull(inOrder, "inOrder");
        if (inOrder.isEmpty()) {
            throw new IllegalArgumentException("a plugin needs at least one currency");
        }
        Map<CurrencyId, Currency> index = new LinkedHashMap<>();
        for (Currency currency : inOrder) {
            Currency clash = index.put(currency.id(), currency);
            if (clash != null) {
                throw new IllegalArgumentException("two currencies share the id " + currency.id());
            }
        }
        this.inOrder = List.copyOf(inOrder);
        this.byId = Map.copyOf(index);
    }

    @Override
    public Optional<Currency> byId(CurrencyId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(byId.get(id)).filter(Currency::isReady);
    }

    @Override
    public List<Currency> ready() {
        List<Currency> usable = new ArrayList<>();
        for (Currency currency : inOrder) {
            if (currency.isReady()) {
                usable.add(currency);
            }
        }
        return List.copyOf(usable);
    }

    @Override
    public Currency preferred() {
        for (Currency currency : inOrder) {
            if (currency.isReady()) {
                return currency;
            }
        }
        // Every currency is a bridge whose plugin is missing. The first of the file is still the answer:
        // the plugin cannot price anything without one, and a caller that gets a currency it cannot use
        // reports that to the console, which is what an operator has to read to fix it.
        return inOrder.get(0);
    }
}
