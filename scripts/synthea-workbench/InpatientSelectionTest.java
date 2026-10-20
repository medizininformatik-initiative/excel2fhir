package org.mitre.synthea.engine;

import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord.EncounterType;

/** Standalone regression check compiled against the pinned Synthea JAR. */
public final class InpatientSelectionTest {
  private static void check(boolean value) { if (!value) throw new AssertionError(); }
  public static void main(String[] args) {
    long day = Utilities.convertTime("days", 1);
    long end = Utilities.convertTime("years", 60);
    Person p = new Person(17);
    p.attributes.put(Person.BIRTHDATE, 0L);
    org.mitre.synthea.world.agents.PayerManager.loadNoInsurance();
    for (int year = 0; year <= 60; year++) p.coverage.setPlanToNoInsurance(Utilities.convertTime("years", year));
    var e = p.record.encounterStart(end - 3 * day, EncounterType.INPATIENT);
    e.stop = e.start + 2 * day;
    check(InpatientSelection.eligible(p.record, end, 10, 2));
    e.stop--;
    check(!InpatientSelection.eligible(p.record, end, 10, 2));
    e.stop = 0;
    check(!InpatientSelection.eligible(p.record, end, 10, 2));
    e.start = end - Utilities.convertTime("years", 11);
    e.stop = e.start + 3 * day;
    check(!InpatientSelection.eligible(p.record, end, 10, 2));
    check(InpatientSelection.eligible(p.record, end, 0, 2));
    e.stop = end - Utilities.convertTime("years", 10);
    check(!InpatientSelection.eligible(p.record, end, 10, 2));
    e.stop++;
    check(InpatientSelection.eligible(p.record, end, 10, 2));
    e.start = end - day;
    e.stop = end + 3 * day;
    check(!InpatientSelection.eligible(p.record, end, 10, 2));
    e.start = end - 3 * day;
    e.type = "ambulatory";
    check(!InpatientSelection.eligible(p.record, end, 10, 2));
    Config.set("generate.workbench_inpatient", "true");
    InpatientSelection selection = new InpatientSelection();
    selection.report(false, 1, 2, 3);
    check(!selection.exhausted());
    selection.report(true, 2, 2, 3);
    check(!selection.exhausted());
    selection.report(false, 2, 2, 3);
    check(selection.exhausted());
    System.out.println("Inpatient selection boundary checks passed");
  }
}
