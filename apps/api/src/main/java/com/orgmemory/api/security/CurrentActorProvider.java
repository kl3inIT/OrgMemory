package com.orgmemory.api.security;

import com.orgmemory.core.organization.CurrentActor;
import org.springframework.security.core.Authentication;

public interface CurrentActorProvider {

    CurrentActor current(Authentication authentication);
}
