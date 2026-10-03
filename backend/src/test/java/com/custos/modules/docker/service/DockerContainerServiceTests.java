package com.custos.modules.docker.service;

import com.custos.modules.backup.dto.StorageItemDto;
import com.custos.shared.BadRequestException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DockerContainerServiceTests {

    private static final List<String> GNU_LISTING = List.of(
            "total 20",
            "drwxr-xr-x 2 root root 4096 Oct  3 12:00 assets/",
            "-rw-r--r-- 1 root root  612 Jan 15  2024 index.html",
            "-rw-r--r-- 1 www-data www-data 1048576 Oct  3 12:01 my report.tar.gz",
            "lrwxrwxrwx 1 root root    9 Oct  3 12:00 current -> /srv/app",
            "drwxr-xr-x 3 root root 4096 Oct  3 12:00 .cache/"
    );

    private static final List<String> BUSYBOX_LISTING = List.of(
            "total 8",
            "drwxr-xr-x    2 root     root          4096 Oct  3 12:00 conf.d/",
            "-rw-r--r--    1 root     root          1234 Oct  3 12:00 nginx.conf"
    );

    @Test
    void normalizePath_cleansAndValidates() {
        assertEquals("/", DockerContainerService.normalizePath("/"));
        assertEquals("/var/www/html", DockerContainerService.normalizePath("/var//www/./html/"));
        assertEquals("/srv/app (1)", DockerContainerService.normalizePath("/srv/app (1)"));
    }

    @Test
    void normalizePath_rejectsRelativeTraversalAndControlChars() {
        assertThrows(BadRequestException.class, () -> DockerContainerService.normalizePath("var/www"));
        assertThrows(BadRequestException.class, () -> DockerContainerService.normalizePath("/var/../etc"));
        assertThrows(BadRequestException.class, () -> DockerContainerService.normalizePath("/var/\nwww"));
        assertThrows(BadRequestException.class, () -> DockerContainerService.normalizePath(" "));
        assertThrows(BadRequestException.class, () -> DockerContainerService.normalizePath(null));
    }

    @Test
    void parentOf_walksUpToRoot() {
        assertEquals("/var/www", DockerContainerService.parentOf("/var/www/html"));
        assertEquals("/", DockerContainerService.parentOf("/var"));
        assertEquals("/", DockerContainerService.parentOf("/"));
    }

    @Test
    void parseItems_handlesGnuOutput() {
        List<StorageItemDto> items = DockerContainerService.parseItems("/var/www", GNU_LISTING);

        assertEquals(5, items.size());
        // directories first, then files, each alphabetically
        assertEquals(List.of(".cache", "assets", "current", "index.html", "my report.tar.gz"),
                items.stream().map(StorageItemDto::getName).toList());

        StorageItemDto assets = items.get(1);
        assertTrue(assets.isDirectory());
        assertEquals("/var/www/assets", assets.getPath());

        StorageItemDto report = items.get(4);
        assertFalse(report.isDirectory());
        assertEquals(1048576L, report.getSizeBytes());
        assertEquals("tar.gz", report.getExtension());

        StorageItemDto link = items.get(2);
        assertEquals("current", link.getName(), "symlink target must be stripped from the name");
        assertFalse(link.isDirectory());

        assertNotEquals(Instant.EPOCH, items.get(3).getLastModified());
    }

    @Test
    void parseItems_handlesBusyboxOutputAtRoot() {
        List<StorageItemDto> items = DockerContainerService.parseItems("/", BUSYBOX_LISTING);

        assertEquals(2, items.size());
        assertEquals("/conf.d", items.get(0).getPath());
        assertTrue(items.get(0).isDirectory());
        assertEquals("/nginx.conf", items.get(1).getPath());
        assertEquals(1234L, items.get(1).getSizeBytes());
    }

    @Test
    void isFileListing_detectsPathThatIsAFile() {
        List<String> fileListing = List.of("-rw-r--r-- 1 root root 13 Oct  3 12:00 /etc/hostname");

        assertTrue(DockerContainerService.isFileListing("/etc/hostname", fileListing));
        assertFalse(DockerContainerService.isFileListing("/etc", GNU_LISTING));
        assertFalse(DockerContainerService.isFileListing("/", GNU_LISTING));
    }

    @Test
    void parseTimestamp_acceptsTimeAndYearForms() {
        assertEquals(Instant.parse("2024-01-15T00:00:00Z"), DockerContainerService.parseTimestamp("Jan", "15", "2024"));
        assertEquals(Instant.EPOCH, DockerContainerService.parseTimestamp("Xyz", "15", "2024"));
        assertNotEquals(Instant.EPOCH, DockerContainerService.parseTimestamp("Oct", "3", "12:00"));
    }
}
