package project.xubous.Nump.service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class Compactor
{
    public Path compactAndSave(MultipartFile file, String token) throws IOException
    {
        Path uploadDir = Paths.get(System.getProperty("user.dir"), "uploads");

        if (!Files.exists(uploadDir))
        {
            Files.createDirectories(uploadDir);
        }

        String zipName = "arquivo_" + token + ".zip";
        Path zipPath = uploadDir.resolve(zipName);

        System.out.println("SALVANDO EM: " + zipPath.toAbsolutePath());

        try (
            FileOutputStream fos = new FileOutputStream(zipPath.toFile());
            ZipOutputStream zos = new ZipOutputStream(fos);
            InputStream inputStream = file.getInputStream()
        )
        {
            String originalName = file.getOriginalFilename();
            ZipEntry entry = new ZipEntry(originalName != null ? originalName : "arquivo");

            zos.putNextEntry(entry);

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1)
            {
                zos.write(buffer, 0, bytesRead);
            }

            zos.closeEntry();
        }

        return zipPath;
    }

}