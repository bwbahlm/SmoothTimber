package com.syntaxphoenix.spigot.smoothtimber.compatibility.uxmclaims;

import java.lang.reflect.Method;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import com.syntaxphoenix.spigot.smoothtimber.event.AsyncPlayerChopTreeEvent;
import com.syntaxphoenix.spigot.smoothtimber.event.reason.DefaultReason;

/**
 * uxmClaims support, accessed via reflection so SmoothTimber can be built
 * without the uxmClaims API on the classpath (its Maven repository is not
 * always reachable).
 */
public class UxmClaimsChopListener implements Listener {

    private final Method getInstance;
    private final Method claimFacade;
    private final Method toDomainLocation;
    private final Method getByLocationUnsafe;
    private final Method hasPermission;
    private final Object blockBreakPermission;

    public UxmClaimsChopListener() {
        try {
            final ClassLoader loader = getClass().getClassLoader();
            final Class<?> api = Class.forName("com.uxplima.claim.bukkit.api.UxmClaimBukkitAPI", true, loader);
            final Class<?> converter = Class.forName("com.uxplima.claim.bukkit.api.BukkitConverter", true, loader);
            final Class<?> facade = Class.forName("com.uxplima.claim.app.facade.ClaimFacade", true, loader);
            final Class<?> domainLocation = Class.forName("com.uxplima.claim.domain.model.vo.Location", true, loader);
            final Class<?> claim = Class.forName("com.uxplima.claim.domain.model.Claim", true, loader);
            final Class<?> permission = Class.forName("com.uxplima.claim.domain.model.enums.ClaimPermission", true, loader);

            getInstance = api.getMethod("getInstance");
            claimFacade = api.getMethod("claimFacade");
            toDomainLocation = converter.getMethod("toDomainLocation", Location.class);
            getByLocationUnsafe = facade.getMethod("getByLocationUnsafe", domainLocation);
            hasPermission = claim.getMethod("hasPermission", UUID.class, permission);
            blockBreakPermission = permission.getField("BLOCK_BREAK").get(null);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported uxmClaims version", exception);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChopEvent(final AsyncPlayerChopTreeEvent event) {
        try {
            final Object facade = claimFacade.invoke(getInstance.invoke(null));
            final UUID uuid = event.getPlayer().getUniqueId();

            for (final Location location : event.getBlockLocations()) {
                // Convert Bukkit location to valid domain location
                final Object domainLoc = toDomainLocation.invoke(null, location);

                // Find claim at check location
                final Object claim = getByLocationUnsafe.invoke(facade, domainLoc);
                if (claim == null || (boolean) hasPermission.invoke(claim, uuid, blockBreakPermission)) {
                    continue;
                }

                // Deny if neither owner nor member with permission
                event.setCancelled(true);
                event.setReason(DefaultReason.UXMCLAIMS);
                return;
            }
        } catch (final ReflectiveOperationException exception) {
            // Fail safe: if we can't check the claim, don't allow the tree to be cut
            event.setCancelled(true);
            event.setReason(DefaultReason.UXMCLAIMS);
        }
    }
}
