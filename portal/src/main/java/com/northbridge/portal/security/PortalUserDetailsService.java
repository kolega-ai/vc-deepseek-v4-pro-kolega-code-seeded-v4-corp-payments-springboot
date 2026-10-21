package com.northbridge.portal.security;

import com.northbridge.portal.repo.PortalUserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PortalUserDetailsService implements UserDetailsService {

    private final PortalUserRepository repository;

    public PortalUserDetailsService(PortalUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repository.findByEmailIgnoreCase(username)
                .map(PortalUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
    }
}
