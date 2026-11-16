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
@Table(name = "leaderboard")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeaderboardEntity extends AbstractEntity {

    @Column(name = "technical_name")
    private String technicalName;

}
