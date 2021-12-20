package de.uni_leipzig.life.csv2fhir.utils;

/**
 * @author AXS (27.04.2014)
 */
public enum StringMatchCondition {

    EQUALS {
        @Override
        public boolean matches(final String string, final String compareValue) {
            return string.equals(compareValue);
        }
    },
    STARTS_WITH {
        @Override
        public boolean matches(final String string, final String compareValue) {
            return string.startsWith(compareValue);
        }
    },
    ENDS_WITH {
        @Override
        public boolean matches(final String string, final String compareValue) {
            return string.endsWith(compareValue);
        }
    },
    CONTAINS {
        @Override
        public boolean matches(final String string, final String compareValue) {
            return string.contains(compareValue);
        }
    },
    CONTAINS_ALL {
        @Override
        public boolean matches(final String string, final String compareValue) {
            return string.contains(compareValue);
        }

        @Override
        public boolean matches(final String string, final String... compareValues) {
            for (String compareValue : compareValues) {
                if (!matches(string, compareValue)) {
                    return false;
                }
            }
            return true;
        }
    },
    EQUALS_IGNORE_CASE {
        @Override
        public boolean matches(final String string, final String compareValue) {
            return EQUALS.matches(string.toLowerCase(), compareValue.toLowerCase());
        }
    },
    STARTS_WITH_IGNORE_CASE {
        @Override
        public boolean matches(final String string, final String compareValue) {
            return STARTS_WITH.matches(string.toLowerCase(), compareValue.toLowerCase());
        }
    },
    ENDS_WITH_IGNORE_CASE {
        @Override
        public boolean matches(final String string, final String compareValue) {
            return ENDS_WITH.matches(string.toLowerCase(), compareValue.toLowerCase());
        }
    },
    CONTAINS_IGNORE_CASE {
        @Override
        public boolean matches(final String string, final String compareValue) {
            return CONTAINS.matches(string.toLowerCase(), compareValue.toLowerCase());
        }
    },
    CONTAINS_ALL_IGNORE_CASE {
        @Override
        public boolean matches(final String string, final String compareValue) {
            return CONTAINS_ALL.matches(string.toLowerCase(), compareValue.toLowerCase());
        }

        @Override
        public boolean matches(final String string, final String... compareValues) {
            for (String compareValue : compareValues) {
                if (!matches(string, compareValue)) {
                    return false;
                }
            }
            return true;
        }
    };

    public abstract boolean matches(String string, String compareValue);

    public boolean matches(final String string, final String... compareValues) {
        for (String compareValue : compareValues) {
            if (matches(string, compareValue)) {
                return true;
            }
        }
        return false;
    }

}
