package io.project.Nikidzawa_bot.store.repositories;

import io.project.Nikidzawa_bot.store.entities.UserEntity;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface UserRepository extends CrudRepository <UserEntity, Long> {
    List<UserEntity> findAllByCityAndIdNotAndIsActiveEquals(String city, Long id, Boolean isActive);
}
