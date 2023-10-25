package io.project.Nikidzawa_bot.store.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "User_entity")
public class UserEntity {
    @Id
    Long id;

    @Column
    String name;

    @Column
    String gender;

    @Column
    String genderSearch;

    @Column
    int age;

    @Column
    String city;

    @Column
    String photo;

    @Column
    String info;

    @Column
    String state_enum;

    @Column
    Boolean isActive = false;
}
