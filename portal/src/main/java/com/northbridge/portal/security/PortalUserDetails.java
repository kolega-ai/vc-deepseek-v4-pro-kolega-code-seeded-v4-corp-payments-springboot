package com.northbridge.portal.security;

import com.northbridge.portal.domain.PortalUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class PortalUserDetails implements UserDetails {

    private final PortalUser user;

    public PortalUserDetails(PortalUser user) {
        this.user = user;
    }

    public PortalUser getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Role names are lowercase API names (company_maker, bank_admin, ...); Spring's
        // hasRole/hasAnyRole checks compare authorities against "ROLE_" + role literally, so the
        // authority must preserve the lowercase form.
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash() == null ? "" : user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
