package com.companyos.auth.local;

import com.companyos.security.contract.CredentialHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptCredentialHasher implements CredentialHasher {
    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder(12);

    @Override
    public String hash(String rawCredential) {
        return delegate.encode(rawCredential);
    }

    @Override
    public boolean matches(String rawCredential, String encodedCredential) {
        return encodedCredential != null && delegate.matches(rawCredential, encodedCredential);
    }
}
