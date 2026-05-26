package com.ussdplatform.config;

import com.ussdplatform.model.User;

/**
 * Centralised permission checks for the USSD platform.
 * Use these in controllers instead of hardcoded role checks.
 */
public class RolePermissions {

    // ─── App permissions ──────────────────────────────────────────────────────
    public static boolean canCreateApp(User user) {
        return isAdminOrOwner(user);
    }

    public static boolean canEditApp(User user) {
        return isAdminOrOwner(user); // settings, gateway config
    }

    public static boolean canActivateApp(User user) {
        return isAdminOrOwner(user); // change status DRAFT→ACTIVE
    }

    public static boolean canDeleteApp(User user) {
        return isOwner(user); // only owner can delete
    }

    // ─── Menu permissions ─────────────────────────────────────────────────────
    public static boolean canEditMenus(User user) {
        return true; // all roles can build menus
    }

    // ─── Team permissions ─────────────────────────────────────────────────────
    public static boolean canInviteMembers(User user) {
        return isAdminOrOwner(user);
    }

    public static boolean canManageRoles(User user) {
        return isOwner(user);
    }

    public static boolean canRemoveMembers(User user) {
        return isOwner(user);
    }

    // ─── Billing permissions ─────────────────────────────────────────────────
    public static boolean canAccessBilling(User user) {
        return isOwner(user);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    public static boolean isOwner(User user) {
        return user.getRole() == User.Role.OWNER;
    }

    public static boolean isAdminOrOwner(User user) {
        return user.getRole() == User.Role.OWNER || user.getRole() == User.Role.ADMIN;
    }

    public static boolean isMember(User user) {
        return user.getRole() == User.Role.MEMBER;
    }

    /**
     * Returns a readable description of what each role can do.
     */
    public static String getRoleDescription(User.Role role) {
        return switch (role) {
            case OWNER  -> "Full access — apps, menus, team, billing";
            case ADMIN  -> "Manage apps and menus, invite members";
            case MEMBER -> "View apps, build and edit menus";
        };
    }
}
