package com.alikuxac.machinemetrics.compat.infohud;

public class InfoHudDetector {

    private static Boolean infoHudPresentCache = null;

    public static boolean isInfoHudPresent() {
        if (infoHudPresentCache == null) {
            infoHudPresentCache = checkClassPresence(
                    // Jade entrypoint & APIs
                    "snownee.jade.Jade",
                    "snownee.jade.api.JadePlugin",
                    "snownee.jade.api.IWailaClientRegistration",
                    // Waila / WTHIT (mcp.mobius.waila)
                    "mcp.mobius.waila.api.IBlockAccessor",
                    "mcp.mobius.waila.Waila",
                    "mcp.mobius.waila.api.IWailaPlugin",
                    // The One Probe
                    "mcjty.theoneprobe.TheOneProbe",
                    "mcjty.theoneprobe.api.ITheOneProbe"
            );
        }
        return infoHudPresentCache;
    }

    private static boolean checkClassPresence(String... classNames) {
        for (String className : classNames) {
            try {
                Class.forName(className, false, InfoHudDetector.class.getClassLoader());
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
