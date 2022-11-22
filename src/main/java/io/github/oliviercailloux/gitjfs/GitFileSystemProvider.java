package io.github.oliviercailloux.gitjfs;

import io.github.oliviercailloux.gitjfs.impl.GitFileFileSystemImpl;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;

public abstract class GitFileSystemProvider extends FileSystemProvider implements IGitFileSystemProvider {

	/**
	 * Behaves, currently, as if {@link #newFileSystem(URI)} had been called.
	 *
	 * @deprecated This method is there to reflect the {@link FileSystemProvider}
	 *             contract. Because the {@code env} parameter is currently not
	 *             used, it is clearer, and more future-proof, to use
	 *             {@link #newFileSystem(URI)}.
	 */
	@Override
	@Deprecated
	public abstract GitFileFileSystemImpl newFileSystem(URI gitFsUri, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException;

	/**
	 * Behaves, currently, as if {@link #newFileSystemFromGitDir(Path)} had been
	 * called.
	 *
	 * @deprecated This method is there to reflect the {@link FileSystemProvider}
	 *             contract. Because the {@code env} parameter is currently not
	 *             used, it is clearer, and more future-proof, to use
	 *             {@link #newFileSystemFromGitDir(Path)}.
	 */
	@Override
	@Deprecated
	public abstract GitFileFileSystemImpl newFileSystem(Path gitDir, Map<String, ?> env)
			throws FileSystemAlreadyExistsException, UnsupportedOperationException, NoSuchFileException, IOException;

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	@Override
	public abstract void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs)
			throws ReadOnlyFileSystemException;

	/**
	 * Throws {@code ReadOnlyFileSystemException}.
	 */
	@Override
	public abstract boolean deleteIfExists(Path path) throws ReadOnlyFileSystemException;
}
