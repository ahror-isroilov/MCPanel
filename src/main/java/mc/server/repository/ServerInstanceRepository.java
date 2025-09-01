package mc.server.repository;

import mc.server.model.ServerInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServerInstanceRepository extends JpaRepository<ServerInstance, Long> {
}
