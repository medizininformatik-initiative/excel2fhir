package de.uni_leipzig.imise.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.ArrayMatching.arrayContaining;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.testng.annotations.Test;
import org.testng.collections.Lists;

public class AlphabeticalTest {

    @Test
    public void getLocalizedComparatorTest() {
        Comparator<Object> localizedComparator1 = Alphabetical.getLocalizedComparator();
        Comparator<Object> localizedComparator2 = Alphabetical.getLocalizedComparator();
        assertEquals(localizedComparator1, localizedComparator2);
        Locale defaultLocale = Locale.getDefault();
        Locale newLocale = Locale.GERMAN.equals(defaultLocale) ? Locale.ENGLISH : Locale.GERMAN;
        Locale.setDefault(newLocale);
        localizedComparator2 = Alphabetical.getLocalizedComparator();
        assertNotEquals(localizedComparator1, localizedComparator2);
    }

    @Test
    public void insertTest() {
        List<Object> mocks;
        Object mock1 = mock(Object.class);
        Object mock2 = mock(Object.class);
        Object mock3 = mock(Object.class);

        when(mock1.toString()).thenReturn("1. a.");
        when(mock2.toString()).thenReturn("1.1 a.");
        when(mock3.toString()).thenReturn("1.2");

        mocks = Lists.newArrayList(mock1, mock2);
        Alphabetical.insert(mocks, mock3);
        assertThat(mocks, contains(mock1, mock2, mock3));

        mocks = Lists.newArrayList(mock2, mock3);
        Alphabetical.insert(mocks, mock1);
        assertThat(mocks, contains(mock1, mock2, mock3));

        mocks = Lists.newArrayList(mock1, mock3);
        Alphabetical.insert(mocks, mock2);
        assertThat(mocks, contains(mock1, mock2, mock3));

        Object mock21 = mock2;
        mocks = Lists.newArrayList(mock1, mock2, mock3);
        Alphabetical.insert(mocks, mock21);
        assertThat(mocks, contains(mock1, mock2, mock21, mock3));

    }

    @Test
    public void sortTest() {
        //sort(List<?>) and sort(T...) must have the same results -> test in one function
        Object mock1 = mock(Object.class);
        Object mock2 = mock(Object.class);
        Object mock3 = mock(Object.class);
        Object mock4 = mock(Object.class);

        when(mock1.toString()).thenReturn("1.2 a.");
        when(mock2.toString()).thenReturn("1. a.");
        when(mock3.toString()).thenReturn("1.1 a.");
        when(mock4.toString()).thenReturn("1.2");

        Object[] mocksArray = {
                mock1, mock2, mock3, mock4
        };
        List<Object> mocksList = Arrays.asList(mocksArray);
        assertThat(mocksList, contains(mock1, mock2, mock3, mock4));
        assertThat(mocksArray, arrayContaining(mock1, mock2, mock3, mock4));

        Alphabetical.sort(mocksList);
        assertThat(mocksList, contains(mock2, mock3, mock4, mock1));
        Alphabetical.sort(mocksArray);
        assertEquals(mocksList, Arrays.asList(mocksArray));

        Alphabetical.sort(mocksList);
        assertThat(mocksList, contains(mock2, mock3, mock4, mock1));
        Alphabetical.sort(mocksArray);
        assertEquals(mocksList, Arrays.asList(mocksArray));

        when(mock1.toString()).thenReturn("B");
        when(mock2.toString()).thenReturn("A");
        when(mock3.toString()).thenReturn("a");
        when(mock4.toString()).thenReturn("b");

        Alphabetical.sort(mocksList);
        assertThat(mocksList, contains(mock3, mock2, mock4, mock1));
        Alphabetical.sort(mocksArray);
        assertEquals(mocksList, Arrays.asList(mocksArray));
    }

    @Test
    public void getInsertPositionTestObjectObject() {
        Object mock1 = mock(Object.class);
        Object mock2 = mock(Object.class);
        Object mock3 = mock(Object.class);

        when(mock1.toString()).thenReturn("1. a.");
        when(mock2.toString()).thenReturn("1.1 a.");
        when(mock3.toString()).thenReturn("1.2");

        Object[] array = {
                mock1, mock2
        };

        int insertPosition = Alphabetical.getInsertPosition(array, mock3);
        assertEquals(insertPosition, 2);

        Object[] array2 = {
                mock1, mock2, mock3
        };

        insertPosition = Alphabetical.getInsertPosition(array2, mock2);
        assertEquals(insertPosition, 1);

    }

    @Test
    public void binarySearchTest() {
        Object mock1 = mock(Object.class);
        Object mock2 = mock(Object.class);
        Object mock3 = mock(Object.class);
        Object mock4 = mock(Object.class);

        when(mock1.toString()).thenReturn("1. a.");
        when(mock2.toString()).thenReturn("1.1 a.");
        when(mock3.toString()).thenReturn("1.2");

        when(mock4.toString()).thenReturn("1.1 a.");

        //sorted list
        List<Object> mocksList = Lists.newArrayList(mock1, mock2, mock3);
        int index = Alphabetical.binarySearch(mocksList, mock4);
        assertEquals(index, 1);
    }

}
