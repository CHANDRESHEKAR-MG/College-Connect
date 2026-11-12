package com.collegeconnect.security;

import java.util.Locale;

public enum Role {
    ADMIN,
    PROFESSOR,
    STUDENT,
    PARENT;

    public static Role fromDatabaseValue(String value) {
        return Role.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
