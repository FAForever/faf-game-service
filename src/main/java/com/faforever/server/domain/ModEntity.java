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
@Table(name = "mod")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModEntity extends AbstractEntity {

    @Column(name = "display_name")
    private String name;

}
