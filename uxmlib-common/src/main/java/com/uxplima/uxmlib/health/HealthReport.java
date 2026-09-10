package com.uxplima.uxmlib.health;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Every check a plugin ran, in the order it ran them, and the worst answer among them.
 *
 * <p>An empty report is {@link HealthStatus#OK}: a plugin with nothing to check is not a plugin in
 * trouble.
 *
 * @param entries one line per check, in run order
 */
public record HealthReport(List<Entry> entries) {

    /** One check's answer, with the name it answered under. */
    public record Entry(String name, HealthResult result) {

        public Entry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(result, "result");
        }
    }

    public HealthReport {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /** Run every check behind its own fault boundary and collect what they said. */
    public static HealthReport run(List<? extends HealthCheck> checks) {
        Objects.requireNonNull(checks, "checks");
        List<Entry> found = new ArrayList<>(checks.size());
        for (HealthCheck check : checks) {
            found.add(new Entry(check.name(), check.safely()));
        }
        return new HealthReport(found);
    }

    /** The worst thing any check said, or {@link HealthStatus#OK} when nothing did. */
    public HealthStatus overall() {
        HealthStatus worst = HealthStatus.OK;
        for (Entry entry : entries) {
            if (entry.result().status().compareTo(worst) > 0) {
                worst = entry.result().status();
            }
        }
        return worst;
    }

    /** Whether anything is actually broken, which is what an operator reads first. */
    public boolean hasFailure() {
        return overall() == HealthStatus.FAIL;
    }
}
