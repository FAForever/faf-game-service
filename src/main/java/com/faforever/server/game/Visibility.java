package com.faforever.server.game;

import java.util.Collection;
import java.util.Set;

public sealed interface Visibility {

    boolean isVisibleTo(int playerId);

    record Public(Set<Integer> foeIds) implements Visibility {

        public Public(Collection<Integer> foeIds) {
            this(Set.copyOf(foeIds));
        }

        @Override
        public boolean isVisibleTo(int playerId) {
            return !foeIds().contains(playerId);
        }
    }

    record Private(Set<Integer> friendIds) implements Visibility {

        public Private(Collection<Integer> friendIds) {
            this(Set.copyOf(friendIds));
        }

        @Override
        public boolean isVisibleTo(int playerId) {
            return friendIds().contains(playerId);
        }
    }

}
