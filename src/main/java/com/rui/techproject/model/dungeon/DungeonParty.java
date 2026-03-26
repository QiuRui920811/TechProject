package com.rui.techproject.model.dungeon;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 副本隊伍 — 玩家可在進入副本前或在大廳中組隊。
 */
public final class DungeonParty {

    private final String partyId;
    private UUID leader;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Set<UUID> invites = ConcurrentHashMap.newKeySet();
    private String targetDungeonId;
    private boolean open;

    public DungeonParty(final String partyId, final UUID leader) {
        this.partyId = partyId;
        this.leader = leader;
        this.members.add(leader);
    }

    public String partyId() { return this.partyId; }

    public UUID leader() { return this.leader; }
    public void setLeader(final UUID leader) { this.leader = leader; }

    public Set<UUID> members() { return Collections.unmodifiableSet(this.members); }
    public int size() { return this.members.size(); }
    public boolean isFull(final int max) { return this.members.size() >= max; }
    public boolean isMember(final UUID uuid) { return this.members.contains(uuid); }

    public void addMember(final UUID uuid) {
        this.members.add(uuid);
        this.invites.remove(uuid);
    }

    public void removeMember(final UUID uuid) {
        this.members.remove(uuid);
        if (uuid.equals(this.leader) && !this.members.isEmpty()) {
            this.leader = this.members.iterator().next();
        }
    }

    public void invite(final UUID uuid) { this.invites.add(uuid); }
    public boolean isInvited(final UUID uuid) { return this.invites.contains(uuid); }
    public void revokeInvite(final UUID uuid) { this.invites.remove(uuid); }

    public String targetDungeonId() { return this.targetDungeonId; }
    public void setTargetDungeonId(final String id) { this.targetDungeonId = id; }

    public boolean isOpen() { return this.open; }
    public void setOpen(final boolean open) { this.open = open; }
}
