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
@Table(name = "map_version")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MapVersionEntity extends AbstractEntity {

    @ManyToOne
    @JoinColumn(name = "map_id")
    private MapEntity map;

    @Column(name = "folder_name")
    private String mapFolderName;

    @Column(name = "ranked")
    private boolean ranked;

}
