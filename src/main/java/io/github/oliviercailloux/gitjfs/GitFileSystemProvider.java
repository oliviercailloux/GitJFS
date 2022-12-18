package io.github.oliviercailloux.gitjfs;

import io.github.oliviercailloux.gitjfs.impl.GitFileSystemProviderImpl;
import java.io.IOException;
import java.net.URI;
import java.nio.file.CopyOption;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;

public abstract class GitFileSystemProvider extends FileSystemProvider implements IGitFileSystemProvider {

	/**
	 * Obtains the same instance as the one reachable through the
	 * {@link FileSystemProvider#installedProviders() installed providers}. Calling
	 * this method causes the default provider to be initialized (if not already
	 * initialized) and loads any other installed providers as described by the
	 * {@link FileSystems} class.
	 *
	 * @return the instance of this class registered for this scheme.
	 */
	public static GitFileSystemProvider instance() {
		return GitFileSystemProviderImpl.getInstance();
	}

	@Override
	@Deprecated
	public GitFileFileSystem newFileSystem(URI gitFsUri, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		return newFileSystem(gitFsUri);
	}

	@Override
	@Deprecated
	public GitFileFileSystem newFileSystem(Path gitDir, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException {
		return newFileSystemFromGitDir(gitDir);
	}

	@Deprecated
	@Override
	public boolean deleteIfExists(Path path) throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	@Deprecated
	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	@Deprecated
	@Override
	public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs)
			throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	@Deprecated
	@Override
	public void createLink(Path link, Path existing) throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	@Deprecated
	@Override
	public void delete(Path path) throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	@Deprecated
	@Override
	public void copy(Path source, Path target, CopyOption... options) throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	@Deprecated
	@Override
	public void move(Path source, Path target, CopyOption... options) throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}

	@Deprecated
	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
			throws ReadOnlyFileSystemException {
		throw new ReadOnlyFileSystemException();
	}
}
