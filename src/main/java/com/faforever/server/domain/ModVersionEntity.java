package com.faforever.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "mod_version")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModVersionEntity extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "mod_id")
    private ModEntity mod;

    @Column(name = "uid")
    private String uid;

    @Column(name = "ranked")
    private boolean ranked;

}
