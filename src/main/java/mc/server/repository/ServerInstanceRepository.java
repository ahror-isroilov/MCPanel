package mc.server.repository;

import mc.server.model.ServerInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServerInstanceRepository extends JpaRepository<ServerInstance, Long> {
    
    @Query("SELECT s.port FROM ServerInstance s WHERE s.port > 0")
    List<Integer> findAllAllocatedPorts();
    
    @Query("SELECT s.rconPort FROM ServerInstance s WHERE s.rconPort > 0")
    List<Integer> findAllAllocatedRconPorts();
}
