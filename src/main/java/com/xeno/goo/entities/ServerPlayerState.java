package com.xeno.goo.entities;

import org.apache.logging.log4j.core.jmx.Server;

public class ServerPlayerState
{
    public static final ServerPlayerState UNKNOWN = new ServerPlayerState(false);
    public final boolean isRightMouseHeld;

    public ServerPlayerState(boolean rightMouseHeld) {
        this.isRightMouseHeld = rightMouseHeld;
    }
}
