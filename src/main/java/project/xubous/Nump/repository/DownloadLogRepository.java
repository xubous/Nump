package project.xubous.Nump.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import project.xubous.Nump.model.DownloadLog;

@Repository
public interface DownloadLogRepository extends JpaRepository < DownloadLog, Long >
{
    List < DownloadLog > findAllByFileIdOrderByDownloadedAtDesc ( long fileId );
    Optional < DownloadLog > findTopByFileIdOrderByDownloadedAtDesc ( long fileId );
}
