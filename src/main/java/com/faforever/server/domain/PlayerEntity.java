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
@Table(name = "login")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerEntity extends AbstractEntity {

    @Column(name = "login")
    private String name;
}
