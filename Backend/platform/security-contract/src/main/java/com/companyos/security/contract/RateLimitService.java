package com.companyos.security.contract;

import java.util.List;

public interface RateLimitService {
    RateLimitDecision consume(List<RateLimitRule> rules);
}
