package com.companyos.identity.access;

import java.util.Set;

public record InviteUserCommand(String email, String displayName, Set<String> roleKeys) {
}
