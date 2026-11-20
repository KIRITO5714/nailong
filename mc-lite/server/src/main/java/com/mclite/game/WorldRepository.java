package com.mclite.game;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldRepository extends JpaRepository<WorldEntity, String> {
  List<WorldEntity> findAllByOrderByUpdatedAtDesc();
}
