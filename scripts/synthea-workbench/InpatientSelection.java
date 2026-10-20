package org.mitre.synthea.engine;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;

/** Workbench selection before export; never changes the generated clinical record. */
public final class InpatientSelection {
  private long attempts;
  private long matched;
  private long sinceMatch;
  private boolean exhausted;

  public boolean enabled() {
    return Config.getAsBoolean("generate.workbench_inpatient", false);
  }

  public static boolean eligible(HealthRecord record, long end, int years, int days) {
    long cutoff = years == 0 ? Long.MIN_VALUE : end - Utilities.convertTime("years", years);
    long duration = Utilities.convertTime("days", days);
    return record.encounters.stream().anyMatch(e -> "inpatient".equalsIgnoreCase(e.type)
        && e.start <= end && e.stop > e.start && e.stop > cutoff
        && Math.min(e.stop, end) - e.start >= duration);
  }

  public boolean matches(Person person, long end) {
    if (!enabled()) return true;
    int years = Config.getAsInteger("exporter.years_of_history", 10);
    int days = Config.getAsInteger("generate.workbench_inpatient_days", 2);
    if (days < 1) throw new IllegalArgumentException("Minimum inpatient duration must be positive");
    if (person.hasMultipleRecords) {
      return person.records.values().stream().anyMatch(r -> eligible(r, end, years, days));
    }
    return eligible(person.record, end, years, days);
  }

  public synchronized boolean exhausted() { return exhausted; }

  public synchronized void report(boolean accepted, int attempt, long limit, int requested) {
    if (!enabled()) return;
    attempts++;
    if (accepted) { matched++; sinceMatch = 0; } else { sinceMatch++; }
    exhausted |= !accepted && attempt >= limit;
    System.out.println("WORKBENCH_SELECTION {\"attempts\":" + attempts
        + ",\"matched\":" + matched + ",\"requested\":" + requested
        + ",\"sinceMatch\":" + sinceMatch + ",\"slotAttempts\":" + attempt
        + ",\"limit\":" + limit + ",\"days\":"
        + Config.getAsInteger("generate.workbench_inpatient_days", 2)
        + ",\"history\":" + Config.getAsInteger("exporter.years_of_history", 10)
        + ",\"exhausted\":" + exhausted + "}");
    System.out.flush();
  }
}
