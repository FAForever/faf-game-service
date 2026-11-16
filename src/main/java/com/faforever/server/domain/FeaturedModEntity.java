package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "game_featuredMods")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeaturedModEntity extends AbstractEntity {

    @Column(name = "gamemod")
    private String technicalName;

}
