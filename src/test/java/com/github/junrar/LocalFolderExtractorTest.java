package com.github.junrar;

import com.github.junrar.rarfile.FileHeader;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LocalFolderExtractorTest {
    private static File tempFolder;

    @BeforeAll
    public static void setupFunctionalTests() throws IOException {
        tempFolder = TestCommons.createTempDir();
    }

    @AfterAll
    public static void tearDownFunctionalTests() throws IOException {
        FileUtils.deleteDirectory(tempFolder);
    }

    @Test
    public void rarWithDirectoriesOutsideTarget_ShouldThrowException() throws IOException {
        File file = TestCommons.createTempDir();
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file);

        Archive archive = mock(Archive.class);
        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isFileHeader()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("../../ops");


        File expectedInvalidPath = new File(file.getParentFile().getParentFile(), "ops");

        Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains file with invalid path")
            .containsIgnoringCase(expectedInvalidPath.toString());
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException() throws IOException {
        File file = TestCommons.createTempDir();
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(file);

        FileHeader fileHeader = mock(FileHeader.class);
        when(fileHeader.isDirectory()).thenReturn(true);
        when(fileHeader.isUnicode()).thenReturn(true);
        when(fileHeader.getFileName()).thenReturn("../../ops/");


        File expectedInvalidPath = new File(file.getParentFile().getParentFile(), "ops");
        Throwable thrown = catchThrowable(() -> localFolderExtractor.createDirectory(fileHeader));

        assertThat(thrown).isInstanceOf(IllegalStateException.class);
        assertThat(thrown.getMessage())
            .containsIgnoringCase("Rar contains invalid path")
            .containsIgnoringCase(expectedInvalidPath.toString());
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException2() throws Exception {
        File file = TestCommons.writeResourceToFolder(tempFolder, "parent-dir.rar");
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(tempFolder);

        try (Archive archive = new Archive(file)) {
            FileHeader fileHeader = archive.nextFileHeader();

            File expectedInvalidPath = new File(tempFolder.getParentFile().getParentFile(), "tmp");
            Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(thrown.getMessage())
                .containsIgnoringCase("Rar contains file with invalid path")
                .containsIgnoringCase(expectedInvalidPath.toString());
        }
    }

    @Test
    public void rarWithBackslashTraversal_ShouldNotOverwriteFileOutsideTarget() throws Exception {
        File sandbox = TestCommons.createTempDir();
        try {
            File destination = new File(sandbox, "a" + File.separator + "b");
            assertThat(destination.mkdirs()).isTrue();
            File rar = TestCommons.writeResourceToFolder(sandbox, "parent-dir.rar");
            // parent-dir.rar holds a single entry named "..\..\tmp\existing-file"
            File victim = new File(sandbox, "tmp" + File.separator + "existing-file");
            FileUtils.writeStringToFile(victim, "original", StandardCharsets.UTF_8);

            Throwable thrown = catchThrowable(() -> Junrar.extract(rar, destination));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(thrown.getMessage())
                .containsIgnoringCase("Rar contains file with invalid path")
                .containsIgnoringCase(victim.getCanonicalPath());
            assertThat(FileUtils.readFileToString(victim, StandardCharsets.UTF_8)).isEqualTo("original");
        } finally {
            FileUtils.deleteDirectory(sandbox);
        }
    }

    @Test
    public void rarWithFileOutsideTarget_ShouldThrowException3() throws Exception {
        File file = TestCommons.writeResourceToFolder(tempFolder, "sibling-prefix-traversal.rar");

        File tempDir = new File("/tmp/extract");
        LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(tempDir);

        try (Archive archive = new Archive(file)) {
            FileHeader fileHeader = archive.nextFileHeader();

            String expectedInvalidPath = "/tmp/extract_evil/pwned.txt";
            Throwable thrown = catchThrowable(() -> localFolderExtractor.extract(archive, fileHeader));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(thrown.getMessage())
                .containsIgnoringCase("Rar contains file with invalid path")
                .containsIgnoringCase(expectedInvalidPath);
        }
    }

    @Test
    public void rarWithSiblingPrefixTraversal_ShouldNotWriteFileOutsideTarget() throws Exception {
        File sandbox = TestCommons.createTempDir();
        try {
            File destination = new File(sandbox, "extract");
            assertThat(destination.mkdirs()).isTrue();
            File rar = TestCommons.writeResourceToFolder(sandbox, "sibling-prefix-traversal.rar");
            // sibling-prefix-traversal.rar holds a single entry named "../extract_evil/pwned.txt"
            File escaped = new File(sandbox, "extract_evil" + File.separator + "pwned.txt");

            Throwable thrown = catchThrowable(() -> Junrar.extract(rar, destination));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(thrown.getMessage())
                .containsIgnoringCase("Rar contains file with invalid path")
                .containsIgnoringCase(escaped.getCanonicalPath());
            assertThat(escaped).doesNotExist();
        } finally {
            FileUtils.deleteDirectory(sandbox);
        }
    }

    @Test
    public void rarWithSiblingPrefixDirectory_ShouldThrowException() throws IOException {
        File sandbox = TestCommons.createTempDir();
        try {
            File destination = new File(sandbox, "extract");
            assertThat(destination.mkdirs()).isTrue();
            LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(destination);

            FileHeader fileHeader = mock(FileHeader.class);
            when(fileHeader.isDirectory()).thenReturn(true);
            when(fileHeader.isUnicode()).thenReturn(true);
            when(fileHeader.getFileName()).thenReturn("../extract_evil/");

            File expectedInvalidPath = new File(sandbox, "extract_evil");
            Throwable thrown = catchThrowable(() -> localFolderExtractor.createDirectory(fileHeader));

            assertThat(thrown).isInstanceOf(IllegalStateException.class);
            assertThat(thrown.getMessage())
                .containsIgnoringCase("Rar contains invalid path")
                .containsIgnoringCase(expectedInvalidPath.getCanonicalPath());
        } finally {
            FileUtils.deleteDirectory(sandbox);
        }
    }

    @Test
    public void rarWithEntriesInsideTarget_ShouldBeAccepted() throws Exception {
        File destination = TestCommons.createTempDir();
        try {
            LocalFolderExtractor localFolderExtractor = new LocalFolderExtractor(destination);

            FileHeader dirHeader = mock(FileHeader.class);
            when(dirHeader.isDirectory()).thenReturn(true);
            when(dirHeader.getFileName()).thenReturn("sub/dir");

            File dir = localFolderExtractor.createDirectory(dirHeader);
            assertThat(dir.getCanonicalPath())
                .isEqualTo(new File(destination, "sub" + File.separator + "dir").getCanonicalPath());

            File rar = TestCommons.writeTestRarToFolder(destination);
            File extractFolder = new File(destination, "out");
            assertThat(extractFolder.mkdirs()).isTrue();
            assertThat(Junrar.extract(rar, extractFolder)).isNotEmpty();
        } finally {
            FileUtils.deleteDirectory(destination);
        }
    }
}
