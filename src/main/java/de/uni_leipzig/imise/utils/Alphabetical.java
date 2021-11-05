package de.uni_leipzig.imise.utils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.google.common.collect.Lists;

/**
 * Klasse zum alphabetischen Sortieren von Objektlisten in Abhängigkeit von der
 * eingestellten Locale.
 *
 * @author AXS created on 15.08.2007
 */
public class Alphabetical {

    /**
     * Aplhabetical can sort every kind of object using the
     * {@link Object#toString()} method. But if there is a need
     *
     * @author AXS (18.02.2021)
     */
    public interface AlphabeticalSortTarget {

        /**
         * @return a string used to compare 2 objects if the toString() method
         *         of the objects return equal strings (second sort criterium)
         */
        public String getSecondSortString();

    }

    /**
     * Die Locale, dessen Comparator die Vergleiche herangezogen wird. Initial
     * ist das die Locale des Systems.
     */
    private static Locale locale = Locale.getDefault();

    /**
     * Comparator, der für alle Stringvergleiche genommen werden sollte.
     */
    private static Comparator<Object> localizedComparator = null;

    /**
     * Liefert einen <code>Comparator</code> für die vom Benutzer gewählte
     * Locale.
     *
     * @return
     */
    public static final Comparator<Object> getLocalizedComparator() {
        //wenn die Locale zwischenzeitlich geändert wurde -> neu setzen und somit auch wieder den
        //richtigen Comparator holen
        Locale defaultLocale = Locale.getDefault();
        if (locale != defaultLocale) {
            locale = defaultLocale;
            localizedComparator = null;
        }
        if (localizedComparator == null) {
            Collator collator = Collator.getInstance(locale);
            localizedComparator = new ObjectToStringComparator(collator);
        }
        return localizedComparator;
    }

    ////////////////////////////////
    // Der eigentliche Comparator //
    ////////////////////////////////
    /**
     * Ein <code>Comparator</code>, der einen anderen <code>Comparator</code>
     * umschließt und für die in compare(Object, Object) übergebenen Objecte
     * erst toString() aufruft und dann den Vergleich an die compare(Object ,
     * Object)-Methode des umschlossenen <code>Comparators</code> weiterleitet
     * und desses Ergebnis zurückliefert. Der übergebene Comparator, ist immer
     * ein <code>RuleBasedComparator</code>, den man für eine
     * <code>Locale</code> über <code>Collator.getInstance(Locale)</code>
     * abfragen kann.
     */
    private static class ObjectToStringComparator implements Comparator<Object> {

        /**
         * Der <code>Comparator</code> der eigentlich für den Vergleich benutzt
         * wird.
         */
        private Comparator<Object> realComparator = null;

        private static final char[] CHAR_33 = {
                33
        }; // = '!'

        private static final String STIRNG_CHAR_33 = new String(CHAR_33);

        /**
         * Legt einen neuen Comparator an, der in seiner Compare-Methode einfach
         * für die übergebenen Objekte <code>toString()</code> aufruft und dann
         * die Strings über die <code>compare()</code>-Methode des übergebenen
         * Komparators vergleicht.
         *
         * @param stringComparator
         */
        public ObjectToStringComparator(final Comparator<Object> realComparator) {
            this.realComparator = realComparator;
        }

        @Override
        public int compare(final Object arg0, final Object arg1) {
            String s1 = getCleanString(arg0);
            String s2 = getCleanString(arg1);
            int compare = realComparator.compare(s1, s2);
            if (compare == 0) {
                s1 = getSecondCompareString(arg0);
                s2 = getSecondCompareString(arg1);
                compare = realComparator.compare(s1, s2);
            }
            return compare;
        }

        /**
         * @param o
         * @return
         */
        public String getSecondCompareString(Object o) {
            if (o != null && o instanceof AlphabeticalSortTarget) {
                o = ((AlphabeticalSortTarget) o).getSecondSortString();
            }
            return getCleanString(o);
        }

        /**
         * @param o
         * @return
         */
        private String getCleanString(final Object o) {
            String s = String.valueOf(o);
            //Leerzeichen und auch alle anderen Zeichen <32 werden Default-mäßig nach allen anderen Zeichen
            //einsortiert (warum auch immer). Um Listen mit Zahlen der Form [1. a, 1.1. a, 1.2. a] in genau
            //dieser Reiehnfolge sortiert zu bekommen (und nicht [1.1. a, 1.2 a., 1. a]), muss man Leerzeichen
            //durch Ausrufezeichen mit char = 33 ersetzen. Die werden als erstes Zeichen "richtig" einsortiert.
            s = s.replaceAll("[\\s]", STIRNG_CHAR_33);
            return s;
        }

    }

    /**
     * @param locale
     * @return
     */
    public static final Comparator<Object> getComparator(final Locale locale) {
        Collator collator = Collator.getInstance(locale);
        Comparator<Object> localizedComparator = new ObjectToStringComparator(collator);
        return localizedComparator;
    }

    /**
     * Sortiert das Element elementToAdd in die uebergebene breits sortierte
     * ArrayList alphabetisch ein.
     *
     * @param list
     * @param elementToInsert
     */
    public static final <T> void insert(final List<T> list, final T elementToInsert) {
        int insertPosition = getInsertPosition(list, elementToInsert);
        list.add(insertPosition, elementToInsert);
    }

    /**
     * Sortiert die Liste aplhabetisch mit nach den Vorgaben der Systemlocale.
     *
     * @param list Liste, die sortiert werden soll
     */
    public static final <T> void sort(final List<? extends T> list) {
        Comparator<Object> localizedComparator = getLocalizedComparator();
        Collections.sort(list, localizedComparator);
    }

    /**
     * Sortiert die Liste aplhabetisch mit nach den Vorgaben der Systemlocale.
     *
     * @param list Liste, die sortiert werden soll
     */
    public static final void sort(final Object... list) {
        Comparator<Object> localizedComparator = getLocalizedComparator();
        Arrays.sort(list, localizedComparator);
    }

    /**
     * Liefert die Position, an der das übergebene Object in die bereits
     * sortierte Liste eingefügt werden müsste.
     *
     * @param list sortierte Liste, in die das Objekt <code>o</code> eingefügt
     *            werden soll
     * @param o Objekt, das in die sortierte Liste <code>list</code> eingefügt
     *            werden soll
     * @return Position, an der das Objekt <code>o</code> in die sortierte Liste
     *         <code>list</code> eingefügt werden soll
     */
    public static final <T> int getInsertPosition(final List<? extends T> list, final T o) {
        Comparator<Object> localizedComparator = getLocalizedComparator();
        int pos = Collections.binarySearch(list, o, localizedComparator);
        if (pos >= 0) {
            return pos;
        }
        return -pos - 1;
    }

    /**
     * Liefert die Position, an der das übergebene Object in das bereits
     * sortierte Array eingefügt werden müsste.
     *
     * @param array sortiertes Array, in die das Objekt <code>o</code> eingefügt
     *            werden soll
     * @param o Objekt, das in die sortierte Liste <code>list</code> eingefügt
     *            werden soll
     * @return Position, an der das Objekt <code>o</code> in die sortierte Liste
     *         <code>list</code> eingefügt werden soll
     */
    public static final int getInsertPosition(final Object[] array, final Object o) {
        Comparator<Object> localizedComparator = getLocalizedComparator();
        int pos = Arrays.binarySearch(array, o, localizedComparator);
        if (pos >= 0) {
            return pos;
        }
        return -pos - 1;
    }

    /**
     * Liefert das Ergebnis der Funktion <code>binarySerach()</code> von
     * <code>Collections</code> mit dem <code>Comparator</code>, den die
     * System-Locale vorgibt.
     *
     * @param list alphabetisch sortierte Liste, in der die Einfüge-Position des
     *            Objektes <code>o</code> ermittelt werden soll
     * @param o Objekt, dessen Einfüge-Position ermittelt werden soll
     * @return Einfüge-Position des Objektes <code>o</code>
     * @see Collections#binarySearch(java.util.List, java.lang.Object)
     */
    public static final int binarySearch(final List<?> list, final Object o) {
        Comparator<Object> localizedComparator = getLocalizedComparator();
        return Collections.binarySearch(list, o, localizedComparator);
    }

    /**
     * Returns the iterable as alphabetical sorted list.
     *
     * @param <T>
     * @param elements
     * @return
     */
    public static <T> List<T> getSorted(final Iterable<T> elements) {
        ArrayList<T> elementsList = Lists.newArrayList(elements);
        Alphabetical.sort(elementsList);
        return elementsList;
    }

}
