package com.companyos.security.contract;

public interface CredentialHasher {
    String hash(String rawCredential);

    boolean matches(String rawCredential, String encodedCredential);
}
