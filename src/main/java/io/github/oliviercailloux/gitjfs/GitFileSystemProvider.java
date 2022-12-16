package io.github.oliviercailloux.gitjfs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.CopyOption;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;

public abstract class GitFileSystemProvider extends FileSystemProvider implements IGitFileSystemProvider {

	@Override
	@Deprecated
	public abstract GitFileFileSystem newFileSystem(URI gitFsUri, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException;

	@Override
	@Deprecated
	public abstract GitFileFileSystem newFileSystem(Path gitDir, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException;

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
}
